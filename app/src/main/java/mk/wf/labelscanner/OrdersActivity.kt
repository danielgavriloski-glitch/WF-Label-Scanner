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

            val masterNumbers = packages.mapNotNull { extractMasterNumber(it).takeIf(String::isNotBlank) }
                .distinct()
            val masterLine = if (masterNumbers.isEmpty()) "-" else masterNumbers.joinToString(", ")

            val packageLines = packages
                .sortedWith(compareBy<PackageRecord> { packageSortKey(it.packageNo) }.thenBy { it.packageNo })
                .joinToString("\n") { row ->
                    val size = row.size.ifBlank { "?" }
                    val packageNo = row.packageNo.ifBlank { "?" }
                    "Пакет $packageNo: Големина $size — ${row.quantity} парчиња"
                }

            val bySize = packages.groupBy { it.size.ifBlank { "Непозната" } }
                .entries.sortedBy { it.key }
                .joinToString("\n") { (size, sizeRows) ->
                    val packageNos = sizeRows.map { it.packageNo.ifBlank { "?" } }.distinct().joinToString(", ")
                    "Големина $size: ${sizeRows.sumOf { it.quantity }} парчиња • пакети: $packageNos"
                }

            buildString {
                append(date)
                append("\nНалог: ").append(nalog)
                append("\nMaster number: ").append(masterLine)
                append("\n\nПАКЕТИ\n").append(packageLines)
                append("\n\nВКУПНО ПО ГОЛЕМИНА\n").append(bySize)
                append("\n\nВКУПНО: ").append(packages.size).append(" пакети • ")
                    .append(packages.sumOf { it.quantity }).append(" парчиња")
            }
        }

        list.adapter = ArrayAdapter(this, R.layout.order_list_item, R.id.orderLineText, rows)
    }

    private fun packageSortKey(value: String): Int =
        Regex("\\d+").find(value)?.value?.toIntOrNull() ?: Int.MAX_VALUE

    private fun extractMasterNumber(record: PackageRecord): String {
        val patterns = listOf(
            Regex("(?i)master\\s*(?:number|no|nr|#)?\\s*[:=#-]?\\s*([A-Z0-9./-]{3,})"),
            Regex("(?i)master[-_ ]?nr\\.?\\s*[:=#-]?\\s*([A-Z0-9./-]{3,})"),
            Regex("(?i)model\\s*[:=#-]?\\s*([A-Z0-9./-]{3,})"),
            Regex("(?i)modell\\s*[:=#-]?\\s*([A-Z0-9./-]{3,})")
        )
        patterns.forEach { rx ->
            rx.find(record.rawText)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return record.article.trim()
    }
}
