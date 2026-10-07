package mk.wf.labelscanner

import org.junit.Assert.*
import org.junit.Test

class AttendanceCalendarTablesTest {
    private val hour = 3600000L
    private val people = listOf("a" to "Ана", "b" to "Борис")
    private fun day(id: String, key: String, worked: Long) = AttendanceReportDay(id, id, key, key, "", worked)
    private fun build(days: List<AttendanceReportDay>, accounting: Boolean = false, from: String = "20261001", to: String = "20261031", today: String = "20261007") =
        AttendanceCalendarTables.build(people, days, from, to, accounting, today)

    @Test fun calendarShowsPresenceMissingLeaveWeekendAndFutureWithoutInventingHours() {
        val result = build(listOf(day("a", "20261005", 8 * hour), day("a", "20261006", 0).copy(status = "Одобрен годишен одмор"))).single()
        assertEquals(34, result.rows.first().size)
        assertEquals("НП", result.rows[1][1])
        assertEquals("В", result.rows[1][3])
        assertEquals("8:00", result.rows[1][5])
        assertEquals("О", result.rows[1][6])
        assertEquals("НП", result.rows[1][7])
        assertEquals("·", result.rows[1][8])
        assertEquals(listOf("1", "8:00"), result.rows[1].takeLast(2))
        assertEquals("НП", result.rows[2][5])
        assertEquals("0:00", result.rows[2].last())
    }

    @Test fun accountingCapsAfterCombiningSessionsAndPreservesShortDays() {
        val days = listOf(day("a", "20261005", 5 * hour), day("a", "20261005", 5 * hour), day("a", "20261006", 6 * hour + hour / 2))
        val month = build(days, true).single()
        assertEquals("8:00", month.rows[1][5])
        assertEquals("6:30", month.rows[1][6])
        assertEquals("14:30", month.rows[1].last())
        assertFalse(month.rows.flatten().any { it == "10:00" })
        assertEquals(14 * hour + hour / 2, month.durations[1].last())
    }

    @Test fun accountingHasNoAdditionalWeeklyReduction() {
        val days = (5..10).map { day("a", "202610" + it.toString().padStart(2, '0'), 10 * hour) }
        val month = build(days, true, today = "20261011").single()
        assertEquals("48:00", month.rows[1].last())
        assertEquals("8:00", month.rows[1][10])
    }

    @Test fun duplicateNamesAndSelectedEmployeesStaySeparate() {
        val result = AttendanceCalendarTables.build(listOf("a" to "Петар", "b" to "Петар"), listOf(
            day("a", "20261005", 9 * hour), day("b", "20261005", 7 * hour), day("other", "20261005", 20 * hour)
        ), "20261001", "20261031", asOfKey = "20261007").single()
        assertEquals("9:00", result.rows[1].last())
        assertEquals("7:00", result.rows[2].last())
        assertEquals("16:00", result.rows.last().last())
    }

    @Test fun separateMonthsAndPartialPeriodsOnlyTotalIncludedDates() {
        val months = build(listOf(day("a", "20260929", hour), day("a", "20260930", 8 * hour), day("a", "20261001", 7 * hour)), from = "20260930", to = "20261002")
        assertEquals(2, months.size)
        assertEquals("8:00", months[0].rows[1].last())
        assertEquals("7:00", months[1].rows[1].last())
        assertEquals(1, months[0].dates.size)
    }

    @Test fun leapMonthAndReadablePdfBlocksCoverEachDateOnce() {
        val month = build(emptyList(), from = "20240201", to = "20240229", today = "20261007").single()
        assertEquals(29, month.dates.size)
        val parts = month.pdfSections()
        assertTrue(parts.all { it.rows.first().size <= 10 })
        assertEquals(29, parts.sumOf { it.rows.first().size - 3 })
        assertEquals(month.rows.first().drop(1).dropLast(2), parts.flatMap { it.rows.first().drop(1).dropLast(2) })
    }

    @Test fun zeroMinuteCheckInIsPresentAndUnpaidLeaveIsNotJustified() {
        val month = build(listOf(day("a", "20261005", 0).copy(present = true), day("a", "20261006", 0).copy(status = "Неоправдано отсуство"))).single()
        assertEquals("0:00", month.rows[1][5])
        assertEquals("НП", month.rows[1][6])
    }
}
