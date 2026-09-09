package mk.wf.labelscanner

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Locale

class OrdersActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var list: ListView
    private var orderNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orders)
        db = AppDatabase(this)
        list = findViewById(R.id.ordersList)
        findViewById<Button>(R.id.closeOrdersButton).setOnClickListener { finish() }
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(Intent(this, ReviewActivity::class.java).putExtra("nalog", orderNames[position]))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val groups = db.getAll().groupBy { it.nalog }
            .toList()
            .sortedByDescending { (_, rows) -> rows.maxOfOrNull { it.createdAt }.orEmpty() }
        orderNames = groups.map { it.first }
        val rows = groups.map { (nalog, packages) ->
            val rawDate = packages.maxOfOrNull { it.createdAt }.orEmpty()
            val date = runCatching {
                val input = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val output = SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale.getDefault())
                output.format(input.parse(rawDate)!!)
            }.getOrDefault(rawDate)
            val bySize = packages.groupBy { it.size.ifBlank { "Непозната" } }
                .entries.sortedBy { it.key }
                .joinToString("\n") { (size, rows) ->
                    "Големина $size: ${rows.size} пакети • ${rows.sumOf { it.quantity }} парчиња"
                }
            "$date\nНалог: $nalog\nВкупно: ${packages.size} пакети • ${packages.sumOf { it.quantity }} парчиња\n$bySize"
        }
        list.adapter = ArrayAdapter(this, R.layout.order_list_item, R.id.orderLineText, rows)
    }
}
