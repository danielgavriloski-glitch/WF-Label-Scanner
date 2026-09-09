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
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
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
    companion object {
        private const val ANALYSIS_INTERVAL_MS = 450L
        private const val REQUIRED_STABLE_READS = 3
        private const val REQUIRED_EMPTY_FRAMES = 4
        private const val REQUIRED_BAD_FRAMES = 10
        private const val REQUIRED_WRONG_ORDER_READS = 3
    }

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var nalogInput: EditText
    private lateinit var labelTypeGroup: RadioGroup
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
    private lateinit var toneGenerator: ToneGenerator

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
    private var badReadFrames = 0
    private var errorSoundPlayed = false
    private var wrongOrderSoundedFor = ""
    private var wrongOrderCandidate = ""
    private var wrongOrderCount = 0
    private val recentOcr = ArrayDeque<String>()
    private var displayedRecord: PackageRecord? = null
    private var formattingOrder = false
    private var pendingExportRecords: List<PackageRecord> = emptyList()
    private var closeOrderAfterExport = false

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else toast("Потребна е дозвола за камера.")
    }

    private val excelLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri == null) {
            closeOrderAfterExport = false
            pendingExportRecords = emptyList()
            return@registerForActivityResult
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.use { XlsxExporter.write(it, pendingExportRecords) }
                ?: error("Не можам да го отворам избраниот фајл.")
        }.onSuccess {
            toast("Документот е успешно зачуван.")
            if (closeOrderAfterExport) startNewOrder()
        }.onFailure {
            toast("Грешка при Excel: ${it.message}")
        }
        closeOrderAfterExport = false
        pendingExportRecords = emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = AppDatabase(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        barcodeScanner = BarcodeScanning.getClient()
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        bindViews()
        installOrderFormatter()
        updateCount()

        findViewById<Button>(R.id.saveButton).setOnClickListener { saveCurrent(manual = true) }
        findViewById<Button>(R.id.newOrderButton).setOnClickListener { startNewOrder() }
        findViewById<Button>(R.id.closeOrderButton).setOnClickListener { closeCurrentOrder() }
        findViewById<Button>(R.id.switchCameraButton).setOnClickListener { switchCamera() }
        findViewById<Button>(R.id.reviewButton).setOnClickListener {
            startActivity(Intent(this, ReviewActivity::class.java))
        }
        findViewById<Button>(R.id.documentsButton).setOnClickListener {
            startActivity(Intent(this, OrdersActivity::class.java))
        }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportExcel() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::db.isInitialized) updateCount()
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        recognizer.close()
        barcodeScanner.close()
        toneGenerator.release()
        super.onDestroy()
    }

    private fun bindViews() {
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        countText = findViewById(R.id.countText)
        nalogInput = findViewById(R.id.nalogInput)
        labelTypeGroup = findViewById(R.id.labelTypeGroup)
        packageInput = findViewById(R.id.packageInput)
        articleInput = findViewById(R.id.articleInput)
        sizeInput = findViewById(R.id.sizeInput)
        quantityInput = findViewById(R.id.quantityInput)
        customerInput = findViewById(R.id.customerInput)
        barcodeInput = findViewById(R.id.barcodeInput)
        rawTextInput = findViewById(R.id.rawTextInput)
    }

    private fun installOrderFormatter() {
        nalogInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (formattingOrder) return
                val current = s?.toString().orEmpty()
                val formatted = formatOrderNumber(current)
                if (formatted == current) return
                formattingOrder = true
                nalogInput.setText(formatted)
                nalogInput.setSelection(formatted.length)
                formattingOrder = false
            }
        })
    }

    private fun formatOrderNumber(value: String): String {
        val digits = value.filter { it.isDigit() }.take(10)
        if (digits.isEmpty()) return value
        if (value.any { it.isLetter() }) return value
        return buildString {
            append(digits.take(2))
            if (digits.length > 2) {
                append('-')
                append(digits.substring(2, minOf(5, digits.length)))
            }
            if (digits.length > 5) {
                append('-')
                append(digits.substring(5))
            }
        }
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
            statusText.text = "Прецизно скенирање — држи ја етикетата мирно и право"
        } catch (e: Exception) {
            statusText.text = "Грешка со камера: ${e.message}"
        }
    }

    private fun switchCamera() {
        val provider = cameraProvider ?: return
        val wanted = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
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
        if (now - lastAnalysisAt < ANALYSIS_INTERVAL_MS || !processing.compareAndSet(false, true)) {
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
        if (raw.length >= 8) {
            recentOcr.addLast(raw)
            while (recentOcr.size > 6) recentOcr.removeFirst()
        }
        val combinedRaw = recentOcr.joinToString("\n")

        // Precision rule: do not fall back to the active order when OCR did not actually read an order.
        val labelType = if (labelTypeGroup.checkedRadioButtonId == R.id.nameLabelRadio) {
            LabelType.NAME
        } else {
            LabelType.STANDARD
        }
        val parsed = LabelParser.parse(combinedRaw, "", labelType).let {
            if (detectedBarcode.isBlank()) it else it.copy(barcode = detectedBarcode)
        }

        val qty = parsed.quantity.filter { it.isDigit() }.toIntOrNull() ?: 0
        val complete = parsed.nalog.isNotBlank() && parsed.size.isNotBlank() && qty > 0
        if (!complete || combinedRaw.length < 16) {
            if (combinedRaw.length >= 12) registerBadRead()
            registerEmptyFrame()
            return
        }

        val activeOrder = nalogInput.text.toString().trim()
        if (activeOrder.isNotBlank() && normalizeOrder(parsed.nalog) != normalizeOrder(activeOrder)) {
            candidateKey = ""
            candidateCount = 0
            scannerArmed = true
            val wrongKey = "${normalizeOrder(parsed.nalog)}|${normalizeOrder(activeOrder)}"
            if (wrongKey == wrongOrderCandidate) wrongOrderCount++ else {
                wrongOrderCandidate = wrongKey
                wrongOrderCount = 1
            }
            runOnUiThread {
                statusText.text = "Проверувам налог... $wrongOrderCount/$REQUIRED_WRONG_ORDER_READS"
            }
            if (wrongOrderCount >= REQUIRED_WRONG_ORDER_READS) signalWrongOrder(parsed.nalog, activeOrder)
            return
        }

        badReadFrames = 0
        errorSoundPlayed = false
        wrongOrderSoundedFor = ""
        wrongOrderCandidate = ""
        wrongOrderCount = 0
        emptyFrames = 0

        val key = listOf(
            normalizeOrder(parsed.nalog),
            parsed.packageNo,
            parsed.article,
            parsed.size,
            parsed.quantity,
            parsed.barcode
        ).joinToString("|") { it.lowercase(Locale.ROOT).replace(" ", "") }

        showParsed(parsed, combinedRaw)

        if (!scannerArmed) {
            if (key == lastSavedKey) {
                candidateKey = ""
                candidateCount = 0
                runOnUiThread {
                    statusText.text = "✓ Зачувано — тргни ја оваа етикета и покажи ја следната"
                }
                return
            }

            if (key == candidateKey) {
                candidateCount++
            } else {
                candidateKey = key
                candidateCount = 1
            }

            if (candidateCount < REQUIRED_STABLE_READS) {
                runOnUiThread {
                    statusText.text = "Ја проверувам следната етикета... $candidateCount/$REQUIRED_STABLE_READS"
                }
                return
            }

            scannerArmed = true
            candidateCount = 1
        }

        if (key == candidateKey) {
            candidateCount++
        } else {
            candidateKey = key
            candidateCount = 1
        }

        if (candidateCount >= REQUIRED_STABLE_READS) {
            runOnUiThread {
                latestPhotoPath = saveFrame(bitmap)
                if (saveCurrent(manual = false)) {
                    lastSavedKey = key
                    scannerArmed = false
                    candidateKey = ""
                    candidateCount = 0
                    playSuccessSound()
                    recentOcr.clear()
                }
            }
        } else {
            runOnUiThread {
                statusText.text = "Проверувам точност... $candidateCount/$REQUIRED_STABLE_READS — држи мирно"
            }
        }
    }

    private fun normalizeOrder(value: String): String =
        value.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun signalWrongOrder(readOrder: String, activeOrder: String) {
        val key = "${normalizeOrder(readOrder)}|${normalizeOrder(activeOrder)}"
        runOnUiThread {
            statusText.text = "✕ Погрешен налог: $readOrder • треба $activeOrder — ПОВТОРИ"
            if (wrongOrderSoundedFor != key) {
                wrongOrderSoundedFor = key
                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 650)
                vibrate(450)
            }
        }
    }

    private fun registerEmptyFrame() {
        emptyFrames++
        if (!scannerArmed && emptyFrames >= REQUIRED_EMPTY_FRAMES) {
            scannerArmed = true
            displayedRecord = null
            candidateKey = ""
            candidateCount = 0
            runOnUiThread {
                statusText.text = "Подготвено — постави ја следната етикета"
            }
        }
    }

    private fun registerBadRead() {
        badReadFrames++
        if (badReadFrames >= REQUIRED_BAD_FRAMES && !errorSoundPlayed) {
            errorSoundPlayed = true
            runOnUiThread {
                statusText.text = "Не ја прочитав целата етикета — намести ја во рамката и држи мирно"
                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 500)
                vibrate(350)
            }
        }
    }

    private fun showParsed(parsed: ParsedLabel, raw: String) = runOnUiThread {
        if (nalogInput.text.isNullOrBlank() && parsed.nalog.isNotBlank()) {
            nalogInput.setText(formatOrderNumber(parsed.nalog))
        }
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

        val qty = quantityInput.text.toString().filter { it.isDigit() }.toIntOrNull() ?: 0
        if (!manual && qty <= 0) return false

        var packageNo = packageInput.text.toString().trim()
        if (packageNo.isBlank()) packageNo = (db.countForOrder(nalog) + 1).toString()

        val currentDisplayed = displayedRecord
        val record = PackageRecord(
                id = currentDisplayed?.id ?: 0,
                createdAt = currentDisplayed?.createdAt
                    ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                nalog = nalog,
                packageNo = packageNo,
                article = articleInput.text.toString().trim(),
                size = sizeInput.text.toString().trim(),
                quantity = qty,
                customer = customerInput.text.toString().trim(),
                barcode = barcodeInput.text.toString().trim(),
                rawText = rawTextInput.text.toString(),
                photoPath = latestPhotoPath
        )

        if (manual && currentDisplayed != null) {
            db.update(record.copy(photoPath = currentDisplayed.photoPath.ifBlank { latestPhotoPath }))
            displayedRecord = record
            statusText.text = "✓ Измената е зачувана за пакет $packageNo"
            updateCount()
            return true
        }

        val insertedId = db.insert(record)
        displayedRecord = record.copy(id = insertedId)

        statusText.text = "✓ Зачуван пакет $packageNo за налог $nalog"
        updateCount()
        return true
    }

    private fun saveFrame(bitmap: Bitmap): String = runCatching {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "WFLabelScanner").apply { mkdirs() }
        val file = File(
            dir,
            SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".jpg"
        )
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        file.absolutePath
    }.getOrDefault("")

    private fun playSuccessSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        vibrate(100)
    }

    private fun vibrate(milliseconds: Long) {
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(
            VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    private fun clearPackageFields(keepNalog: Boolean) {
        if (!keepNalog) nalogInput.setText("")
        packageInput.setText("")
        articleInput.setText("")
        sizeInput.setText("")
        quantityInput.setText("")
        customerInput.setText("")
        barcodeInput.setText("")
        rawTextInput.setText("")
        latestPhotoPath = ""
    }

    private fun resetScanner() {
        scannerArmed = true
        emptyFrames = 0
        candidateKey = ""
        candidateCount = 0
        lastSavedKey = ""
        badReadFrames = 0
        errorSoundPlayed = false
        wrongOrderSoundedFor = ""
        wrongOrderCandidate = ""
        wrongOrderCount = 0
        recentOcr.clear()
        displayedRecord = null
    }

    private fun startNewOrder() {
        clearPackageFields(keepNalog = false)
        resetScanner()
        statusText.text = "Нов налог — внеси го налогот или постави ја првата етикета"
    }

    private fun exportExcel() {
        pendingExportRecords = db.getAll()
        if (pendingExportRecords.isEmpty()) return toast("Нема зачувани пакети за Excel.")
        closeOrderAfterExport = false
        val date = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        excelLauncher.launch("WF_Nalozi_$date.xlsx")
    }

    private fun closeCurrentOrder() {
        val nalog = nalogInput.text.toString().trim()
        if (nalog.isBlank()) return toast("Нема активен налог.")
        val records = db.getForOrder(nalog)
        if (records.isEmpty()) return toast("Нема зачувани пакети за налог $nalog.")
        toast("Налог $nalog е зачуван: ${records.size} пакети, ${records.sumOf { it.quantity }} парчиња.")
        startNewOrder()
    }

    private fun updateCount() {
        val total = db.countAll()
        val nalog = if (::nalogInput.isInitialized) nalogInput.text.toString().trim() else ""
        val current = if (nalog.isBlank()) 0 else db.countForOrder(nalog)
        countText.text = if (nalog.isBlank()) {
            "$total зачувани пакети"
        } else {
            "$total вкупно • $current за налог $nalog"
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
