package mk.wf.labelscanner

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AttendanceXlsxExporterTest {
    @Test fun monthlyHoursAreNumericWithFormulasAndFrozenEmployeeColumn() {
        val hour = 3600000L
        val month = AttendanceCalendarTables.build(listOf("a" to "Ана"), listOf(
            AttendanceReportDay("a", "Ана", "20261005", "05.10.2026", "понеделник", 10 * hour),
            AttendanceReportDay("a", "Ана", "20261006", "06.10.2026", "вторник", 6 * hour + hour / 2)
        ), "20261001", "20261031", accountant = true, asOfKey = "20261007").single()
        val out = ByteArrayOutputStream()
        AttendanceXlsxExporter.write(out, listOf(month.excelSection()))
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            var entry = z.nextEntry
            while (entry != null) { files[entry.name] = z.readBytes(); entry = z.nextEntry }
        }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        files.values.forEach { parser.parse(ByteArrayInputStream(it)) }
        val sheet = parser.parse(ByteArrayInputStream(files.getValue("xl/worksheets/sheet1.xml")))
        val cells = sheet.getElementsByTagName("c")
        val byRef = (0 until cells.length).associate { index ->
            val cell = cells.item(index) as org.w3c.dom.Element
            cell.getAttribute("r") to cell
        }
        assertEquals(8.0 / 24, byRef.getValue("F2").getElementsByTagName("v").item(0).textContent.toDouble(), 0.0000001)
        assertEquals((14.5 / 24), byRef.getValue("AH2").getElementsByTagName("v").item(0).textContent.toDouble(), 0.0000001)
        assertEquals("SUM(B2:AF2)", byRef.getValue("AH2").getElementsByTagName("f").item(0).textContent)
        assertEquals("1", sheet.getElementsByTagName("pane").item(0).attributes.getNamedItem("xSplit").nodeValue)
        assertEquals("НП", byRef.getValue("B2").textContent)
    }
    @Test fun exportsAllSectionsAndEscapesNamesWithoutChangingValues() {
        val out = ByteArrayOutputStream()
        val name = "Ана & Петар <Тим>"
        AttendanceXlsxExporter.write(out, listOf(
            ReportSection("Vkupno", "Вкупно", listOf(listOf("Вработен", "Работено"), listOf(name, "16ч 30м"), listOf("ВКУПНО", "16ч 30м"))),
            ReportSection("Dnevno", "Дневно", listOf(listOf("Датум", "Вработен"), listOf("05.10.2026", name))),
            ReportSection("Nedelno", "Неделно", listOf(listOf("Вработен", "Недела"), listOf(name, "05.10.2026 - 11.10.2026")))
        ))
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            var entry = z.nextEntry
            while (entry != null) { files[entry.name] = z.readBytes(); entry = z.nextEntry }
        }
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        // Every XML part must parse, including workbook relationships and styles.
        files.values.forEach { parser.parse(ByteArrayInputStream(it)) }
        val workbook = parser.parse(ByteArrayInputStream(files.getValue("xl/workbook.xml")))
        assertEquals(3, workbook.getElementsByTagName("sheet").length)
        val sheet = parser.parse(ByteArrayInputStream(files.getValue("xl/worksheets/sheet1.xml")))
        val texts = sheet.getElementsByTagName("t")
        assertEquals(name, texts.item(2).textContent)
        assertEquals("16ч 30м", texts.item(3).textContent)
        assertEquals("frozen", sheet.getElementsByTagName("pane").item(0).attributes.getNamedItem("state").nodeValue)
        assertEquals("3", sheet.getElementsByTagName("c").item(4).attributes.getNamedItem("s").nodeValue)
    }
}
