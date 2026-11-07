package com.shari.aivoicegen

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/** Validates PCM16 WAV, merges engine-generated files with constant-memory streaming. */
object WavTools {
    data class Info(val sampleRate: Int, val channels: Int, val dataOffset: Long, val dataSize: Long) {
        val frameBytes: Int get() = channels * 2
        val durationMs: Long get() = dataSize * 1000L / (sampleRate * frameBytes)
    }

    private fun RandomAccessFile.u16(): Int {
        val a = readUnsignedByte()
        val b = readUnsignedByte()
        return a or (b shl 8)
    }
    private fun RandomAccessFile.u32(): Long {
        val a = u16().toLong()
        val b = u16().toLong()
        return a or (b shl 16)
    }
    private fun DataOutputStream.u16(n: Int) {
        writeByte(n and 255); writeByte((n ushr 8) and 255)
    }
    private fun DataOutputStream.u32(n: Long) {
        writeByte((n and 255).toInt()); writeByte(((n ushr 8) and 255).toInt())
        writeByte(((n ushr 16) and 255).toInt()); writeByte(((n ushr 24) and 255).toInt())
    }

    fun inspect(file: File): Info = RandomAccessFile(file, "r").use { wav ->
        require(wav.length() >= 44) { "TTS engine produced an empty or short audio file." }
        require(ByteArray(4).also { wav.readFully(it) }.decodeToString() == "RIFF") { "TTS output isn't a RIFF WAV file." }
        wav.u32()
        require(ByteArray(4).also { wav.readFully(it) }.decodeToString() == "WAVE") { "TTS output isn't WAV audio." }
        var fmt = false; var rate = 0; var channels = 0; var bits = 0; var encoding = 0
        var offset = -1L; var length = 0L
        while (wav.filePointer + 8 <= wav.length()) {
            val id = ByteArray(4).also { wav.readFully(it) }.decodeToString()
            val size = wav.u32()
            val start = wav.filePointer
            if (size > wav.length() - start) error("Invalid WAV chunk length ($id).")
            if (id == "fmt ") {
                require(size >= 16) { "Malformed WAV format." }
                encoding = wav.u16(); channels = wav.u16(); rate = wav.u32().toInt()
                wav.u32(); wav.u16(); bits = wav.u16(); fmt = true
            } else if (id == "data") {
                offset = start; length = size
            }
            val next = start + size + (size and 1L)
            if (next > wav.length()) break
            wav.seek(next)
        }
        require(fmt && offset >= 0 && length > 0) { "TTS engine did not generate audible WAV data." }
        require(encoding == 1 && bits == 16 && channels in 1..2 && rate in 8000..192000) {
            "This engine exported an unsupported WAV format: codec=$encoding, ${bits}-bit, channels=$channels, sampleRate=$rate. Choose another engine."
        }
        require(length % (channels * 2) == 0L) { "Audio frames are truncated." }
        Info(rate, channels, offset, length)
    }

    data class Part(val file: File, val pauseAfterMs: Int = 0)

