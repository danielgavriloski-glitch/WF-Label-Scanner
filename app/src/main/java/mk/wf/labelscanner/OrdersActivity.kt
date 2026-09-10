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
    private var documentRecords: List<List<PackageRecord>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)
        db = AppDatabase(this)
        list = findViewById(R.id.ordersList)
        findViewById<Button>(R.id.closeOrdersButton).setOnClickListener { finish() }
        list.setOnItemClickListener { _, _, position, _ ->
            val uriText = documentRecords.getOrNull(position)?.firstOrNull()?.documentUri.orEmpty()
            if (uriText.isBlank()) {
                android.widget.Toast.makeText(this, "Овој налог сè уште нема зачуван Word документ.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(android.net.Uri.parse(uriText), "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }.onFailure {
                    android.widget.Toast.makeText(this, "Нема апликација за отворање Word документ.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
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
        documentRecords = groups.map { it.second }
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
            this.text = value
            setTextColor(Color.rgb(20, 20, 20))
            textSize = if (bold) 18f else 15f
            setPadding(10, 8, 10, 8)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundResource(R.drawable.card_background)
            addView(text("НАЛОГ $nalog", true))
            addView(text("Датум и време: $date"))
            addView(text("${packages.size} пакети • допри за да го отвориш Word документот"))
            addView(Button(this@OrdersActivity).apply {
                text = "ИЗБРИШИ НАЛОГ"
                setOnClickListener {
                    AlertDialog.Builder(this@OrdersActivity)
                        .setTitle("Избриши налог $nalog?")
                        .setMessage("Ќе се избрише документот од листата и сите ${packages.size} пакети.")
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
