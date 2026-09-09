package mk.wf.labelscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var nalogInput: EditText
    private lateinit var packageInput: EditText
    private lateinit var articleInput: EditText
    private lateinit var sizeInput: EditText
    private lateinit var quantityInput: EditText
    private lateinit var customerInput: EditText
    private lateinit var barcodeInput: EditText
    private lateinit var rawTextInput: EditText
    private lateinit var db: AppDatabase
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var recognizer: TextRecognizer
    private lateinit var barcodeScanner: BarcodeScanner
    private val processing = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var latestPhotoPath = ""
    private var lastAnalysisAt = 0L
    private var candidateKey = ""
    private var candidateCount = 0
    private var lastSavedKey = ""
    private var scannerArmed = true
    private var emptyFrames = 0

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else toast("Потребна е дозвола за камера.")
    }

    private val excelLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { XlsxExporter.write(it, db.getAll()) }
                ?: error("Не можам да го отворам избраниот фајл.")
        }.onSuccess { toast("Excel е успешно зачуван.") }
            .onFailure { toast("Грешка при Excel: ${it.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = AppDatabase(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        barcodeScanner = BarcodeScanning.getClient()
        bindViews()
        updateCount()
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveCurrent(manual = true) }
        findViewById<Button>(R.id.newOrderButton).setOnClickListener { startNewOrder() }
        findViewById<Button>(R.id.switchCameraButton).setOnClickListener { switchCamera() }
        findViewById<Button>(R.id.reviewButton).setOnClickListener {
            startActivity(Intent(this, ReviewActivity::class.java))
        }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportExcel() }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) updateCount()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        recognizer.close()
        barcodeScanner.close()
        super.onDestroy()
    }

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        countText = findViewById(R.id.countText)
        nalogInput = findViewById(R.id.nalogInput)
        packageInput = findViewById(R.id.packageInput)
        articleInput = findViewById(R.id.articleInput)
        sizeInput = findViewById(R.id.sizeInput)
        quantityInput = findViewById(R.id.quantityInput)
        customerInput = findViewById(R.id.customerInput)
        barcodeInput = findViewById(R.id.barcodeInput)
        rawTextInput = findViewById(R.id.rawTextInput)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, ::analyzeFrame) }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, preview, analysis)
            statusText.text = "Автоматско скенирање — постави една етикета во кадар"
        } catch (e: Exception) {
            statusText.text = "Грешка со камера: ${e.message}"
        }
    }

    private fun switchCamera() {
        val provider = cameraProvider ?: return
        val wanted = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT
        else CameraSelector.LENS_FACING_BACK
        val available = runCatching {
            provider.hasCamera(CameraSelector.Builder().requireLensFacing(wanted).build())
        }.getOrDefault(false)
        if (!available) return toast("Другата камера не е достапна.")
        lensFacing = wanted
        resetScanner()
        bindCameraUseCases()
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisAt < 650 || !processing.compareAndSet(false, true)) {
            proxy.close()
            return
        }
        lastAnalysisAt = now
        val bitmap = runCatching { proxy.toBitmap() }.getOrNull()
        val rotation = proxy.imageInfo.rotationDegrees
        proxy.close()
        if (bitmap == null) {
            processing.set(false)
            return
        }
        val image = InputImage.fromBitmap(bitmap, rotation)
        recognizer.process(image)
            .addOnSuccessListener { textResult ->
                val raw = textResult.text
                barcodeScanner.process(image)
                    .addOnSuccessListener { codes ->
                        val code = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue.orEmpty()
                        handleAutomaticResult(raw, code, bitmap)
                    }
                    .addOnFailureListener { handleAutomaticResult(raw, "", bitmap) }
                    .addOnCompleteListener { processing.set(false) }
            }
            .addOnFailureListener {
                registerEmptyFrame()
                processing.set(false)
            }
    }

    private fun handleAutomaticResult(raw: String, detectedBarcode: String, bitmap: Bitmap) {
        val parsed = LabelParser.parse(raw, nalogInput.text.toString().trim()).let {
            if (detectedBarcode.isBlank()) it else it.copy(barcode = detectedBarcode)
        }
        val useful = parsed.nalog.isNotBlank() && (
            parsed.packageNo.isNotBlank() || parsed.article.isNotBlank() || parsed.size.isNotBlank() ||
                parsed.quantity.isNotBlank() || parsed.barcode.isNotBlank()
            )
        if (!useful || raw.length < 12) {
            registerEmptyFrame()
            return
        }
        emptyFrames = 0
        val key = listOf(parsed.nalog, parsed.packageNo, parsed.article, parsed.size, parsed.quantity, parsed.barcode)
            .joinToString("|") { it.lowercase(Locale.ROOT).replace(" ", "") }
        showParsed(parsed, raw)
        if (!scannerArmed) {
            if (key == lastSavedKey) {
                candidateKey = ""
                candidateCount = 0
                runOnUiThread { statusText.text = "Зачувано — покажи ја следната етикета" }
                return
            }
            if (key == candidateKey) candidateCount++ else {
                candidateKey = key
                candidateCount = 1
            }
            if (candidateCount < 3) {
                runOnUiThread { statusText.text = "Ја проверувам следната етикета..." }
                return
            }
            scannerArmed = true
            candidateCount = 1
        }
        if (key == candidateKey) candidateCount++ else {
            candidateKey = key
            candidateCount = 1
        }
        if (candidateCount >= 2) {
            runOnUiThread {
                latestPhotoPath = saveFrame(bitmap)
                if (saveCurrent(manual = false)) {
                    lastSavedKey = key
                    scannerArmed = false
                    candidateKey = ""
                    candidateCount = 0
                    beepAndVibrate()
                }
            }
        } else runOnUiThread { statusText.text = "Препознавам... држи ја етикетата мирно" }
    }

    private fun registerEmptyFrame() {
        emptyFrames++
        if (!scannerArmed && emptyFrames >= 2) {
            scannerArmed = true
            candidateKey = ""
            candidateCount = 0
            runOnUiThread { statusText.text = "Подготвено — постави ја следната етикета" }
        }
    }

    private fun showParsed(parsed: ParsedLabel, raw: String) = runOnUiThread {
        if (nalogInput.text.isNullOrBlank() && parsed.nalog.isNotBlank()) nalogInput.setText(parsed.nalog)
        packageInput.setText(parsed.packageNo)
        articleInput.setText(parsed.article)
        sizeInput.setText(parsed.size)
        quantityInput.setText(parsed.quantity)
        customerInput.setText(parsed.customer)
        barcodeInput.setText(parsed.barcode)
        rawTextInput.setText(raw)
    }

    private fun saveCurrent(manual: Boolean): Boolean {
        val nalog = nalogInput.text.toString().trim()
        if (nalog.isBlank()) {
            if (manual) toast("Внеси или скенирај број на налог.")
            return false
        }
        var packageNo = packageInput.text.toString().trim()
        if (packageNo.isBlank()) packageNo = (db.countForOrder(nalog) + 1).toString()
        val qty = quantityInput.text.toString().filter { it.isDigit() }.toIntOrNull() ?: 0
        val created = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        db.insert(PackageRecord(
            createdAt = created, nalog = nalog, packageNo = packageNo,
            article = articleInput.text.toString().trim(), size = sizeInput.text.toString().trim(),
            quantity = qty, customer = customerInput.text.toString().trim(),
            barcode = barcodeInput.text.toString().trim(), rawText = rawTextInput.text.toString(),
            photoPath = latestPhotoPath
        ))
        statusText.text = "✓ Зачуван пакет $packageNo за налог $nalog"
        clearPackageFields(keepNalog = true)
        updateCount()
        return true
    }

    private fun saveFrame(bitmap: Bitmap): String = runCatching {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "WFLabelScanner").apply { mkdirs() }
        val file = File(dir, SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".jpg")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }.getOrDefault("")

    private fun beepAndVibrate() {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 250)
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
            VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private fun clearPackageFields(keepNalog: Boolean) {
        if (!keepNalog) nalogInput.setText("")
        packageInput.setText(""); articleInput.setText(""); sizeInput.setText("")
        quantityInput.setText(""); customerInput.setText(""); barcodeInput.setText("")
        rawTextInput.setText(""); latestPhotoPath = ""
    }

    private fun resetScanner() {
        scannerArmed = true; emptyFrames = 0; candidateKey = ""; candidateCount = 0; lastSavedKey = ""
    }

    private fun startNewOrder() {
        clearPackageFields(keepNalog = false)
        resetScanner()
        statusText.text = "Нов налог — постави ја првата етикета во кадар"
    }

    private fun exportExcel() {
        if (db.getAll().isEmpty()) return toast("Нема зачувани пакети за Excel.")
        val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        excelLauncher.launch("WF_Nalozi_$date.xlsx")
    }

    private fun updateCount() {
        val total = db.countAll()
        val nalog = if (::nalogInput.isInitialized) nalogInput.text.toString().trim() else ""
        val current = if (nalog.isBlank()) 0 else db.countForOrder(nalog)
        countText.text = if (nalog.isBlank()) "$total зачувани пакети" else "$total вкупно • $current за налог $nalog"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
