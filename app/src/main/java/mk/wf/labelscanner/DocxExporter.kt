package mk.wf.labelscanner

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExporter {
    private fun xml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun cell(value: String, bold: Boolean = false, shade: String? = null): String {
        val fill = shade?.let { "<w:shd w:fill=\"$it\"/>" }.orEmpty()
        val weight = if (bold) "<w:b/>" else ""
        return "<w:tc><w:tcPr>$fill<w:tcMar><w:top w:w=\"100\" w:type=\"dxa\"/><w:left w:w=\"100\" w:type=\"dxa\"/><w:bottom w:w=\"100\" w:type=\"dxa\"/><w:right w:w=\"100\" w:type=\"dxa\"/></w:tcMar></w:tcPr><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr>$weight</w:rPr><w:t>${xml(value)}</w:t></w:r></w:p></w:tc>"
    }
    private fun row(values: List<String>, header: Boolean = false): String =
        "<w:tr>" + values.joinToString("") { cell(it, header, if (header) "F4C400" else null) } + "</w:tr>"
    private fun table(headers: List<String>, rows: List<List<String>>): String =
        "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/><w:tblBorders><w:top w:val=\"single\" w:sz=\"6\" w:color=\"D9D9D9\"/><w:left w:val=\"single\" w:sz=\"6\" w:color=\"D9D9D9\"/><w:bottom w:val=\"single\" w:sz=\"6\" w:color=\"D9D9D9\"/><w:right w:val=\"single\" w:sz=\"6\" w:color=\"D9D9D9\"/><w:insideH w:val=\"single\" w:sz=\"6\" w:color=\"D9D9D9\"/><w:insideV w:val=\"single\" w:sz=\"6\" w:color=\"D9D9D9\"/></w:tblBorders></w:tblPr>" + row(headers, true) + rows.joinToString("") { row(it) } + "</w:tbl>"

    fun write(output: OutputStream, records: List<PackageRecord>) {
        require(records.isNotEmpty())
        val nalog = records.first().nalog
        val date = records.maxOf { it.createdAt }
        val details = records.map { listOf(nalog, it.size, it.article) }
        val totals = records.groupBy { it.size }.toSortedMap().map { (size, rows) -> listOf(size, rows.sumOf { it.quantity }.toString()) }
        val body = "<w:p><w:r><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr><w:t>WF Налог ${xml(nalog)}</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>Датум и време: ${xml(date)}</w:t></w:r></w:p>" +
            "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Пакети</w:t></w:r></w:p>" + table(listOf("Налог", "Големина", "Master number"), details) +
            "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Вкупен збир</w:t></w:r></w:p>" + table(listOf("Големина", "Парчиња"), totals) +
            "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Вкупно пакети: ${records.size}    Вкупно парчиња: ${records.sumOf { it.quantity }}</w:t></w:r></w:p>" +
            "<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1080\" w:right=\"1080\" w:bottom=\"1080\" w:left=\"1080\"/></w:sectPr>"
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>"
        val types = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"
        ZipOutputStream(output).use { zip ->
            fun add(name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            add("[Content_Types].xml", types); add("_rels/.rels", rels); add("word/document.xml", document)
        }
    }
}
