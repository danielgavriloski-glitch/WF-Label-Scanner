package mk.wf.labelscanner

data class ReportSection(val sheetName: String, val title: String, val rows: List<List<String>>, val calendar: Boolean = false,
    val calendarDurations: List<List<Long?>> = emptyList())

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
    val status: String = "",
    val present: Boolean = worked > 0L
)

data class AttendanceReportTables(val daily: List<List<String>>, val summary: List<List<String>>) {
    companion object {
        fun duration(millis: Long): String {
            val minutes = millis.coerceAtLeast(0L) / 60000L
            return "${minutes / 60}ч ${minutes % 60}м"
        }

        /** Combine all sessions first, then apply the accounting limit once per employee/day. */
        fun visibleDays(employees: List<Pair<String, String>>, days: List<AttendanceReportDay>, accountant: Boolean): List<AttendanceReportDay> {
            val names = employees.toMap()
            return days.filter { it.employeeId in names }.groupBy { it.employeeId to it.dateKey }.values.map { sessions ->
                val first = sessions.first()
                val worked = sessions.sumOf { it.worked.coerceAtLeast(0L) }
                val combined = first.copy(employeeName = names.getValue(first.employeeId), worked = worked,
                    pause = sessions.sumOf { it.pause }, regular = sessions.sumOf { it.regular },
                    overtime = sessions.sumOf { it.overtime }, covered = sessions.sumOf { it.covered },
                    present = sessions.any { it.present || it.worked > 0L })
                if (accountant) {
                    val allowed = worked.coerceIn(0L, 8L * 3600000L)
                    combined.copy(worked = allowed, regular = allowed, pause = 0L, overtime = 0L, covered = 0L,
                        status = if (combined.present) "Присутен" else combined.status)
                } else combined
            }
        }

        fun build(
            employees: List<Pair<String, String>>,
            days: List<AttendanceReportDay>,
            accountant: Boolean = false
        ): AttendanceReportTables {
            // Employee IDs keep two people with the same name separate in the totals.
            val selected = visibleDays(employees, days, accountant)
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
