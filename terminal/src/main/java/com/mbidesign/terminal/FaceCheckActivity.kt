package com.mbidesign.terminal

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class FaceCheckActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())
    private var closing = false
    private var visible = false
    private var finished = false
    private var engine: FaceEngine? = null
    private var provider: ProcessCameraProvider? = null
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var worker: Worker
    private lateinit var vault: Vault
    private var enroll = false
    private var samples = emptyList<FloatArray>()
    private val collected = mutableListOf<FloatArray>()
    private var lastCapture = 0L
    private var lastFrame = 0L
    private var lastValid = 0L
    private var tracking: Int? = null
    private val random = SecureRandom()
    private val challenge = Liveness(if (random.nextBoolean()) 1 else -1, random.nextBoolean())
    private val detector by lazy { FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(.2f).enableTracking().build()) }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        val id = intent.getStringExtra("worker") ?: return finish()
        worker = LocalStore(this).worker(id) ?: return finish()
        if (!worker.active) return finish()
        vault = Vault(this); enroll = intent.getBooleanExtra("enroll", false)
        if (enroll && !AdminGate.isOpen()) return finish()
        try { samples = if (enroll) emptyList() else vault.faces(worker) }
        catch (_: Exception) { return errorExit("Регистрацијата на лице не може да се отвори.") }
        if (!enroll && samples.size < 5) return errorExit("Лицето сè уште не е регистрирано.")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); setBackgroundColor(Color.rgb(20, 24, 29)) }
        root.addView(TextView(this).apply { text = worker.name; textSize = 24f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(TextView(this).apply {
            text = if (enroll) "Регистрација на лице • под надзор на администратор" else Attendance.label(intent.getStringExtra("type") ?: "")
            textSize = 16f; setTextColor(Color.rgb(232, 188, 76)); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(8))
        })
        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FIT_CENTER }
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply { text = "Подготвување камера и модел…"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(16), 0, dp(10)) }
        detail = TextView(this).apply { text = "Само едно лице • добро светло • без маска или кацига"; textSize = 15f; setTextColor(Color.rgb(183, 193, 205)); gravity = Gravity.CENTER }
        root.addView(status); root.addView(detail)
        root.addView(Button(this).apply { text = "Откажи"; setOnClickListener { finish() } })
        setContentView(root)
        executor.execute {
            try { engine = FaceEngine(this); main.post { if (!closing) permissionAndCamera() } }
            catch (e: Exception) { main.post { errorExit(e.message ?: "Моделот за лице не може да се вклучи.") } }
        }
        main.postDelayed({ if (!finished && !closing) errorExit("Проверката истече. Пробај повторно со добро осветлување.") }, if (enroll) 120000 else 60000)
    }
    private fun permissionAndCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) bindCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 7)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) bindCamera()
            else errorExit("Потребна е дозвола за камера за потврда со лице.")
        }
    }
    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (closing) return@addListener
            try {
                provider = future.get()
                if (provider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) != true) return@addListener errorExit("Уредот нема достапна предна камера.")
                val p = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val analysis = ImageAnalysis.Builder().setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { analyze(it) }
                provider?.unbindAll(); provider?.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, p, analysis)
            } catch (e: Exception) { errorExit("Камерата не може да се отвори: ${e.message ?: "Пробај повторно."}") }
        }, ContextCompat.getMainExecutor(this))
    }
    private fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (closing || !visible || finished || now - lastFrame < 100 || !busy.compareAndSet(false, true)) { image.close(); return }
        lastFrame = now
        val bitmap: Bitmap
        try {
            val original = image.toBitmap()
            val rotate = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            bitmap = if (image.imageInfo.rotationDegrees == 0) original else Bitmap.createBitmap(original, 0, 0, original.width, original.height, rotate, false).also { original.recycle() }
        } catch (_: Exception) { image.close(); busy.set(false); return }
        image.close()
        detector.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { result ->
            if (closing) { bitmap.recycle(); busy.set(false); return@addOnCompleteListener }
            try { executor.execute {
                try {
                    if (result.isSuccessful) observe(bitmap, result.result ?: emptyList(), now)
                    else update("Камерата не може да го провери лицето. Пробај повторно.")
                } catch (e: Exception) { main.post { errorExit("Проверката на лице не успеа: ${e.message ?: "Пробај повторно."}") } }
                finally { bitmap.recycle(); busy.set(false) }
            } } catch (_: java.util.concurrent.RejectedExecutionException) { bitmap.recycle(); busy.set(false) }
        }
    }
    private fun observe(bitmap: Bitmap, faces: List<Face>, now: Long) {
        if (closing || !visible || finished) return
        if (faces.size != 1) {
            if (now - lastValid > 700) { challenge.reset(); tracking = null }
            update(if (faces.isEmpty()) "Постави го лицето во камерата" else "Пред камерата треба да има само едно лице"); return
        }
        val face = faces[0]
        if (face.boundingBox.width() < 140 || face.boundingBox.height() < 140 || abs(face.headEulerAngleZ) > 20) {
            challenge.reset(); update("Приближи се и држи ја главата исправено"); return
        }
        if (tracking != null && tracking != face.trackingId) { challenge.reset(); collected.clear() }
        tracking = face.trackingId
        val feature = engine?.feature(bitmap) ?: run { challenge.reset(); update("Лицето не е доволно јасно. Гледај во камерата."); return }
        lastValid = now
        if (enroll) {
            if (!AdminGate.isOpen()) { main.post { errorExit("Администраторската потврда истече.") }; return }
            if (abs(face.headEulerAngleY) > 15 || (face.leftEyeOpenProbability ?: 0f) < .55f || (face.rightEyeOpenProbability ?: 0f) < .55f) {
                update("Гледај право со отворени очи"); return
            }
            if (collected.isNotEmpty() && collected.any { FaceEngine.similarity(feature, it) < .55f }) {
                collected.clear(); update("Лицето се смени. Почни повторно со истиот вработен."); return
            }
            if (now - lastCapture >= 750) { collected.add(feature); lastCapture = now }
            update("Регистрација: ${collected.size} / 5", "Гледај право; благо промени ја положбата меѓу примероците")
            if (collected.size >= 5) {
                // Prevent registering the same face to another cached active worker.
                val duplicate = LocalStore(this).workers().firstOrNull { other ->
                    other.id != worker.id && vault.hasFace(other) && FaceEngine.matches(feature, vault.faces(other), .50f)
                }
                if (duplicate != null) { main.post { errorExit("Ова лице веќе е регистрирано за ${duplicate.name}.") }; return }
                vault.saveFaces(worker, collected.toList()); complete()
            }
        } else {
            val threshold = if (abs(face.headEulerAngleY) > 15) .45f else .50f
            val same = FaceEngine.matches(feature, samples, threshold)
            if (!same) { challenge.reset(); update("Лицето не одговара на избраниот вработен", "Провери го името и осветлувањето"); return }
            if (challenge.observe(true, face.headEulerAngleY, face.leftEyeOpenProbability, face.rightEyeOpenProbability, now)) complete()
            else update(challenge.instruction(), "Потврда на идентитет и присуство во живо")
        }
    }
    private fun update(message: String, secondary: String? = null) { main.post { if (!closing) { status.text = message; secondary?.let { detail.text = it } } } }
    private fun complete() {
        if (finished || closing) return
        finished = true
        main.post {
            if (!closing) {
                status.text = if (enroll) "Лицето е регистрирано" else "Лицето е потврдено"
                status.setTextColor(Color.rgb(99, 206, 150))
                setResult(Activity.RESULT_OK, Intent().putExtra("worker", worker.id).putExtra("workerUid",worker.uid).putExtra("enroll", enroll))
                main.postDelayed({ finish() }, 600)
            }
        }
    }
    private fun errorExit(message: String) {
        if (closing) return
        setResult(Activity.RESULT_CANCELED, Intent().putExtra("error", message))
        finish()
    }
    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() { visible = false; super.onStop(); challenge.reset(); collected.clear() }
    override fun onDestroy() {
        closing = true; main.removeCallbacksAndMessages(null); provider?.unbindAll()
        detector.close(); executor.shutdown(); super.onDestroy()
    }
}
