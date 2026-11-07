package com.shari.aivoicegen

import org.junit.Assert.*
import org.junit.Test

class TextToolsTest {
    @Test fun splitsLongScriptWithoutExceedingEngineLimit() {
        val text = (1..100).joinToString(" ") { "Assalam-o-Alaikum dosto! Tareekhi waqia number $it." }
        val chunks = TextTools.chunks(text, 90)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 90 && it.isNotBlank() })
    }
    @Test fun correctsWholeWordsOnlyWithCaseInsensitivity() {
        val map = mapOf("waqia" to "واقعہ", "hairaan" to "حیران")
        assertEquals("Ek واقعہ, magar waqian nahi. حیران!", TextTools.corrected("Ek waqia, magar waqian nahi. HAIRAAN!", map))
    }
    @Test fun countsMultilingualWhitespaceWords() {
        assertEquals(4, TextTools.wordCount("السلام علیکم dosto hello"))
        assertEquals(0, TextTools.wordCount(" \n \t "))
    }
    @Test fun safeFileNamesRemoveSeparators() {
        val name = TextTools.safeFileName("../narration:hello")
        assertFalse(name.contains("/"))
        assertFalse(name.contains(":"))
    }
}
