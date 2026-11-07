package com.shari.cutstudio.core

import org.junit.Assert.*
import org.junit.Test

class TimelineTest {
    @Test fun findsSilencesAndPreservesPhrases() {
        val log = """
            [silencedetect] silence_start: 1.200
            [silencedetect] silence_end: 2.300 | silence_duration: 1.100
            [silencedetect] silence_start: 4.000
            [silencedetect] silence_end: 4.750 | silence_duration: 0.750
        """.trimIndent()
        val silences = Timeline.detectSilences(log, 6.0)
        assertEquals(2, silences.size)
        val keep = Timeline.keepRanges(6.0, silences, 0.1, 0.5)
        assertEquals(3, keep.size)
        assertEquals(1.3, keep[0].end, .002)
        assertEquals(2.2, keep[1].start, .002)
        assertEquals(4.65, keep[2].start, .002)
        assertEquals(4.55, Timeline.duration(keep), .002)
    }

    @Test fun handlesContinuousSilence() {
        val silences = Timeline.detectSilences("silence_start: 0.000", 10.0)
        assertEquals(1, silences.size)
        assertEquals(0.24, Timeline.duration(Timeline.keepRanges(10.0, silences, 0.12, 0.3)), .002)
    }
    @Test fun noSilencesMeansOriginalDuration() {
        assertEquals(10.0, Timeline.duration(Timeline.keepRanges(10.0, emptyList(), .1, .4)), .002)
    }
}
