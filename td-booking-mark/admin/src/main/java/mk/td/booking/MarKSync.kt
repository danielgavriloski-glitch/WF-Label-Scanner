package mk.td.booking

import android.app.Activity
import android.content.Context
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Local-only TD Booking <-> MarK desktop pairing.
 * No booking/finance storage migration and no cloud credential transfer.
 * Server TLS certificate is accepted ONLY if its SHA256 exactly matches the
 * out-of-band fingerprint shown inside the logged-in MarK desktop.
 */
class MarKSync(private val activity: Activity, private val web: WebView) {
    private val pref = activity.getSharedPreferences("mark_bridge_private_v1", Context.MODE_PRIVATE)
    private val io = Executors.newSingleThreadExecutor()

    private fun deviceId(): String {
        val old = pref.getString("deviceId", null)
        if (old != null) return old
        val id = UUID.randomUUID().toString()
        pref.edit().putString("deviceId", id).apply()
        return id
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun fingerprint(): String = (pref.getString("fingerprint", "") ?: "")
        .lowercase().replace(":", "").replace(" ", "")

    private fun connect(path: String, payload: JSONObject, auth: Boolean = false): JSONObject {
        val fp = fingerprint()
        if (!fp.matches(Regex("[0-9a-f]{64}"))) throw IOException("TLS отпечатокот е невалиден")
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) { throw CertificateException("Клиентски TLS не е поддржан") }
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("Недостига серверски сертификат")
                leaf.checkValidity()
                if (sha256(leaf.encoded) != fp) throw CertificateException("MarK сертификатот не се совпаѓа")
            }
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trust), SecureRandom())
        val base = pref.getString("endpoint", "") ?: ""
        val connection = (URL(base + path).openConnection() as HttpsURLConnection)
        connection.sslSocketFactory = context.socketFactory
        // Pin authenticates the exact self-signed leaf, including when LAN IP changes.
        connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
            try { sha256(session.peerCertificates.first().encoded) == fp } catch (_: Exception) { false }
        }
        connection.connectTimeout = 7000
        connection.readTimeout = 12000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (auth) {
            val token = pref.getString("token", "") ?: ""
            if (token.isEmpty()) throw IOException("Телефонот не е спарен со MarK")
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        val inputBytes = payload.toString().toByteArray(Charsets.UTF_8)
        connection.setFixedLengthStreamingMode(inputBytes.size)
        connection.outputStream.use { it.write(inputBytes) }
        val code = connection.responseCode
        val stream = if (code < 400) connection.inputStream else connection.errorStream
        val answer = JSONObject(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        connection.disconnect()
        if (code >= 400) throw IOException(answer.optString("error", "HTTP $code"))
        answer.put("httpStatus", code)
        return answer
    }

    private fun result(type: String, success: Boolean, body: JSONObject) {
        body.put("type", type)
        body.put("ok", success)
        val text = JSONObject.quote(body.toString())
        activity.runOnUiThread {
            web.evaluateJavascript("window.tdMarKResult && window.tdMarKResult(JSON.parse($text))", null)
        }
    }

    private fun run(type: String, task: () -> JSONObject) {
        io.execute {
            try { result(type, true, task()) }
            catch (error: Exception) { result(type, false, JSONObject().put("error", error.message ?: "Грешка")) }
        }
    }

    fun info(): String = JSONObject()
        .put("paired", !(pref.getString("token", "") ?: "").isEmpty())
        .put("endpoint", pref.getString("endpoint", "") ?: "")
        .put("deviceId", deviceId()).toString()

    fun requestPair(endpoint: String, fingerprint: String) = run("pair-request") {
        val url = URL(endpoint.trim())
        if (url.protocol != "https" || url.port != 8766 || url.host.isBlank() || url.userInfo != null || url.path !in listOf("", "/"))
            throw IOException("Внеси https://IP:8766 од MarK")
        val fp = fingerprint.trim().lowercase().replace(":", "").replace(" ", "")
        if (!fp.matches(Regex("[0-9a-f]{64}"))) throw IOException("Внеси SHA256 отпечаток од MarK")
        pref.edit().putString("endpoint", "https://${url.host}:8766").putString("fingerprint", fp)
            .remove("token").remove("challenge").apply()
        val data = connect("/v1/pair/request", JSONObject().put("deviceId", deviceId()))
        pref.edit().putString("challenge", data.getString("challenge")).apply()
        JSONObject().put("code", data.getString("code")).put("pending", true)
    }

    fun finishPair() = run("pair-finish") {
        val challenge = pref.getString("challenge", "") ?: ""
        if (challenge.isEmpty()) throw IOException("Најпрво започни спарување")
        val data = connect("/v1/pair/complete", JSONObject().put("challenge", challenge))
        if (data.optBoolean("pending", false)) JSONObject().put("pending", true)
        else {
            val token = data.getString("token")
            pref.edit().putString("token", token).remove("challenge").apply()
            JSONObject().put("paired", true)
        }
    }

    fun syncBookings(rawJson: String) = run("sync") {
        if (rawJson.length > 500000) throw IOException("Премногу резервации за еден пренос")
        val all = JSONArray(rawJson)
        var changed = 0
        var sent = 0
        var i = 0
        val projects = JSONArray()
        while (i < all.length()) {
            val batch = JSONArray()
            for (j in i until minOf(i + 80, all.length())) batch.put(all.getJSONObject(j))
            val res = connect("/v1/bookings", JSONObject().put("deviceId", deviceId()).put("bookings", batch), true)
            sent += res.optInt("received", 0)
            changed += res.optJSONArray("changed")?.length() ?: 0
            val related = res.optJSONArray("projects") ?: JSONArray()
            for (k in 0 until related.length()) projects.put(related.getJSONObject(k))
            i += 80
        }
        JSONObject().put("received", sent).put("changed", changed).put("projects", projects)
    }

    fun disconnect() {
        // Local disconnect only; revoke this device in the MarK panel for server-side revocation.
        pref.edit().remove("token").remove("challenge").apply()
    }
}
