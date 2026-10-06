package com.mbidesign.terminal

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var store: LocalStore
    private lateinit var vault: Vault
    private lateinit var remote: Remote
    private lateinit var root: LinearLayout
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var roster: ListenerRegistration? = null
    private var liveAttendance: ListenerRegistration? = null
    private var liveCompany: ListenerRegistration? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var statusMessage = "Подготвување…"
    private var screen = "home"
    private var selectedId = ""
    private var pendingId = ""
    private var pendingType = ""
    private var pendingEnroll = false
    private var terminalStatus: TextView? = null
    private var workerHours: TextView? = null
    private var closing = false
    private val bg = Color.rgb(20,24,29)
    private val panel = Color.rgb(36,42,50)
    private val gold = Color.rgb(232,188,76)
    private val steel = Color.rgb(187,197,209)
    private val green = Color.rgb(99,206,150)
    private val red = Color.rgb(244,131,123)
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT).apply { timeZone = Attendance.zone }
    private val date = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).apply { timeZone = Attendance.zone }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat() }
    private fun text(s: String, size: Float = 17f, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD); setPadding(0,dp(6),0,dp(6))
    }
    private fun button(s: String, color: Int = gold, action: () -> Unit) = Button(this).apply {
        text = s; textSize = 17f; isAllCaps = false; minHeight = dp(60); background = shape(color)
        setTextColor(if (color == gold || color == green) bg else Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(10); bottomMargin = dp(4) }
        setPadding(dp(12),dp(8),dp(12),dp(8)); setOnClickListener { action() }
    }
    private fun field(hintText: String, password: Boolean = false, numeric: Boolean = false) = EditText(this).apply {
        hint = hintText; textSize = 18f; setTextColor(Color.WHITE); setHintTextColor(steel)
        background = shape(panel); setPadding(dp(16),dp(14),dp(16),dp(14)); isSingleLine = true
        inputType = when {
            numeric -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else -> InputType.TYPE_CLASS_TEXT
        }
        layoutParams = LinearLayout.LayoutParams(-1,dp(58)).apply { bottomMargin = dp(12) }
    }
    private fun card(s: String, color: Int = steel) = text(s,16f,color).apply { background = shape(panel); setPadding(dp(16),dp(16),dp(16),dp(16)) }
    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        store = LocalStore(this); vault = Vault(this); remote = Remote(this)
        pendingId = savedInstanceState?.getString("pendingId") ?: ""
        pendingType = savedInstanceState?.getString("pendingType") ?: ""
        pendingEnroll = savedInstanceState?.getBoolean("pendingEnroll") ?: false
        try {
            if (remote.configured()) { home(); attachListeners(); requestSync(); Remote.schedule(this) }
            else setup()
        } catch (_: Exception) { fatal("Заштитените податоци не можат да се отворат. Потребна е администраторска проверка.") }
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) main.post {
                    if (!closing && remote.configured()) { attachListeners(); requestSync() }
                }
            }
            override fun onLost(network: Network) { main.post { if (!closing) { statusMessage = "Без интернет • записите се зачувуваат на таблетот"; updateStatus() } } }
        }
        cm.registerDefaultNetworkCallback(networkCallback!!)
        main.post(tick)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (closing) return
            updateStatus()
            if (screen == "worker" && selectedId.isNotEmpty()) updateHours(selectedId)
            if (screen == "settings" && !AdminGate.isOpen()) home()
            main.postDelayed(this,1000)
        }
    }
    private fun base(title: String, subtitle: String = "") {
        val scroll = ScrollView(this).apply { isFillViewport = true; fitsSystemWindows = true; setBackgroundColor(bg) }
        workerHours = null
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(24),dp(20),dp(24))
            layoutParams = android.widget.FrameLayout.LayoutParams(-1,-2)
        }
        scroll.addView(root); setContentView(scroll)
        root.addView(text("MBI  /  ТЕРМИНАЛ",14f,gold,true))
        root.addView(text(title,28f,Color.WHITE,true))
        if (subtitle.isNotEmpty()) root.addView(text(subtitle,16f,steel))
        terminalStatus = text("",14f,steel).apply { setPadding(0,dp(8),0,dp(14)) }
        root.addView(terminalStatus); updateStatus()
    }
    private fun updateStatus() {
        if (!::store.isInitialized) return
        val n = store.pending(true).size
        terminalStatus?.text = "${date.format(Date())}  •  ${time.format(Date())}\n$statusMessage${if (n > 0) "\nЧекаат испраќање / проверка: $n" else ""}"
    }
    private fun setup() {
        screen = "setup"; base("Поставување на таблет", "Поставувањето го прави MBI администратор со интернет.")
        root.addView(card("Најави се со твојот постоен MBI акаунт. Потоа избери посебен код за поставките на овој таблет."))
        val user = field("MBI администратор • корисничко име")
        val pass = field("MBI лозинка",true)
        val label = field("Име на работното место").apply { setText("MBI • работно место") }
        val pin = field("Нов администраторски код • 6–12 цифри",numeric = true)
        val again = field("Повтори го кодот",numeric = true)
        root.addView(user); root.addView(pass); root.addView(label); root.addView(pin); root.addView(again)
        val state = text("",16f,steel); root.addView(state)
        lateinit var submit: Button
        submit = button("Поврзи со MBI") {
            if (pin.text.toString() != again.text.toString()) { state.text = "Двата кода треба да бидат исти."; return@button }
            if (user.text.isBlank() || pass.text.isBlank()) { state.text = "Внеси MBI корисничко име и лозинка."; return@button }
            submit.isEnabled = false; state.text = "Проверка на администратор и поврзување…"
            remote.pair(user.text.toString(),pass.text.toString(),pin.text.toString(),label.text.toString()) { error ->
                if (closing) return@pair
                submit.isEnabled = true
                if (error != null) state.text = error
                else { pass.text.clear(); pin.text.clear(); again.text.clear(); attachListeners(); requestSync(); settingsPage() }
            }
        }
        root.addView(submit)
        root.addView(text("Вработените и часовите остануваат во постојната MBI база. За терминалот се создава посебен пристап; администраторската лозинка не се чува.",15f,steel))
    }
    private fun home() {
        screen = "home"; selectedId = ""
        base("Избери го твоето име", "Доаѓање, заминување и пауза со потврда со лице.")
        if (!remote.enabled()) root.addView(card("Терминалот е оневозможен. Потребен е администратор.",red))
        val workers = store.workers()
        if (workers.isEmpty()) root.addView(card("Нема преземени вработени. Поврзи интернет и провери го MBI пристапот."))
        val grid = GridLayout(this).apply { columnCount = if (resources.configuration.screenWidthDp >= 720) 3 else if (resources.configuration.screenWidthDp >= 500) 2 else 1 }
        val day = Attendance.dayStart(System.currentTimeMillis())
        for ((index,w) in workers.withIndex()) {
            val state = Attendance.state(store.events(w.id,day))
            val status = when (state) { ShiftState.OUT -> "Надвор од работа"; ShiftState.WORKING -> "На работа"; ShiftState.PAUSED -> "На пауза" }
            val b = button("${w.name}\n$status${if (!vault.hasFace(w)) " • нерегистрирано лице" else ""}",panel) { workerPage(w.id) }
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.layoutParams = GridLayout.LayoutParams(GridLayout.spec(index / grid.columnCount),GridLayout.spec(index % grid.columnCount,1f)).apply {
                width = 0; height = dp(105); setMargins(dp(4),dp(4),dp(4),dp(4))
            }
            grid.addView(b)
        }
        root.addView(grid)
        root.addView(button("Поставки • администратор",panel) { askPin { settingsPage() } })
        root.addView(text("MBI Терминал 1.0 • Лицата се проверуваат на овој таблет",14f,steel))
    }
    private fun workerPage(id: String) {
        val w = store.worker(id) ?: return home()
        if (!w.active) { toast("Вработениот е блокиран."); home(); return }
        screen = "worker"; selectedId = id
        base(w.name,"Избери дејство и потврди го твоето лице.")
        val events = store.events(id,Attendance.dayStart(System.currentTimeMillis()))
        val state = Attendance.state(events)
        val duration = Attendance.durations(events,System.currentTimeMillis())
        workerHours = card("Денес: ${Attendance.duration(duration.first)} часа\nПауза: ${Attendance.duration(duration.second)}\nПаузата се брои во вкупното работно време.")
        root.addView(workerHours)
        if (!vault.hasFace(w)) root.addView(card("Лицето не е регистрирано. Администраторот треба прво да ја направи регистрацијата.",gold))
        val available = when (state) {
            ShiftState.OUT -> listOf("work_start")
            ShiftState.WORKING -> listOf("break_start","work_end")
            ShiftState.PAUSED -> listOf("break_end","work_end")
        }
        for (type in available) root.addView(button(Attendance.label(type),if (type == "work_end") panel else gold) { verify(w,type) }.apply {
            isEnabled = vault.hasFace(w) && remote.enabled()
        })
        root.addView(button("Назад кон имињата",panel) { home() })
    }
    private fun verify(w: Worker, type: String) {
        if (!remote.autoTime()) { toast("Вклучи автоматски датум и време на таблетот."); return }
        try { remote.now() } catch (e: Exception) { toast(e.message ?: "Провери го времето."); return }
        pendingId = w.id; pendingType = type; pendingEnroll = false
        startActivityForResult(Intent(this,FaceCheckActivity::class.java).putExtra("worker",w.id).putExtra("type",type),30)
    }
    private fun updateHours(id: String) {
        val durations = Attendance.durations(store.events(id,Attendance.dayStart(System.currentTimeMillis())),System.currentTimeMillis())
        workerHours?.text = "Денес: ${Attendance.duration(durations.first)} часа\nПауза: ${Attendance.duration(durations.second)}\nПаузата се брои во вкупното работно време."
    }
    private fun enroll(w: Worker) {
        if (!AdminGate.isOpen()) { askPin { enroll(w) }; return }
        AlertDialog.Builder(this).setTitle("Регистрација • ${w.name}")
            .setMessage("Вработениот треба лично да биде пред камерата. Ќе се зачува заштитен шаблон за споредба на лицето на овој таблет. Регистрацијата ја потврдуваш ти.")
            .setNegativeButton("Откажи",null).setPositiveButton("Почни") { _,_ ->
                pendingId = w.id; pendingEnroll = true; pendingType = ""
                startActivityForResult(Intent(this,FaceCheckActivity::class.java).putExtra("worker",w.id).putExtra("enroll",true),30)
            }.show()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode != 30) return
        if (resultCode != Activity.RESULT_OK || data?.getStringExtra("worker") != pendingId) {
            data?.getStringExtra("error")?.let { toast(it) }
            if (pendingEnroll && AdminGate.isOpen()) settingsPage() else home()
            return
        }
        val w = store.worker(pendingId)
        if (w == null || !w.active) { toast("Профилот не е активен."); home(); return }
        if (pendingEnroll) { toast("Лицето е регистрирано за ${w.name}."); settingsPage(); return }
        try {
            check(remote.enabled() && remote.autoTime()) { "Провери го пристапот и автоматското време." }
            val captured = remote.now()
            val e = store.saveVerified(w,pendingType,captured.first,remote.deviceUid(),captured.second)
            confirmation(e)
            requestSync(); Remote.schedule(this)
        } catch (e: Exception) { toast(e.message ?: "Пријавата не може да се зачува."); home() }
        pendingId = ""; pendingType = ""
    }
    private fun confirmation(e: AttendanceEvent) {
        screen = "confirmation"; base("Успешно зачувано",e.name)
        root.addView(text("✓ ${Attendance.label(e.type)}",24f,green,true))
        root.addView(text(time.format(Date(e.time)),38f,Color.WHITE,true))
        root.addView(card(if (remote.online()) "Записот е зачуван на таблетот и се испраќа во MBI." else "Без интернет: записот е зачуван на таблетот. Ќе се испрати кога ќе се врати интернетот."))
        root.addView(button("Следен вработен") { home() })
        main.postDelayed({ if (!closing && screen == "confirmation") home() },3500)
    }
    private fun askPin(allowed: () -> Unit) {
        val input = field("Администраторски код",numeric = true)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(10),dp(20),0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle("Администратор").setView(box).setNegativeButton("Откажи",null)
            .setNeutralButton("Заборавен код") { _,_ -> setup() }.setPositiveButton("Отвори",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val entered = input.text.toString(); input.text.clear()
            executor.execute {
                try {
                    val ok = vault.checkPin(entered)
                    main.post { if (!closing) { if (ok) { AdminGate.open(); dialog.dismiss(); allowed() } else toast("Погрешен код.") } }
                } catch (e: Exception) { main.post { toast(e.message ?: "Проверката не успеа.") } }
            }
        } }
        dialog.show()
    }
    private fun settingsPage() {
        if (!AdminGate.isOpen()) { home(); return }
        screen = "settings"; base("Администратор",vault.get("label") ?: "MBI Терминал")
        root.addView(button("Синхронизирај сега") { requestSync() })
        root.addView(button("Записи што чекаат / проблеми",panel) { pendingPage() })
        root.addView(button("Промени го кодот",panel) { changePin() })
        root.addView(button("Упатство за пристап",panel) { backendHelp() })
        root.addView(button("Повторно одобри го терминалот",panel) {
            if (store.pending(true).isNotEmpty()) toast("Задржуваме ${store.pending(true).size} зачувани записи при повторно поврзување.")
            setup()
        })
        root.addView(text("Регистрација на вработени",22f,Color.WHITE,true))
        for (w in store.workers()) {
            root.addView(button("${w.name} • ${if (vault.hasFace(w)) "обнови лице" else "регистрирај лице"}",panel) {
                if (AdminGate.isOpen()) enroll(w) else askPin { enroll(w) }
            })
        }
        root.addView(button("Затвори администратор") { AdminGate.close(); home() })
    }
    private fun changePin() {
        if (!AdminGate.isOpen()) return
        val p = field("Нов код • 6–12 цифри",numeric = true)
        val again = field("Повтори код",numeric = true)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0); addView(p); addView(again) }
        AlertDialog.Builder(this).setTitle("Нов администраторски код").setView(box).setNegativeButton("Откажи",null)
            .setPositiveButton("Зачувај") { _,_ ->
                if (!AdminGate.isOpen()) return@setPositiveButton
                try {
                    require(p.text.toString() == again.text.toString()) { "Кодовите се различни." }
                    vault.setPin(p.text.toString()); toast("Кодот е сменет.")
                } catch (e: Exception) { toast(e.message ?: "Кодот не може да се зачува.") }
            }.show()
    }
    private fun pendingPage() {
        if (!AdminGate.isOpen()) return home()
        screen = "settings"; base("Записи за испраќање / проверка")
        val pending = store.pending(true)
        if (pending.isEmpty()) root.addView(card("Сите записи се испратени."))
        for (e in pending) {
            root.addView(card("${e.name}\n${date.format(Date(e.time))} ${time.format(Date(e.time))} • ${Attendance.label(e.type)}\n${if (e.status == "conflict") "ПОТРЕБНА Е ПРОВЕРКА" else "ЧЕКА ИСПРАЌАЊЕ"}\n${e.message}",if (e.status == "conflict") red else steel))
            if (e.status == "conflict") root.addView(button("Провери • ${e.name}",panel) {
                if (!AdminGate.isOpen()) return@button
                AlertDialog.Builder(this).setTitle("Провери го записот").setMessage("${e.message}\n\nПровери ја евиденцијата во главната MBI апликација. Повторниот обид повторно ќе го провери редоследот. Обележи дупликат само ако оригиналот веќе постои таму.")
                    .setPositiveButton("Повторно провери") { _,_ -> if (AdminGate.isOpen()) { store.status(e.id,"pending"); requestSync(); pendingPage() } }
                    .setNeutralButton("Ова е дупликат") { _,_ -> if (AdminGate.isOpen()) {
                        store.status(e.id,"duplicate","Администраторот го обележа како дупликат."); pendingPage()
                    } }.setNegativeButton("Откажи",null).show()
            })
        }
        root.addView(button("Назад",panel) { settingsPage() })
    }
    private fun backendHelp() {
        if (!AdminGate.isOpen()) return
        val uid = remote.deviceUid()
        AlertDialog.Builder(this).setTitle("Пристап на MBI Терминал")
            .setMessage("Терминалот користи посебен акаунт, а не твојата администраторска сесија.\n\nАко пишува „Нема серверска дозвола“, потребно е додавање на правилата за терминал во постојните Firebase правила.\n\nТерминал UID:\n$uid\n\nPackage: com.mbidesign.terminal\n\nКамерата бара прво тестирање со вистински вработен, туѓо лице, фотографија и видео. Проверка со обична камера не гарантира заштита од сите измами.")
            .setPositiveButton("Во ред",null).setNeutralButton("Копирај UID") { _,_ ->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("MBI терминал UID",uid))
                toast("UID е копиран.")
            }.show()
    }
    private fun attachListeners() {
        if (roster != null || !remote.configured() || remote.auth.currentUser == null) return
        roster = remote.db.collection("employees").addSnapshotListener(MetadataChanges.INCLUDE) { snapshot,error ->
            if (closing) return@addSnapshotListener
            if (error != null) {
                if (Remote.accessDenied(error)) vault.put("enabled","false")
                statusMessage = Remote.friendly(error); roster?.remove(); roster = null; updateStatus()
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.metadata.isFromCache) {
                store.setWorkers(snapshot.documents.mapNotNull(Remote::workerFrom))
                if (screen == "home") home() else if (screen == "worker") workerPage(selectedId)
            }
        }
        val attendanceFloor = Attendance.dayStart(System.currentTimeMillis()) - 86400000
        liveAttendance = remote.db.collection("attendance").whereGreaterThanOrEqualTo("timestamp",Timestamp(Date(attendanceFloor)))
            .addSnapshotListener { snapshot,_ ->
                if (!closing && snapshot != null && !snapshot.metadata.isFromCache) {
                    store.replaceRemoteWindow(attendanceFloor,snapshot.documents.mapNotNull(Remote::eventFrom))
                    if (screen == "home") home() else if (screen == "worker") workerPage(selectedId)
                }
            }
        liveCompany = remote.db.collection("settings").document("company").addSnapshotListener { document,_ ->
            if (!closing && document != null && !document.metadata.isFromCache) {
                val devices = document.get("terminalDevices") as? Map<*, *>
                val grant = devices?.get(remote.deviceUid()) as? Map<*, *>
                vault.put("enabled",if (grant?.get("active") == true && grant?.get("ownerUid") == vault.get("ownerUid")) "true" else "false")
                if (!remote.enabled()) { statusMessage = "Терминалот е оневозможен од администраторот"; if (screen == "home") home() }
            }
        }
    }
    private fun requestSync() {
        if (closing || !remote.configured()) return
        statusMessage = if (remote.online()) "Проверка и синхронизација…" else "Без интернет • записите се зачувуваат на таблетот"
        updateStatus()
        executor.execute {
            val message = remote.syncOnce()
            main.post { if (!closing) {
                statusMessage = message; updateStatus()
                if (message.startsWith("Поврзано") || message.startsWith("Има записи")) attachListeners()
                if (screen == "home") home()
            } }
        }
    }
    private fun fatal(message: String) { screen = "fatal"; base("Потребна е проверка"); root.addView(card(message,red)) }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pendingId",pendingId); outState.putString("pendingType",pendingType); outState.putBoolean("pendingEnroll",pendingEnroll)
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { AdminGate.close(); if (remote.configured()) home() else super.onBackPressed() }
    override fun onDestroy() {
        closing = true; main.removeCallbacksAndMessages(null); roster?.remove(); liveAttendance?.remove(); liveCompany?.remove()
        networkCallback?.let { (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) }
        executor.shutdown(); super.onDestroy()
    }
}
