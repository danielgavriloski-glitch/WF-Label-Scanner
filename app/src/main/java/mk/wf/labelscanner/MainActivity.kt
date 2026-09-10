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
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.google.mlkit.vision.text.Text
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
        private const val ANALYSIS_INTERVAL_MS = 220L
        private const val REQUIRED_STABLE_READS = 2
        private const val REQUIRED_BAD_FRAMES = 6
        private const val REQUIRED_WRONG_ORDER_READS = 2
    }

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var countText: TextView
    private lateinit var scanButton: Button
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
    private lateinit var toneGenerator: ToneGenerator

    private val processing = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var latestPhotoPath = ""
    private var lastAnalysisAt = 0L
    private var scanRequested = false
    private var candidateKey = ""
    private var candidateCount = 0
    private var badReadFrames = 0
    private var errorSoundPlayed = false
    private var wrongOrderCandidate = ""
    private var wrongOrderCount = 0
    private var formattingOrder = false
    private var displayedRecord: PackageRecord? = null
    private var lastSavedFingerprint = ""
    private var activeDocumentId = newDocumentId()
    private val recentOcr = ArrayDeque<String>()
    private var pendingExportRecords: List<PackageRecord> = emptyList()
    private var pendingWordRecords: List<PackageRecord> = emptyList()
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

    private val wordLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri: Uri? ->
        if (uri == null) {
            toast("Word документот не е зачуван. Налогот останува отворен.")
            return@registerForActivityResult
        }
        runCatching {
            contentResolver.openOutputStream(uri)?.use { DocxExporter.write(it, pendingWordRecords) }
                ?: error("Не можам да го отворам избраниот фајл.")
        }.onSuccess {
            toast("Word документот е зачуван.")
            pendingWordRecords = emptyList()
            startNewOrder()
        }.onFailure {
            toast("Грешка при Word документ: ${it.message}")
        }
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

        scanButton.setOnClickListener { beginScan() }
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
        scanButton = findViewById(R.id.scanButton)
        nalogInput = findViewById(R.id.nalogInput)
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
            statusText.text = "Камера подготвена — стави етикета и притисни СКЕНИРАЈ"
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
        stopScan("Камера сменета — притисни СКЕНИРАЈ")
        bindCameraUseCases()
    }

    private fun beginScan() {
        if (scanRequested) return
        clearPackageFields(keepNalog = true)
        candidateKey = ""
        candidateCount = 0
        badReadFrames = 0
        errorSoundPlayed = false
        wrongOrderCandidate = ""
        wrongOrderCount = 0
        recentOcr.clear()
        displayedRecord = null
        latestPhotoPath = ""
        scanRequested = true
        scanButton.isEnabled = false
        statusText.text = "Скенирам... држи ја етикетата право во рамката"
    }

    private fun stopScan(message: String) = runOnUiThread {
        scanRequested = false
        scanButton.isEnabled = true
        candidateKey = ""
        candidateCount = 0
        recentOcr.clear()
        statusText.text = message
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        if (!scanRequested) {
            proxy.close()
            return
        }

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
                val spatialFields = extractSpatialFields(textResult)
                barcodeScanner.process(image)
                    .addOnSuccessListener { codes ->
                        val code = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue.orEmpty()
                        handleScanResult(raw, code, bitmap, spatialFields)
                    }
                    .addOnFailureListener { handleScanResult(raw, "", bitmap, spatialFields) }
                    .addOnCompleteListener { processing.set(false) }
            }
            .addOnFailureListener {
                registerBadRead()
                processing.set(false)
            }
    }

    private fun extractSpatialFields(result: Text): Pair<String, String>? {
        val lines = result.textBlocks.flatMap { it.lines }
        fun normalized(value: String) = value.uppercase(Locale.ROOT)
            .replace("Ö", "O").replace("Ü", "U").replace(" ", "")
        val sizeHeader = lines.firstOrNull {
            normalized(it.text).contains("GROSSE") || normalized(it.text) == "OSSE"
        } ?: return null
        val quantityHeader = lines.firstOrNull {
            normalized(it.text).contains("STUCK") || normalized(it.text).contains("STUICK")
        } ?: return null
        val sizeBox = sizeHeader.boundingBox ?: return null
        val qtyBox = quantityHeader.boundingBox ?: return null
        val middle = (sizeBox.centerX() + qtyBox.centerX()) / 2
        val headerBottom = maxOf(sizeBox.bottom, qtyBox.bottom)
        val numbers = lines.mapNotNull { line ->
            val value = line.text.trim()
            val box = line.boundingBox
            if (box != null && value.matches(Regex("\\d{1,3}")) && box.top > headerBottom) {
                Triple(value, box.centerX(), box.top)
            } else null
        }
        val size = numbers.filter { it.second < middle }
            .minByOrNull { it.third }?.first.orEmpty()
        val quantity = numbers.filter { it.second >= middle && it.second < qtyBox.right + qtyBox.width() }
            .minByOrNull { it.third }?.first.orEmpty()
        return if (size.isNotBlank() && quantity.isNotBlank()) size to quantity else null
    }

    private fun handleScanResult(raw: String, detectedBarcode: String, bitmap: Bitmap, spatialFields: Pair<String, String>?) {
        if (!scanRequested) return

        if (raw.length >= 8) {
            recentOcr.addLast(raw)
            while (recentOcr.size > 3) recentOcr.removeFirst()
        }
        val combinedRaw = recentOcr.joinToString("\n")

        val parsed = LabelParser.parse(combinedRaw, "", LabelType.AUTO).let {
            val withBarcode = if (detectedBarcode.isBlank()) it else it.copy(barcode = detectedBarcode)
            spatialFields?.let { fields ->
                withBarcode.copy(size = fields.first, quantity = fields.second)
            } ?: withBarcode
        }

        val qty = parsed.quantity.filter { it.isDigit() }.toIntOrNull() ?: 0
        val complete = parsed.nalog.isNotBlank() && parsed.size.isNotBlank() && qty > 0
        if (!complete || combinedRaw.length < 14) {
            registerBadRead()
            return
        }

        val activeOrder = nalogInput.text.toString().trim()
        if (activeOrder.isNotBlank() && normalizeOrder(parsed.nalog) != normalizeOrder(activeOrder)) {
            val wrongKey = "${normalizeOrder(parsed.nalog)}|${normalizeOrder(activeOrder)}"
            if (wrongKey == wrongOrderCandidate) {
                wrongOrderCount++
            } else {
                wrongOrderCandidate = wrongKey
                wrongOrderCount = 1
            }
            runOnUiThread {
                statusText.text = "Проверувам налог... $wrongOrderCount/$REQUIRED_WRONG_ORDER_READS"
            }
            if (wrongOrderCount >= REQUIRED_WRONG_ORDER_READS) {
                signalWrongOrder(parsed.nalog, activeOrder)
            }
            return
        }

        badReadFrames = 0
        errorSoundPlayed = false
        wrongOrderCandidate = ""
        wrongOrderCount = 0

        val key = listOf(
            normalizeOrder(parsed.nalog),
            parsed.packageNo,
            parsed.article,
            parsed.size,
            parsed.quantity,
            parsed.barcode
        ).joinToString("|") { it.lowercase(Locale.ROOT).replace(" ", "") }

        showParsed(parsed, combinedRaw)

        if (key == candidateKey) {
            candidateCount++
        } else {
            candidateKey = key
            candidateCount = 1
        }

        if (candidateCount < REQUIRED_STABLE_READS) {
            runOnUiThread {
                statusText.text = "Проверувам точност... $candidateCount/$REQUIRED_STABLE_READS"
            }
            return
        }

        scanRequested = false
        runOnUiThread {
            scanButton.isEnabled = true
            showScanConfirmation(parsed, combinedRaw, bitmap)
        }
    }

    private fun showScanConfirmation(parsed: ParsedLabel, raw: String, bitmap: Bitmap) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 12, 36, 4)
        }
        fun field(label: String, value: String, numeric: Boolean = false): EditText =
            EditText(this).apply {
                hint = label
                setText(value)
                if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
                box.addView(this)
            }
        val order = field("Налог", parsed.nalog)
        val size = field("Големина", parsed.size)
        val qty = field("Парчиња", parsed.quantity, true)
        val master = field("Master number", parsed.article)
        val packageNo = field("Пакет", parsed.packageNo)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Провери го скенирањето")
            .setView(box)
            .setPositiveButton("ПОТВРДИ", null)
            .setNegativeButton("ОТКАЖИ") { _, _ ->
                statusText.text = "Откажано — притисни СКЕНИРАЈ за повторно"
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val quantity = qty.text.toString().filter(Char::isDigit)
                if (order.text.isBlank() || size.text.isBlank() || quantity.toIntOrNull() == null) {
                    toast("Провери налог, големина и парчиња.")
                    return@setOnClickListener
                }
                nalogInput.setText(formatOrderNumber(order.text.toString().trim()))
                nalogInput.isEnabled = false
                sizeInput.setText(size.text.toString().trim())
                quantityInput.setText(quantity)
                articleInput.setText(master.text.toString().trim())
                packageInput.setText(packageNo.text.toString().trim())
                rawTextInput.setText(raw)
                latestPhotoPath = saveFrame(bitmap)
                if (saveCurrent(manual = false)) {
                    playSuccessSound()
                    dialog.dismiss()
                    stopScan("✓ Потврдено и зачувано — стави следна етикета")
                }
            }
        }
        dialog.show()
    }

    private fun normalizeOrder(value: String): String =
        value.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun normalizeText(value: String): String =
        value.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun signalWrongOrder(readOrder: String, activeOrder: String) {
        runOnUiThread {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 650)
            vibrate(450)
            stopScan("✕ Погрешен налог: $readOrder • активен е $activeOrder")
        }
    }

    private fun registerBadRead() {
        if (!scanRequested) return
        badReadFrames++
        if (badReadFrames >= REQUIRED_BAD_FRAMES && !errorSoundPlayed) {
            errorSoundPlayed = true
            runOnUiThread {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 450)
                vibrate(250)
                stopScan("Не ја прочитав целата етикета — намести ја подобро и притисни СКЕНИРАЈ")
            }
        }
    }

    private fun showParsed(parsed: ParsedLabel, raw: String) = runOnUiThread {
        if (nalogInput.text.isNullOrBlank() && parsed.nalog.isNotBlank()) {
            nalogInput.setText(formatOrderNumber(parsed.nalog))
            nalogInput.isEnabled = false
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
        if (packageNo.isBlank()) packageNo = (db.countForDocument(activeDocumentId) + 1).toString()

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
            photoPath = latestPhotoPath,
            documentId = activeDocumentId
        )

        if (manual && currentDisplayed != null) {
            db.update(record.copy(photoPath = currentDisplayed.photoPath.ifBlank { latestPhotoPath }))
            displayedRecord = record
            statusText.text = "✓ Измената е зачувана за пакет $packageNo"
            updateCount()
            return true
        }

        if (isDuplicate(record)) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 500)
            vibrate(250)
            stopScan("⚠ Овој пакет е веќе скениран — не е зачуван повторно")
            return false
        }

        val insertedId = db.insert(record)
        displayedRecord = record.copy(id = insertedId)
        lastSavedFingerprint = recordFingerprint(record)
        updateCount()
        return true
    }

    private fun recordFingerprint(record: PackageRecord): String = listOf(
        record.nalog, record.packageNo, record.article, record.size, record.quantity.toString()
    ).joinToString("|") { normalizeText(it) }

    private fun isDuplicate(record: PackageRecord): Boolean {
        val fingerprint = recordFingerprint(record)
        if (fingerprint == lastSavedFingerprint) return true
        val existing = db.getForDocument(record.documentId)
        return existing.any { old ->
            val samePackage = record.packageNo.isNotBlank() && old.packageNo.isNotBlank() &&
                normalizeText(record.packageNo) == normalizeText(old.packageNo)
            val sameBarcode = record.barcode.isNotBlank() && old.barcode.isNotBlank() &&
                normalizeText(record.barcode) == normalizeText(old.barcode)
            val sameContent = normalizeText(record.rawText).length > 30 &&
                normalizeText(record.rawText) == normalizeText(old.rawText)
            val sameLabelFields = record.article.isNotBlank() && record.size.isNotBlank() && record.quantity > 0 &&
                normalizeText(record.article) == normalizeText(old.article) &&
                normalizeText(record.size) == normalizeText(old.size) && record.quantity == old.quantity &&
                (record.packageNo.isBlank() || old.packageNo.isBlank())
            samePackage || sameBarcode || sameContent || sameLabelFields
        }
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

    private fun newDocumentId(): String = "${System.currentTimeMillis()}-${java.util.UUID.randomUUID()}"

    private fun startNewOrder() {
        activeDocumentId = newDocumentId()
        lastSavedFingerprint = ""
        clearPackageFields(keepNalog = false)
        nalogInput.isEnabled = true
        scanRequested = false
        scanButton.isEnabled = true
        candidateKey = ""
        candidateCount = 0
        badReadFrames = 0
        errorSoundPlayed = false
        wrongOrderCandidate = ""
        wrongOrderCount = 0
        recentOcr.clear()
        displayedRecord = null
        statusText.text = "Нов налог — стави ја првата етикета и притисни СКЕНИРАЈ"
        updateCount()
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
        val records = db.getForDocument(activeDocumentId)
        if (records.isEmpty()) return toast("Нема зачувани пакети за налог $nalog.")

        pendingWordRecords = records
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val safeNalog = nalog.replace(Regex("[^A-Za-z0-9_-]"), "_")
        wordLauncher.launch("WF_Nalog_${safeNalog}_$date.docx")
    }

    private fun updateCount() {
        val total = db.countAll()
        val nalog = if (::nalogInput.isInitialized) nalogInput.text.toString().trim() else ""
        val current = if (nalog.isBlank()) 0 else db.countForDocument(activeDocumentId)
        countText.text = if (nalog.isBlank()) {
            "$total зачувани пакети"
        } else {
            "$total вкупно • $current за налог $nalog"
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
