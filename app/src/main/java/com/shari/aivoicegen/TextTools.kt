package com.shari.aivoicegen

import java.util.Locale

object TextTools {
    /** TTS requests are bounded by the Android API. Scripts are not. Split on punctuation/space. */
    fun chunks(text: String, limit: Int = 1200): List<String> {
        require(limit >= 16)
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var remaining = cleaned
        while (remaining.isNotEmpty()) {
            if (remaining.length <= limit) { out += remaining; break }
            val window = remaining.take(limit)
            val separator = listOf(window.lastIndexOf('।'), window.lastIndexOf('۔'),
                window.lastIndexOf('.'), window.lastIndexOf('?'), window.lastIndexOf('!'),
                window.lastIndexOf('\n'), window.lastIndexOf(' ')).filter { it >= limit / 3 }.maxOrNull()
            val cut = if (separator == null) limit else separator + 1
            out += remaining.take(cut).trim()
            remaining = remaining.drop(cut).trimStart()
        }
        return out.filter { it.isNotBlank() }
    }

    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    /** Rough estimator, not an acoustic measurement. */
    fun estimatedSeconds(text: String, speed: Float): Int =
        (wordCount(text) / (2.45f * speed.coerceIn(0.5f, 2f))).toInt().coerceAtLeast(0)

    fun corrected(text: String, dictionary: Map<String, String>): String {
        var result = text
        dictionary.entries.filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .sortedByDescending { it.key.length }.forEach { (original, replacement) ->
                val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(original)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                result = pattern.replace(result) { replacement }
            }
        return result
    }

    fun localeLabel(tag: String): String = try {
        val loc = Locale.forLanguageTag(tag)
        if (loc.language.isBlank()) tag else "${loc.getDisplayName(Locale.ENGLISH)} ($tag)"
    } catch (_: Exception) { tag }

    fun safeFileName(name: String): String =
        name.trim().replace(Regex("[^\\p{L}\\p{N}._-]"), "_").take(60).ifBlank { "narration" }
}
