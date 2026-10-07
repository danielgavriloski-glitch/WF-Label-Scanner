package mk.wf.labelscanner

object ReportTableLayout {
    /** Wrap every character; long names and reasons are never shortened in exports. */
    fun wrap(value: String, width: Float, measure: (String) -> Float): List<String> {
        require(width > 0f)
        val result = mutableListOf<String>()
        value.replace("\r", "").split('\n').forEach { paragraph ->
            var line = ""
            val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
            words.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure(candidate) <= width) {
                    line = candidate
                } else {
                    if (line.isNotEmpty()) result.add(line)
                    line = ""
                    for (character in word) {
                        if (line.isNotEmpty() && measure(line + character) > width) {
                            result.add(line)
                            line = ""
                        }
                        line += character
                    }
                }
            }
            result.add(line)
        }
        return result.ifEmpty { listOf("") }
    }
}
