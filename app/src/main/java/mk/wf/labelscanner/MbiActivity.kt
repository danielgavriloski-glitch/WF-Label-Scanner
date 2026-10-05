package mk.wf.labelscanner

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class MbiActivity : AppCompatActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var root: LinearLayout
    private var docId: String? = null
    private var profile: Map<String, Any> = emptyMap()

    private val bg = Color.rgb(15, 18, 21)
    private val top = Color.rgb(22, 26, 30)
    private val panel = Color.rgb(31, 36, 41)
    private val panel2 = Color.rgb(40, 46, 52)
    private val border = Color.rgb(67, 75, 82)
    private val gold = Color.rgb(245, 190, 55)
    private val steel = Color.rgb(169, 177, 184)
    private val green = Color.rgb(45, 157, 88)
    private val red = Color.rgb(196, 63, 63)
    private val blue = Color.rgb(62, 115, 178)

    private val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val dtf = SimpleDateFormat("dd.MM.yyyy • HH:mm", Locale.getDefault())

    private var filterFrom: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private var filterTo: Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }
    private var filterEmployeeId: String? = null
    private val filterEmployeeIds = linkedSetOf<String>()
    private var filterEmployeeName: String = "Сите вработени"
    private var lastDailyExportRows: List<List<String>> = emptyList()
    private var lastWeeklyExportRows: List<List<String>> = emptyList()

    data class AttEvent(
        val id: String,
        val employeeId: String,
        val employeeName: String,
        val type: String,
        val time: Date
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            addView(root)
        }
        setContentView(scroll)

        if (auth.currentUser == null) loginScreen() else loadProfile()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 18, stroke: Int = 0, strokeColor: Int = border) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke > 0) setStroke(dp(stroke), strokeColor)
        }

    private fun space(h: Int = 10) = Space(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun text(
        value: String,
        size: Float = 16f,
        color: Int = Color.WHITE,
        bold: Boolean = false,
        gravityValue: Int = Gravity.START
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = gravityValue
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun field(hintText: String, password: Boolean = false): EditText =
        EditText(this).apply {
            hint = hintText
            textSize = 17f
            setTextColor(Color.WHITE)
            setHintTextColor(steel)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            minHeight = dp(58)
            background = shape(panel, 14, 1)
            backgroundTintList = null
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(0, dp(6), 0, dp(6)) }
        }

    private fun button(
        label: String,
        color: Int = gold,
        textColor: Int = Color.BLACK,
        compact: Boolean = false,
        onClick: () -> Unit
    ) = Button(this).apply {
        text = label
        textSize = if (compact) 14f else 17f
        isAllCaps = false
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(textColor)
        backgroundTintList = ColorStateList.valueOf(color)
        minHeight = dp(if (compact) 48 else 62)
        setPadding(dp(16), 0, dp(16), 0)
        layoutParams = LinearLayout.LayoutParams(-1, dp(if (compact) 50 else 64)).apply {
            setMargins(0, dp(6), 0, dp(6))
        }
        setOnClickListener { onClick() }
    }

    private fun card(title: String, subtitle: String = "", accent: Int? = null, onClick: (() -> Unit)? = null): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = shape(panel, 16, 1, accent ?: border)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, dp(6)) }
        }
        box.addView(text(title, 17f, Color.WHITE, true))
        if (subtitle.isNotBlank()) {
            val sub = text(subtitle, 14f, steel)
            sub.setPadding(0, dp(5), 0, 0)
            box.addView(sub)
        }
        if (onClick != null) {
            box.isClickable = true
            box.isFocusable = true
            box.setOnClickListener { onClick() }
        }
        return box
    }

    private fun metric(value: String, label: String, accent: Int): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(14))
            background = shape(panel, 16, 1, accent)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }
        }
        box.addView(text(value, 25f, accent, true, Gravity.CENTER))
        box.addView(text(label, 12f, steel, false, Gravity.CENTER))
        return box
    }

    private fun clearScreen() {
        root.removeAllViews()
    }

    private fun brandHeader(tag: String = "") {
        val logo = text("MBI", 40f, gold, true, Gravity.CENTER)
        root.addView(logo)
        root.addView(text("METAL DESIGN", 16f, Color.WHITE, true, Gravity.CENTER))
        if (tag.isNotBlank()) {
            val t = text(tag, 12f, steel, false, Gravity.CENTER)
            t.setPadding(0, dp(4), 0, dp(12))
            root.addView(t)
        } else root.addView(space(12))
    }

    private fun adminHeader(title: String, subtitle: String = "") {
        clearScreen()
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = shape(top, 16, 1)
        }
        val menu = TextView(this).apply {
            text = "☰"
            textSize = 31f
            setTextColor(gold)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(54))
            setOnClickListener { showAdminMenu(this) }
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        titleBox.addView(text("MBI METAL DESIGN", 17f, Color.WHITE, true))
        titleBox.addView(text("ADMINISTRATOR", 11f, gold, true))
        bar.addView(menu)
        bar.addView(titleBox)
        root.addView(bar)
        root.addView(space(18))
        root.addView(text(title, 27f, Color.WHITE, true))
        if (subtitle.isNotBlank()) {
            val s = text(subtitle, 14f, steel)
            s.setPadding(0, dp(4), 0, dp(12))
            root.addView(s)
        } else root.addView(space(8))
    }

    private fun employeeHeader(title: String, subtitle: String = "") {
        clearScreen()
        brandHeader("ЕВИДЕНЦИЈА НА РАБОТНО ВРЕМЕ")
        root.addView(text(title, 26f, Color.WHITE, true))
        if (subtitle.isNotBlank()) {
            val s = text(subtitle, 14f, steel)
            s.setPadding(0, dp(4), 0, dp(14))
            root.addView(s)
        }
    }

    private fun showAdminMenu(anchor: View) {
        val p = PopupMenu(this, anchor)
        p.menu.add(0, 1, 0, "Почетна")
        p.menu.add(0, 2, 1, "Вработени")
        p.menu.add(0, 3, 2, "Додај вработен")
        p.menu.add(0, 4, 3, "Евиденција")
        p.menu.add(0, 5, 4, "Извештаи")
        p.menu.add(0, 6, 5, "Барања за слободен ден")
        p.menu.add(0, 7, 6, "Отсуства")
        p.menu.add(0, 8, 7, "Поставки")
        p.menu.add(0, 9, 8, "Историја на промени")
        p.menu.add(0, 10, 9, "Одјава")
        p.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> adminDashboard()
                2 -> adminEmployees()
                3 -> adminAddEmployee()
                4 -> adminAttendance(false)
                5 -> adminAttendance(true)
                6 -> adminLeaveRequests()
                7 -> adminAbsences()
                8 -> adminSettings()
                9 -> adminAudit()
                10 -> { auth.signOut(); loginScreen() }
            }
            true
        }
        p.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun loginScreen() {
        clearScreen()
        root.setPadding(dp(24), dp(32), dp(24), dp(28))
        brandHeader("СИГУРНА НАЈАВА")
        root.addView(card(
            "Добредојдовте",
            "Најавете се со вашето MBI корисничко име и лозинка.",
            gold
        ))
        root.addView(space(8))
        val user = field("Корисничко име")
        val pass = field("Лозинка", true)
        root.addView(user)
        root.addView(pass)
        root.addView(space(6))
        root.addView(button("Најави се") {
            val u = user.text.toString().trim()
            val pw = pass.text.toString()
            if (u.isBlank() || pw.isBlank()) {
                toast("Внеси корисничко име и лозинка.")
                return@button
            }
            val email = if (u.contains("@")) u else u.lowercase(Locale.ROOT).replace(" ", "") + "@mbi.local"
            auth.signInWithEmailAndPassword(email, pw)
                .addOnSuccessListener { loadProfile() }
                .addOnFailureListener { e ->
                    toast("Најавата не успеа. Провери го корисничкото име и лозинката. ${e.localizedMessage ?: ""}")
                }
        })
        root.addView(space(10))
        root.addView(text("MBI Metal Design • интерна апликација", 12f, steel, false, Gravity.CENTER))
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return loginScreen()
        db.collection("employees").whereEqualTo("uid", uid).limit(1).get()
            .addOnSuccessListener { q ->
                if (q.isEmpty) {
                    toast("Најавата е успешна, но овој профил не е поврзан со вработен.")
                    auth.signOut()
                    loginScreen()
                } else {
                    val d = q.documents[0]
                    docId = d.id
                    profile = d.data ?: emptyMap()
                    val admin = profile["isAdmin"] == true
                    val active = profile["active"] as? Boolean ?: true
                    if (!admin && !active) {
                        employeeHeader("Профилот е блокиран", "Контактирај администратор.")
                        root.addView(button("Одјава", panel2, Color.WHITE) { auth.signOut(); loginScreen() })
                    } else if (admin) {
                        adminDashboard()
                    } else {
                        employeeHome()
                    }
                }
            }
            .addOnFailureListener { e ->
                toast("Не може да се прочита профилот: ${e.localizedMessage ?: "Firestore грешка"}")
            }
    }

    private fun employeeHome() {
        val name = profile["name"]?.toString() ?: "Вработен"
        employeeHeader("Здраво, $name", "Избери ја активноста што ја започнуваш.")

        val wifiBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = shape(panel, 16, 1, border)
        }
        wifiBox.addView(text("Мрежна проверка", 15f, Color.WHITE, true))
        val wifiStatus = text("Проверувам одобрена Wi‑Fi мрежа…", 14f, steel)
        wifiStatus.setPadding(0, dp(5), 0, 0)
        wifiBox.addView(wifiStatus)
        root.addView(wifiBox)
        updateWifiStatus(wifiStatus)

        root.addView(space(12))
        if (profile["pinHash"] == null) {
            root.addView(button("Постави PIN", blue, Color.WHITE) { setPin() })
            root.addView(space(6))
        }

        root.addView(button("Дојдов на работа", green, Color.WHITE) { employeeAction("work_start") })
        root.addView(button("Почеток на пауза", gold, Color.BLACK) { employeeAction("break_start") })
        root.addView(button("Продолжи со работа", blue, Color.WHITE) { employeeAction("break_end") })
        root.addView(button("Заврши смена", red, Color.WHITE) { employeeAction("work_end") })
        root.addView(button("Побарај слободен ден", panel2, Color.WHITE) { requestLeaveDay() })

        root.addView(space(12))
        val statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = shape(panel, 16, 1, border)
        }
        statusBox.addView(text("Мој статус", 17f, Color.WHITE, true))
        val status = text("Последна активност: проверувам…", 14f, steel)
        status.setPadding(0, dp(5), 0, 0)
        statusBox.addView(status)
        root.addView(statusBox)
        loadMyLastStatus(status)

        root.addView(space(12))
        root.addView(text("Мои барања за слободен ден", 17f, Color.WHITE, true))
        val leaveHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(leaveHolder)
        loadMyLeaveRequests(leaveHolder)

        root.addView(space(8))
        root.addView(button("Одјава", panel2, Color.WHITE) { auth.signOut(); loginScreen() })
    }

    private fun updateWifiStatus(v: TextView) {
        if (!hasWifiPermission()) {
            v.text = "Потребна е дозвола за проверка на Wi‑Fi."
            v.setTextColor(gold)
            return
        }
        val wi = currentWifi()
        if (wi == null) {
            v.text = "Не е откриена активна Wi‑Fi мрежа."
            v.setTextColor(red)
            return
        }
        db.collection("settings").document("company").get().addOnSuccessListener { d ->
            val list = d.get("approvedNetworks") as? List<*> ?: emptyList<Any>()
            val ok = list.any {
                val m = it as? Map<*, *> ?: return@any false
                networkMatches(wi.first, wi.second, m["ssid"]?.toString(), m["bssid"]?.toString())
            }
            if (ok) {
                v.text = "✓ Одобрена мрежа: ${wi.first}"
                v.setTextColor(green)
            } else {
                v.text = "✕ Мрежата не е одобрена: ${wi.first}"
                v.setTextColor(red)
            }
        }
    }

    private fun loadMyLastStatus(v: TextView) {
        val id = docId ?: return
        db.collection("attendance").orderBy("timestamp", Query.Direction.DESCENDING).limit(300).get()
            .addOnSuccessListener { q ->
                val d = q.documents.firstOrNull { it.getString("employeeId") == id }
                if (d == null) {
                    v.text = "Нема претходна евиденција."
                    return@addOnSuccessListener
                }
                val whenText = d.getTimestamp("timestamp")?.toDate()?.let { dtf.format(it) } ?: ""
                v.text = "${label(d.getString("type"))}\n$whenText"
                v.setTextColor(Color.WHITE)
            }
    }

    private fun setPin() {
        val e = field("Нов PIN", true).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this)
            .setTitle("Постави PIN")
            .setView(e)
            .setPositiveButton("Зачувај") { _, _ ->
                val p = e.text.toString()
                if (p.length < 4) {
                    toast("PIN мора да има најмалку 4 цифри.")
                } else {
                    val id = docId ?: return@setPositiveButton
                    db.collection("employees").document(id).update("pinHash", sha(p))
                        .addOnSuccessListener { loadProfile() }
                        .addOnFailureListener { ex -> toast("Не може да се зачува PIN: ${ex.localizedMessage}") }
                }
            }
            .setNegativeButton("Откажи", null)
            .show()
    }

    private fun employeeAction(type: String) {
        ensureApprovedWifi {
            val expected = profile["pinHash"]?.toString()
            if (expected == null) {
                toast("Прво постави PIN.")
                return@ensureApprovedWifi
            }
            val e = field("PIN", true).apply { inputType = InputType.TYPE_CLASS_NUMBER }
            AlertDialog.Builder(this)
                .setTitle("Потврди PIN")
                .setView(e)
                .setPositiveButton("Потврди") { _, _ ->
                    if (sha(e.text.toString()) != expected) toast("Погрешен PIN.")
                    else recordAttendance(type)
                }
                .setNegativeButton("Откажи", null)
                .show()
        }
    }

    private fun ensureApprovedWifi(allowed: () -> Unit) {
        if (!hasWifiPermission()) {
            requestWifiPermission()
            toast("Дозволи пристап до Wi‑Fi и пробај повторно.")
            return
        }
        val wi = currentWifi()
        if (wi == null) {
            toast("Не си поврзан на Wi‑Fi мрежа.")
            return
        }
        db.collection("settings").document("company").get()
            .addOnSuccessListener { d ->
                val list = d.get("approvedNetworks") as? List<*> ?: emptyList<Any>()
                if (list.isEmpty()) {
                    toast("Администраторот сè уште нема поставено одобрена Wi‑Fi мрежа.")
                    return@addOnSuccessListener
                }
                val ok = list.any {
                    val m = it as? Map<*, *> ?: return@any false
                    networkMatches(wi.first, wi.second, m["ssid"]?.toString(), m["bssid"]?.toString())
                }
                if (ok) allowed() else toast("Евиденцијата работи само на одобрена фирмена Wi‑Fi мрежа.")
            }
            .addOnFailureListener { toast("Не може да се провери одобрената Wi‑Fi мрежа.") }
    }

    private fun recordAttendance(type: String) {
        val id = docId ?: return
        val name = profile["name"]?.toString() ?: ""
        val wi = currentWifi()
        val data = hashMapOf<String, Any>(
            "uid" to (auth.currentUser?.uid ?: ""),
            "employeeId" to id,
            "employeeName" to name,
            "type" to type,
            "timestamp" to FieldValue.serverTimestamp()
        )
        if (wi != null) {
            data["wifiSsid"] = wi.first
            data["wifiBssid"] = wi.second
        }
        db.collection("attendance").add(data)
            .addOnSuccessListener {
                toast("Успешно евидентирано: ${label(type)}")
                employeeHome()
            }
            .addOnFailureListener { e -> toast("Не може да се запише: ${e.localizedMessage}") }
    }

    private fun adminDashboard() {
        val name = profile["name"]?.toString() ?: "Администратор"
        adminHeader("Контролен панел", "Здраво, $name. Моментална состојба на фирмата.")

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val atWork = metric("…", "На работа", green)
        val onBreak = metric("…", "На пауза", gold)
        val finished = metric("…", "Завршиле", steel)
        metrics.addView(atWork); metrics.addView(onBreak); metrics.addView(finished)
        root.addView(metrics)
        root.addView(space(14))

        root.addView(button("Додај вработен", gold, Color.BLACK) { adminAddEmployee() })
        root.addView(button("Отвори евиденција", blue, Color.WHITE) { adminAttendance(false) })
        val leaveBtn = button("Барања за слободен ден", gold, Color.BLACK) { adminLeaveRequests() }
        root.addView(leaveBtn)
        db.collection("leaveRequests").whereEqualTo("status", "pending").get().addOnSuccessListener { q ->
            leaveBtn.text = "Барања за слободен ден (${q.size()})"
        }
        root.addView(space(10))
        root.addView(text("Моментална состојба", 20f, Color.WHITE, true))
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(holder)

        db.collection("attendance").orderBy("timestamp", Query.Direction.DESCENDING).limit(500).get()
            .addOnSuccessListener { q ->
                holder.removeAllViews()
                val seen = mutableSetOf<String>()
                var workCount = 0
                var breakCount = 0
                var finishedCount = 0

                q.documents.forEach { d ->
                    val id = d.getString("employeeId") ?: return@forEach
                    if (!seen.add(id)) return@forEach
                    val type = d.getString("type")
                    val status = when (type) {
                        "work_start", "break_end" -> { workCount++; "НА РАБОТА" }
                        "break_start" -> { breakCount++; "НА ПАУЗА" }
                        "work_end" -> { finishedCount++; "ЗАВРШЕНА СМЕНА" }
                        else -> "НЕПОЗНАТО"
                    }
                    val accent = when (type) {
                        "work_start", "break_end" -> green
                        "break_start" -> gold
                        "work_end" -> steel
                        else -> border
                    }
                    val whenText = d.getTimestamp("timestamp")?.toDate()?.let { dtf.format(it) } ?: ""
                    holder.addView(card(d.getString("employeeName") ?: "Вработен", "$status • $whenText", accent))
                }

                setMetricValue(atWork, workCount.toString())
                setMetricValue(onBreak, breakCount.toString())
                setMetricValue(finished, finishedCount.toString())

                if (seen.isEmpty()) holder.addView(card("Нема евиденција", "Сè уште нема запишани активности."))
            }
            .addOnFailureListener { e -> holder.addView(card("Грешка", e.localizedMessage ?: "Не може да се вчита состојбата.", red)) }
    }

    private fun setMetricValue(view: View, value: String) {
        if (view is LinearLayout && view.childCount > 0) (view.getChildAt(0) as? TextView)?.text = value
    }

    private fun adminEmployees() {
        adminHeader("Вработени", "Преглед и управување со профили.")
        root.addView(button("＋ Додај нов вработен", gold, Color.BLACK) { adminAddEmployee() })
        root.addView(space(8))
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(holder)

        db.collection("employees").get()
            .addOnSuccessListener { q ->
                holder.removeAllViews()
                val docs = q.documents.sortedBy { it.getString("name") ?: it.id }
                docs.forEach { d ->
                    val data = d.data ?: emptyMap()
                    val name = data["name"]?.toString() ?: d.id
                    val admin = data["isAdmin"] == true
                    val active = data["active"] as? Boolean ?: true
                    val username = data["username"]?.toString()
                        ?: data["email"]?.toString()?.substringBefore("@")
                        ?: "—"
                    val subtitle = buildString {
                        append("@$username")
                        if (admin) append(" • ADMIN")
                        append(if (active) " • Активен" else " • Блокиран")
                    }
                    holder.addView(card(name, subtitle, if (active) green else red) {
                        adminEmployeeDetail(d.id, data)
                    })
                }
            }
            .addOnFailureListener { e -> holder.addView(card("Грешка", e.localizedMessage ?: "", red)) }
    }

    private fun adminAddEmployee() {
        adminHeader("Додај вработен", "Само администраторот креира пристап.")
        val name = field("Име и презиме")
        val username = field("Корисничко име")
        val password = field("Почетна лозинка", true)
        root.addView(name)
        root.addView(username)
        root.addView(password)
        root.addView(space(8))
        root.addView(button("Креирај вработен", green, Color.WHITE) {
            val n = name.text.toString().trim()
            val u = username.text.toString().trim().lowercase(Locale.ROOT).replace(" ", "")
            val p = password.text.toString()
            if (n.isBlank() || u.length < 3 || p.length < 6) {
                toast("Внеси име, username од најмалку 3 знаци и лозинка од најмалку 6 знаци.")
                return@button
            }
            createEmployeeAuthAndProfile(n, u, p)
        })
        root.addView(button("Назад", panel2, Color.WHITE) { adminEmployees() })
    }

    private fun provisionAuth(): FirebaseAuth {
        val existing = FirebaseApp.getApps(this).firstOrNull { it.name == "MBI_PROVISIONER" }
        val app = existing ?: FirebaseApp.initializeApp(this, FirebaseApp.getInstance().options, "MBI_PROVISIONER")
        return FirebaseAuth.getInstance(app)
    }

    private fun createEmployeeAuthAndProfile(name: String, username: String, password: String) {
        val email = "$username@mbi.local"
        val pAuth = provisionAuth()
        pAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                pAuth.signOut()
                if (uid == null) {
                    toast("Корисникот е креиран, но UID недостига.")
                    return@addOnSuccessListener
                }
                val d = db.collection("employees").document()
                val data = hashMapOf<String, Any>(
                    "uid" to uid,
                    "name" to name,
                    "username" to username,
                    "email" to email,
                    "isAdmin" to false,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                d.set(data)
                    .addOnSuccessListener {
                        audit("Креиран вработен", "$name (@$username)")
                        toast("Вработениот е креиран.")
                        adminEmployees()
                    }
                    .addOnFailureListener { e -> toast("Auth е креиран, но профилот не се зачува: ${e.localizedMessage}") }
            }
            .addOnFailureListener { e -> toast("Не може да се креира корисник: ${e.localizedMessage}") }
    }

    private fun adminEmployeeDetail(id: String, data: Map<String, Any>) {
        val name = data["name"]?.toString() ?: "Вработен"
        val username = data["username"]?.toString() ?: data["email"]?.toString()?.substringBefore("@") ?: "—"
        val active = data["active"] as? Boolean ?: true
        val admin = data["isAdmin"] == true

        adminHeader(name, if (admin) "Администратор" else "Вработен • @$username")
        root.addView(card(
            if (active) "Профил: АКТИВЕН" else "Профил: БЛОКИРАН",
            "UID: ${data["uid"]?.toString() ?: "—"}",
            if (active) green else red
        ))

        if (!admin) {
            root.addView(button("Промени име", blue, Color.WHITE) {
                val e = field("Име и презиме").apply { setText(name) }
                AlertDialog.Builder(this)
                    .setTitle("Промени име")
                    .setView(e)
                    .setPositiveButton("Зачувај") { _, _ ->
                        val nn = e.text.toString().trim()
                        if (nn.isNotBlank()) {
                            db.collection("employees").document(id).update("name", nn)
                                .addOnSuccessListener {
                                    audit("Променето име", "$name → $nn")
                                    adminEmployees()
                                }
                        }
                    }
                    .setNegativeButton("Откажи", null)
                    .show()
            })

            root.addView(button(if (active) "Блокирај профил" else "Активирај профил", if (active) red else green, Color.WHITE) {
                db.collection("employees").document(id).update("active", !active)
                    .addOnSuccessListener {
                        audit(if (active) "Блокиран профил" else "Активиран профил", name)
                        adminEmployees()
                    }
            })

            root.addView(button("Ресетирај PIN", panel2, Color.WHITE) {
                db.collection("employees").document(id).update("pinHash", FieldValue.delete())
                    .addOnSuccessListener {
                        audit("Ресетиран PIN", name)
                        toast("PIN е ресетиран. Вработениот ќе постави нов.")
                    }
            })

            root.addView(button("Нова најава (username + лозинка)", gold, Color.BLACK) {
                resetEmployeeLogin(id, data)
            })

            root.addView(button("Избриши профил", red, Color.WHITE) {
                AlertDialog.Builder(this)
                    .setTitle("Избриши $name?")
                    .setMessage("Историјата на евиденција ќе остане зачувана.")
                    .setPositiveButton("Избриши") { _, _ ->
                        audit("Избришан профил", name)
                        db.collection("employees").document(id).delete()
                            .addOnSuccessListener { adminEmployees() }
                    }
                    .setNegativeButton("Откажи", null)
                    .show()
            })
        }

        root.addView(space(8))
        root.addView(button("Евиденција за овој вработен", panel2, Color.WHITE) {
            filterEmployeeId = id
            filterEmployeeIds.clear()
            filterEmployeeIds.add(id)
            filterEmployeeName = name
            adminAttendance(false)
        })
        root.addView(button("Назад", panel2, Color.WHITE) { adminEmployees() })
    }

    private fun resetEmployeeLogin(id: String, data: Map<String, Any>) {
        val name = data["name"]?.toString() ?: "Вработен"
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val user = field("Ново корисничко име")
        val pass = field("Нова лозинка", true)
        box.addView(user); box.addView(pass)
        AlertDialog.Builder(this)
            .setTitle("Нова најава за $name")
            .setMessage("Старата најава ќе престане да има MBI профил. Користи ново корисничко име.")
            .setView(box)
            .setPositiveButton("Креирај") { _, _ ->
                val u = user.text.toString().trim().lowercase(Locale.ROOT).replace(" ", "")
                val p = pass.text.toString()
                if (u.length < 3 || p.length < 6) {
                    toast("Username најмалку 3 знаци, лозинка најмалку 6.")
                    return@setPositiveButton
                }
                val email = "$u@mbi.local"
                val pAuth = provisionAuth()
                pAuth.createUserWithEmailAndPassword(email, p)
                    .addOnSuccessListener { r ->
                        val newUid = r.user?.uid
                        pAuth.signOut()
                        if (newUid != null) {
                            db.collection("employees").document(id).update(
                                mapOf("uid" to newUid, "username" to u, "email" to email, "active" to true)
                            ).addOnSuccessListener {
                                audit("Променета најава", "$name → @$u")
                                toast("Новата најава е активна.")
                                adminEmployees()
                            }
                        }
                    }
                    .addOnFailureListener { e -> toast("Не може да се креира новата најава: ${e.localizedMessage}") }
            }
            .setNegativeButton("Откажи", null)
            .show()
    }

    private fun requestLeaveDay() {
        val selected = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val dateLabel = text("Датум: ${df.format(selected.time)}", 16f, Color.WHITE, true)
        val reason = field("Причина / забелешка (опционално)")
        val pick = button("Избери датум", panel2, Color.WHITE, true) {
            DatePickerDialog(this, { _, y, m, d ->
                selected.set(Calendar.YEAR, y); selected.set(Calendar.MONTH, m); selected.set(Calendar.DAY_OF_MONTH, d)
                dateLabel.text = "Датум: ${df.format(selected.time)}"
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
        }
        box.addView(dateLabel); box.addView(pick); box.addView(reason)
        AlertDialog.Builder(this)
            .setTitle("Побарај слободен ден")
            .setView(box)
            .setPositiveButton("Испрати") { _, _ ->
                val id = docId ?: return@setPositiveButton
                val data = hashMapOf<String, Any>(
                    "employeeId" to id,
                    "employeeUid" to (auth.currentUser?.uid ?: ""),
                    "employeeName" to (profile["name"]?.toString() ?: "Вработен"),
                    "requestedDate" to Timestamp(selected.time),
                    "reason" to reason.text.toString().trim(),
                    "status" to "pending",
                    "requestedAt" to FieldValue.serverTimestamp()
                )
                db.collection("leaveRequests").add(data)
                    .addOnSuccessListener {
                        toast("Барањето е испратено до администраторот.")
                        employeeHome()
                    }
                    .addOnFailureListener { e -> toast("Не може да се испрати барањето: ${e.localizedMessage}") }
            }
            .setNegativeButton("Откажи", null)
            .show()
    }

    private fun loadMyLeaveRequests(holder: LinearLayout) {
        val id = docId ?: return
        db.collection("leaveRequests").whereEqualTo("employeeId", id).get()
            .addOnSuccessListener { q ->
                holder.removeAllViews()
                val docs = q.documents.sortedByDescending {
                    it.getTimestamp("requestedDate")?.toDate()?.time ?: 0L
                }.take(8)
                if (docs.isEmpty()) {
                    holder.addView(card("Нема барања", "Кога ќе побараш слободен ден, статусот ќе се појави тука."))
                }
                docs.forEach { d ->
                    val status = d.getString("status") ?: "pending"
                    val stateLabel = when (status) {
                        "approved" -> "ОДОБРЕНО"
                        "denied" -> "ОДБИЕНО"
                        else -> "ЧЕКА ОДЛУКА"
                    }
                    val accent = when (status) {
                        "approved" -> green
                        "denied" -> red
                        else -> gold
                    }
                    val date = d.getTimestamp("requestedDate")?.toDate()?.let { df.format(it) } ?: ""
                    val reason = d.getString("reason").orEmpty()
                    holder.addView(card("${date} • ${stateLabel}", reason, accent))
                }
            }
    }

    private fun adminLeaveRequests() {
        adminHeader("Барања за слободен ден", "Одобри или одбиј барање. Одобреното останува во евиденција.")
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(holder)
        db.collection("leaveRequests").get().addOnSuccessListener { q ->
            holder.removeAllViews()
            val docs = q.documents.sortedWith(compareBy(
                { if (it.getString("status") == "pending") 0 else 1 },
                { it.getTimestamp("requestedDate")?.toDate()?.time ?: Long.MAX_VALUE }
            ))
            if (docs.isEmpty()) holder.addView(card("Нема барања", "Сè уште нема поднесени барања."))
            docs.forEach { d ->
                val status = d.getString("status") ?: "pending"
                val date = d.getTimestamp("requestedDate")?.toDate()?.let { df.format(it) } ?: ""
                val name = d.getString("employeeName") ?: "Вработен"
                val reason = d.getString("reason").orEmpty()
                val statusText = when (status) {
                    "approved" -> "ОДОБРЕНО"
                    "denied" -> "ОДБИЕНО"
                    else -> "ЧЕКА ОДЛУКА"
                }
                val accent = when (status) {
                    "approved" -> green
                    "denied" -> red
                    else -> gold
                }
                val subtitle = statusText + if (reason.isNotBlank()) "\n" + reason else ""
                holder.addView(card("${name} • ${date}", subtitle, accent) {
                    if (status == "pending") resolveLeaveRequest(d.id, d.data ?: emptyMap())
                })
            }
        }.addOnFailureListener { e ->
            holder.removeAllViews()
            holder.addView(card("Грешка", e.localizedMessage ?: "Не може да се вчитаат барањата.", red))
        }
    }

    private fun resolveLeaveRequest(requestId: String, data: Map<String, Any>) {
        val name = data["employeeName"]?.toString() ?: "Вработен"
        val date = (data["requestedDate"] as? Timestamp)?.toDate()?.let { df.format(it) } ?: ""
        AlertDialog.Builder(this)
            .setTitle("${name} • ${date}")
            .setMessage(data["reason"]?.toString()?.ifBlank { "Без забелешка." } ?: "Без забелешка.")
            .setPositiveButton("Одобри") { _, _ ->
                db.collection("leaveRequests").document(requestId).update(
                    mapOf(
                        "status" to "approved",
                        "resolvedBy" to (profile["name"]?.toString() ?: "Admin"),
                        "resolvedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {
                    audit("Одобрен слободен ден", "${name} • ${date}")
                    adminLeaveRequests()
                }
            }
            .setNegativeButton("Одбиј") { _, _ ->
                db.collection("leaveRequests").document(requestId).update(
                    mapOf(
                        "status" to "denied",
                        "resolvedBy" to (profile["name"]?.toString() ?: "Admin"),
                        "resolvedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {
                    audit("Одбиен слободен ден", "${name} • ${date}")
                    adminLeaveRequests()
                }
            }
            .setNeutralButton("Откажи", null)
            .show()
    }

    private fun adminAbsences() {
        adminHeader("Отсуства", "Денови без евидентирано доаѓање се означени црвено. Ти ја носиш конечната одлука.")
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(holder)
        val from = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val to = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
        }

        db.collection("employees").get().addOnSuccessListener { eq ->
            val employees = eq.documents.filter { it.getBoolean("isAdmin") != true && (it.getBoolean("active") ?: true) }
            db.collection("attendance")
                .whereGreaterThanOrEqualTo("timestamp", Timestamp(from.time))
                .whereLessThanOrEqualTo("timestamp", Timestamp(to.time))
                .get().addOnSuccessListener { aq ->
                    db.collection("leaveRequests").whereEqualTo("status", "approved").get().addOnSuccessListener { lq ->
                        db.collection("absenceDecisions").get().addOnSuccessListener { dq ->
                            holder.removeAllViews()
                            val attendanceKeys = aq.documents.mapNotNull { d ->
                                val eid = d.getString("employeeId") ?: return@mapNotNull null
                                val date = d.getTimestamp("timestamp")?.toDate() ?: return@mapNotNull null
                                eid + "_" + dayKey(date)
                            }.toSet()
                            val leaveKeys = lq.documents.mapNotNull { d ->
                                val eid = d.getString("employeeId") ?: return@mapNotNull null
                                val date = d.getTimestamp("requestedDate")?.toDate() ?: return@mapNotNull null
                                eid + "_" + dayKey(date)
                            }.toSet()
                            val decisions = dq.documents.associateBy { it.id }
                            var count = 0
                            employees.forEach { e ->
                                val eid = e.id
                                val name = e.getString("name") ?: "Вработен"
                                val cal = from.clone() as Calendar
                                while (!cal.after(to)) {
                                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                                    val workday = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
                                    val key = dayKey(cal.time)
                                    val compound = eid + "_" + key
                                    if (workday && compound !in attendanceKeys && compound !in leaveKeys) {
                                        count++
                                        val decision = decisions[compound]?.getString("decision") ?: "unresolved"
                                        val label = when (decision) {
                                            "annual_leave" -> "ОД ГОДИШЕН ОДМОР"
                                            "make_up" -> "ЌЕ ОДРАБОТИ"
                                            "justified" -> "ОПРАВДАНО ОТСУСТВО"
                                            "unpaid" -> "НЕОПРАВДАНО ОТСУСТВО"
                                            else -> "НЕМА ЕВИДЕНЦИЈА — ЧЕКА ОДЛУКА"
                                        }
                                        val accent = if (decision == "unresolved" || decision == "unpaid") red else gold
                                        holder.addView(card(name, "${df.format(cal.time)} • ${label}", accent) {
                                            decideAbsence(eid, name, key, decision)
                                        })
                                    }
                                    cal.add(Calendar.DAY_OF_MONTH, 1)
                                }
                            }
                            if (count == 0) holder.addView(card("Нема нерешени отсуства", "За тековниот месец нема работен ден без евиденција.", green))
                        }
                    }
                }
        }
    }

    private fun decideAbsence(employeeId: String, employeeName: String, dateKey: String, previous: String) {
        val labels = arrayOf("Од годишен одмор", "Неоправдано отсуство", "Ќе одработи друг ден", "Оправдано отсуство")
        val codes = arrayOf("annual_leave", "unpaid", "make_up", "justified")
        val note = field("Администраторска забелешка")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MbiActivity, android.R.layout.simple_spinner_dropdown_item, labels)
        }
        box.addView(spinner); box.addView(note)
        AlertDialog.Builder(this)
            .setTitle("${employeeName} • ${df.format(dateAtNoon(dateKey))}")
            .setView(box)
            .setPositiveButton("Зачувај") { _, _ ->
                val decision = codes[spinner.selectedItemPosition]
                val ref = db.collection("absenceDecisions").document(employeeId + "_" + dateKey)
                ref.set(mapOf(
                    "employeeId" to employeeId,
                    "employeeName" to employeeName,
                    "dateKey" to dateKey,
                    "date" to Timestamp(dateAtNoon(dateKey)),
                    "decision" to decision,
                    "note" to note.text.toString().trim(),
                    "updatedBy" to (profile["name"]?.toString() ?: "Admin"),
                    "updatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge()).addOnSuccessListener {
                    val delta = when {
                        previous != "annual_leave" && decision == "annual_leave" -> 1L
                        previous == "annual_leave" && decision != "annual_leave" -> -1L
                        else -> 0L
                    }
                    if (delta != 0L) {
                        db.collection("employees").document(employeeId)
                            .update("annualLeaveUsed", FieldValue.increment(delta))
                    }
                    audit("Одлука за отсуство", "${employeeName} • ${df.format(dateAtNoon(dateKey))} • ${labels[spinner.selectedItemPosition]}")
                    adminAbsences()
                }
            }
            .setNegativeButton("Откажи", null)
            .show()
    }

    private fun adminAttendance(reportOnly: Boolean) {
        adminHeader(if (reportOnly) "Извештаи" else "Евиденција", "Филтрирај по вработен и датум.")

        val employeeBtn = button("Вработен: $filterEmployeeName", panel2, Color.WHITE) {
            chooseEmployee { adminAttendance(reportOnly) }
        }
        val fromBtn = button("Од: ${df.format(filterFrom.time)}", panel2, Color.WHITE) {
            pickDate(filterFrom) { adminAttendance(reportOnly) }
        }
        val toBtn = button("До: ${df.format(filterTo.time)}", panel2, Color.WHITE) {
            pickDate(filterTo) {
                filterTo.set(Calendar.HOUR_OF_DAY, 23); filterTo.set(Calendar.MINUTE, 59); filterTo.set(Calendar.SECOND, 59)
                adminAttendance(reportOnly)
            }
        }
        root.addView(employeeBtn)
        val dates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fromBtn.layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(0, dp(4), dp(4), dp(4)) }
        toBtn.layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(4), dp(4), 0, dp(4)) }
        dates.addView(fromBtn); dates.addView(toBtn)
        root.addView(dates)
        root.addView(space(8))

        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(holder)
        loadAttendance(holder, reportOnly)
    }

    private fun chooseEmployee(done: () -> Unit) {
        db.collection("employees").get().addOnSuccessListener { q ->
            val list = q.documents.filter { it.getBoolean("isAdmin") != true }.sortedBy { it.getString("name") ?: it.id }
            val names = list.map { it.getString("name") ?: it.id }.toTypedArray()
            val checked = BooleanArray(list.size) { filterEmployeeIds.contains(list[it].id) }
            AlertDialog.Builder(this)
                .setTitle("Избери вработени")
                .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
                .setPositiveButton("Примени") { _, _ ->
                    filterEmployeeId = null
                    filterEmployeeIds.clear()
                    list.forEachIndexed { index, d -> if (checked[index]) filterEmployeeIds.add(d.id) }
                    filterEmployeeName = when (filterEmployeeIds.size) {
                        0 -> "Сите вработени"
                        1 -> list.firstOrNull { it.id in filterEmployeeIds }?.getString("name") ?: "1 вработен"
                        2 -> list.filter { it.id in filterEmployeeIds }.joinToString(", ") { it.getString("name") ?: it.id }
                        else -> filterEmployeeIds.size.toString() + " вработени"
                    }
                    done()
                }
                .setNeutralButton("Сите") { _, _ ->
                    filterEmployeeId = null
                    filterEmployeeIds.clear()
                    filterEmployeeName = "Сите вработени"
                    done()
                }
                .setNegativeButton("Откажи", null)
                .show()
        }
    }

    private fun pickDate(cal: Calendar, done: () -> Unit) {
        DatePickerDialog(
            this,
            { _, y, m, d ->
                cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
                done()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadAttendance(holder: LinearLayout, reportOnly: Boolean) {
        holder.removeAllViews()
        holder.addView(card("Вчитувам…", "Момент, податоците се синхронизираат.", border))

        db.collection("attendance").orderBy("timestamp", Query.Direction.DESCENDING).limit(1500).get()
            .addOnSuccessListener { q ->
                val events = q.documents.mapNotNull { d ->
                    val t = d.getTimestamp("timestamp")?.toDate() ?: return@mapNotNull null
                    val eid = d.getString("employeeId") ?: ""
                    if (t.before(filterFrom.time) || t.after(filterTo.time)) return@mapNotNull null
                    if (filterEmployeeId != null && eid != filterEmployeeId) return@mapNotNull null
                    AttEvent(
                        d.id,
                        eid,
                        d.getString("employeeName") ?: "Вработен",
                        d.getString("type") ?: "",
                        t
                    )
                }
                holder.removeAllViews()

                if (events.isEmpty()) {
                    holder.addView(card("Нема резултати", "За избраниот период нема евиденција."))
                    return@addOnSuccessListener
                }

                holder.addView(text("Вкупно работно време", 19f, Color.WHITE, true))
                val grouped = events.groupBy { it.employeeId }
                grouped.entries.sortedBy { it.value.firstOrNull()?.employeeName ?: "" }.forEach { (_, ev) ->
                    val total = workedMillis(ev)
                    holder.addView(card(
                        ev.firstOrNull()?.employeeName ?: "Вработен",
                        "Вкупно: ${formatDuration(total)} • ${ev.size} активности",
                        green
                    ))
                }

                if (!reportOnly) {
                    holder.addView(space(12))
                    holder.addView(text("Детална евиденција", 19f, Color.WHITE, true))
                    events.sortedByDescending { it.time }.forEach { ev ->
                        holder.addView(card(
                            ev.employeeName,
                            "${label(ev.type)}\n${dtf.format(ev.time)}",
                            when (ev.type) {
                                "work_start", "break_end" -> green
                                "break_start" -> gold
                                "work_end" -> red
                                else -> border
                            }
                        ) { editAttendance(ev) })
                    }
                    holder.addView(text("Допри на запис за корекција.", 12f, steel))
                }
            }
            .addOnFailureListener { e ->
                holder.removeAllViews()
                holder.addView(card("Грешка", e.localizedMessage ?: "Не може да се вчита евиденција.", red))
            }
    }

    private fun workedMillis(events: List<AttEvent>): Long =
        events.groupBy { dayKey(it.time) }.values.sumOf { dailyWorkedMillis(it) }

    private fun dailyWorkedMillis(events: List<AttEvent>): Long {
        val sorted = events.sortedBy { it.time }
        var shiftStart: Long? = null
        var total = 0L
        for (e in sorted) {
            when (e.type) {
                "work_start" -> if (shiftStart == null) shiftStart = e.time.time
                "work_end" -> {
                    if (shiftStart != null) total += (e.time.time - shiftStart!!).coerceAtLeast(0)
                    shiftStart = null
                }
            }
        }
        if (shiftStart != null && sorted.isNotEmpty() && dayKey(sorted.first().time) == dayKey(Date())) {
            total += (System.currentTimeMillis() - shiftStart!!).coerceAtLeast(0)
        }
        return total
    }

    private fun breakMillis(events: List<AttEvent>): Long {
        val sorted = events.sortedBy { it.time }
        var start: Long? = null
        var total = 0L
        for (e in sorted) {
            when (e.type) {
                "break_start" -> if (start == null) start = e.time.time
                "break_end" -> {
                    if (start != null) total += (e.time.time - start!!).coerceAtLeast(0)
                    start = null
                }
                "work_end" -> {
                    if (start != null) total += (e.time.time - start!!).coerceAtLeast(0)
                    start = null
                }
            }
        }
        return total
    }

    private fun dayKey(date: Date): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(date)

    private fun dateAtNoon(key: String): Date =
        SimpleDateFormat("yyyyMMdd HH:mm", Locale.US).parse("$key 12:00") ?: Date()


    private fun formatDuration(ms: Long): String {
        val min = ms / 60000
        val h = min / 60
        val m = min % 60
        return "${h}ч ${m}м"
    }

    private fun editAttendance(ev: AttEvent) {
        val cal = Calendar.getInstance().apply { time = ev.time }
        val types = arrayOf("Дојде на работа", "Почеток на пауза", "Продолжи со работа", "Заврши смена")
        val codes = arrayOf("work_start", "break_start", "break_end", "work_end")
        var selectedType = codes.indexOf(ev.type).coerceAtLeast(0)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MbiActivity, android.R.layout.simple_spinner_dropdown_item, types)
            setSelection(selectedType)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selectedType = position }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        val dateLabel = text("Датум: ${df.format(cal.time)}", 15f, Color.WHITE, true)
        val timeLabel = text("Време: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)}", 15f, Color.WHITE, true)
        val dateBtn = button("Промени датум", panel2, Color.WHITE, true) {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
                dateLabel.text = "Датум: ${df.format(cal.time)}"
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        val timeBtn = button("Промени време", panel2, Color.WHITE, true) {
            TimePickerDialog(this, { _, h, m ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m)
                timeLabel.text = "Време: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)}"
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
        box.addView(spinner)
        box.addView(dateLabel)
        box.addView(dateBtn)
        box.addView(timeLabel)
        box.addView(timeBtn)

        AlertDialog.Builder(this)
            .setTitle("Корекција на запис")
            .setMessage("${ev.employeeName}\n${dtf.format(ev.time)}")
            .setView(box)
            .setPositiveButton("Зачувај") { _, _ ->
                db.collection("attendance").document(ev.id).update(
                    mapOf(
                        "type" to codes[selectedType],
                        "timestamp" to Timestamp(cal.time),
                        "editedBy" to (profile["name"]?.toString() ?: "Admin"),
                        "editedAt" to FieldValue.serverTimestamp()
                    )
                ).addOnSuccessListener {
                    audit("Корегирана евиденција", "${ev.employeeName}: ${label(ev.type)} → ${label(codes[selectedType])}")
                    adminAttendance(false)
                }.addOnFailureListener { e -> toast("Не може да се зачува: ${e.localizedMessage}") }
            }
            .setNeutralButton("Избриши") { _, _ ->
                audit("Избришана евиденција", "${ev.employeeName}: ${label(ev.type)} ${dtf.format(ev.time)}")
                db.collection("attendance").document(ev.id).delete().addOnSuccessListener { adminAttendance(false) }
            }
            .setNegativeButton("Откажи", null)
            .show()
    }

    private fun adminSettings() {
        adminHeader("Поставки", "Одобрени мрежи и системски поставки.")
        root.addView(text("Одобрена Wi‑Fi мрежа", 20f, Color.WHITE, true))
        root.addView(text(
            "Вработените можат да евидентираат време само на овие мрежи. Администраторот има пристап од секаде.",
            14f, steel
        ))
        root.addView(space(10))

        if (!hasWifiPermission()) {
            root.addView(card("Потребна е Wi‑Fi дозвола", "Дозволи пристап за да ја прочитаме тековната мрежа.", gold))
            root.addView(button("Дозволи Wi‑Fi пристап", gold, Color.BLACK) { requestWifiPermission() })
        } else {
            val wi = currentWifi()
            if (wi != null) {
                root.addView(card("Тековна мрежа: ${wi.first}", "BSSID: ${wi.second}", blue))
                root.addView(button("Одобри ја тековната мрежа", green, Color.WHITE) { approveCurrentWifi() })
            } else {
                root.addView(card("Нема активна Wi‑Fi мрежа", "Поврзи се на фирмената мрежа и пробај повторно.", red))
            }
        }

        root.addView(space(12))
        root.addView(text("Одобрени точки", 18f, Color.WHITE, true))
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(holder)
        loadApprovedNetworks(holder)
    }

    private fun approveCurrentWifi() {
        if (!hasWifiPermission()) {
            requestWifiPermission()
            return
        }
        val wi = currentWifi() ?: return toast("Не е откриена активна Wi‑Fi мрежа.")
        val ref = db.collection("settings").document("company")
        ref.get().addOnSuccessListener { d ->
            val old = (d.get("approvedNetworks") as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()
            val exists = old.any { networkMatches(wi.first, wi.second, it["ssid"]?.toString(), it["bssid"]?.toString()) }
            if (exists) {
                toast("Оваа мрежа веќе е одобрена.")
                return@addOnSuccessListener
            }
            val updated = old.map { mapOf(
                "ssid" to (it["ssid"]?.toString() ?: ""),
                "bssid" to (it["bssid"]?.toString() ?: "")
            ) }.toMutableList()
            updated.add(mapOf("ssid" to wi.first, "bssid" to wi.second))
            ref.set(mapOf("approvedNetworks" to updated, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .addOnSuccessListener {
                    audit("Одобрена Wi‑Fi мрежа", "${wi.first} • ${wi.second}")
                    toast("Мрежата е одобрена.")
                    adminSettings()
                }
        }
    }

    private fun loadApprovedNetworks(holder: LinearLayout) {
        db.collection("settings").document("company").get().addOnSuccessListener { d ->
            holder.removeAllViews()
            val list = d.get("approvedNetworks") as? List<*> ?: emptyList<Any>()
            if (list.isEmpty()) {
                holder.addView(card("Нема одобрени мрежи", "Додека нема мрежа, вработените нема да можат да евидентираат време.", gold))
                return@addOnSuccessListener
            }
            list.forEachIndexed { index, item ->
                val m = item as? Map<*, *> ?: return@forEachIndexed
                val ssid = m["ssid"]?.toString() ?: "Wi‑Fi"
                val bssid = m["bssid"]?.toString() ?: ""
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(10), dp(8), dp(10))
                    background = shape(panel, 14, 1)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(5), 0, dp(5)) }
                }
                val t = text("$ssid\n$bssid", 14f, Color.WHITE, true).apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                val del = Button(this).apply {
                    text = "Избриши"
                    textSize = 12f
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    backgroundTintList = ColorStateList.valueOf(red)
                    layoutParams = LinearLayout.LayoutParams(dp(92), dp(46))
                    setOnClickListener { removeApprovedNetwork(index, ssid) }
                }
                row.addView(t); row.addView(del)
                holder.addView(row)
            }
        }
    }

    private fun removeApprovedNetwork(index: Int, ssid: String) {
        val ref = db.collection("settings").document("company")
        ref.get().addOnSuccessListener { d ->
            val list = (d.get("approvedNetworks") as? List<*>)?.toMutableList() ?: mutableListOf()
            if (index in list.indices) list.removeAt(index)
            ref.set(mapOf("approvedNetworks" to list, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .addOnSuccessListener {
                    audit("Избришана Wi‑Fi мрежа", ssid)
                    adminSettings()
                }
        }
    }

    private fun hasWifiPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= 33)
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        else true
        return fine && nearby
    }

    private fun requestWifiPermission() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        requestPermissions(perms.toTypedArray(), 501)
    }

    @Suppress("DEPRECATION")
    private fun currentWifi(): Pair<String, String>? {
        if (!hasWifiPermission()) return null
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo ?: return null
        val rawSsid = info.ssid ?: return null
        val ssid = rawSsid.removePrefix(""").removeSuffix(""")
        val bssid = info.bssid ?: ""
        if (ssid.isBlank() || ssid == "<unknown ssid>") return null
        return ssid to bssid.lowercase(Locale.ROOT)
    }

    private fun networkMatches(currentSsid: String, currentBssid: String, allowedSsid: String?, allowedBssid: String?): Boolean {
        val ssidOk = !allowedSsid.isNullOrBlank() && currentSsid == allowedSsid
        val bssidOk = !allowedBssid.isNullOrBlank() && currentBssid.equals(allowedBssid, true)
        return bssidOk || ssidOk
    }

    private fun audit(action: String, details: String) {
        val adminName = profile["name"]?.toString() ?: "Admin"
        db.collection("audit").add(
            mapOf(
                "action" to action,
                "details" to details,
                "adminUid" to (auth.currentUser?.uid ?: ""),
                "adminName" to adminName,
                "timestamp" to FieldValue.serverTimestamp()
            )
        )
    }

    private fun adminAudit() {
        adminHeader("Историја на промени", "Кој што сменил во админ панелот.")
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(holder)
        db.collection("audit").orderBy("timestamp", Query.Direction.DESCENDING).limit(250).get()
            .addOnSuccessListener { q ->
                holder.removeAllViews()
                if (q.isEmpty) holder.addView(card("Нема промени", "Сè уште нема админ активности."))
                q.documents.forEach { d ->
                    val whenText = d.getTimestamp("timestamp")?.toDate()?.let { dtf.format(it) } ?: ""
                    holder.addView(card(
                        d.getString("action") ?: "Промена",
                        "${d.getString("details") ?: ""}\n${d.getString("adminName") ?: "Admin"} • $whenText",
                        gold
                    ))
                }
            }
    }

    private fun label(type: String?) = when (type) {
        "work_start" -> "Дојде на работа"
        "break_start" -> "Почеток на пауза"
        "break_end" -> "Продолжи со работа"
        "work_end" -> "Заврши смена"
        else -> type ?: ""
    }

    private fun sha(v: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(v.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
