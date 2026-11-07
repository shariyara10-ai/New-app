package com.shari.cutstudio

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.lifecycleScope
import com.shari.cutstudio.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val ink = Color(0xFF0A0E18)
private val panel = Color(0xFF151B29)
private val stroke = Color(0xFF2B3345)
private val lavender = Color(0xFFAE9CFF)
private val aqua = Color(0xFF74E6D3)
private val muted = Color(0xFF9CA8C0)

class MainActivity : ComponentActivity() {
    private var info by mutableStateOf<MediaInfo?>(null)
    private var fileName by mutableStateOf("")
    private var scanResults by mutableStateOf<List<TimeRange>>(emptyList())
    private var didScan by mutableStateOf(false)
    private var threshold by mutableFloatStateOf(-38f)
    private var pause by mutableFloatStateOf(0.48f)
    private var pad by mutableFloatStateOf(0.13f)
    private var removeSilence by mutableStateOf(true)
    private var cleanNoise by mutableStateOf(true)
    private var voiceBoost by mutableStateOf(true)
    private var boostVideo by mutableStateOf(false)
    private var resolution by mutableIntStateOf(0)
    private var burnCaptions by mutableStateOf(false)
    private var audioOnlyExport by mutableStateOf(false)
    private var exportedWasVideo by mutableStateOf(false)
    private var captionStyle by mutableStateOf("Modern")
    private var captions by mutableStateOf("")
    private var apiKey by mutableStateOf("")
    private var speechLanguage by mutableStateOf("auto")
    private var romanCaptions by mutableStateOf(true)
    private var busy by mutableStateOf(false)
    private var busyLabel by mutableStateOf("")
    private var notice by mutableStateOf("")
    private var exportedPath by mutableStateOf<String?>(null)
    private var exportedUri by mutableStateOf<Uri?>(null)
    private var tab by mutableIntStateOf(0)

