package com.mbidesign.terminal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.provider.Settings
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class Remote(private val context: Context) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    private val vault = Vault(context)
    private val store = LocalStore(context)
    fun configured() = vault.get("configured") == "true"
    fun online(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
    fun enabled() = vault.get("enabled") == "true"
    fun deviceUid() = vault.get("deviceUid") ?: ""
    fun autoTime() = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
    fun now(): Pair<Long, Boolean> {
        val wall = System.currentTimeMillis()
        val anchor = vault.get("clockAnchor")?.let { JSONObject(it) } ?: return wall to false
        val up = SystemClock.elapsedRealtime()
        val baseUp = anchor.getLong("up")
        // A reboot invalidates a monotonic anchor; local automatic time remains explicit.
        if (up < baseUp || up - baseUp > 86400000) return wall to false
        val measured = anchor.getLong("server") + (up - baseUp)
        if (abs(wall - measured) > 120000) error("Датумот или времето е сменето. Поврзи интернет за проверка.")
        return measured to true
    }
    fun pair(username: String, password: String, pin: String, label: String, finished: (String?) -> Unit) {
        if (!online()) { finished("Првото поврзување бара интернет."); return }
        if (!pin.matches(Regex("[0-9]{6,12}"))) { finished("Избери код со 6–12 цифри."); return }
        val app = FirebaseApp.getApps(context).firstOrNull { it.name == "MBI_TERMINAL_SETUP" }
            ?: FirebaseApp.initializeApp(context, TerminalApplication.options(), "MBI_TERMINAL_SETUP")
        val setupAuth = FirebaseAuth.getInstance(app)
        val setupDb = FirebaseFirestore.getInstance(app)
        val user = username.trim().lowercase().replace(" ", "")
        val email = if (user.contains("@")) user else "$user@mbi.local"
        fun fail(message: String) { setupAuth.signOut(); finished(message) }
        setupAuth.signInWithEmailAndPassword(email, password).addOnFailureListener { fail(friendly(it)) }.addOnSuccessListener { logged ->
            val owner = logged.user?.uid ?: return@addOnSuccessListener fail("Нема администраторска најава.")
            setupDb.collection("employees").whereEqualTo("uid", owner).limit(1).get(Source.SERVER)
                .addOnFailureListener { fail(friendly(it)) }.addOnSuccessListener profile@{ q ->
                    if (q.documents.firstOrNull()?.getBoolean("isAdmin") != true) return@profile fail("Поставувањето е дозволено само за MBI администратор.")
                    fun grant() {
                        val uid = auth.currentUser?.uid ?: return fail("Нема терминалски пристап.")
                        vault.put("deviceUid", uid)
                        val data = mapOf("active" to true, "ownerUid" to owner, "label" to label.take(80),
                            "package" to "com.mbidesign.terminal", "pairedAt" to FieldValue.serverTimestamp())
                        setupDb.collection("settings").document("company")
                            .set(mapOf("terminalDevices" to mapOf(uid to data)), SetOptions.merge())
                            .addOnFailureListener { fail(friendly(it)) }.addOnSuccessListener {
                                setupDb.collection("employees").get(Source.SERVER).addOnFailureListener { fail(friendly(it)) }
                                    .addOnSuccessListener { workers ->
                                        store.setWorkers(workers.documents.mapNotNull(::workerFrom))
                                        vault.put("ownerUid", owner); vault.put("label", label.take(80))
                                        vault.setPin(pin); vault.put("enabled", "true"); vault.put("configured", "true")
                                        setupAuth.signOut(); AdminGate.open(); schedule(context); finished(null)
                                    }
                            }
                    }
                    val existingEmail = vault.get("deviceEmail")
                    if (existingEmail != null) {
                        auth.signInWithEmailAndPassword(existingEmail, vault.get("devicePassword") ?: "")
                            .addOnFailureListener { fail(friendly(it)) }.addOnSuccessListener { grant() }
                    } else {
                        val deviceEmail = "terminal_${UUID.randomUUID().toString().replace("-", "")}@mbi.local"
                        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                        val devicePassword = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        auth.createUserWithEmailAndPassword(deviceEmail, devicePassword)
                            .addOnFailureListener { fail(friendly(it)) }.addOnSuccessListener {
                                vault.put("deviceEmail", deviceEmail); vault.put("devicePassword", devicePassword); grant()
                            }
                    }
                }
        }
    }
    fun syncOnce(): String {
        if (!configured()) return "Потребно е прво поставување."
        if (!online()) return "Без интернет • записите се зачувуваат на таблетот"
        if (!syncing.compareAndSet(false, true)) return "Синхронизацијата е во тек"
        try {
            val uid = deviceUid()
            if (auth.currentUser?.uid != uid) {
                Tasks.await(auth.signInWithEmailAndPassword(vault.get("deviceEmail") ?: "", vault.get("devicePassword") ?: ""), 20, TimeUnit.SECONDS)
            }
            val company = Tasks.await(db.collection("settings").document("company").get(Source.SERVER), 20, TimeUnit.SECONDS)
            val devices = company.get("terminalDevices") as? Map<*, *>
            val grant = devices?.get(uid) as? Map<*, *>
            if (grant?.get("active") != true || grant?.get("ownerUid") != vault.get("ownerUid")) {
                vault.put("enabled", "false"); return "Терминалот е оневозможен. Потребен е администратор."
            }
            vault.put("enabled", "true")
            val workers = Tasks.await(db.collection("employees").get(Source.SERVER), 20, TimeUnit.SECONDS)
            store.setWorkers(workers.documents.mapNotNull(::workerFrom))
            val pending = store.pending()
            val floor = minOf(Attendance.dayStart(System.currentTimeMillis()) - 86400000,
                pending.minOfOrNull { Attendance.dayStart(it.time) } ?: Long.MAX_VALUE)
            val snapshot = Tasks.await(db.collection("attendance").whereGreaterThanOrEqualTo("timestamp", Timestamp(Date(floor))).get(Source.SERVER), 20, TimeUnit.SECONDS)
            val cloud = snapshot.documents.mapNotNull(::eventFrom).toMutableList()
            store.replaceRemoteWindow(floor,cloud)
            for (e in pending) {
                val existing = cloud.firstOrNull { it.id == e.id }
                if (existing != null) {
                    if (existing.employeeId == e.employeeId && existing.uid == e.uid && existing.type == e.type && existing.time == e.time)
                        store.status(e.id, "synced")
                    else store.status(e.id, "conflict", "Записот на серверот е различен. Потребна е проверка.")
                    continue
                }
                val w = store.worker(e.employeeId)
                if (w == null || !w.active || w.uid != e.uid) {
                    store.status(e.id, "conflict", "Профилот е сменет, отстранет или блокиран."); continue
                }
                val day = Attendance.dayStart(e.time)
                val events = cloud.filter { it.employeeId == e.employeeId && Attendance.dayStart(it.time) == day }
                if (events.any { it.type == e.type && abs(it.time - e.time) < 3000 }) {
                    store.status(e.id, "conflict", "Можна двојна пријава преку таблет и телефон."); continue
                }
                val remaining = pending.filter { it.id != e.id && it.employeeId == e.employeeId && Attendance.dayStart(it.time) == day && cloud.none { c -> c.id == it.id } }
                val conflict = Attendance.conflict(events + remaining, e)
                if (conflict != null) { store.status(e.id, "conflict", conflict); continue }
                val data = mapOf("employeeId" to e.employeeId, "employeeName" to e.name, "uid" to e.uid,
                    "type" to e.type, "timestamp" to Timestamp(Date(e.time)), "source" to "mbi_terminal",
                    "terminalUid" to uid, "verification" to e.method, "clockTrusted" to e.clockTrusted,
                    "receivedAt" to FieldValue.serverTimestamp())
                try {
                    Tasks.await(db.collection("attendance").document(e.id).set(data), 20, TimeUnit.SECONDS)
                    store.status(e.id, "synced"); cloud.add(e.copy(status = "synced"))
                } catch (ex: Exception) {
                    // Never replace original capture time or discard an unacknowledged event.
                    store.status(e.id, "pending", friendly(ex)); throw ex
                }
            }
            try {
                val heartbeat = db.collection("terminalHeartbeats").document(uid)
                Tasks.await(heartbeat.set(mapOf("terminalUid" to uid, "timestamp" to FieldValue.serverTimestamp())), 10, TimeUnit.SECONDS)
                val beat = Tasks.await(heartbeat.get(Source.SERVER), 10, TimeUnit.SECONDS).getTimestamp("timestamp")
                if (beat != null) vault.put("clockAnchor", JSONObject().put("server", beat.toDate().time).put("up", SystemClock.elapsedRealtime()).toString())
            } catch (_: Exception) { /* Automatic device time remains marked as unverified. */ }
            vault.put("lastSync", System.currentTimeMillis().toString())
            return if (store.pending(true).any { it.status == "conflict" }) "Има записи за администраторска проверка" else "Поврзано • евиденцијата е синхронизирана"
        } catch (e: Exception) {
            if (accessDenied(e)) vault.put("enabled","false")
            return friendly(e)
        }
        finally { syncing.set(false) }
    }
    companion object {
        private val syncing = AtomicBoolean(false)
        fun workerFrom(d: DocumentSnapshot): Worker? {
            if (d.getBoolean("isAdmin") == true || d.getBoolean("isAccountant") == true || d.getBoolean("isTerminal") == true) return null
            val uid = d.getString("uid") ?: return null
            val name = d.getString("name")?.takeIf { it.isNotBlank() } ?: return null
            return Worker(d.id, uid, name, d.getBoolean("active") ?: true)
        }
        fun eventFrom(d: DocumentSnapshot): AttendanceEvent? {
            val id = d.getString("employeeId") ?: return null
            val timestamp = d.getTimestamp("timestamp") ?: return null
            val type = d.getString("type") ?: return null
            if (type !in Attendance.types) return null
            return AttendanceEvent(d.id, id, d.getString("uid") ?: "", d.getString("employeeName") ?: "", type,
                timestamp.toDate().time, d.getString("verification") ?: "phone", "synced", d.getString("terminalUid") ?: "")
        }
        fun friendly(ex: Exception): String {
            val cause = generateSequence<Throwable>(ex) { it.cause }.last()
            if (cause is FirebaseFirestoreException && cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                return "Нема серверска дозвола за терминалот. Администраторот треба да го овозможи пристапот. Записите се зачувани."
            return "Поврзувањето не успеа. Записите остануваат зачувани. ${cause.localizedMessage?.take(180) ?: "Провери интернет и пристап."}"
        }
        fun accessDenied(ex: Exception): Boolean = generateSequence<Throwable>(ex) { it.cause }.any {
            it is FirebaseFirestoreException && it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        fun schedule(context: Context) {
            val constraints = androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
            val immediate = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("mbi-terminal-sync", androidx.work.ExistingWorkPolicy.KEEP, immediate)
            val regular = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(constraints).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork("mbi-terminal-periodic", androidx.work.ExistingPeriodicWorkPolicy.KEEP, regular)
        }
    }
}

class SyncWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.Worker(context, params) {
    override fun doWork(): Result {
        val remote = Remote(applicationContext)
        if (!remote.configured()) return Result.success()
        remote.syncOnce()
        return if (LocalStore(applicationContext).pending().isNotEmpty()) Result.retry() else Result.success()
    }
}
