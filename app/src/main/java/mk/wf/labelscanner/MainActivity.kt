package mk.wf.labelscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Size
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
    private data class SpatialFields(
        val nalog: String = "",
        val packageNo: String = "",
        val article: String = "",
        val size: String = "",
        val quantity: String = "",
        val rows: List<Pair<String, String>> = emptyList()
    )

    companion object {
        private const val ANALYSIS_INTERVAL_MS = 280L
        private const val REQUIRED_STABLE_READS = 2
        private const val REQUIRED_BAD_FRAMES = 10
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
    private val frozenFrameProcessing = AtomicBoolean(false)
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
    private var bestPartial: ParsedLabel? = null
    private var bestPartialRaw = ""
    private var bestPartialBitmap: Bitmap? = null
    private var bestPartialScore = -1
    private val accumulatedSizeRows = linkedMapOf<String, Pair<String, String>>()
    private val sizeRowVotes = linkedMapOf<String, Int>()

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
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            pendingWordRecords.forEach { db.update(it.copy(documentUri = uri.toString())) }
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
        restoreClosedOrderIfRequested()
        updateCount()

        scanButton.setOnClickListener { beginScan() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveCurrent(manual = true) }
        findViewById<Button>(R.id.newOrderButton).setOnClickListener { startNewOrder() }
        findViewById<Button>(R.id.closeOrderButton).setOnClickListener { closeCurrentOrder() }
        findViewById<Button>(R.id.switchCameraButton).visibility = android.view.View.GONE
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
            .setTargetResolution(Size(1920, 1080))
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
        bestPartial = null
        bestPartialRaw = ""
        bestPartialBitmap = null
        bestPartialScore = -1
        accumulatedSizeRows.clear()
        sizeRowVotes.clear()
        scanRequested = true
        frozenFrameProcessing.set(false)
        scanButton.isEnabled = false
        statusText.text = "Фотографирам... држи ја етикетата право во рамката"
    }

    private fun stopScan(message: String) = runOnUiThread {
        scanRequested = false
        frozenFrameProcessing.set(false)
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
        if (now - lastAnalysisAt < ANALYSIS_INTERVAL_MS ||
            frozenFrameProcessing.get() || !processing.compareAndSet(false, true)) {
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

        val scanBitmap = cropToScanFrame(bitmap)
        frozenFrameProcessing.set(true)
        runOnUiThread { statusText.text = "Сликата е направена — проверувам 1/2" }
        verifyFrozenFrame(scanBitmap, rotation, pass = 1)
        processing.set(false)
    }

    /**
     * Both verification passes use the exact same captured frame. This avoids
     * mixing text from two labels while the hand or camera is moving.
     */
    private fun verifyFrozenFrame(bitmap: Bitmap, rotation: Int, pass: Int) {
        if (!scanRequested) {
            frozenFrameProcessing.set(false)
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
                    .addOnCompleteListener { finishFrozenPass(bitmap, rotation, pass) }
            }
            .addOnFailureListener {
                registerBadRead()
                finishFrozenPass(bitmap, rotation, pass)
            }
    }

    private fun finishFrozenPass(bitmap: Bitmap, rotation: Int, pass: Int) {
        if (!scanRequested) {
            frozenFrameProcessing.set(false)
            return
        }
        if (pass < REQUIRED_STABLE_READS) {
            runOnUiThread { statusText.text = "Ја проверувам истата слика... 2/2" }
            verifyFrozenFrame(bitmap, rotation, pass + 1)
            return
        }

        frozenFrameProcessing.set(false)
        val partial = bestPartial
        val partialBitmap = bestPartialBitmap
        runOnUiThread {
            if (partial != null && partialBitmap != null && bestPartialScore > 0) {
                scanRequested = false
                scanButton.isEnabled = true
                statusText.text = "Не е прочитано сè — провери и дополни"
                showScanConfirmation(partial, bestPartialRaw, partialBitmap)
            } else {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 450)
                vibrate(250)
                stopScan("Не прочитав ништо — намести ја етикетата и притисни СКЕНИРАЈ")
            }
        }
    }

    private fun cropToScanFrame(bitmap: Bitmap): Bitmap {
        // The yellow guide occupies the center of the preview. Ignoring the outer area
        // prevents text from nearby cartons and shelves from contaminating one label.
        val cropWidth = (bitmap.width * 0.97f).toInt().coerceAtLeast(1)
        val cropHeight = (bitmap.height * 0.88f).toInt().coerceAtLeast(1)
        val left = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((bitmap.height - cropHeight) / 2).coerceAtLeast(0)
        return runCatching { Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight) }
            .getOrDefault(bitmap)
    }

    private fun extractSpatialFields(result: Text): SpatialFields? {
        val lines = result.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
        if (lines.isEmpty()) return null

        fun norm(value: String) = value.uppercase(Locale.ROOT)
            .replace("Ö", "O").replace("Ü", "U").replace("ß", "SS")
            .replace(Regex("[^A-Z0-9/]"), "")

        fun header(vararg keys: String): Text.Line? = lines.firstOrNull { line ->
            val n = norm(line.text)
            keys.any { key -> n.contains(key) }
        }

        fun overlapX(a: Rect, b: Rect): Int = maxOf(0, minOf(a.right, b.right) - maxOf(a.left, b.left))

        fun nearestValue(anchor: Text.Line?, validator: (String) -> Boolean): String {
            val anchorBox = anchor?.boundingBox ?: return ""
            val anchorHeight = anchorBox.height().coerceAtLeast(18)
            return lines.asSequence()
                .filter { it !== anchor }
                .mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    val value = norm(line.text)
                    if (!validator(value)) return@mapNotNull null

                    val sameRow = kotlin.math.abs(box.centerY() - anchorBox.centerY()) <=
                        maxOf(anchorHeight, box.height())
                    val toRight = sameRow && box.left >= anchorBox.right - 12 &&
                        box.left <= anchorBox.right + anchorHeight * 12

                    val below = box.top >= anchorBox.bottom - 8 &&
                        box.top <= anchorBox.bottom + anchorHeight * 7 &&
                        (overlapX(anchorBox, box) > 0 ||
                            kotlin.math.abs(box.centerX() - anchorBox.centerX()) <= anchorHeight * 5)

                    if (!toRight && !below) return@mapNotNull null
                    val score = if (toRight) {
                        (box.left - anchorBox.right).coerceAtLeast(0) +
                            kotlin.math.abs(box.centerY() - anchorBox.centerY()) * 2
                    } else {
                        (box.top - anchorBox.bottom).coerceAtLeast(0) +
                            kotlin.math.abs(box.centerX() - anchorBox.centerX())
                    }
                    value to score
                }
                .minByOrNull { it.second }?.first.orEmpty()
        }

        fun inlineDigits(line: Text.Line?, vararg keys: String): String {
            val value = norm(line?.text.orEmpty())
            for (key in keys) {
                val index = value.indexOf(key)
                if (index >= 0) {
                    val digits = value.substring(index + key.length).filter(Char::isDigit)
                    if (digits.isNotBlank()) return digits
                }
            }
            return ""
        }

        val orderHeader = header("NALOG", "AUFTRAG", "ORDER", "PAPOS", "KOMMISSION")
        val packageHeader = header("KARTON", "PAKET", "PACKAGE", "BOX", "KOLLI")
        val masterHeader = header("MASTER", "ARTIKEL", "ARTICLE", "MODEL")
        val sizeHeader = header("GROSSE", "GROESSE", "SIZE", "GOLEMINA", "VELICINA")
        val quantityHeader = header("STUCK", "STUICK", "QTY", "QUANTITY", "KOLICINA", "PARCINJA", "PCS")

        val sizePattern = Regex("^(XXS|XS|S|M|L|XL|XXL|[2-6]XL|MN|ML|LN|XLN|XXLN|[2-4]XLN|L/N|[1-9]|[2-8][0-9])$")
        val rawNalog = nearestValue(orderHeader) {
            it.filter(Char::isDigit).length in 5..10
        }
        val nalog = normalizeDetectedOrder(rawNalog)
        val packageNo = inlineDigits(packageHeader, "KARTONNR", "KARTON", "PAKET", "PACKAGE", "BOX")
            .takeIf { it.length in 1..4 }
            ?: nearestValue(packageHeader) {
            it.matches(Regex("^\\d{1,4}(/\\d{1,4})?$"))
        }
        val article = inlineDigits(masterHeader, "MASTER")
            .takeIf { it.length in 5..8 }
            ?: nearestValue(masterHeader) {
            it.matches(Regex("^[A-Z0-9./-]{3,16}$")) && !sizePattern.matches(it)
        }
        val size = nearestValue(sizeHeader) { sizePattern.matches(it) }
        val quantity = nearestValue(quantityHeader) {
            it.matches(Regex("^\\d{1,3}$")) && (it.toIntOrNull() ?: 0) in 1..500
        }

        // Read every size/quantity row in the printed table, not just the first one.
        val elements = result.textBlocks.flatMap { it.lines }.flatMap { it.elements }
            .filter { it.boundingBox != null }
        val sizeAnchor = elements.firstOrNull {
            val n = norm(it.text)
            n.contains("GROSSE") || n.contains("GROESSE") || n == "SIZE" || n.contains("VELICINA")
        }?.boundingBox
        val qtyAnchor = elements.firstOrNull {
            val n = norm(it.text)
            n.contains("STUCK") || n.contains("QTY") || n.contains("KOLICINA") || n.contains("PARCINJA")
        }?.boundingBox

        fun handwrittenNumber(value: String): String {
            val compact = norm(value)
            return compact.map { ch ->
                when (ch) {
                    'O', 'D', 'Q' -> '0'
                    'I', 'L' -> '1'
                    'Z' -> '2'
                    'S' -> '5'
                    'G' -> '6'
                    'B' -> '8'
                    else -> ch
                }
            }.joinToString("").filter(Char::isDigit)
        }

        val rows = if (sizeAnchor != null && qtyAnchor != null) {
            // GRÖSSE and STÜCK define two strict neighboring columns.
            // Only values inside those columns and on the same horizontal row are paired.
            val headerBottom = maxOf(sizeAnchor.bottom, qtyAnchor.bottom)
            val middleX = (sizeAnchor.centerX() + qtyAnchor.centerX()) / 2
            val columnGap = kotlin.math.abs(qtyAnchor.centerX() - sizeAnchor.centerX()).coerceAtLeast(50)
            val sizeLeft = sizeAnchor.left - columnGap / 2
            val sizeRight = middleX
            val qtyLeft = middleX
            // The printed vertical divider between GRÖSSE and STÜCK is the hard split.
            // The MASTER/MODEL area begins immediately to the right of STÜCK and must
            // never contribute a number to the quantity column.
            val masterLeft = masterHeader?.boundingBox?.left
            val naturalQtyRight = qtyAnchor.right + columnGap / 2
            val qtyRight = masterLeft?.minus(6)?.coerceAtLeast(qtyAnchor.right)
                ?.coerceAtMost(naturalQtyRight) ?: naturalQtyRight

            // Stop before the next printed section (KARTON/MASTER/BARCODE/etc.),
            // so numbers outside the size table can never become extra sizes.
            // KARTON and MASTER are printed to the right of the size table on the
            // handwritten WF label. They must not shorten the table vertically,
            // otherwise the second/third handwritten size row is lost.
            // A second GRÖSSE/MASTER heading belongs to the small printed sticker
            // below the large handwritten label. Stop before it so its composition
            // percentages and barcode digits cannot become extra size rows.
            val stopWords = listOf("BARCODE", "EAN", "GTIN", "GROSSE", "GROESSE", "MASTERNR")
            val nextSectionTop = elements.mapNotNull { element ->
                val box = element.boundingBox ?: return@mapNotNull null
                val n = norm(element.text)
                val insideTableColumns = box.centerX() in sizeLeft..qtyRight
                box.top.takeIf {
                    insideTableColumns && box.top > headerBottom + 8 && stopWords.any(n::contains)
                }
            }.minOrNull()
            // Only inspect the small table directly below the two headers. Do not
            // continue searching down into the printed barcode sticker.
            val headerHeight = maxOf(sizeAnchor.height(), qtyAnchor.height()).coerceAtLeast(24)
            val tableBottom = nextSectionTop ?: (headerBottom + headerHeight * 9)

            data class CellValue(val value: String, val box: Rect)
            val sizeValues = elements.mapNotNull { element ->
                val box = element.boundingBox ?: return@mapNotNull null
                val original = norm(element.text).replace("|", "/")
                val numeric = handwrittenNumber(original)
                val value = if (numeric.length in 1..2) numeric else original
                val inside = box.centerX() in sizeLeft..sizeRight &&
                    box.centerY() > headerBottom && box.centerY() < tableBottom
                if (inside && sizePattern.matches(value)) CellValue(value, box) else null
            }.sortedBy { it.box.centerY() }

            val qtyValues = elements.mapNotNull { element ->
                val box = element.boundingBox ?: return@mapNotNull null
                val value = handwrittenNumber(element.text)
                val number = value.toIntOrNull()
                val inside = box.centerX() in qtyLeft..qtyRight &&
                    box.centerY() > headerBottom && box.centerY() < tableBottom
                if (inside && number != null && number in 1..500) CellValue(value, box) else null
            }.sortedBy { it.box.centerY() }

            val usedQuantities = mutableSetOf<Int>()
            val pairedElements = sizeValues.mapNotNull { sizeCell ->
                val quantityIndex = qtyValues.indices
                    .filterNot(usedQuantities::contains)
                    .minByOrNull { index ->
                        kotlin.math.abs(qtyValues[index].box.centerY() - sizeCell.box.centerY())
                    } ?: return@mapNotNull null
                val quantityCell = qtyValues[quantityIndex]
                val rowTolerance = (maxOf(sizeCell.box.height(), quantityCell.box.height()) * 1.8f)
                    .toInt().coerceAtLeast(18)
                if (kotlin.math.abs(quantityCell.box.centerY() - sizeCell.box.centerY()) > rowTolerance) {
                    return@mapNotNull null
                }
                usedQuantities += quantityIndex
                sizeCell.value to quantityCell.value
            }.distinct()

            // ML Kit often returns a handwritten row such as "68 10" as one line,
            // instead of two separate elements. Recover those rows by reading the
            // two numeric tokens from every line that crosses the size table.
            val pairedLines = lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                if (box.centerY() <= headerBottom || box.centerY() >= tableBottom) return@mapNotNull null
                if (box.right < sizeLeft || box.left > qtyRight) return@mapNotNull null
                val tokens = Regex("[A-Z0-9]+").findAll(norm(line.text)).map { it.value }.toList()
                val numbers = tokens.map(::handwrittenNumber).filter { it.isNotBlank() }
                if (numbers.size < 2) return@mapNotNull null
                val possibleSize = numbers.firstOrNull { sizePattern.matches(it) } ?: return@mapNotNull null
                val possibleQty = numbers.dropWhile { it != possibleSize }.drop(1)
                    .firstOrNull { (it.toIntOrNull() ?: 0) in 1..500 } ?: return@mapNotNull null
                possibleSize to possibleQty
            }

            // Prefer clean, separately detected cells. Use the combined-line fallback
            // only when ML Kit did not separate the two columns at all. Stop at four
            // rows; numbers elsewhere on the label are deliberately ignored.
            (if (pairedElements.isNotEmpty()) pairedElements else pairedLines)
                .distinct()
                .take(4)
        } else emptyList()

        val finalRows = rows.ifEmpty {
            if (size.isNotBlank() && quantity.isNotBlank()) listOf(size to quantity) else emptyList()
        }
        val firstRow = finalRows.firstOrNull()
        return SpatialFields(
            nalog, packageNo, article,
            firstRow?.first ?: size,
            firstRow?.second ?: quantity,
            finalRows
        ).takeIf {
            listOf(it.nalog, it.packageNo, it.article, it.size, it.quantity).any(String::isNotBlank)
        }
    }

    private fun handleScanResult(raw: String, detectedBarcode: String, bitmap: Bitmap, spatialFields: SpatialFields?) {
        if (!scanRequested) return

        // Parse one frame at a time. Combining frames can mix two adjacent labels.
        val combinedRaw = raw
        val activeOrderForParsing = nalogInput.text.toString().trim()

        val parsed = LabelParser.parse(combinedRaw, activeOrderForParsing, LabelType.AUTO).let {
            val withBarcode = if (detectedBarcode.isBlank()) it else it.copy(barcode = detectedBarcode)
            spatialFields?.let { fields ->
                withBarcode.copy(
                    nalog = fields.nalog.ifBlank { withBarcode.nalog },
                    packageNo = fields.packageNo.ifBlank { withBarcode.packageNo },
                    article = fields.article.ifBlank { withBarcode.article },
                    size = fields.size.ifBlank { withBarcode.size },
                    quantity = fields.quantity.ifBlank { withBarcode.quantity }
                )
            } ?: withBarcode
        }

        spatialFields?.rows.orEmpty().forEach { row ->
            val cleanSize = row.first.trim().uppercase(Locale.ROOT)
            val cleanQty = row.second.filter(Char::isDigit)
            if (cleanSize.isNotBlank() && (cleanQty.toIntOrNull() ?: 0) > 0) {
                val rowKey = "$cleanSize|$cleanQty"
                accumulatedSizeRows[rowKey] = cleanSize to cleanQty
                sizeRowVotes[rowKey] = (sizeRowVotes[rowKey] ?: 0) + 1
            }
        }

        val qty = parsed.quantity.filter { it.isDigit() }.toIntOrNull() ?: 0
        rememberBestPartial(parsed, combinedRaw, bitmap)
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
            val confirmedRows = accumulatedSizeRows.filterKeys { (sizeRowVotes[it] ?: 0) >= 2 }.values
                .toList().ifEmpty { accumulatedSizeRows.values.toList() }
            showScanConfirmation(
                parsed,
                combinedRaw,
                bitmap,
                confirmedRows.ifEmpty { spatialFields?.rows.orEmpty() }
            )
        }
    }

    private fun rememberBestPartial(parsed: ParsedLabel, raw: String, bitmap: Bitmap) {
        val score = listOf(parsed.nalog, parsed.packageNo, parsed.article, parsed.size,
            parsed.quantity, parsed.customer, parsed.barcode).count { it.isNotBlank() }
        if (score > bestPartialScore || (score == bestPartialScore && raw.length > bestPartialRaw.length)) {
            bestPartial = parsed
            bestPartialRaw = raw
            bestPartialBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            bestPartialScore = score
        }
    }

    private fun showScanConfirmation(
        parsed: ParsedLabel,
        raw: String,
        bitmap: Bitmap,
        detectedRows: List<Pair<String, String>> = emptyList()
    ) {
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
        val master = field("Master number", parsed.article)
        val packageNo = field("Пакет", parsed.packageNo)
        val rowEditors = mutableListOf<Pair<EditText, EditText>>()

        fun addSizeRow(sizeValue: String = "", quantityValue: String = "") {
            val number = rowEditors.size + 1
            val sizeEditor = field("Големина $number", sizeValue)
            val qtyEditor = field("Парчиња $number", quantityValue, true)
            rowEditors += sizeEditor to qtyEditor
        }

        detectedRows.ifEmpty { listOf(parsed.size to parsed.quantity) }
            .distinct()
            .forEach { (sizeValue, quantityValue) -> addSizeRow(sizeValue, quantityValue) }

        box.addView(Button(this).apply {
            text = "+ ДОДАЈ ДРУГА ГОЛЕМИНА"
            setOnClickListener { addSizeRow() }
        })

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
                val rowsToSave = rowEditors.mapNotNull { (sizeEditor, qtyEditor) ->
                    val sizeValue = sizeEditor.text.toString().trim()
                    val quantityValue = qtyEditor.text.toString().filter(Char::isDigit)
                    if (sizeValue.isBlank() && quantityValue.isBlank()) null else sizeValue to quantityValue
                }
                if (order.text.isBlank() || rowsToSave.isEmpty() ||
                    rowsToSave.any { it.first.isBlank() || (it.second.toIntOrNull() ?: 0) <= 0 }) {
                    toast("Провери го налогот и сите големини со нивните парчиња.")
                    return@setOnClickListener
                }
                nalogInput.setText(formatOrderNumber(order.text.toString().trim()))
                nalogInput.isEnabled = false
                articleInput.setText(master.text.toString().trim())
                var confirmedPackage = packageNo.text.toString().trim()
                if (confirmedPackage.isBlank()) {
                    confirmedPackage = (db.getForDocument(activeDocumentId)
                        .map { it.packageNo }.distinct().size + 1).toString()
                }
                packageInput.setText(confirmedPackage)
                rawTextInput.setText(raw)
                latestPhotoPath = saveFrame(bitmap)

                var allSaved = true
                rowsToSave.forEach { (sizeValue, quantityValue) ->
                    displayedRecord = null
                    sizeInput.setText(sizeValue)
                    quantityInput.setText(quantityValue)
                    if (!saveCurrent(manual = false)) allSaved = false
                }
                if (allSaved) {
                    playSuccessSound()
                    dialog.dismiss()
                    if (isLastPackage(packageInput.text.toString())) {
                        AlertDialog.Builder(this)
                            .setTitle("Последен пакет")
                            .setMessage("Ова е последниот пакет според KARTON NR. Да го затворам налогот и да зачувам Word документ?")
                            .setPositiveButton("ЗАТВОРИ И ЗАЧУВАЈ") { _, _ -> closeCurrentOrder() }
                            .setNegativeButton("ПРОДОЛЖИ") { _, _ ->
                                stopScan("✓ Зачувано — притисни СКЕНИРАЈ за следен пакет")
                            }
                            .show()
                    } else {
                        stopScan("✓ Потврдено и зачувано — стави следна етикета")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun isLastPackage(value: String): Boolean {
        val match = Regex("(\\d+)\\s*/\\s*(\\d+)").find(value) ?: return false
        val current = match.groupValues[1].toIntOrNull() ?: return false
        val total = match.groupValues[2].toIntOrNull() ?: return false
        return total > 0 && current == total
    }

    private fun normalizeOrder(value: String): String =
        value.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    /**
     * PA/POS on handwritten WF labels contains a five-digit order followed by
     * a slash and position/total (for example 00 289/10). Only the first five
     * digits are the order; the app expands them to 26-010-00289.
     */
    private fun normalizeDetectedOrder(value: String): String {
        val beforeSlash = value.substringBefore('/').filter(Char::isDigit)
        if (beforeSlash.length == 5) return "26-010-$beforeSlash"
        val digits = value.filter(Char::isDigit)
        return when {
            digits.length == 5 -> "26-010-$digits"
            digits.length >= 10 -> "${digits.take(2)}-${digits.substring(2, 5)}-${digits.substring(5, 10)}"
            else -> value
        }
    }

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
                val partial = bestPartial
                val partialBitmap = bestPartialBitmap
                if (partial != null && partialBitmap != null && bestPartialScore > 0) {
                    scanRequested = false
                    scanButton.isEnabled = true
                    statusText.text = "Не е прочитано сè — провери и дополни"
                    showScanConfirmation(partial, bestPartialRaw, partialBitmap)
                } else {
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 450)
                    vibrate(250)
                    stopScan("Не прочитав ништо — намести ја етикетата и притисни СКЕНИРАЈ")
                }
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

    private fun restoreClosedOrderIfRequested() {
        val requestedDocumentId = intent.getStringExtra("reopenDocumentId").orEmpty()
        if (requestedDocumentId.isBlank()) return
        val records = if (requestedDocumentId.startsWith("legacy:")) {
            db.getForOrder(requestedDocumentId.removePrefix("legacy:"))
        } else {
            db.getForDocument(requestedDocumentId)
        }
        if (records.isEmpty()) return
        activeDocumentId = if (requestedDocumentId.startsWith("legacy:")) newDocumentId() else requestedDocumentId
        val nalog = records.first().nalog
        nalogInput.setText(nalog)
        nalogInput.isEnabled = false
        clearPackageFields(keepNalog = true)
        displayedRecord = null
        lastSavedFingerprint = ""
        statusText.text = "Налог $nalog е повторно отворен — скенирај го дополнителниот пакет"
    }

    private fun startNewOrder() {
        activeDocumentId = newDocumentId()
        lastSavedFingerprint = ""
        clearPackageFields(keepNalog = false)
        nalogInput.isEnabled = true
        scanRequested = false
        frozenFrameProcessing.set(false)
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

        val warehouseInput = EditText(this).apply {
            hint = "На пример: Магацин 1"
            setSingleLine(true)
            val lastWarehouse = getSharedPreferences("wf_settings", MODE_PRIVATE)
                .getString("last_warehouse", "").orEmpty()
            setText(records.firstOrNull()?.warehouse?.ifBlank { lastWarehouse }.orEmpty())
            setSelection(text.length)
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Од кој магацин е примен налогот?")
            .setMessage("Магацинот ќе биде запишан во налогот и во документот.")
            .setView(warehouseInput)
            .setPositiveButton("ПРОДОЛЖИ", null)
            .setNegativeButton("ОТКАЖИ", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val warehouse = warehouseInput.text.toString().trim()
                        if (warehouse.isBlank()) {
                            warehouseInput.error = "Внеси магацин"
                            return@setOnClickListener
                        }
                        getSharedPreferences("wf_settings", MODE_PRIVATE).edit()
                            .putString("last_warehouse", warehouse).apply()
                        val updatedRecords = records.map { it.copy(warehouse = warehouse) }
                        updatedRecords.forEach(db::update)
                        dialog.dismiss()
                        launchWordDocument(nalog, updatedRecords)
                    }
                }
                dialog.show()
            }
    }

    private fun launchWordDocument(nalog: String, records: List<PackageRecord>) {
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
