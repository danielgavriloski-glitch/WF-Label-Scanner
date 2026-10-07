package mk.wf.labelscanner

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class AttendanceXlsxExporterTest {
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
