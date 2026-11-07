package com.shari.cutstudio.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Time is measured in seconds; all ranges are half-open [start, end). */
data class TimeRange(val start: Double, val end: Double) {
    val length: Double get() = max(0.0, end - start)
}

object Timeline {
    private val startRegex = Regex("silence_start:\\s*([0-9.]+)")
    private val endRegex = Regex("silence_end:\\s*([0-9.]+)")

    fun detectSilences(ffmpegLog: String, duration: Double): List<TimeRange> {
        val silences = mutableListOf<TimeRange>()
        var pendingStart: Double? = null
        for (line in ffmpegLog.lineSequence()) {
            val started = startRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            if (started != null) pendingStart = started
            val ended = endRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            if (ended != null) {
                val a = pendingStart
                if (a != null && ended > a) silences.add(TimeRange(a, ended))
                pendingStart = null
            }
        }
        if (pendingStart != null && duration > pendingStart) {
            silences.add(TimeRange(pendingStart, duration))
        }
        return silences.sortedBy { it.start }
    }

    /** Retain a small pause at both sides of each removed silence for natural editing. */
    fun keepRanges(duration: Double, silences: List<TimeRange>, pad: Double, minPause: Double): List<TimeRange> {
        if (duration <= 0.0) return emptyList()
        val cuts = silences.asSequence()
            .filter { it.length >= minPause }
            .map {
                TimeRange(
                    max(0.0, it.start + pad),
                    min(duration, it.end - pad)
                )
            }
            .filter { it.length > 0.02 }
            .sortedBy { it.start }
            .toList()
        if (cuts.isEmpty()) return listOf(TimeRange(0.0, duration))
        val mergedCuts = mutableListOf<TimeRange>()
        for (cut in cuts) {
            if (mergedCuts.isNotEmpty() && cut.start <= mergedCuts.last().end) {
                val previous = mergedCuts.removeAt(mergedCuts.lastIndex)
                mergedCuts += TimeRange(previous.start, max(previous.end, cut.end))
            } else mergedCuts += cut
        }
        val result = mutableListOf<TimeRange>()
        var cursor = 0.0
        for (cut in mergedCuts) {
            if (cut.start - cursor > 0.04) result += TimeRange(cursor, cut.start)
            cursor = max(cursor, cut.end)
        }
        if (duration - cursor > 0.04) result += TimeRange(cursor, duration)
        return result
    }

    fun duration(ranges: List<TimeRange>) = ranges.sumOf { it.length }

    fun seconds(value: Double): String = String.format(Locale.US, "%.3f", value)
}
