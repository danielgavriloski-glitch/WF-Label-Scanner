package mk.wf.labelscanner

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object AttendanceXlsxExporter {
    fun write(out: OutputStream, sections: List<ReportSection>) {
        require(sections.isNotEmpty())
        ZipOutputStream(out).use { z ->
            put(z, "[Content_Types].xml", contentTypes(sections.size))
            put(z, "_rels/.rels", """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put(z, "xl/workbook.xml", workbook(sections))
            put(z, "xl/_rels/workbook.xml.rels", workbookRelationships(sections.size))
            put(z, "xl/styles.xml", calendarStyles())
            sections.forEachIndexed { i, section -> put(z, "xl/worksheets/sheet${i + 1}.xml", sheet(section)) }
        }
    }

    private fun put(z: ZipOutputStream, path: String, value: String) {
        z.putNextEntry(ZipEntry(path)); z.write(value.toByteArray(Charsets.UTF_8)); z.closeEntry()
    }

    private fun contentTypes(count: Int) = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
        (1..count).joinToString("") { "<Override PartName=\"/xl/worksheets/sheet$it.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>"

    private fun workbook(sections: List<ReportSection>) = """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
        sections.mapIndexed { i, s -> "<sheet name=\"${escape(s.sheetName)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets><calcPr calcId=\"191029\" fullCalcOnLoad=\"1\"/></workbook>"

    private fun workbookRelationships(count: Int) = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
        (1..count).joinToString("") { "<Relationship Id=\"rId$it\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$it.xml\"/>" } +
        "<Relationship Id=\"rId${count + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>"

    private fun styles() = """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="11"/><name val="Calibri"/><color rgb="FF1F2429"/></font><font><b/><sz val="11"/><name val="Calibri"/><color rgb="FFFFFFFF"/></font><font><b/><sz val="11"/><name val="Calibri"/><color rgb="FF1F2429"/></font></fonts><fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF1F2429"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF6F8FA"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFF1CC"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><bottom style="hair"><color rgb="FFDBE1E6"/></bottom></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFill="1" applyFont="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyFill="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="2" fillId="4" borderId="0" xfId="0" applyFill="1" applyFont="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf></cellXfs></styleSheet>"""

    private fun calendarStyles(): String = styles()
        .replace("<fonts count=\"3\">", "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"[h]:mm\"/></numFmts><fonts count=\"3\">")
        .replace("<cellXfs count=\"4\">", "<cellXfs count=\"7\">")
        .replace("</cellXfs>", (0..2).joinToString("") { index ->
            val font = if (index == 2) 2 else 0
            val fill = when (index) { 1 -> 3; 2 -> 4; else -> 0 }
            "<xf numFmtId=\"164\" fontId=\"$font\" fillId=\"$fill\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" horizontal=\"center\"/></xf>"
        } + "</cellXfs>")

    private fun sheet(section: ReportSection): String {
        val rows = section.rows
        val columnCount = rows.maxOfOrNull { it.size } ?: 1
        val lastCell = column(columnCount) + maxOf(1, rows.size)
        val b = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        val pane = if (section.calendar) "xSplit=\"1\" ySplit=\"1\" topLeftCell=\"B2\" activePane=\"bottomRight\"" else "ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\""
        b.append("<sheetPr><pageSetUpPr fitToPage=\"1\"/></sheetPr><dimension ref=\"A1:$lastCell\"/><sheetViews><sheetView workbookViewId=\"0\"><pane $pane state=\"frozen\"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight=\"22\"/><cols>")
        rows.firstOrNull()?.forEachIndexed { i, label ->
            val width = when (label) {
                "Вработен" -> 30
                "Статус", "Причина" -> 38
                "Недела" -> 27
                "Датум", "Од", "До" -> 15
                "Ден" -> 18
                "Вкупно месец" -> 20
                "Денови со работа" -> 17
                else -> if (section.calendar) 10 else 19
            }
            b.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$width\" customWidth=\"1\"/>")
        }
        b.append("</cols><sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            val rn = rowIndex + 1
            val total = row.firstOrNull()?.startsWith("ВКУПНО") == true
            b.append("<row r=\"$rn\"" + if (rowIndex == 0) " ht=\"34\" customHeight=\"1\">" else ">")
            row.forEachIndexed { i, value ->
                val millis = if (section.calendar && rowIndex > 0) section.calendarDurations.getOrNull(rowIndex)?.getOrNull(i) else null
                val count = if (section.calendar && rowIndex > 0 && i == columnCount - 2) value.toIntOrNull() else null
                val style = when { rowIndex == 0 -> 1; millis != null && total -> 6; millis != null && rowIndex % 2 == 1 -> 5; millis != null -> 4; total -> 3; rowIndex % 2 == 1 -> 2; else -> 0 }
                if (millis != null || count != null) {
                    val number = if (millis != null) millis / 86400000.0 else count!!.toDouble()
                    val formula = when {
                        total && rowIndex > 1 -> "SUM(${column(i + 1)}2:${column(i + 1)}$rowIndex)"
                        !total && i == columnCount - 1 -> "SUM(B$rn:${column(columnCount - 2)}$rn)"
                        !total && count != null -> "COUNTIF(B$rn:${column(columnCount - 2)}$rn,\"&gt;0\")"
                        else -> ""
                    }
                    b.append("<c r=\"${column(i + 1)}$rn\" s=\"$style\">${if (formula.isEmpty()) "" else "<f>$formula</f>"}<v>$number</v></c>")
                } else b.append("<c r=\"${column(i + 1)}$rn\" t=\"inlineStr\" s=\"$style\"><is><t xml:space=\"preserve\">${escape(value)}</t></is></c>")
            }
            b.append("</row>")
        }
        val filterLast = column(columnCount) + if (section.calendar && rows.size > 2) rows.size - 1 else maxOf(1, rows.size)
        b.append("</sheetData><autoFilter ref=\"A1:$filterLast\"/><printOptions horizontalCentered=\"1\"/><pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/><pageSetup paperSize=\"9\" orientation=\"landscape\" fitToWidth=\"1\" fitToHeight=\"0\"/></worksheet>")
        return b.toString()
    }

    private fun column(value: Int): String {
        var n = value
        val result = StringBuilder()
        while (n > 0) { n--; result.append(('A'.code + n % 26).toChar()); n /= 26 }
        return result.reverse().toString()
    }

    private fun escape(value: String) = value.filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
