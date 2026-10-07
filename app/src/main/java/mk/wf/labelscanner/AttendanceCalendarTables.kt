package mk.wf.labelscanner

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AttendanceCalendarMonth(val monthKey: String, val title: String, val dates: List<LocalDate>, val rows: List<List<String>>,
    val durations: List<List<Long?>>) {
    fun excelSection() = ReportSection("Mesec_${monthKey.replace("-", "_")}", title, rows, calendar = true, calendarDurations = durations)

    /** The phone and Excel show the full month; PDF keeps readable Monday-Sunday blocks. */
    fun pdfSections(): List<ReportSection> = dates.indices.groupBy { dates[it].minusDays(dates[it].dayOfWeek.value.toLong() - 1) }
        .values.mapIndexed { index, columns ->
            val partRows = rows.map { row -> listOf(row.first()) + columns.map { row[it + 1] } + row.takeLast(2) }
            val start = dates[columns.first()].format(DateTimeFormatter.ofPattern("dd.MM"))
            val end = dates[columns.last()].format(DateTimeFormatter.ofPattern("dd.MM"))
            ReportSection("${excelSection().sheetName}_${index + 1}", "$title | $start - $end", partRows, calendar = true)
        }
}

object AttendanceCalendarTables {
    const val LEGEND = "Часови: ч:мм | НП: нема пријава | В: викенд | О: одмор | СД: слободен ден | ОП: оправдано | ·: иден ден"
    private val keyFormat = DateTimeFormatter.BASIC_ISO_DATE
    private val mk = Locale.forLanguageTag("mk-MK")

    fun compact(millis: Long): String {
        val minutes = millis.coerceAtLeast(0L) / 60000L
        return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }

    fun build(employees: List<Pair<String, String>>, days: List<AttendanceReportDay>, fromKey: String, toKey: String,
        accountant: Boolean = false, asOfKey: String = LocalDate.now().format(keyFormat)): List<AttendanceCalendarMonth> {
        val from = LocalDate.parse(fromKey, keyFormat)
        val to = LocalDate.parse(toKey, keyFormat)
        val today = LocalDate.parse(asOfKey, keyFormat)
        require(!to.isBefore(from)) { "Почетниот датум треба да биде пред крајниот." }
        val visible = AttendanceReportTables.visibleDays(employees, days, accountant)
            .filter { it.dateKey >= fromKey && it.dateKey <= toKey && it.dateKey <= asOfKey }
            .associateBy { it.employeeId to it.dateKey }
        val dates = generateSequence(from) { if (it < to) it.plusDays(1) else null }.toList()
        return dates.groupBy { it.toString().take(7) }.map { (month, monthDates) ->
            val headers = listOf("Вработен") + monthDates.map {
                it.format(DateTimeFormatter.ofPattern("dd.MM")) + "\n" + it.format(DateTimeFormatter.ofPattern("EEE", mk))
            } + listOf("Денови со работа", "Вкупно месец")
            val rows = mutableListOf(headers)
            val durations = mutableListOf<List<Long?>>(List(headers.size) { null })
            employees.forEach { (id, name) ->
                val records = monthDates.mapNotNull { visible[id to it.format(keyFormat)] }
                val cells = monthDates.map { date ->
                    val d = visible[id to date.format(keyFormat)]
                    when {
                        date > today -> "·"
                        d != null && (d.present || d.worked > 0L) -> compact(d.worked)
                        d?.status?.contains("годишен одмор", ignoreCase = true) == true -> "О"
                        d?.status?.contains("слободен ден", ignoreCase = true) == true -> "СД"
                        d?.status?.equals("Оправдано отсуство", ignoreCase = true) == true -> "ОП"
                        date.dayOfWeek.value >= 6 -> "В"
                        else -> "НП"
                    }
                }
                rows.add(listOf(name) + cells + listOf(records.count { it.worked > 0L }.toString(), compact(records.sumOf { it.worked })))
                durations.add(listOf<Long?>(null) + monthDates.map { date ->
                    visible[id to date.format(keyFormat)]?.takeIf { it.present || it.worked > 0L }?.worked
                } + listOf(null, records.sumOf { it.worked }))
            }
            val dailyTotals = monthDates.map { date ->
                if (date > today) "·" else compact(employees.sumOf { visible[it.first to date.format(keyFormat)]?.worked ?: 0L })
            }
            val monthRecords = visible.values.filter { it.dateKey.take(6) == month.replace("-", "") }
            rows.add(listOf("ВКУПНО - сите вработени") + dailyTotals + listOf(monthRecords.count { it.worked > 0L }.toString(), compact(monthRecords.sumOf { it.worked })))
            durations.add(listOf<Long?>(null) + monthDates.map { date ->
                if (date > today) null else employees.sumOf { visible[it.first to date.format(keyFormat)]?.worked ?: 0L }
            } + listOf(null, monthRecords.sumOf { it.worked }))
            AttendanceCalendarMonth(month, monthDates.first().format(DateTimeFormatter.ofPattern("MMMM yyyy", mk)), monthDates, rows, durations)
        }
    }
}
