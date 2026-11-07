package com.shari.cutstudio.core

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Standalone SRT parsing / cut-timeline mapping, shared by export and tests. */
data class Subtitle(val startMs: Long, val endMs: Long, val text: String)

object Subtitles {
    private val timestamp = Regex("(\\d{1,3}):(\\d{2}):(\\d{2})[,\\.](\\d{3})")

    fun parse(srt: String): List<Subtitle> = srt.trim().split(Regex("\\r?\\n\\s*\\r?\\n"))
        .mapNotNull { block ->
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) return@mapNotNull null
            val times = lines[timingIndex].split("-->")
            if (times.size != 2) return@mapNotNull null
            val start = readTime(times[0]) ?: return@mapNotNull null
            val end = readTime(times[1]) ?: return@mapNotNull null
            val text = lines.drop(timingIndex + 1).joinToString("\n").trim()
            if (end <= start || text.isBlank()) null else Subtitle(start, end, text)
        }.sortedBy { it.startMs }

    fun readTime(value: String): Long? {
        val match = timestamp.find(value.trim()) ?: return null
        val (h, m, s, ms) = match.destructured
        return ((h.toLong() * 3600 + m.toLong() * 60 + s.toLong()) * 1000L) + ms.toLong()
    }

    fun formatTime(ms: Long): String {
        val n = max(0, ms)
        val hours = n / 3_600_000
        val mins = (n / 60_000) % 60
        val secs = (n / 1000) % 60
        val millis = n % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }

    fun asSrt(items: List<Subtitle>) = items.mapIndexed { index, item ->
        "${index + 1}\n${formatTime(item.startMs)} --> ${formatTime(item.endMs)}\n${item.text}"
    }.joinToString("\n\n", postfix = if (items.isEmpty()) "" else "\n")

    /** Translate source-time subtitles onto a timeline after all silence cuts. */
    fun remap(items: List<Subtitle>, kept: List<TimeRange>): List<Subtitle> {
        val result = mutableListOf<Subtitle>()
        var cumulativeMs = 0L
        for (range in kept) {
            val clipStart = (range.start * 1000).toLong()
            val clipEnd = (range.end * 1000).toLong()
            for (item in items) {
                val intersectionStart = max(item.startMs, clipStart)
                val intersectionEnd = min(item.endMs, clipEnd)
                if (intersectionEnd - intersectionStart >= 80) {
                    result += Subtitle(
                        cumulativeMs + intersectionStart - clipStart,
                        cumulativeMs + intersectionEnd - clipStart,
                        item.text
                    )
                }
            }
            cumulativeMs += clipEnd - clipStart
        }
        return result.sortedBy { it.startMs }
    }
}
