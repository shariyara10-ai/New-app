package com.shari.aivoicegen

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Real Android TextToSpeech engine. No online API keys, made-up voice catalog, or fake synthesis. */
class TtsManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: TextToSpeech? = null
    private val initialization = CompletableDeferred<Unit>()
    private val responses = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var playback: Job? = null

    val ready = MutableStateFlow(false)
    val voices = MutableStateFlow<List<Voice>>(emptyList())
    val state = MutableStateFlow("Connecting to installed Android speech engine…")
    val isPlaying = MutableStateFlow(false)
    val paused = MutableStateFlow(false)
    val isExporting = MutableStateFlow(false)
    val progress = MutableStateFlow(0f)

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) {
            utteranceId?.let { responses.remove(it)?.complete(Unit) }
        }
        @Deprecated("Required to support Android engines with old error callbacks")
        override fun onError(utteranceId: String?) {
            utteranceId?.let { responses.remove(it)?.completeExceptionally(IllegalStateException("Speech synthesis failed for this segment.")) }
        }
        override fun onError(utteranceId: String?, errorCode: Int) {
            utteranceId?.let { responses.remove(it)?.completeExceptionally(IllegalStateException("TTS error code $errorCode.")) }
        }
        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            utteranceId?.let { responses.remove(it)?.completeExceptionally(InterruptedException("Speech stopped.")) }
        }
    }

    init {
        engine = TextToSpeech(appContext) { code ->
            Handler(Looper.getMainLooper()).post {
                try {
                    if (code != TextToSpeech.SUCCESS) error("Android speech engine failed to initialize. Install or select an Android TTS engine in system Settings.")
                    val tts = engine ?: error("Android TTS engine isn't available.")
                    tts.setOnUtteranceProgressListener(listener)
                    voices.value = (tts.voices ?: emptySet()).sortedWith(
                        compareBy<Voice>({ it.locale.toLanguageTag() }, { it.name })
                    )
                    ready.value = true
                    state.value = "Ready · ${voices.value.size} voices reported by your TTS engine"
                    initialization.complete(Unit)
                } catch (e: Exception) {
                    state.value = "TTS unavailable: ${e.message}"
                    initialization.completeExceptionally(e)
                }
            }
        }
    }

    fun refreshVoices() {
        val tts = engine ?: return
        try {
            voices.value = (tts.voices ?: emptySet()).sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
            state.value = "${voices.value.size} voice(s) detected. Availability depends on installed engine and data."
        } catch (e: Exception) { state.value = "Voice discovery failed: ${e.message}" }
    }

    fun engines(): List<String> = runCatching {
        engine?.engines?.map { "${it.label} (${it.name})" } ?: emptyList()
    }.getOrDefault(emptyList())

    fun findVoice(name: String): Voice? = voices.value.firstOrNull { it.name == name }
    private suspend fun activeEngine(): TextToSpeech {
        initialization.await()
        return engine ?: error("TTS engine isn't initialized.")
    }

    private fun applyVoice(tts: TextToSpeech, voiceName: String, speed: Float, pitch: Float) {
        val v = findVoice(voiceName) ?: error("Voice is unavailable. Refresh the voice library and choose an installed voice.")
        require(tts.setVoice(v) != TextToSpeech.ERROR) { "This voice could not be activated by the installed TTS engine." }
        require(tts.setSpeechRate(speed.coerceIn(0.5f, 2f)) != TextToSpeech.ERROR) { "This engine rejected speed adjustment." }
        require(tts.setPitch(pitch.coerceIn(0.5f, 2f)) != TextToSpeech.ERROR) { "This engine rejected pitch adjustment." }
    }

    private suspend fun callEngine(text: String, wavPath: File? = null) {
        val tts = activeEngine()
        val id = UUID.randomUUID().toString()
        val response = CompletableDeferred<Unit>()
        responses[id] = response
        val result = if (wavPath == null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        } else {
            tts.synthesizeToFile(text, Bundle(), wavPath, id)
        }
        if (result == TextToSpeech.ERROR) {
            responses.remove(id)
            error("Android TTS engine rejected the request. Check voice language and installed data.")
        }
        try { withTimeout(120_000L) { response.await() } }
        finally { responses.remove(id) }
        if (wavPath != null) WavTools.inspect(wavPath)
    }

    fun play(text: String, voice: String, speed: Float, pitch: Float) {
        if (isExporting.value) { state.value = "Finish exporting before playing."; return }
        stop()
        if (text.isBlank()) { state.value = "Enter narration text first."; return }
        playback = scope.launch {
            try {
                val tts = activeEngine()
                applyVoice(tts, voice, speed, pitch)
                val chunks = TextTools.chunks(text)
                isPlaying.value = true; paused.value = false
                chunks.forEachIndexed { index, chunk ->
                    // If paused mid-chunk, pause stops the engine; resume repeats that chunk.
                    var completed = false
                    while (!completed) {
                        while (paused.value) delay(100)
                        try { callEngine(chunk); completed = true }
                        catch (e: InterruptedException) {
                            if (!paused.value) throw e
                        }
                    }
                    progress.value = (index + 1).toFloat() / chunks.size
                }
                state.value = "Playback completed."
            } catch (_: CancellationException) {
                state.value = "Playback stopped."
            } catch (e: Exception) {
                state.value = "Playback error: ${e.message}"
            } finally {
                isPlaying.value = false; paused.value = false
            }
        }
    }

    fun pauseOrResume() {
        if (!isPlaying.value) return
        if (paused.value) {
            paused.value = false
            state.value = "Resuming from the current text segment…"
        } else {
            paused.value = true
            state.value = "Paused; resume restarts the interrupted segment."
            engine?.stop()
        }
    }

    fun stop() {
        playback?.cancel()
        playback = null
        paused.value = false
        isPlaying.value = false
        progress.value = 0f
        if (!isExporting.value) engine?.stop()
    }

    /** Generate all segments, validate each WAV, then join without loading long audio into RAM. */
    suspend fun export(
        lines: List<SpeakerLine>, speed: Float, pitch: Float, gain: Float = 1f,
        trim: Boolean = false, title: String = "narration"
    ): File {
        require(!isExporting.value) { "An export is already running." }
        stop()
        isExporting.value = true
        progress.value = 0f
        val temp = File(appContext.cacheDir, "tts-${UUID.randomUUID()}").apply { mkdirs() }
        val output = File(appContext.filesDir, "audio/${TextTools.safeFileName(title)}-${System.currentTimeMillis()}.wav")
        try {
            val tts = activeEngine()
            val steps = lines.filter { it.content.isNotBlank() }.flatMap { line ->
                val pieces = TextTools.chunks(line.content)
                pieces.mapIndexed { index, chunk ->
                    Triple(line.voiceName, chunk, if (index == pieces.lastIndex) line.pauseMs else 0)
                }
            }
            require(steps.isNotEmpty()) { "No text found to export." }
            val parts = mutableListOf<WavTools.Part>()
            steps.forEachIndexed { index, (voiceName, chunk, pause) ->
                applyVoice(tts, voiceName, speed, pitch)
                val file = File(temp, "$index.wav")
                state.value = "Synthesizing segment ${index + 1} / ${steps.size}…"
                callEngine(chunk, file)
                parts += WavTools.Part(file, pause)
                progress.value = (index + 1).toFloat() / (steps.size + 1)
            }
            state.value = "Validating and merging audio…"
            val result = withContext(Dispatchers.IO) { WavTools.merge(parts, output, gain, trim) }
            progress.value = 1f
            state.value = "Exported ${result.durationMs / 1000}s WAV · ${output.name}"
            return output
        } catch (ex: Exception) {
            output.delete()
            state.value = "Export failed: ${ex.message}"
            throw ex
        } finally {
            isExporting.value = false
            withContext(Dispatchers.IO) { temp.deleteRecursively() }
        }
    }

    fun close() {
        stop()
        engine?.shutdown()
        engine = null
        scope.cancel()
    }
}
