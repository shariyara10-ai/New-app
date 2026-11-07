package com.shari.cutstudio.core

import android.icu.text.Transliterator
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Optional cloud STT. Audio is uploaded ONLY on explicit Generate Captions action. */
object GroqCaptioner {
    data class Result(val subtitles: List<Subtitle>, val language: String)

    fun makeTranscriptionAudio(source: File, destination: File) {
        val command = "-hide_banner -y -nostdin -i \"${source.absolutePath}\" -map 0:a:0 " +
            "-vn -ac 1 -ar 16000 -c:a libmp3lame -b:a 48k \"${destination.absolutePath}\""
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode) || !destination.exists()) {
            throw IllegalStateException("Could not extract voice for captions. ${session.allLogsAsString?.takeLast(650)}")
        }
        require(destination.length() < 25L * 1024 * 1024) {
            "Audio exceeds the 25 MB free-tier limit. Split your recording into shorter parts."
        }
    }

    fun transcribe(mp3: File, apiKey: String, language: String, hinglish: Boolean): Result {
        require(apiKey.isNotBlank()) { "Enter your own Groq API key to use online auto-captions." }
        val boundary = "CutStudio-${UUID.randomUUID()}"
        val connection = (URL("https://api.groq.com/openai/v1/audio/transcriptions")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30000
            readTimeout = 180000
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            connection.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                }
                field("model", "whisper-large-v3-turbo")
                field("response_format", "verbose_json")
                field("timestamp_granularities[]", "segment")
                if (language != "auto") field("language", language)
                if (hinglish) field("prompt", "Natural Urdu and Hindi speech, including English words and South Asian names.")
                out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
                    "filename=\"speech.mp3\"\r\nContent-Type: audio/mpeg\r\n\r\n").toByteArray())
                mp3.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: "No response"
            if (status !in 200..299) {
                throw IllegalStateException("Caption API HTTP $status: ${text.take(600)}")
            }
            val root = JSONObject(text)
            val segments = root.optJSONArray("segments")
            val items = mutableListOf<Subtitle>()
            if (segments != null) {
                for (i in 0 until segments.length()) {
                    val s = segments.getJSONObject(i)
                    val phrase = s.optString("text").trim()
                    val start = (s.optDouble("start", 0.0) * 1000).toLong()
                    val end = (s.optDouble("end", 0.0) * 1000).toLong()
                    if (phrase.isNotBlank() && end > start) {
                        items += Subtitle(start, end, if (hinglish) romanize(phrase) else phrase)
                    }
                }
            }
            if (items.isEmpty()) throw IllegalStateException("No timed speech was returned. Try clearer audio or a shorter recording.")
            return Result(items, root.optString("language", "unknown"))
        } finally { connection.disconnect() }
    }

    /** Phonetic transliteration; NOT a semantic Hinglish translator or perfect Roman Urdu. */
    private fun romanize(value: String): String {
        return try {
            Transliterator.getInstance("Any-Latin; Latin-ASCII").transliterate(value)
                .replace(Regex("\\s+"), " ").trim()
        } catch (_: Exception) { value }
    }
}
