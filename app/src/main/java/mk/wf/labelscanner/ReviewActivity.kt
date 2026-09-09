package mk.wf.labelscanner

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ReviewActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var list: ListView
    private var records: List<PackageRecord> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)
        db = AppDatabase(this)
        list = findViewById(R.id.reviewList)
        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }
        list.setOnItemClickListener { _, _, position, _ -> showDetails(records[position]) }
        refresh()
    }

    private fun refresh() {
        records = db.getAll()
        val lines = records.map {
            "#${it.id}  Налог ${it.nalog}  • Пакет ${it.packageNo}  • ${it.size.ifBlank { "-" }}  • Qty ${it.quantity}"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lines)
    }

    private fun showDetails(r: PackageRecord) {
        val message = """
            Налог: ${r.nalog}
            Пакет: ${r.packageNo}
            Артикл: ${r.article}
            Големина: ${r.size}
            Количина: ${r.quantity}
            Клиент: ${r.customer}
            Barcode: ${r.barcode}
            Датум: ${r.createdAt}
            Фото: ${r.photoPath}

            Цел OCR текст:
            ${r.rawText}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Пакет #${r.id}")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("ИЗМЕНИ") { _, _ -> editRecord(r) }
            .setNegativeButton("ИЗБРИШИ") { _, _ -> db.delete(r.id); refresh() }
            .show()
    }

    private fun editRecord(r: PackageRecord) {
        val wrap = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 8, 32, 8)
        }
        wrap.addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun field(hint: String, value: String, number: Boolean = false, lines: Int = 1): EditText {
            return EditText(this).apply {
                this.hint = hint
                setText(value)
                inputType = if (number) InputType.TYPE_CLASS_NUMBER else if (lines > 1) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
                minLines = lines
                box.addView(this)
            }
        }

        val nalog = field("Налог", r.nalog)
        val paket = field("Пакет", r.packageNo)
        val article = field("Артикл", r.article)
        val size = field("Големина", r.size)
        val qty = field("Количина", r.quantity.toString(), number = true)
        val customer = field("Клиент", r.customer)
        val barcode = field("Barcode", r.barcode)
        val raw = field("Цел OCR текст", r.rawText, lines = 5)

        AlertDialog.Builder(this)
            .setTitle("Измени пакет #${r.id}")
            .setView(wrap)
            .setPositiveButton("ЗАЧУВАЈ") { _, _ ->
                db.update(
                    r.copy(
                        nalog = nalog.text.toString().trim(),
                        packageNo = paket.text.toString().trim(),
                        article = article.text.toString().trim(),
                        size = size.text.toString().trim(),
                        quantity = qty.text.toString().toIntOrNull() ?: 0,
                        customer = customer.text.toString().trim(),
                        barcode = barcode.text.toString().trim(),
                        rawText = raw.text.toString()
                    )
                )
                refresh()
            }
            .setNegativeButton("ОТКАЖИ", null)
            .show()
    }
}
