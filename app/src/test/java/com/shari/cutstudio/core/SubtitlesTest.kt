package com.shari.cutstudio.core

import org.junit.Assert.*
import org.junit.Test

class SubtitlesTest {
    @Test fun importsRomanUrduAndRemapsAfterCut() {
        val srt = """
            1
            00:00:00,400 --> 00:00:01,100
            Kya aap jaante hain?

            2
            00:00:02,500 --> 00:00:03,400
            Allah ne unhein hikmat di.
        """.trimIndent()
        val blocks = Subtitles.parse(srt)
        assertEquals(2, blocks.size)
        val kept = listOf(TimeRange(0.0, 1.2), TimeRange(2.3, 4.0))
        val updated = Subtitles.remap(blocks, kept)
        assertEquals(2, updated.size)
        assertEquals(1400, updated[1].startMs)
        assertEquals(2300, updated[1].endMs)
        assertTrue(Subtitles.asSrt(updated).contains("00:00:01,400 --> 00:00:02,300"))
    }
    @Test fun supportsUrduAndHindiScript() {
        val samples = listOf("یہ کہانی ہے", "यह एक कहानी है", "Kahani shuru hoti hai")
        val captions = samples.mapIndexed { i, text -> Subtitle((i * 2000).toLong(), ((i + 1) * 2000).toLong(), text) }
        assertEquals(samples, Subtitles.parse(Subtitles.asSrt(captions)).map { it.text })
    }
}
