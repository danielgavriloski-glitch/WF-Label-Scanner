package mk.wf.labelscanner

data class ParsedLabel(
    val nalog: String = "",
    val packageNo: String = "",
    val article: String = "",
    val size: String = "",
    val quantity: String = "",
    val customer: String = "",
    val barcode: String = ""
)

object LabelParser {
    private fun clean(s: String) = s.trim().trim(':', '-', '#', '|', '=').trim()

    private fun valueAfter(text: String, keys: List<String>): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        for (i in lines.indices) {
            val line = lines[i]
            for (key in keys) {
                val rx = Regex("(?i)^\\s*${Regex.escape(key)}\\s*[:#=\\-]?\\s*(.*)$")
                val m = rx.find(line) ?: continue
                val sameLine = clean(m.groupValues[1])
                if (sameLine.isNotBlank()) return sameLine
                if (i + 1 < lines.size) return clean(lines[i + 1])
            }
        }
        return ""
    }

    fun parse(raw: String, currentNalog: String = ""): ParsedLabel {
        val text = raw.replace('\u00A0', ' ')

        var nalog = valueAfter(text, listOf(
            "nalog", "auftrag", "auftragsnr", "auftrags-nr", "auftrag nr", "auftrag-nr",
            "order", "order no", "order nr", "order number", "ordre", "job", "work order", "kommission"
        ))
        if (nalog.isBlank()) nalog = currentNalog

        val packageNo = valueAfter(text, listOf(
            "paket", "package", "pack", "box", "karton", "carton", "colli", "kolli", "kollinr", "kolli-nr"
        ))
        val article = valueAfter(text, listOf(
            "artikl", "artikel", "artikel nr", "artikel-nr", "artikelnr", "article", "item", "model", "style", "art.", "art nr", "art-nr"
        ))
        val size = valueAfter(text, listOf(
            "golemina", "size", "größe", "grösse", "groesse", "gr.", "gr", "taille", "mass"
        ))

        var quantity = valueAfter(text, listOf(
            "kolicina", "količina", "qty", "quantity", "menge", "anzahl", "pcs", "pairs", "pair", "paar", "stück", "stuck", "st"
        ))
        quantity = Regex("\\d+").find(quantity)?.value ?: quantity

        val customer = valueAfter(text, listOf(
            "klient", "client", "customer", "kunde", "kundenname", "name"
        ))

        var barcode = valueAfter(text, listOf("barcode", "ean", "gtin", "ean13", "ean-13"))
        barcode = Regex("\\d{8,14}").find(barcode)?.value ?: barcode
        if (barcode.isBlank()) {
            barcode = Regex("(?<!\\d)\\d{8,14}(?!\\d)").findAll(text)
                .map { it.value }
                .maxByOrNull { it.length }
                .orEmpty()
        }

        fun firstToken(v: String, max: Int = 80) = clean(v).take(max)
        return ParsedLabel(
            nalog = firstToken(nalog, 50),
            packageNo = firstToken(packageNo, 30),
            article = firstToken(article),
            size = firstToken(size, 30),
            quantity = firstToken(quantity, 20),
            customer = firstToken(customer),
            barcode = firstToken(barcode, 30)
        )
    }
}
