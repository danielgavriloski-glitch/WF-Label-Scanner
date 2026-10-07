package mk.wf.labelscanner

data class ReportSection(val sheetName: String, val title: String, val rows: List<List<String>>)

data class AttendanceReportDay(
    val employeeId: String,
    val employeeName: String,
    val dateKey: String,
    val dateLabel: String,
    val dayName: String,
    val worked: Long,
    val pause: Long = 0L,
    val regular: Long = worked,
    val overtime: Long = 0L,
    val covered: Long = 0L,
    val status: String = ""
)

data class AttendanceReportTables(val daily: List<List<String>>, val summary: List<List<String>>) {
    companion object {
        fun duration(millis: Long): String {
            val minutes = millis.coerceAtLeast(0L) / 60000L
            return "${minutes / 60}ч ${minutes % 60}м"
        }

        fun build(
            employees: List<Pair<String, String>>,
            days: List<AttendanceReportDay>,
            accountant: Boolean = false
        ): AttendanceReportTables {
            // Employee IDs keep two people with the same name separate in the totals.
            val selectedIds = employees.map { it.first }.toSet()
            val selected = days.filter { it.employeeId in selectedIds }.map { day ->
                if (accountant) {
                    val allowed = day.regular.coerceIn(0L, 8L * 3600000L)
                    day.copy(worked = allowed, regular = allowed, pause = 0L, overtime = 0L, covered = 0L, status = "")
                } else day
            }
            val sorted = selected.sortedWith(compareBy({ it.dateKey }, { it.employeeName }, { it.employeeId }))
            val daily = mutableListOf(
                if (accountant) listOf("Датум", "Ден", "Вработен", "Редовни часови")
                else listOf("Датум", "Ден", "Вработен", "Работено", "Пауза", "Редовен фонд", "Прекувремено", "Статус")
            )
            sorted.forEach { d ->
                daily.add(
                    if (accountant) listOf(d.dateLabel, d.dayName, d.employeeName, duration(d.regular))
                    else listOf(d.dateLabel, d.dayName, d.employeeName, duration(d.worked),
                        duration(d.pause), duration(d.regular), duration(d.overtime), d.status)
                )
            }
            val summary = mutableListOf(
                if (accountant) listOf("Вработен", "Денови со работа", "Редовни часови")
                else listOf("Вработен", "Денови со работа", "Работено", "Пауза", "Одмор/оправдано", "Редовен фонд", "Прекувремено")
            )
            fun totalRow(name: String, records: List<AttendanceReportDay>): List<String> {
                val workedDays = records.filter { (if (accountant) it.regular else it.worked) > 0L }
                    .distinctBy { it.employeeId to it.dateKey }.size
                return if (accountant) listOf(name, workedDays.toString(), duration(records.sumOf { it.regular }))
                else listOf(name, workedDays.toString(), duration(records.sumOf { it.worked }),
                    duration(records.sumOf { it.pause }), duration(records.sumOf { it.covered }),
                    duration(records.sumOf { it.regular }), duration(records.sumOf { it.overtime }))
            }
            employees.forEach { (id, name) -> summary.add(totalRow(name, selected.filter { it.employeeId == id })) }
            summary.add(totalRow("ВКУПНО - сите вработени", selected))
            return AttendanceReportTables(daily, summary)
        }
    }
}
