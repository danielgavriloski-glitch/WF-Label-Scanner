package mk.wf.labelscanner

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

object AttendancePdfExporter {
    fun write(out: OutputStream, title: String, period: String, sections: List<ReportSection>, note: String) {
        val document = PdfDocument()
        val ink = Color.rgb(31, 36, 41)
        val gold = Color.rgb(245, 190, 55)
        val lineColor = Color.rgb(219, 225, 230)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 9f }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val heading = Paint(bold).apply { textSize = 19f }
        val sectionPaint = Paint(bold).apply { textSize = 12f }
        val muted = Paint(body).apply { color = Color.rgb(104, 115, 125); textSize = 8f }
        val fill = Paint()
        val border = Paint().apply { color = lineColor; strokeWidth = 0.5f }
        val left = 34f
        val right = 808f
        val bottom = 553f
        val lineHeight = 12f
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun finishPage() {
            val p = page ?: return
            p.canvas.drawLine(left, 566f, right, 566f, border)
            p.canvas.drawText("MBI Metal Design | Извештај за избраниот период", left, 579f, muted)
            val label = "Страница $pageNumber"
            p.canvas.drawText(label, right - muted.measureText(label), 579f, muted)
            document.finishPage(p)
            page = null
        }

        fun newPage() {
            finishPage()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(842, 595, pageNumber).create())
            val canvas = page!!.canvas
            fill.color = ink
            canvas.drawRect(0f, 0f, 842f, 82f, fill)
            fill.color = gold
            canvas.drawRect(left, 25f, left + 5f, 60f, fill)
            heading.color = Color.WHITE
            canvas.drawText("MBI METAL DESIGN", left + 17f, 42f, heading)
            body.color = Color.WHITE
            canvas.drawText(title, left + 17f, 62f, body)
            body.color = ink
            canvas.drawText("Период: $period", left, 105f, bold)
            canvas.drawText(note, left, 121f, muted)
            if (sections.any { it.calendar }) canvas.drawText(AttendanceCalendarTables.LEGEND, left, 135f, muted)
            y = if (sections.any { it.calendar }) 159f else 145f
        }

        fun columnWeight(label: String): Float = when (label) {
            "Вработен" -> 2.1f
            "Статус", "Причина" -> 2.5f
            "Недела" -> 2.2f
            "Датум", "Од", "До" -> 1.15f
            "Ден" -> 1.2f
            "Денови со работа", "Одмор/оправдано", "Редовен фонд", "Редовни часови", "Прекувремено" -> 1.4f
            "Вкупно месец" -> 1.6f
            else -> 1f
        }

        try {
            newPage()
            sections.filter { it.rows.isNotEmpty() }.forEach { section ->
                val headers = section.rows.first()
                val weights = headers.map { columnWeight(it) }
                val widths = weights.map { (right - left) * it / weights.sum() }
                val headerLines = headers.mapIndexed { i, text -> ReportTableLayout.wrap(text, widths[i] - 14f, bold::measureText) }
                val headerHeight = headerLines.maxOf { it.size } * lineHeight + 14f

                fun drawHeader(continued: Boolean = false) {
                    if (y + 26f + headerHeight + 26f > bottom) newPage()
                    page!!.canvas.drawText(section.title + if (continued) " (продолжение)" else "", left, y, sectionPaint)
                    y += 13f
                    fill.color = ink
                    page!!.canvas.drawRect(left, y, right, y + headerHeight, fill)
                    bold.color = Color.WHITE
                    var x = left
                    headerLines.forEachIndexed { i, lines ->
                        lines.forEachIndexed { j, text -> page!!.canvas.drawText(text, x + 7f, y + 12f + j * lineHeight, bold) }
                        x += widths[i]
                    }
                    bold.color = ink
                    y += headerHeight
                }

                drawHeader()
                section.rows.drop(1).forEachIndexed { rowIndex, row ->
                    val isTotal = row.firstOrNull()?.startsWith("ВКУПНО") == true
                    val textPaint = if (isTotal) bold else body
                    val cellLines = headers.indices.map { i ->
                        ReportTableLayout.wrap(row.getOrElse(i) { "" }, widths[i] - 14f, textPaint::measureText)
                    }
                    val maxLines = cellLines.maxOf { it.size }
                    val naturalHeight = maxLines * lineHeight + 14f
                    if (y + naturalHeight > bottom && naturalHeight <= bottom - 145f - 13f - headerHeight) {
                        newPage()
                        drawHeader(true)
                    }
                    var offset = 0
                    while (offset < maxLines) {
                        var available = ((bottom - y - 14f) / lineHeight).toInt()
                        if (available < 1) {
                            newPage()
                            drawHeader(true)
                            available = ((bottom - y - 14f) / lineHeight).toInt()
                        }
                        val count = minOf(available, maxLines - offset)
                        val height = count * lineHeight + 14f
                        fill.color = when {
                            isTotal -> Color.rgb(255, 241, 204)
                            rowIndex % 2 == 0 -> Color.rgb(246, 248, 250)
                            else -> Color.WHITE
                        }
                        page!!.canvas.drawRect(left, y, right, y + height, fill)
                        var x = left
                        cellLines.forEachIndexed { i, lines ->
                            if (section.calendar && !isTotal && i > 0 && i < headers.size - 2) {
                                val value = row.getOrElse(i) { "" }
                                fill.color = when (value) {
                                    "НП" -> Color.rgb(255, 235, 235)
                                    "О", "СД", "ОП" -> Color.rgb(232, 241, 253)
                                    "В", "·" -> Color.rgb(237, 240, 243)
                                    else -> Color.rgb(230, 246, 235)
                                }
                                page!!.canvas.drawRect(x, y, x + widths[i], y + height, fill)
                            }
                            for (j in 0 until count) {
                                lines.getOrNull(offset + j)?.let { text ->
                                    page!!.canvas.drawText(text, x + 7f, y + 12f + j * lineHeight, textPaint)
                                }
                            }
                            if (section.calendar) page!!.canvas.drawLine(x, y, x, y + height, border)
                            x += widths[i]
                        }
                        page!!.canvas.drawLine(left, y + height, right, y + height, border)
                        y += height
                        offset += count
                        if (offset < maxLines) {
                            newPage()
                            drawHeader(true)
                        }
                    }
                }
                y += 30f
            }
            finishPage()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }
}
