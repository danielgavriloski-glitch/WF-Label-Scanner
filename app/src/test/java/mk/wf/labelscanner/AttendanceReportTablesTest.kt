package mk.wf.labelscanner

import org.junit.Assert.*
import org.junit.Test

class AttendanceReportTablesTest {
    private val hour = 3600000L
    private fun day(id: String, name: String, date: String, hours: Long) =
        AttendanceReportDay(id, name, date, "${date.takeLast(2)}.10.2026", "понеделник", hours * hour)

    @Test fun totalsKeepDuplicateNamesSeparateAndExcludeUnselectedEmployees() {
        val result = AttendanceReportTables.build(listOf("a" to "Петар", "b" to "Петар"), listOf(
            day("a", "Петар", "20261005", 8), day("a", "Петар", "20261006", 9),
            day("b", "Петар", "20261005", 7), day("other", "Друго лице", "20261005", 50)
        ))
        assertEquals("17ч 0м", result.summary[1][2])
        assertEquals("7ч 0м", result.summary[2][2])
        assertEquals("24ч 0м", result.summary.last()[2])
        assertEquals("3", result.summary.last()[1])
        assertEquals(4, result.daily.size)
    }

    @Test fun dailyRowsAreOrderedByDateThenEmployee() {
        val result = AttendanceReportTables.build(listOf("a" to "Ана", "b" to "Петар"), listOf(
            day("b", "Петар", "20261006", 8), day("b", "Петар", "20261005", 8), day("a", "Ана", "20261005", 8)
        ))
        assertEquals(listOf("05.10.2026", "понеделник", "Ана", "8ч 0м"), result.daily[1].take(4))
        assertEquals("Петар", result.daily[2][2])
        assertEquals("06.10.2026", result.daily[3][0])
    }

    @Test fun leaveAndPauseDoNotInflateActualWorkedTotal() {
        val result = AttendanceReportTables.build(listOf("a" to "Ана"), listOf(
            day("a", "Ана", "20261005", 8).copy(pause = hour / 2),
            day("a", "Ана", "20261006", 0).copy(covered = 8 * hour, regular = 8 * hour, status = "Годишен одмор")
        ))
        assertEquals(listOf("Ана", "1", "8ч 0м", "0ч 30м", "8ч 0м", "16ч 0м", "0ч 0м"), result.summary[1])
    }

    @Test fun accountantExportsOnlyAllowedRegularHours() {
        val record = day("a", "Ана", "20261005", 12).copy(regular = 8 * hour, overtime = 4 * hour)
        val result = AttendanceReportTables.build(listOf("a" to "Ана"), listOf(record), accountant = true)
        assertEquals(listOf("Датум", "Ден", "Вработен", "Редовни часови"), result.daily.first())
        assertEquals("8ч 0м", result.daily[1].last())
        assertEquals(listOf("Ана", "1", "8ч 0м"), result.summary[1])
        assertFalse(result.daily.flatten().any { it == "12ч 0м" || it == "4ч 0м" })
    }

    @Test fun accountantExportBoundaryAlwaysCapsADayAtEightHours() {
        val result = AttendanceReportTables.build(listOf("a" to "Ана"), listOf(day("a", "Ана", "20261005", 15)), accountant = true)
        assertEquals("8ч 0м", result.daily[1].last())
        assertEquals("8ч 0м", result.summary.last().last())
    }

    @Test fun totalAddsDurationsBeforeRoundingToMinutes() {
        val result = AttendanceReportTables.build(listOf("a" to "Ана"), listOf(
            day("a", "Ана", "20261005", 0).copy(worked = 90000L, regular = 90000L),
            day("a", "Ана", "20261006", 0).copy(worked = 90000L, regular = 90000L)
        ))
        assertEquals("0ч 3м", result.summary.last()[2])
    }

    @Test fun accountantCountsActualShortHoursAndDoesNotPayLeaveAsWorkedHours() {
        val result = AttendanceReportTables.build(listOf("a" to "Ана"), listOf(
            day("a", "Ана", "20261005", 6).copy(worked = 6 * hour + hour / 2, regular = 8 * hour),
            day("a", "Ана", "20261006", 0).copy(regular = 8 * hour, covered = 8 * hour, status = "Годишен одмор")
        ), accountant = true)
        assertEquals("6ч 30м", result.summary[1].last())
        assertEquals("0ч 0м", result.daily.last().last())
    }

    @Test fun accountantCapsCombinedSameDaySessionsOnce() {
        val result = AttendanceReportTables.build(listOf("a" to "Ана"), listOf(
            day("a", "Ана", "20261005", 5), day("a", "Ана", "20261005", 5)
        ), accountant = true)
        assertEquals(2, result.daily.size)
        assertEquals("8ч 0м", result.summary[1].last())
    }
}
