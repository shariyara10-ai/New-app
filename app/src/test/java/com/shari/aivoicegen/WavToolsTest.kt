package com.shari.aivoicegen

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavToolsTest {
    /** Test fixture uses generated PCM tone; the shipping app never substitutes fixture audio for TTS. */
    private fun writeFixture(file: File, sampleRate: Int = 16000, samples: Int = 1600) {
        val dataBytes = samples * 2
        val wav = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()); wav.putInt(36 + dataBytes)
        wav.put("WAVEfmt ".toByteArray()); wav.putInt(16); wav.putShort(1); wav.putShort(1)
        wav.putInt(sampleRate); wav.putInt(sampleRate * 2); wav.putShort(2); wav.putShort(16)
        wav.put("data".toByteArray()); wav.putInt(dataBytes)
        repeat(samples) { wav.putShort((if (it % 2 == 0) 2500 else -2500).toShort()) }
        file.writeBytes(wav.array())
    }

    @Test fun inspectsAndMergesValidWavs() {
        val dir = createTempDir(prefix = "aivoicegen_test_")
        try {
            val a = File(dir, "a.wav"); val b = File(dir, "b.wav"); val out = File(dir, "joined.wav")
            writeFixture(a); writeFixture(b)
            assertEquals(100L, WavTools.inspect(a).durationMs)
            val merged = WavTools.merge(listOf(WavTools.Part(a, 250), WavTools.Part(b)), out)
            assertEquals(450L, merged.durationMs)
            assertEquals(1, merged.channels)
            assertEquals(16000, merged.sampleRate)
            assertEquals(48, WavTools.waveform(out).size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsInvalidOrEmptySpeech() {
        val file = File.createTempFile("invalid_", ".wav")
        try {
            file.writeText("Not real speech")
            assertThrows(IllegalArgumentException::class.java) { WavTools.inspect(file) }
        } finally { file.delete() }
    }
}
