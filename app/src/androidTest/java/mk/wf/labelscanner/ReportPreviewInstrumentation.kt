package mk.wf.labelscanner

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Export isolated fictional fixtures with the real Android PDF renderer, without signing in. */
class ReportPreviewInstrumentation : Instrumentation() {
    private var testArguments: Bundle? = null
    override fun onCreate(arguments: Bundle?) { testArguments = arguments; super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        try {
            val directory = File(targetContext.getExternalFilesDir(null), "report-previews").apply { mkdirs() }
            File(directory, "CHECKS_PASSED.txt").delete()
            initializeIsolatedFirestore()
            val hour = 3600000L
            val half = hour / 2
            val people = listOf("petar" to "Петар Стојановски", "ana" to "Ана Трајковска")
            val days = listOf(
                AttendanceReportDay("petar", people[0].second, "20261005", "05.10.2026", "понеделник", 8 * hour, half, 8 * hour, status = "Исполнето"),
                AttendanceReportDay("ana", people[1].second, "20261005", "05.10.2026", "понеделник", 8 * hour, half, 8 * hour, status = "Исполнето"),
                AttendanceReportDay("petar", people[0].second, "20261006", "06.10.2026", "вторник", 9 * hour + half, half, 8 * hour, hour + half, status = "Исполнето + прекувремено"),
                AttendanceReportDay("ana", people[1].second, "20261006", "06.10.2026", "вторник", 0, regular = 8 * hour, covered = 8 * hour, status = "Одобрен годишен одмор"),
                AttendanceReportDay("petar", people[0].second, "20261007", "07.10.2026", "среда", 8 * hour, half, 8 * hour, status = "Исполнето"),
                AttendanceReportDay("ana", people[1].second, "20261007", "07.10.2026", "среда", 7 * hour + half, half, 7 * hour + half, status = "Недостига 0ч 30м до 8ч")
            )
            val timedDays = days.map { day ->
                if (day.worked == 0L) day else {
                    val start = LocalDate.parse(day.dateKey, DateTimeFormatter.BASIC_ISO_DATE).atTime(8, 3, 17)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    day.copy(arrivals = listOf(start), departures = listOf(start + day.worked))
                }
            }
            val tables = AttendanceReportTables.build(people, timedDays)
            check(tables.daily[1][3] == "08:03:17")
            check(tables.daily[1][4] == "16:03:17")
            check(tables.summary.last()[2] == "41ч 0м")
            val weekly = listOf(
                listOf("Вработен", "Недела", "Работено", "Одмор/оправдано", "Редовен фонд", "Прекувремено", "Недостига"),
                listOf(people[0].second, "05.10.2026 - 11.10.2026", "25ч 30м", "0ч 0м", "24ч 0м", "1ч 30м", "16ч 0м"),
                listOf(people[1].second, "05.10.2026 - 11.10.2026", "15ч 30м", "8ч 0м", "23ч 30м", "0ч 0м", "16ч 30м")
            )
            val sections = listOf(
                ReportSection("Vkupno", "ВКУПНО ПО ВРАБОТЕН", tables.summary),
                ReportSection("Dnevno", "ДНЕВНА ЕВИДЕНЦИЈА - ПО ДАТУМ", tables.daily),
                ReportSection("Nedelno", "НЕДЕЛЕН ПРЕСЕК", weekly)
            )
            File(directory, "MBI_Izvestaj_Primer.pdf").outputStream().use {
                AttendancePdfExporter.write(it, "ПРИМЕР - измислени податоци | Извештај за работно време", "05.10.2026 - 07.10.2026", sections,
                    "Пауза: вклучена во работеното време. Одмор/оправдано: прикажано одделно.")
            }
            File(directory, "MBI_Izvestaj_Primer.xlsx").outputStream().use { AttendanceXlsxExporter.write(it, sections) }
            for (accounting in listOf(false, true)) {
                val calendarPeople = people + ("boris" to "Борис Петров")
                val calendar = AttendanceCalendarTables.build(calendarPeople, days, "20261001", "20261031", accounting, "20261007").single()
                check(calendar.rows[1].last() == if (accounting) "24:00" else "25:30")
                check(calendar.rows[2].last() == "15:30")
                check(calendar.rows[3].last() == "0:00")
                val suffix = if (accounting) "Smetkovodstvo" else "Admin"
                val summary = AttendanceReportTables.build(calendarPeople, days, accounting)
                val pdfSections = calendar.pdfSections() + ReportSection("Vkupno", "ВКУПНО ПО ВРАБОТЕН", summary.summary)
                val note = if (accounting) "До 8ч дневно. Ако се работени помалку часови, се бројат реалните часови." else "Реално работени часови. Одморот се евидентира одделно."
                File(directory, "MBI_Mesecna_Tabela_${suffix}.pdf").outputStream().use {
                    AttendancePdfExporter.write(it, "ПРИМЕР - измислени податоци | Месечна табела", "01.10.2026 - 31.10.2026", pdfSections, note)
                }
                File(directory, "MBI_Mesecna_Tabela_${suffix}.xlsx").outputStream().use {
                    AttendanceXlsxExporter.write(it, listOf(calendar.excelSection(), ReportSection("Vkupno", "ВКУПНО ПО ВРАБОТЕН", summary.summary)))
                }
            }
            val longRows = mutableListOf(listOf("Вработен", "Од", "До", "Причина"))
            longRows.add(listOf("Вработен со многу долго име Стојановски Трајковски Петровски", "01.10.2026", "31.10.2026",
                "Тест на повеќе страници со долг текст. ".repeat(250) + "КРАЈ_НА_ДОЛГИОТ_ТЕКСТ"))
            repeat(100) { longRows.add(listOf("Петар $it", "01.10.2026", "02.10.2026", "Запис број $it")) }
            File(directory, "MBI_Pdf_Layout_Stress.pdf").outputStream().use {
                AttendancePdfExporter.write(it, "ПРИМЕР - проверка на пренос меѓу страници", "2026", listOf(ReportSection("Boluvanje", "БОЛУВАЊА", longRows)), "Измислени податоци за проверка на изгледот.")
            }
            verifyManualAttendanceForm()
            verifyLiveAttendanceStatus()
            File(directory, "CHECKS_PASSED.txt").writeText("Report previews, admin form and live attendance status passed\n")
            result.putString("stream", "Report previews, admin form and live attendance status passed\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "Report preview failed: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun initializeIsolatedFirestore() {
        check(testArguments?.getString("firestoreHost") == "10.0.2.2") { "A local test Firestore emulator is required" }
        FirebaseApp.getApps(targetContext).forEach { it.delete() }
        val options = FirebaseOptions.Builder().setProjectId("demo-mbi-live-status")
            .setApplicationId("1:1234567890:android:0123456789abcdef").setApiKey("fake-emulator-only-api-key").build()
        FirebaseApp.initializeApp(targetContext, options)
        configureTestDatabase(FirebaseFirestore.getInstance())
    }

    private fun configureTestDatabase(db: FirebaseFirestore) {
        check(db.app.options.projectId == "demo-mbi-live-status")
        db.useEmulator("10.0.2.2", 8080)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder(db.firestoreSettings).setPersistenceEnabled(false).build()
    }

    private fun verifyLiveAttendanceStatus() {
        val reader = FirebaseFirestore.getInstance()
        val writerApp = FirebaseApp.initializeApp(targetContext, FirebaseApp.getInstance().options, "fixture-admin-writer")
        val writer = FirebaseFirestore.getInstance(writerApp)
        configureTestDatabase(writer)
        val employeeId = "fixture_live_employee"
        val name = "ПРИМЕР ВРАБОТЕН"
        Tasks.await(writer.collection("employees").document(employeeId).set(mapOf(
            "name" to name, "uid" to "fixture-user-uid", "active" to true, "isAdmin" to false)), 20, TimeUnit.SECONDS)
        val at = System.currentTimeMillis() - 10000L
        fun writeEvent(id: String, type: String, whenAt: Long, admin: Boolean = false, serverNow: Boolean = false) {
            Tasks.await(writer.collection("attendance").document(id).set(mapOf(
                "employeeId" to employeeId, "employeeName" to name, "type" to type,
                "timestamp" to if (serverNow) FieldValue.serverTimestamp() else Timestamp(Date(whenAt)), "source" to if (admin) "admin_manual" else "terminal",
                "enteredBy" to if (admin) "ПРИМЕР АДМИН" else "")), 20, TimeUnit.SECONDS)
        }
        writeEvent("fixture_start", "work_start", at - 60000)
        writeEvent("fixture_break", "break_start", at - 30000)
        val employeeState = AtomicReference(WorkPresenceSnapshot())
        val adminState = AtomicReference(WorkPresenceSnapshot())
        val employeeFeed = WorkPresenceFeed(reader).watch(employeeId) { employeeState.set(it) }
        val adminFeed = WorkPresenceFeed(writer).watch(employeeId) { adminState.set(it) }
        fun awaitCondition(description: String, predicate: () -> Boolean) {
            val until = System.currentTimeMillis() + 30000
            while (System.currentTimeMillis() < until) { if (predicate()) return; Thread.sleep(50) }
            error("Timed out: $description; employee=${employeeState.get()}; admin=${adminState.get()}")
        }
        val activity = startActivitySync(Intent().setClassName(targetContext.packageName, "mk.wf.labelscanner.MbiActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val profile = MbiActivity::class.java.getDeclaredField("profile").apply { isAccessible = true }
        val docId = MbiActivity::class.java.getDeclaredField("docId").apply { isAccessible = true }
        val home = MbiActivity::class.java.getDeclaredMethod("employeeHome").apply { isAccessible = true }
        val dashboard = MbiActivity::class.java.getDeclaredMethod("adminDashboard").apply { isAccessible = true }
        fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
            (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
        fun uiCheck(predicate: (List<View>) -> Boolean): Boolean {
            var passed = false
            runOnMainSync { passed = predicate(views(activity.window.decorView)) }
            return passed
        }
        fun button(nodes: List<View>, label: String) = nodes.filterIsInstance<Button>().first { it.text.toString() == label }
        try {
            waitForIdleSync()
            runOnMainSync {
                profile.set(activity, mapOf("name" to name, "isAdmin" to false, "pinHash" to "fixture-pin"))
                docId.set(activity, employeeId); home.invoke(activity)
            }
            awaitCondition("both independent clients see the active break") {
                employeeState.get().confirmed && adminState.get().confirmed &&
                    employeeState.get().state == WorkPresence.BREAK && adminState.get().state == WorkPresence.BREAK &&
                    uiCheck { nodes -> nodes.filterIsInstance<TextView>().any { it.text.toString().startsWith("НА ПАУЗА") } && button(nodes, "Продолжи со работа").isEnabled }
            }
            // Same attendance shape as an administrative departure; real emulator network/listeners.
            writeEvent("fixture_admin_end", "work_end", at, admin = true, serverNow = true)
            val actualEnd = Tasks.await(writer.collection("attendance").document("fixture_admin_end").get(Source.SERVER), 20, TimeUnit.SECONDS)
                .getTimestamp("timestamp")!!.toDate().time
            awaitCondition("administrator departure closes both clients and actual employee controls") {
                employeeState.get().confirmed && adminState.get().confirmed &&
                    employeeState.get().state == WorkPresence.OFF && adminState.get().state == WorkPresence.OFF &&
                    employeeState.get().event?.at == actualEnd && adminState.get().event?.enteredBy == "ПРИМЕР АДМИН" &&
                    uiCheck { nodes ->
                        nodes.filterIsInstance<TextView>().any { it.text.toString().startsWith("ОДЈАВЕН ОД РАБОТА") && it.text.contains("ПРИМЕР АДМИН") } &&
                            button(nodes, "Дојдов на работа").isEnabled && !button(nodes, "Продолжи со работа").isEnabled &&
                            !button(nodes, "Почеток на пауза").isEnabled && !button(nodes, "Заврши смена").isEnabled
                    }
            }
            writeEvent("fixture_old_correction", "work_start", at - 120000, admin = true)
            writeEvent("fixture_orphan_resume", "break_end", actualEnd + 1)
            awaitCondition("a backdated record and orphan resume do not reopen the closed shift") {
                employeeState.get().confirmed && adminState.get().confirmed && employeeState.get().eventCount == 5 &&
                    adminState.get().eventCount == 5 && employeeState.get().state == WorkPresence.OFF && adminState.get().state == WorkPresence.OFF
            }
            runOnMainSync { profile.set(activity, mapOf("name" to "ПРИМЕР АДМИН", "isAdmin" to true)); dashboard.invoke(activity) }
            awaitCondition("actual administrator dashboard shows the same closed status") {
                uiCheck { nodes -> nodes.filterIsInstance<TextView>().any { it.text.toString().startsWith("ОДЈАВЕН ОД РАБОТА") && it.text.contains("ПРИМЕР АДМИН") } }
            }
        } finally {
            employeeFeed.remove(); adminFeed.remove()
            runOnMainSync { activity.finish() }
        }
    }

    /** Exercise the actual dialog with a fictional in-memory role, without saving to Firebase. */
    private fun verifyManualAttendanceForm() {
        val intent = Intent().setClassName(targetContext.packageName, "mk.wf.labelscanner.MbiActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = startActivitySync(intent)
        waitForIdleSync()
        val profile = MbiActivity::class.java.getDeclaredField("profile").apply { isAccessible = true }
        val form = MbiActivity::class.java.getDeclaredMethod("manualAttendance", String::class.java, String::class.java).apply { isAccessible = true }
        var dialog: AlertDialog? = null
        try {
            runOnMainSync {
                check(form.invoke(activity, "fixture_employee", "ПРИМЕР ВРАБОТЕН") == null)
                profile.set(activity, mapOf("isAdmin" to true, "name" to "ПРИМЕР АДМИН"))
                dialog = form.invoke(activity, "fixture_employee", "ПРИМЕР ВРАБОТЕН") as AlertDialog
            }
            waitForIdleSync()
            fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
            runOnMainSync {
                val shown = checkNotNull(dialog); check(shown.isShowing)
                val views = nodes(shown.window!!.decorView)
                val seconds = views.filterIsInstance<EditText>().first { it.hint?.toString() == "Секунди (0–59)" }
                seconds.setText("60")
                shown.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(views.filterIsInstance<TextView>().any { it.text.toString().contains("Секундите треба да бидат од 0 до 59") })
                profile.set(activity, emptyMap<String, Any>())
                shown.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                check(views.filterIsInstance<TextView>().any { it.text.toString() == "Потребен е администратор." })
            }
        } finally { runOnMainSync { dialog?.dismiss(); activity.finish() } }
    }
}

