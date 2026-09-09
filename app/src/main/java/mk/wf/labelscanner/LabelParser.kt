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

enum class LabelType { AUTO, STANDARD, NAME }

object LabelParser {
    private fun clean(s: String) = s.trim().trim(':', '-', '#', '|', '=').trim()

    private val standardOrder = Regex("(?<!\\d)(\\d{2})[\\s.-]?(\\d{3})[\\s.-]?(\\d{5})(?!\\d)")

    private fun standardOrderFrom(text: String): String = standardOrder.find(text)?.let {
        "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}"
    }.orEmpty()

    private fun isLn(value: String): Boolean =
        value.replace(" ", "").matches(Regex("(?i)^L[/I|]N$"))

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

    fun parse(raw: String, currentNalog: String = "", labelType: LabelType = LabelType.AUTO): ParsedLabel {
        val text = raw.replace('\u00A0', ' ')

        val lines = text.lines().map { clean(it) }.filter { it.isNotBlank() }

        val standardNalog = standardOrderFrom(text).ifBlank { valueAfter(text, listOf(
            "nalog", "auftrag", "auftragsnr", "auftrags-nr", "auftrag nr", "auftrag-nr",
            "order", "order no", "order nr", "order number", "ordre", "job", "work order", "kommission"
        )) }
        val lnIndex = lines.indexOfFirst(::isLn)
        val nameNalog = if (lnIndex > 0) {
            lines.subList(0, lnIndex).lastOrNull {
                    it.any(Char::isLetter) &&
                        !it.contains("workfashion", ignoreCase = true) &&
                        !it.matches(Regex("^[\\d.]+$"))
            }.orEmpty()
        } else ""
        var nalog = when (labelType) {
            LabelType.STANDARD -> standardNalog
            LabelType.NAME -> nameNalog
            LabelType.AUTO -> standardNalog.ifBlank { nameNalog }
        }
        if (nalog.isBlank()) nalog = currentNalog

        val packageNo = valueAfter(text, listOf(
            "paket", "package", "pack", "box", "karton", "karton nr", "karton-nr", "carton", "colli", "kolli", "kollinr", "kolli-nr"
        ))
        var article = valueAfter(text, listOf(
            "artikl", "artikel", "artikel nr", "artikel-nr", "artikelnr", "article", "item", "model", "style", "art.", "art nr", "art-nr"
        ))
        if (article.isBlank() && lines.any(::isLn)) {
            article = Regex("(?<!\\d)\\d{3,6}[.]\\d{3,6}(?!\\d)").find(text)?.value.orEmpty()
        }

        var size = valueAfter(text, listOf(
            "golemina", "size", "größe", "grösse", "groesse", "gr.", "gr", "taille", "mass"
        ))
        if (size.isBlank()) {
            size = lines.firstOrNull {
                isLn(it) || it.matches(Regex("(?i)^(?:XXS|XS|S|M|L|XL|XXL|3XL|4XL|5XL|\\d{1,3})$"))
            }.orEmpty().let { if (isLn(it)) "L/N" else it.replace(Regex("\\s+"), "") }
        }

        var quantity = valueAfter(text, listOf(
            "kolicina", "količina", "qty", "quantity", "menge", "anzahl", "pcs", "pairs", "pair", "paar", "stück", "stuck", "st"
        ))
        if (quantity.isBlank()) {
            quantity = Regex("(?i)(?<!\\d)(\\d{1,6})\\s*(?:stück|stuck|stiick|stiick|pcs|pieces|paar|pairs?)(?![a-z])")
                .find(text)?.groupValues?.get(1).orEmpty()
        }
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
