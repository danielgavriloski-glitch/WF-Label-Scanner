package mk.wf.labelscanner

import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AttendanceTimesTest {
    private val now = Instant.parse("2026-10-24T10:00:00Z").toEpochMilli()
    private fun at(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun administratorCanRecordCurrentOrHistoricalArrivalsAndDepartures() {
        assertNull(AdminAttendanceInput("a", "work_start", now).error(now))
        assertNull(AdminAttendanceInput("a", "work_end", now - 10 * 86400000L, "Пропуштена одјава").error(now))
        assertNotNull(AdminAttendanceInput("a", "work_end", now + 1000).error(now))
        assertNotNull(AdminAttendanceInput("", "work_end", now).error(now))
        assertNotNull(AdminAttendanceInput("a", "break_end", now).error(now))
        assertNotNull(AdminAttendanceInput("a", "work_start", now, "x".repeat(301)).error(now))
    }

    @Test fun retryIdentityIncludesEmployeeActionAndActualSecond() {
        val input = AdminAttendanceInput("a", "work_start", now)
        assertEquals(input.documentId(), input.copy(note = "Повторен обид").documentId())
        assertEquals(input.documentId(), input.copy(occurredAt = now + 999).documentId())
        assertNotEquals(input.documentId(), input.copy(type = "work_end").documentId())
        assertNotEquals(input.documentId(), input.copy(employeeId = "b").documentId())
        assertNotEquals(input.documentId(), input.copy(occurredAt = now - 1000).documentId())
    }

    @Test fun actualSecondsRespectTheChosenTimezoneAndDst() {
        val zone = ZoneId.of("Europe/Skopje")
        assertEquals("08:03:17", AttendanceTimes.format(at("2026-10-05T06:03:17Z"), zone))
        assertEquals("08:03:17", AttendanceTimes.format(at("2026-12-05T07:03:17Z"), zone))
        assertEquals("—", AttendanceTimes.lines(emptyList(), zone))
        assertEquals("08:03:17\n16:37:42", AttendanceTimes.lines(listOf(at("2026-10-05T14:37:42Z"), at("2026-10-05T06:03:17Z")), zone))
    }

    @Test fun dailyRowsAndXlsxKeepEveryArrivalAndDepartureWithoutInventingPartners() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Skopje"))
            val first = AttendanceReportDay("a", "Ана", "20261005", "05.10.2026", "понеделник", 4 * 3600000L,
                arrivals = listOf(at("2026-10-05T06:03:17Z")), departures = listOf(at("2026-10-05T10:03:17Z")))
            val second = first.copy(arrivals = listOf(at("2026-10-05T11:00:01Z")), departures = listOf(at("2026-10-05T15:00:01Z")))
            val orphan = first.copy(dateKey = "20261006", dateLabel = "06.10.2026", worked = 0,
                arrivals = emptyList(), departures = listOf(at("2026-10-06T14:37:42Z")))
            val tables = AttendanceReportTables.build(listOf("a" to "Ана"), listOf(second, first, orphan))
            assertEquals("08:03:17\n13:00:01", tables.daily[1][3])
            assertEquals("12:03:17\n17:00:01", tables.daily[1][4])
            assertEquals("8ч 0м", tables.daily[1][5])
            assertEquals("—", tables.daily[2][3]); assertEquals("16:37:42", tables.daily[2][4])
            val out = ByteArrayOutputStream()
            AttendanceXlsxExporter.write(out, listOf(ReportSection("Dnevno", "Дневно", tables.daily)))
            var xml = ""
            ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/worksheets/sheet1.xml") xml = zip.readBytes().toString(Charsets.UTF_8)
                    entry = zip.nextEntry
                }
            }
            val sheet = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray()))
            assertTrue(sheet.documentElement.textContent.contains("08:03:17\n13:00:01"))
            assertTrue(sheet.documentElement.textContent.contains("16:37:42"))
            val accounting = AttendanceReportTables.build(listOf("a" to "Ана"), listOf(first, second), accountant = true)
            assertEquals(4, accounting.daily[1].size)
            assertFalse(accounting.daily.flatten().any { it.contains("08:03:17") })
        } finally { TimeZone.setDefault(previous) }
    }
}
