package mk.wf.labelscanner

import org.junit.Assert.*
import org.junit.Test

class ReportTableLayoutTest {
    private fun measure(value: String) = value.length.toFloat()

    @Test fun longWordsPreserveAllCharactersAndStayWithinCell() {
        val value = "СтојановскиТрајковскиПетровски"
        val lines = ReportTableLayout.wrap(value, 7f, ::measure)
        assertEquals(value, lines.joinToString(""))
        assertTrue(lines.all { it.length <= 7 })
    }

    @Test fun paragraphsAndEmptyLinesArePreserved() {
        assertEquals(listOf("Прв", "", "Втор"), ReportTableLayout.wrap("Прв\n\nВтор", 20f, ::measure))
    }

    @Test fun wordsWrapWithoutTruncatingLongReasons() {
        val value = "Одобрен годишен одмор за вработениот"
        val lines = ReportTableLayout.wrap(value, 12f, ::measure)
        assertEquals(value, lines.joinToString(" "))
        assertTrue(lines.all { it.length <= 12 })
    }
}