    private val openMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadMedia(it) }
    }
    private val openSrt = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                try {
                    captions = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(it)!!.bufferedReader(Charsets.UTF_8).use { r -> r.readText() }
                    }
                    notice = "Imported ${Subtitles.parse(captions).size} timed caption segments."
                } catch (e: Exception) { notice = "SRT import failed: ${e.message}" }
            }
        }
    }
    private val saveSrt = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
        uri?.let {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(it)!!.bufferedWriter(Charsets.UTF_8).use { writer ->
                            writer.write(captions)
                        }
                    }
                    notice = "Caption file saved."
                } catch (e: Exception) { notice = "Could not save captions: ${e.message}" }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme = darkColorScheme(
                primary = lavender, onPrimary = ink, secondary = aqua,
                background = ink, surface = panel, onSurface = Color.White,
                onBackground = Color.White, outline = stroke
            )
            MaterialTheme(colorScheme = theme) { AppUI() }
        }
    }

    private fun displayName(uri: Uri): String = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else "Imported media"
        } ?: "Imported media"
    } catch (_: Exception) { "Imported media" }

    private fun loadMedia(uri: Uri) {
        if (busy) return
        busy = true
        busyLabel = "Importing your media…"
        notice = ""
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val title = displayName(uri)
                    val ext = title.substringAfterLast('.', "mp4").lowercase(Locale.US)
                        .takeIf { it.matches(Regex("[a-z0-9]{2,5}")) } ?: "mp4"
                    val staged = File(cacheDir, "source_${System.nanoTime()}.$ext")
                    contentResolver.openInputStream(uri)!!.use { source ->
                        staged.outputStream().use { dest -> source.copyTo(dest) }
                    }
                    Triple(title, staged, VideoEngine.probe(staged.absolutePath))
                }
                require(result.third.length > 0.01) { "No readable media duration found. Try MP4, MOV, MP3, M4A or WAV." }
                require(result.third.hasAudio || result.third.hasVideo) { "No supported media tracks were found." }
                fileName = result.first
                info = result.third
                didScan = false
                scanResults = emptyList()
                captions = ""
                exportedPath = null
                exportedUri = null
                removeSilence = result.third.hasAudio
                audioOnlyExport = false
                tab = 0
                notice = "Media imported. Select your edits, then export."
            } catch (e: Exception) {
                notice = "Import failed: ${e.message}"
            } finally { busy = false }
        }
    }

    private fun scan() {
        val media = info ?: return
        if (busy) return
        if (!media.hasAudio) { notice = "This video has no audio to detect silence from."; return }
        busy = true
        busyLabel = "Analyzing pauses and speaking segments…"
        notice = ""
        lifecycleScope.launch {
            try {
                scanResults = withContext(Dispatchers.IO) {
                    VideoEngine.detect(media, threshold.toInt(), pause.toDouble())
                }
                didScan = true
                val keep = Timeline.keepRanges(media.length, scanResults, pad.toDouble(), pause.toDouble())
                notice = "Scan complete • ${scanResults.size} silent parts • ${durationText(media.length - Timeline.duration(keep))} removable."
            } catch (e: LinkageError) {
                android.util.Log.e("ShariCutExport", "Silence scanner library load failed", e)
                notice = "Silence scanner failed to load. Install the latest APK."
                didScan = false
            } catch (e: Exception) {
                android.util.Log.e("ShariCutExport", "Silence scan failed", e)
                notice = "Scan error: ${e.message}"
                didScan = false
            } finally { busy = false }
        }
    }

    private fun export() {
        val media = info ?: return
        if (busy) return
        if (removeSilence && !media.hasAudio) { notice = "Turn off silence removal: the selected video has no audio track."; return }
        busy = true
        busyLabel = "Processing media; keep this screen open…"
        notice = ""
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val found = if (removeSilence && !didScan) {
                        VideoEngine.detect(media, threshold.toInt(), pause.toDouble())
                    } else scanResults
                    val kept = if (removeSilence) {
                        Timeline.keepRanges(media.length, found, pad.toDouble(), pause.toDouble())
                    } else listOf(TimeRange(0.0, media.length))
                    require(kept.isNotEmpty()) { "Nothing remains after cutting. Lower the silence sensitivity." }
                    val settings = ExportOptions(
                        removeSilence = removeSilence, padSeconds = pad.toDouble(),
                        denoise = cleanNoise, voiceBoost = voiceBoost,
                        enhanceVideo = boostVideo, resolution = resolution,
                        burnCaptions = burnCaptions, captionStyle = captionStyle,
                        audioOnly = audioOnlyExport
                    )
                    val outputVideo = media.hasVideo && !audioOnlyExport
                    val output = File(cacheDir, "cutstudio_export_${System.currentTimeMillis()}.${if (outputVideo) "mp4" else "m4a"}")
                    VideoEngine.export(this@MainActivity, media, kept, settings, captions, output)
                    val saved = putInGallery(output, outputVideo)
                    Triple(Triple(output.absolutePath, saved, found), kept, Timeline.duration(kept))
                }
                exportedPath = result.first.first
                exportedWasVideo = media.hasVideo && !audioOnlyExport
                exportedUri = result.first.second
                scanResults = result.first.third
                didScan = true
                notice = "Export saved • ${durationText(result.third)} • ${if (exportedWasVideo) "Movies" else "Music"}/ShariCut Studio"
            } catch (e: LinkageError) {
                android.util.Log.e("ShariCutExport", "Native FFmpeg library load failed", e)
                notice = "Export engine could not load. Please install the updated APK. ${e.message.orEmpty()}"
            } catch (e: Exception) {
                android.util.Log.e("ShariCutExport", "Export failed", e)
                notice = "Export failed: ${e.message ?: e.javaClass.simpleName}"
            } finally { busy = false }
        }
    }

    private fun putInGallery(file: File, video: Boolean): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC) + "/ShariCut Studio")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val bucket = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uri = contentResolver.insert(bucket, values) ?: error("Could not open device MediaStore")
        try {
            contentResolver.openOutputStream(uri)!!.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun transcribe() {
        val media = info ?: return
        if (busy) return
        if (!media.hasAudio) { notice = "No voice track to transcribe."; return }
        if (apiKey.isBlank()) { notice = "Enter your Groq API key in the Captions tab."; return }
        busy = true
        busyLabel = "Preparing audio and generating multilingual subtitles…"
        notice = ""
        lifecycleScope.launch {
            try {
                val generated = withContext(Dispatchers.IO) {
                    val mp3 = File(cacheDir, "speech_${System.nanoTime()}.mp3")
                    try {
                        GroqCaptioner.makeTranscriptionAudio(File(media.path), mp3)
                        GroqCaptioner.transcribe(mp3, apiKey, speechLanguage, romanCaptions)
                    } finally { mp3.delete() }
                }
                captions = Subtitles.asSrt(generated.subtitles)
                tab = 1
                notice = "${generated.subtitles.size} timed subtitles generated (${generated.language}). Review spelling before export."
            } catch (e: Exception) {
                notice = "Auto-caption error: ${e.message}"
            } finally { busy = false }
        }
    }

    private fun continueEditingExport() {
        val path = exportedPath ?: return
        try {
            val previous = info ?: return
            val kept = if (removeSilence) {
                Timeline.keepRanges(previous.length, scanResults, pad.toDouble(), pause.toDouble())
            } else listOf(TimeRange(0.0, previous.length))
            if (captions.isNotBlank()) captions = Subtitles.asSrt(Subtitles.remap(Subtitles.parse(captions), kept))
            info = VideoEngine.probe(path)
            audioOnlyExport = false
            fileName = File(path).name
            didScan = false
            scanResults = emptyList()
            removeSilence = false // already cut; don't remove twice by accident
            burnCaptions = captions.isNotBlank()
            exportedUri = null
            exportedPath = null
            tab = 1
            notice = "Now editing the cut export. Generate/import captions, then export again to burn them into the video."
        } catch (e: Exception) { notice = "Could not reopen output: ${e.message}" }
    }

    private fun shareExport() {
        val uri = exportedUri ?: return
        val video = exportedWasVideo
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (video) "video/mp4" else "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share your edited media"))
    }

    @Composable
    private fun AppUI() {
        Scaffold(
            containerColor = ink,
            bottomBar = {
                NavigationBar(containerColor = panel, modifier = Modifier.navigationBarsPadding()) {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Rounded.ContentCut, null) }, label = { Text("Editor") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Rounded.ClosedCaption, null) }, label = { Text("Captions") })
                    NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.Rounded.Tune, null) }, label = { Text("Settings") })
                }
            }
        ) { safePadding ->
            Column(
                Modifier.fillMaxSize().padding(safePadding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Header()
                if (busy) {
                    PanelCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = aqua, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(busyLabel, color = Color.White, fontSize = 13.sp)
                        }
                        Text("Processing stays on this screen. Avoid closing the app.", color = muted, fontSize = 11.sp)
                    }
                }
                if (notice.isNotBlank()) {
                    Surface(color = Color(0xFF222C40), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, null, tint = aqua, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(notice, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                when (tab) {
                    0 -> EditorTab()
                    1 -> CaptionsTab()
                    else -> SettingsTab()
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    @Composable
    private fun Header() {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).background(
                Brush.linearGradient(listOf(lavender, Color(0xFF5C60FA))), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ContentCut, null, tint = ink, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("SHARICUT", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.5.sp)
                Text("STUDIO PRO", color = aqua, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 3.sp)
            }
            Surface(color = Color(0xFF233B3B), shape = RoundedCornerShape(50)) {
                Text("ON DEVICE", color = aqua, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
        }
    }

    @Composable
    private fun EditorTab() {
        Box(Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF31275E), Color(0xFF17333A))),
            RoundedCornerShape(23.dp)).padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your cleanest cut.\nIn a few taps.", fontSize = 25.sp,
                    lineHeight = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("Trim dead air • Clean voice • Polish video", fontSize = 12.sp, color = Color(0xFFD4D9EC))
                Button(onClick = { openMedia.launch(arrayOf("video/*", "audio/*")) }, enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ink)) {
                    Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (info == null) "Import media" else "Change media", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (info == null) {
            PanelCard {
                Text("Made for faceless YouTube creators", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Videos, voiceovers, podcasts and clips. Import an MP4, MP3, WAV or M4A to begin.", color = muted, fontSize = 13.sp)
                FeatureRow("✂️", "Smart Silence Cutter", "Remove unwanted pauses from BOTH video and audio")
                FeatureRow("🎙️", "Voice Cleanup", "Reduce hiss and level voice automatically")
                FeatureRow("💬", "Subtitle Studio", "Hindi, Urdu, Hinglish, English and SRT")
            }
            return
        }
        val media = info!!
        PanelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (media.hasVideo) Icons.Rounded.Movie else Icons.Rounded.Mic, null, tint = aqua)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (media.hasVideo) "VIDEO" else "AUDIO"}  •  ${durationText(media.length)}  •  Local file", color = muted, fontSize = 11.sp)
                }
            }
            if (media.hasVideo) {
                key(media.path) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                            .background(Color.Black, RoundedCornerShape(14.dp)),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoPath(media.path)
                                val videoView = this
                                setMediaController(MediaController(ctx).apply { setAnchorView(videoView) })
                                setOnPreparedListener { player -> player.isLooping = false }
                            }
                        }
                    )
                }
                Text("Tap the preview to play original media.", color = muted, fontSize = 11.sp)
            }
        }
        PanelCard {
            SectionHeading("Silence Cutter", "01", "Remove hesitation and dead air")
            AppSwitch("Remove silent sections", removeSilence, { removeSilence = it }, media.hasAudio)
            if (removeSilence && media.hasAudio) {
                SliderLabel("Silence sensitivity", "${threshold.toInt()} dB")
                Slider(value = threshold, onValueChange = { threshold = it; didScan = false },
                    valueRange = -55f..-20f, enabled = !busy)
                CaptionText("Lower dB detects only very quiet sections. Higher dB cuts more aggressively.")
                SliderLabel("Minimum pause", String.format(Locale.US, "%.2f sec", pause))
                Slider(value = pause, onValueChange = { pause = it; didScan = false },
                    valueRange = .2f..1.8f, enabled = !busy)
                SliderLabel("Keep natural breathing room", "${(pad * 1000).toInt()} ms")
                Slider(value = pad, onValueChange = { pad = it }, valueRange = .03f..0.3f, enabled = !busy)
                OutlinedButton(onClick = { scan() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.GraphicEq, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan for silences")
                }
                if (didScan) {
                    val keep = Timeline.keepRanges(media.length, scanResults, pad.toDouble(), pause.toDouble())
                    val saved = media.length - Timeline.duration(keep)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Silences", "${scanResults.size}", Modifier.weight(1f))
                        StatCard("Time cut", durationText(saved), Modifier.weight(1f))
                        StatCard("Final", durationText(Timeline.duration(keep)), Modifier.weight(1f))
                    }
                    scanResults.take(4).forEachIndexed { index, range ->
                        Text("Pause ${index + 1}   ${durationText(range.start)} → ${durationText(range.end)}", fontSize = 11.sp, color = muted)
                    }
                    if (scanResults.size > 4) CaptionText("…and ${scanResults.size - 4} more detected pauses")
                }
            }
        }
        PanelCard {
            SectionHeading("Audio Enhancement", "02", "Crisper voice, controlled loudness")
            AppSwitch("Background hiss reduction", cleanNoise, { cleanNoise = it }, media.hasAudio)
            AppSwitch("Voice boost + loudness balance", voiceBoost, { voiceBoost = it }, media.hasAudio)
            CaptionText("Spectral reduction is designed for steady noise, not guaranteed to remove voices, music or every background sound.")
        }
        if (media.hasVideo) PanelCard {
            SectionHeading("Video Polish", "03", "Clean picture, same aspect ratio")
            AppSwitch("Enhance contrast, color and sharpness", boostVideo, { boostVideo = it })
            CaptionText("Export resolution")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Original" to 0, "720p" to 1280, "1080p" to 1920).forEach { (label, value) ->
                    FilterChip(selected = resolution == value, onClick = { resolution = value },
                        label = { Text(label, fontSize = 11.sp) })
                }
            }
            AppSwitch("Burn captions onto exported video", burnCaptions, { burnCaptions = it })
            if (burnCaptions) {
                CaptionText(if (captions.isBlank()) "No SRT loaded. Add subtitles in the Captions tab first." else "${Subtitles.parse(captions).size} caption segments will be embedded.")
            }
        }
        PanelCard {
            SectionHeading("Export Studio", "04", "Final video or audio, no watermark")
            if (media.hasVideo && media.hasAudio) {
                AppSwitch("Extract cleaned voiceover only (M4A)", audioOnlyExport, { audioOnlyExport = it })
            }
            Button(onClick = { export() }, enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = lavender, contentColor = ink),
                modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("Export ${if (media.hasVideo && !audioOnlyExport) "MP4 video" else "M4A audio"}", fontWeight = FontWeight.ExtraBold)
            }
            if (exportedUri != null) {
                OutlinedButton(onClick = { shareExport() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Share saved export")
                }
                TextButton(onClick = { continueEditingExport() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue editing this export → captions")
                }
            }
            CaptionText("First export is local; auto-captions are an optional separate online step. Your original file is never overwritten.")
        }
    }

    @Composable
    private fun CaptionsTab() {
        PanelCard {
            SectionHeading("Caption Studio", "01", "Timed subtitles for global audiences")
            Text("Urdu • Hindi • Hinglish • English", color = aqua, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Auto transcription is optional and uses your own Groq API key. Uploaded audio leaves your phone only when you tap Generate.", color = muted, fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it }, label = { Text("Groq API key (not stored)") },
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                singleLine = true, enabled = !busy
            )
            CaptionText("Auto-transcription language")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("Auto" to "auto", "Urdu" to "ur", "Hindi" to "hi", "English" to "en")
                    .forEach { (name, code) ->
                        FilterChip(selected = speechLanguage == code, onClick = { speechLanguage = code },
                            label = { Text(name, fontSize = 10.sp) })
                    }
            }
            AppSwitch("Convert Urdu/Hindi script into Roman text (approx.)", romanCaptions, { romanCaptions = it })
            Button(onClick = { transcribe() }, enabled = !busy && info?.hasAudio == true,
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp))
                Text("Generate timed auto-captions")
            }
            CaptionText("Whisper may return Urdu script, Hindi script or mixed-language text. Roman transliteration is phonetic, not guaranteed natural Hinglish. Review and correct it.")
        }
        PanelCard {
            SectionHeading("Subtitle Editor", "02", "Import, edit and save SRT")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { openSrt.launch(arrayOf("application/x-subrip", "text/*", "*/*")) },
                    enabled = !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.UploadFile, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Import SRT")
                }
                OutlinedButton(onClick = { saveSrt.launch("sharicut_captions.srt") },
                    enabled = captions.isNotBlank() && !busy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Save SRT")
                }
            }
            OutlinedTextField(value = captions, onValueChange = { captions = it },
                label = { Text("SRT captions") }, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                placeholder = { Text("1\n00:00:00,000 --> 00:00:02,500\nYahan apna caption likhein…") },
                minLines = 9, maxLines = 18, enabled = !busy)
            Text("Valid caption blocks: ${Subtitles.parse(captions).size}", color = aqua, fontSize = 11.sp)
        }
        PanelCard {
            SectionHeading("Caption Design", "03", "Readable on mobile")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Modern", "Cinematic", "Minimal").forEach { name ->
                    FilterChip(selected = captionStyle == name, onClick = { captionStyle = name },
                        label = { Text(name, fontSize = 10.sp) })
                }
            }
            AppSwitch("Burn captions during MP4 export", burnCaptions, { burnCaptions = it })
            Text("For best sync: cut the silences first, then tap 'Continue editing this export', generate captions and export again.", color = muted, fontSize = 12.sp)
            OutlinedButton(onClick = { tab = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Go to Video Export") }
        }
    }

    @Composable
    private fun SettingsTab() {
        PanelCard {
            SectionHeading("Creator Workflow", "PRO", "Designed for your phone")
            FeatureRow("①", "Import media", "MP4, MOV, MP3, M4A or WAV from phone storage")
            FeatureRow("②", "Scan & cut", "Use threshold, pause length and breathing padding")
            FeatureRow("③", "Enhance", "Noise cleanup, voice loudness, optional video polish")
            FeatureRow("④", "Export", "Local MP4/M4A saved in your phone gallery")
            FeatureRow("⑤", "Add captions", "Optional Groq speech-to-text or manually imported SRT")
        }
        PanelCard {
            SectionHeading("Privacy and Limits", "INFO", "No misleading AI claims")
            Text("✓ No app account, mandatory ads or watermark", color = aqua, fontSize = 13.sp)
            Text("✓ FFmpeg processing runs locally on the phone", color = aqua, fontSize = 13.sp)
            Text("✓ API key stays in current app memory and is not saved", color = aqua, fontSize = 13.sp)
            Text("• Automatic captions require a Groq account/key and internet. Provider limits or charges may apply.", color = muted, fontSize = 12.sp)
            Text("• No offline Whisper model is bundled. Roman Urdu/Hinglish conversion is approximate.", color = muted, fontSize = 12.sp)
            Text("• Large 4K files, many cuts or advanced filter graphs can process slowly or fail on some Android chipsets.", color = muted, fontSize = 12.sp)
            Text("• Outputs are available under Movies or Music / ShariCut Studio.", color = muted, fontSize = 12.sp)
        }
        PanelCard {
            Text("SHARICUT STUDIO PRO  •  v1.0.1", fontWeight = FontWeight.Bold)
            Text("Open-source Android starter implementation built with Kotlin, Jetpack Compose and FFmpeg. See README for dependencies, testing and third-party licensing.", color = muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, stroke, RoundedCornerShape(21.dp))
        .background(panel, RoundedCornerShape(21.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun SectionHeading(name: String, number: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, fontSize = 11.sp, color = muted)
        }
        Text(number, color = aqua, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppSwitch(label: String, selected: Boolean, onChanged: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            color = if (enabled) Color.White else muted, modifier = Modifier.weight(1f))
        Switch(checked = selected, onCheckedChange = onChanged, enabled = enabled,
            modifier = Modifier.height(29.dp))
    }
}

@Composable
private fun SliderLabel(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White, fontSize = 12.sp)
        Text(value, color = aqua, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CaptionText(value: String) { Text(value, color = muted, fontSize = 11.sp, lineHeight = 16.sp) }

@Composable
private fun FeatureRow(emoji: String, name: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(39.dp).background(Color(0xFF252C42), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp, color = aqua) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(description, color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF263044), RoundedCornerShape(12.dp)).padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = aqua, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = muted, fontSize = 10.sp)
    }
}

private fun durationText(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toLong()
    return if (total >= 3600L) String.format(Locale.US, "%d:%02d:%02d", total / 3600, (total / 60) % 60, total % 60)
    else String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
}
