package com.mbidesign.terminal

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object AdminGate {
    private var expires = 0L
    fun open() { expires = SystemClock.elapsedRealtime() + 180000 }
    fun isOpen() = SystemClock.elapsedRealtime() < expires
    fun close() { expires = 0 }
}
class Vault(context: Context) {
    private val prefs = context.getSharedPreferences("mbi-protected", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("mbi-terminal-v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("mbi-terminal-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun un64(value: String) = Base64.decode(value, Base64.NO_WRAP)
    @Synchronized fun put(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val encoded = b64(cipher.iv) + ":" + b64(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
        check(prefs.edit().putString(name, encoded).commit()) { "Не може да се зачуваат заштитените податоци." }
    }
    @Synchronized fun get(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        val parts = encoded.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, un64(parts[0])))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(un64(parts[1])), Charsets.UTF_8)
    }
    fun has(name: String) = prefs.contains(name)
    fun remove(name: String) { check(prefs.edit().remove(name).commit()) }
    private fun pinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun setPin(pin: String) {
        require(pin.matches(Regex("[0-9]{6,12}"))) { "Кодот мора да има 6–12 цифри." }
        val salt = ByteArray(16).also { random.nextBytes(it) }
        put("pin", JSONObject().put("salt", b64(salt)).put("hash", b64(pinHash(pin, salt))).toString())
        prefs.edit().remove("attempts").remove("lockedUntil").commit()
    }
    fun checkPin(pin: String): Boolean {
        require(System.currentTimeMillis() >= prefs.getLong("lockedUntil", 0)) { "Почекај 5 минути по погрешните обиди." }
        val stored = JSONObject(get("pin") ?: return false)
        val ok = MessageDigest.isEqual(un64(stored.getString("hash")), pinHash(pin, un64(stored.getString("salt"))))
        val attempts = if (ok) 0 else prefs.getInt("attempts", 0) + 1
        prefs.edit().putInt("attempts", attempts).putLong("lockedUntil", if (attempts >= 5) System.currentTimeMillis() + 300000 else 0).commit()
        return ok
    }
    private fun faceKey(w: Worker) = "face:${w.id}:${w.uid}"
    fun hasFace(w: Worker) = has(faceKey(w))
    fun saveFaces(w: Worker, features: List<FloatArray>) {
        check(AdminGate.isOpen()) { "Потребен е администраторски код." }
        require(features.size >= 5 && features.all { it.size == 128 && it.all { v -> v.isFinite() } })
        put(faceKey(w), JSONArray().apply { features.forEach { add -> put(JSONArray(add.toList())) } }.toString())
    }
    fun faces(w: Worker): List<FloatArray> {
        val all = JSONArray(get(faceKey(w)) ?: return emptyList())
        return (0 until all.length()).map { n -> all.getJSONArray(n).let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } } }
    }
    fun deleteFace(w: Worker) { check(AdminGate.isOpen()); remove(faceKey(w)) }
}