    /** Input WAV formats must match. Output is PCM16, mono or stereo as produced by the engine. */
    fun merge(parts: List<Part>, output: File, gain: Float = 1f, trimSilence: Boolean = false): Info {
        require(parts.isNotEmpty()) { "No narration segments were generated." }
        val infos = parts.map { inspect(it.file) }
        val first = infos.first()
        require(infos.all { it.sampleRate == first.sampleRate && it.channels == first.channels }) {
            "Voices exported different sample rates or channel layouts. Select compatible voices or export them separately."
        }
        val ranges = parts.mapIndexed { index, part ->
            if (trimSilence) nonSilentRange(part.file, infos[index]) else 0L to infos[index].dataSize
        }
        val pauseFrames = parts.map { it.pauseAfterMs.coerceIn(0, 5000).toLong() * first.sampleRate / 1000L }
        val total = ranges.sumOf { it.second - it.first } + pauseFrames.sum() * first.frameBytes
        require(total in 2..(0xFFFFFFFFL - 36)) { "Generated audio is empty or exceeds the WAV 4GB limit." }
        output.parentFile?.mkdirs()
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(output))).use { stream ->
                stream.writeBytes("RIFF"); stream.u32(36L + total); stream.writeBytes("WAVE")
                stream.writeBytes("fmt "); stream.u32(16); stream.u16(1); stream.u16(first.channels)
                stream.u32(first.sampleRate.toLong()); stream.u32((first.sampleRate * first.frameBytes).toLong())
                stream.u16(first.frameBytes); stream.u16(16)
                stream.writeBytes("data"); stream.u32(total)
                parts.forEachIndexed { index, part ->
                    RandomAccessFile(part.file, "r").use { wav ->
                        val (start, end) = ranges[index]
                        wav.seek(infos[index].dataOffset + start)
                        var left = end - start
                        val buf = ByteArray(32768)
                        while (left > 0) {
                            val count = minOf(left, buf.size.toLong()).toInt()
                            wav.readFully(buf, 0, count)
                            if (gain != 1f) {
                                var pos = 0
                                while (pos + 1 < count) {
                                    val sample = ((buf[pos].toInt() and 255) or (buf[pos + 1].toInt() shl 8)).toShort().toInt()
                                    val adjusted = (sample * gain).toInt().coerceIn(-32768, 32767)
                                    buf[pos] = adjusted.toByte(); buf[pos + 1] = (adjusted shr 8).toByte()
                                    pos += 2
                                }
                            }
                            stream.write(buf, 0, count); left -= count
                        }
                    }
                    var silence = pauseFrames[index] * first.frameBytes
                    val zeros = ByteArray(32768)
                    while (silence > 0) {
                        val n = minOf(silence, zeros.size.toLong()).toInt()
                        stream.write(zeros, 0, n); silence -= n
                    }
                }
            }
            return inspect(output)
        } catch (ex: Exception) {
            output.delete()
            throw ex
        }
    }

    /** Small actual PCM amplitude preview, sampled rather than reading long files fully. */
    fun waveform(file: File, bars: Int = 48): List<Float> {
        val info = inspect(file)
        val frames = info.dataSize / info.frameBytes
        return RandomAccessFile(file, "r").use { wav ->
            (0 until bars).map { bar ->
                val start = frames * bar / bars
                val end = frames * (bar + 1) / bars
                val samples = 96L
                val stride = ((end - start) / samples).coerceAtLeast(1L)
                var peak = 0
                val frame = ByteArray(info.frameBytes)
                var f = start
                while (f < end) {
                    wav.seek(info.dataOffset + f * info.frameBytes)
                    wav.readFully(frame)
                    for (channel in 0 until info.channels) {
                        val x = channel * 2
                        val sample = ((frame[x].toInt() and 255) or (frame[x + 1].toInt() shl 8)).toShort().toInt()
                        peak = maxOf(peak, kotlin.math.abs(sample))
                    }
                    f += stride
                }
                (peak / 32768f).coerceIn(0.02f, 1f)
            }
        }
    }

    private fun nonSilentRange(file: File, info: Info): Pair<Long, Long> =
        RandomAccessFile(file, "r").use { wav ->
            val frameBytes = info.frameBytes
            val totalFrames = info.dataSize / frameBytes
            val buf = ByteArray((65536 / frameBytes) * frameBytes)
            var firstFrame = -1L
            var lastFrame = -1L
            var frameCursor = 0L
            wav.seek(info.dataOffset)
            while (frameCursor < totalFrames) {
                val blockFrames = minOf(totalFrames - frameCursor, (buf.size / frameBytes).toLong()).toInt()
                val n = blockFrames * frameBytes
                wav.readFully(buf, 0, n)
                for (i in 0 until blockFrames) {
                    val start = i * frameBytes
                    var voiced = false
                    for (channel in 0 until info.channels) {
                        val x = start + channel * 2
                        val sample = ((buf[x].toInt() and 255) or (buf[x + 1].toInt() shl 8)).toShort().toInt()
                        if (kotlin.math.abs(sample) > 120) { voiced = true; break }
                    }
                    if (voiced) {
                        if (firstFrame == -1L) firstFrame = frameCursor + i
                        lastFrame = frameCursor + i
                    }
                }
                frameCursor += blockFrames
            }
            if (firstFrame < 0) 0L to info.dataSize else {
                val pad = (info.sampleRate / 100L).toLong()
                (firstFrame - pad).coerceAtLeast(0) * frameBytes to
                    (lastFrame + 1 + pad).coerceAtMost(totalFrames) * frameBytes
            }
        }
}
