package mk.td.booking

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ValueCallback
import android.content.Intent
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var pendingWedding: String? = null
    private lateinit var markSync: MarKSync
    private var pendingFilePicker: ValueCallback<Array<Uri>>? = null
    private val FILE_PICKER_CODE = 8614
    private val BACKUP_EXPORT_CODE = 8615
    private var pendingBackupJson: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        @Suppress("DEPRECATION")
        web.settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        web.settings.allowUniversalAccessFromFileURLs = true
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, params: WebChromeClient.FileChooserParams?): Boolean {
                pendingFilePicker?.onReceiveValue(null)
                pendingFilePicker = filePathCallback
                return try {
                    val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent.createChooser(pick, "Избери бренд лого"), FILE_PICKER_CODE)
                    true
                } catch (_: Exception) {
                    pendingFilePicker?.onReceiveValue(null)
                    pendingFilePicker = null
                    false
                }
            }
        }
        markSync = MarKSync(this, web)
        web.addJavascriptInterface(Bridge(), "TDNative")
        web.loadUrl("file:///android_asset/index.html")
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        }
    }

    inner class Bridge {
        @JavascriptInterface fun marKInfo(): String = markSync.info()
        @JavascriptInterface fun marKPair(endpoint: String, fingerprint: String) = markSync.requestPair(endpoint, fingerprint)
        @JavascriptInterface fun marKConfirm() = markSync.finishPair()
        @JavascriptInterface fun marKSync(raw: String) = markSync.syncBookings(raw)
        @JavascriptInterface fun marKReplica(raw: String) = markSync.syncReplica(raw)
        @JavascriptInterface fun marKDisconnect() = markSync.disconnect()

        /** Native Android save dialog. Never overwrite app data or rely on WebView downloads. */
        @JavascriptInterface fun saveBackupJson(raw: String) = runOnUiThread {
            try {
                if (raw.toByteArray(Charsets.UTF_8).size > 16 * 1024 * 1024)
                    throw IllegalArgumentException("Резервната копија е преголема")
                val obj = JSONObject(raw)
                val format = obj.optString("format")
                if (format != "TD-Booking-backup" && format != "TD-Booking-FULL-backup")
                    throw IllegalArgumentException("Непознат Backup формат")
                if (obj.optJSONArray("bookings") == null || obj.optJSONObject("finance") == null)
                    throw IllegalArgumentException("Недостасуваат свадбите или финансиите")
                pendingBackupJson = raw
                val date = java.time.LocalDate.now().toString()
                val fileName = "TD_Booking_" + date + "_backup.json"
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }
                startActivityForResult(intent, BACKUP_EXPORT_CODE)
            } catch (e: Exception) {
                backupResult(false, "Грешка при подготовка: " + (e.message ?: "непозната"))
            }
        }

        @JavascriptInterface fun printContract() = runOnUiThread {
            val pm = getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
            pm.print("TD Booking", web.createPrintDocumentAdapter("TD Booking"), null)
        }
        @JavascriptInterface fun notify(title: String, body: String) = runOnUiThread { showNotification(title, body) }
        @JavascriptInterface fun syncWedding(json: String) = runOnUiThread {
            if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                pendingWedding = json
                requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 43)
            } else syncNow(json)
        }
    }

    private fun backupResult(ok: Boolean, message: String) {
        val result = JSONObject().put("ok", ok).put("message", message).toString()
        web.evaluateJavascript("window.tdNativeBackupResult&&window.tdNativeBackupResult($result)", null)
    }

    private fun writableCalendar(): Long? {
        val p = arrayOf(CalendarContract.Calendars._ID)
        val sel = "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?"
        contentResolver.query(CalendarContract.Calendars.CONTENT_URI, p, sel, arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return null
    }

    private fun syncNow(raw: String) {
        try {
            val o = JSONObject(raw)
            val calId = o.optLong("calendarId", -1L).takeIf { it > 0 } ?: writableCalendar() ?: throw IllegalStateException("Нема календар со дозвола за запишување.")
            val date = LocalDate.parse(o.getString("date"))
            val time = runCatching { LocalTime.parse(o.optString("start").takeIf { it.isNotBlank() } ?: "12:00") }.getOrDefault(LocalTime.NOON)
            val zone = ZoneId.systemDefault()
            val start = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            val end = start + 8 * 60 * 60 * 1000L
            val eventType = o.optString("eventType", "wedding")
            val eventLabel = when (eventType) {
                "christening" -> "Крштевка"
                "sunet" -> "Сунет"
                "photoshoot" -> "Фотосесија"
                "other" -> "Друг настан"
                else -> "Свадба"
            }
            val eventName = if (eventType.isBlank() || eventType == "wedding") {
                "${o.optString("bride")} и ${o.optString("groom")}"
            } else o.optString("bride")
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, "$eventLabel · $eventName")
                put(CalendarContract.Events.EVENT_LOCATION, o.optString("venue"))
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
                put(CalendarContract.Events.DESCRIPTION, "TD Booking")
            }
            var eventId = o.optLong("calendarEventId", -1L)
            if (eventId > 0) {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                if (contentResolver.update(uri, values, null, null) <= 0) eventId = -1
            }
            if (eventId <= 0) eventId = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull() ?: throw IllegalStateException("Не успеа запишувањето во Calendar.")
            val js = "onCalendarSynced(${JSONObject.quote(o.getString("id"))},${JSONObject.quote(eventId.toString())},${JSONObject.quote(calId.toString())})"
            web.evaluateJavascript(js, null)
        } catch (e: Exception) {
            web.evaluateJavascript("alert(${JSONObject.quote("Calendar: ${e.message ?: "грешка"}")})", null)
        }
    }

    private fun showNotification(title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "td_team"
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(channelId, "TD Team", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else @Suppress("DEPRECATION") Notification.Builder(this)
        n.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi)
        nm.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), n.build())
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 43 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) pendingWedding?.let { syncNow(it); pendingWedding = null }
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER_CODE) {
            val chosen = if (resultCode == Activity.RESULT_OK) data?.data?.let { arrayOf(it) } else null
            pendingFilePicker?.onReceiveValue(chosen)
            pendingFilePicker = null
        }
        if (requestCode == BACKUP_EXPORT_CODE) {
            val payload = pendingBackupJson
            pendingBackupJson = null
            if (resultCode != Activity.RESULT_OK || data?.data == null) {
                backupResult(false, "Зачувувањето е откажано. Нема изменети записи.")
            } else if (payload == null) {
                backupResult(false, "Податоците за Backup не се достапни — обиди се повторно.")
            } else {
                try {
                    contentResolver.openOutputStream(data.data!!, "w")?.use { stream ->
                        stream.write(payload.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: throw java.io.IOException("Не може да се отвори избраната датотека")
                    backupResult(true, "✓ Backup.json е зачуван на избраното место.")
                } catch (e: Exception) {
                    backupResult(false, "Не успеа зачувувањето: " + (e.message ?: "грешка"))
                }
            }
        }
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
}
