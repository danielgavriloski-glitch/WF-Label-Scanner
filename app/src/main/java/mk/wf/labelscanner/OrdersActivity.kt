package mk.wf.labelscanner

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Locale

class OrdersActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var list: ListView
    private var documentIds: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)
        db = AppDatabase(this)
        list = findViewById(R.id.ordersList)
        findViewById<Button>(R.id.closeOrdersButton).setOnClickListener { finish() }
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, ReviewActivity::class.java).putExtra("documentId", documentIds[position]))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val groups = db.getAll().groupBy { it.documentId.ifBlank { "legacy:${it.nalog}" } }
            .toList()
            .sortedByDescending { (_, rows) -> rows.maxOfOrNull { it.createdAt }.orEmpty() }
        documentIds = groups.map { it.first }
        list.adapter = object : BaseAdapter() {
            override fun getCount() = groups.size
            override fun getItem(position: Int) = groups[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val (_, packages) = groups[position]
                return orderCard(packages.firstOrNull()?.nalog.orEmpty(), packages)
            }
        }
    }

    private fun orderCard(nalog: String, packages: List<PackageRecord>): View {
            val rawDate = packages.maxOfOrNull { it.createdAt }.orEmpty()
            val date = runCatching {
                val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val output = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
                output.format(input.parse(rawDate)!!)
            }.getOrDefault(rawDate)
        fun text(value: String, bold: Boolean = false) = TextView(this).apply {
            this.text = value; setTextColor(Color.rgb(20, 20, 20)); textSize = 15f
            setPadding(10, 9, 10, 9)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        fun table(headers: List<String>, rows: List<List<String>>) = TableLayout(this).apply {
            isStretchAllColumns = true
            addView(TableRow(this@OrdersActivity).apply {
                setBackgroundColor(Color.rgb(245, 196, 0))
                headers.forEach { addView(text(it, true)) }
            })
            rows.forEachIndexed { index, values -> addView(TableRow(this@OrdersActivity).apply {
                if (index % 2 == 1) setBackgroundColor(Color.rgb(245, 245, 245))
                values.forEach { addView(text(it)) }
            }) }
        }
        val details = packages.sortedBy { it.packageNo.toIntOrNull() ?: Int.MAX_VALUE }.map {
            listOf(nalog, it.size.ifBlank { "—" }, it.article.ifBlank { "—" })
        }
        val totals = packages.groupBy { it.size.ifBlank { "—" } }.toSortedMap().map { (size, rows) ->
            listOf(size, rows.sumOf { it.quantity }.toString())
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundResource(R.drawable.card_background)
            addView(text("НАЛОГ $nalog", true))
            addView(text(date))
            addView(table(listOf("Налог", "Големина", "Master number"), details))
            addView(text("ВКУПЕН ЗБИР", true))
            addView(table(listOf("Големина", "Парчиња"), totals))
            addView(text("Вкупно пакети: ${packages.size}     Вкупно парчиња: ${packages.sumOf { it.quantity }}", true))
            addView(Button(this@OrdersActivity).apply {
                text = "ИЗБРИШИ НАЛОГ"
                setOnClickListener {
                    AlertDialog.Builder(this@OrdersActivity)
                        .setTitle("Избриши налог ${nalog}?")
                        .setMessage("Ќе се избрише целиот документ и сите ${packages.size} зачувани пакети.")
                        .setPositiveButton("ИЗБРИШИ") { _, _ ->
                            if (packages.first().documentId.isBlank()) db.deleteOrder(nalog)
                            else db.deleteDocument(packages.first().documentId)
                            refresh()
                        }
                        .setNegativeButton("ОТКАЖИ", null)
                        .show()
                }
            })
        }
    }
}
