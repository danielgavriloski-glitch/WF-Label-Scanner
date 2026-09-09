package mk.wf.labelscanner

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxExporter {
    fun write(output: OutputStream, records: List<PackageRecord>) {
        ZipOutputStream(output).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes())
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook())
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            put(zip, "xl/styles.xml", styles())
            put(zip, "xl/worksheets/sheet1.xml", detailsSheet(records))
            put(zip, "xl/worksheets/sheet2.xml", summarySheet(records))
        }
    }

    private fun put(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
<sheet name="Paketi" sheetId="1" r:id="rId1"/>
<sheet name="Rezime" sheetId="2" r:id="rId2"/>
</sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
<borders count="1"><border/></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs>
</styleSheet>"""

    private fun detailsSheet(records: List<PackageRecord>): String {
        val rows = mutableListOf<List<Cell>>()
        rows += listOf("ID", "Datum", "Nalog", "Paket", "Artikl", "Golemina", "Kolicina", "Klient", "Barcode", "Cel OCR tekst", "Foto path", "Provereno OK", "Zabeleshka").map { Cell.S(it, true) }
        records.forEach { r ->
            rows += listOf(
                Cell.N(r.id.toString()), Cell.S(r.createdAt), Cell.S(r.nalog), Cell.S(r.packageNo),
                Cell.S(r.article), Cell.S(r.size), Cell.N(r.quantity.toString()), Cell.S(r.customer),
                Cell.S(r.barcode), Cell.S(r.rawText), Cell.S(r.photoPath), Cell.S(""), Cell.S("")
            )
        }
        return sheetXml(rows)
    }

    private fun summarySheet(records: List<PackageRecord>): String {
        data class Key(val nalog: String, val size: String)
        val grouped = records.groupBy { Key(it.nalog, it.size.ifBlank { "(bez golemina)" }) }
        val rows = mutableListOf<List<Cell>>()
        rows += listOf("Nalog", "Golemina", "Vkupna kolicina", "Broj paketi").map { Cell.S(it, true) }
        grouped.toSortedMap(compareBy<Key> { it.nalog }.thenBy { it.size }).forEach { (k, v) ->
            rows += listOf(Cell.S(k.nalog), Cell.S(k.size), Cell.N(v.sumOf { it.quantity }.toString()), Cell.N(v.size.toString()))
        }
        rows.add(emptyList())
        rows += listOf(Cell.S("VKUPNO", true), Cell.S(""), Cell.N(records.sumOf { it.quantity }.toString()), Cell.N(records.size.toString()))
        return sheetXml(rows)
    }

    private sealed class Cell {
        data class S(val value: String, val bold: Boolean = false) : Cell()
        data class N(val value: String, val bold: Boolean = false) : Cell()
    }

    private fun sheetXml(rows: List<List<Cell>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            val rn = rowIndex + 1
            sb.append("<row r=\"").append(rn).append("\">")
            row.forEachIndexed { colIndex, cell ->
                val ref = colName(colIndex + 1) + rn
                when (cell) {
                    is Cell.S -> {
                        val style = if (cell.bold) " s=\"1\"" else ""
                        sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"").append(style).append("><is><t xml:space=\"preserve\">")
                            .append(esc(cell.value)).append("</t></is></c>")
                    }
                    is Cell.N -> {
                        val style = if (cell.bold) " s=\"1\"" else ""
                        sb.append("<c r=\"").append(ref).append("\" t=\"n\"").append(style).append("><v>")
                            .append(cell.value.ifBlank { "0" }).append("</v></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colName(index: Int): String {
        var n = index
        val s = StringBuilder()
        while (n > 0) {
            n--
            s.append(('A'.code + (n % 26)).toChar())
            n /= 26
        }
        return s.reverse().toString()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
}
