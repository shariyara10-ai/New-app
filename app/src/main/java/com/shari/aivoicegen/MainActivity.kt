package com.shari.aivoicegen

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Base = Color(0xFF090B13)
private val Panel = Color(0xFF151928)
private val Elevated = Color(0xFF20243A)
private val Purple = Color(0xFFAE8CFF)
private val Cyan = Color(0xFF61E7F1)
private val Muted = Color(0xFFA8ACC2)
private val White = Color(0xFFF7F6FF)
private val DarkColors = darkColorScheme(
    primary = Purple, onPrimary = Base, secondary = Cyan, background = Base,
    surface = Panel, onSurface = White, onBackground = White,
    surfaceVariant = Elevated, onSurfaceVariant = Muted
)
private val Tabs = listOf("Text to Speech", "Voice Library", "Voice Studio", "Pronunciation", "Projects", "Downloads", "Settings")
private val Samples = mapOf(
    "en" to "Welcome to AiVoiceGen. Create your next story with clear, professional narration.",
    "ur" to "السلام علیکم دوستو، آج ہم ایک دلچسپ تاریخی واقعہ سنیں گے۔",
    "hi" to "नमस्कार दोस्तों, आज हम एक रोचक कहानी सुनेंगे।"
)
private val Presets = listOf(
    Triple("Natural · Normal", 1f, 1f), Triple("Viral Shorts · Fast", 1.22f, 1.05f),
    Triple("Very Fast Tutorial", 1.45f, 1.0f), Triple("Suspense · Measured", 0.91f, 0.91f),
    Triple("Documentary · Deep", 0.94f, 0.87f), Triple("Islamic History · Dramatic", 1.12f, 0.92f),
    Triple("Education · Clear", 1.02f, 1.05f), Triple("Gaming · Energetic", 1.29f, 1.12f),
    Triple("Motivational · Strong", 1.11f, 1.0f), Triple("Breaking News", 1.18f, 0.98f)
)

class MainActivity : ComponentActivity() {
    private lateinit var manager: TtsManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = TtsManager(this)
        setContent {
            MaterialTheme(colorScheme = DarkColors) {
                AiVoiceGenScreen(manager, ProjectStore(applicationContext))
            }
        }
    }
    override fun onDestroy() {
        manager.close()
        super.onDestroy()
    }
}

@Composable
private fun AiVoiceGenScreen(tts: TtsManager, store: ProjectStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var script by remember { mutableStateOf(store.draft()) }
    var title by remember { mutableStateOf("My narration") }
    var voiceName by remember { mutableStateOf(store.voiceName()) }
    var speed by remember { mutableFloatStateOf(1f) }
    var pitch by remember { mutableFloatStateOf(1f) }
    var gain by remember { mutableFloatStateOf(1f) }
    var pauseMs by remember { mutableFloatStateOf(250f) }
    var trimSilence by remember { mutableStateOf(false) }
    var projectId by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf("") }
    var audioFiles by remember { mutableStateOf(store.audioFiles()) }
    var projects by remember { mutableStateOf(store.listProjects()) }
    var dictionary by remember { mutableStateOf(store.pronunciation()) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    val lines = remember { mutableStateListOf<SpeakerLine>().apply { addAll(store.speakerDraft()) } }
    var history by remember { mutableStateOf(store.recentVoices()) }
    val voices by tts.voices.collectAsState()
    val ready by tts.ready.collectAsState()
    val state by tts.state.collectAsState()
    val playing by tts.isPlaying.collectAsState()
    val paused by tts.paused.collectAsState()
    val exporting by tts.isExporting.collectAsState()
    val progress by tts.progress.collectAsState()
    var requestedFile by remember { mutableStateOf<File?>(null) }

    val saveToDownloads = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        val file = requestedFile
        requestedFile = null
        if (uri != null && file != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                            ?: error("Can't write to selected destination")
                    }
                }.onSuccess { toast = "Saved ${file.name} to selected location." }
                 .onFailure { toast = "Save failed: ${it.message}" }
            }
        }
    }

    LaunchedEffect(voices) {
        if (voices.isNotEmpty() && voices.none { it.name == voiceName }) {
            voiceName = voices.firstOrNull { !it.isNetworkConnectionRequired }?.name ?: voices.first().name
        }
    }
    LaunchedEffect(voiceName) { if (voiceName.isNotBlank()) store.saveVoice(voiceName) }
    LaunchedEffect(script) { delay(700); withContext(Dispatchers.IO) { store.saveDraft(script) } }
    val speakerDraftSnapshot = lines.toList()
    LaunchedEffect(speakerDraftSnapshot) {
        delay(700)
        withContext(Dispatchers.IO) { store.saveSpeakerDraft(speakerDraftSnapshot) }
    }
    LaunchedEffect(toast) { if (toast.isNotBlank()) { delay(7000); toast = "" } }

    fun startExport(parts: List<SpeakerLine>, fileTitle: String) {
        parts.forEach { store.recordVoiceUsed(it.voiceName) }
        history = store.recentVoices()
        scope.launch {
            runCatching {
                tts.export(parts, speed, pitch, gain, trimSilence, fileTitle)
            }.onSuccess {
                audioFiles = store.audioFiles()
                tab = 5
                toast = "WAV created and checked: ${it.name}"
            }.onFailure { toast = "Export failed: ${it.message}" }
        }
    }
    fun saveProject() {
        if (script.isBlank()) { toast = "Please enter a script before saving."; return }
        val p = NarrationProject(id = projectId ?: java.util.UUID.randomUUID().toString(),
            title = title.ifBlank { "Untitled" }, script = script, voiceName = voiceName, speed = speed, pitch = pitch)
        store.save(p)
        projectId = p.id
        projects = store.listProjects()
        toast = "Project saved locally."
    }
    fun previewSample(voice: Voice) {
        store.recordVoiceUsed(voice.name)
        history = store.recentVoices()
        val sample = Samples[voice.locale.language] ?: Samples["en"]!!
        tts.play(sample, voice.name, speed, pitch)
    }

    Column(Modifier.fillMaxSize().background(Base).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(listOf(Color(0xFF191529), Base, Color(0xFF0D2030)))
            ).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(42.dp).background(Purple, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text("A", color = Base, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text("AiVoiceGen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("FREE · LOCAL-FIRST VOICE STUDIO", color = Cyan, style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.size(9.dp).background(if (ready) Cyan else Color(0xFFFFBB5E), CircleShape))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Tabs.forEachIndexed { index, label ->
                FilterChip(
                    selected = tab == index,
                    onClick = { tab = index },
                    label = { Text(label, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Purple, selectedLabelColor = Base)
                )
            }
        }
        HorizontalDivider(color = Color(0xFF292B40))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> StudioPage(
                    title, { title = it }, script, { script = it }, voices, voiceName, { voiceName = it },
                    speed, { speed = it }, pitch, { pitch = it }, gain, { gain = it },
                    pauseMs, { pauseMs = it }, trimSilence, { trimSilence = it },
                    playing, paused, exporting, progress,
                    onPlay = { store.recordVoiceUsed(voiceName); history = store.recentVoices(); tts.play(TextTools.corrected(script, dictionary), voiceName, speed, pitch) },
                    onPause = { tts.pauseOrResume() }, onStop = { tts.stop() }, onSave = ::saveProject,
                    onExport = { startExport(listOf(SpeakerLine(content = TextTools.corrected(script, dictionary), voiceName = voiceName, pauseMs = 0)), title) }
                )
                1 -> VoiceLibraryPage(voices, voiceName, { voiceName = it }, favorites, history,
                    onFavorite = { favorites = store.toggleFavorite(it) }, onPreview = ::previewSample)
                2 -> VoiceStudioPage(lines, voices, voiceName, pauseMs.toInt(),
                    onPreview = { line -> store.recordVoiceUsed(line.voiceName); history = store.recentVoices(); tts.play(TextTools.corrected(line.content, dictionary), line.voiceName, speed, pitch) },
                    onExport = { startExport(lines.map { it.copy(content = TextTools.corrected(it.content, dictionary)) }, "${title}_multi") },
                    exporting = exporting)
                3 -> PronunciationPage(dictionary, onPut = { from, to ->
                    dictionary = dictionary + (from to to); store.savePronunciation(dictionary)
                }, onRemove = { key ->
                    dictionary = dictionary - key; store.savePronunciation(dictionary)
                }, onTest = { test -> tts.play(test, voiceName, speed, pitch) },
                    currentVoiceAvailable = voiceName.isNotBlank())
                4 -> ProjectsPage(projects, onLoad = { p ->
                    projectId = p.id; title = p.title; script = p.script; voiceName = p.voiceName
                    speed = p.speed; pitch = p.pitch; tab = 0; toast = "Loaded ${p.title}"
                }, onDelete = { p ->
                    store.delete(p.id); projects = store.listProjects()
                    if (projectId == p.id) projectId = null
                })
                5 -> DownloadsPage(audioFiles,
                    onShare = { file ->
                        runCatching {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"; putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share audio to CapCut or another app"))
                        }.onFailure { toast = "Share failed: ${it.message}" }
                    },
                    onSave = { requestedFile = it; saveToDownloads.launch(it.name) },
                    onDelete = { it.delete(); audioFiles = store.audioFiles() },
                    onRefresh = { audioFiles = store.audioFiles() })
                else -> SettingsPage(tts, voices, onRefresh = { tts.refreshVoices() }, onSystemSettings = {
                    val intent = Intent("com.android.settings.TTS_SETTINGS")
                    runCatching { context.startActivity(intent) }.onFailure {
                        context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    }
                })
            }
        }
        Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 18.dp, vertical = 9.dp)) {
            if (exporting || playing) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Cyan)
            Text(state, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
            if (toast.isNotBlank()) Text(toast, color = Cyan, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun PageTitle(text: String, subtitle: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().background(Panel, RoundedCornerShape(19.dp)).border(1.dp, Color(0xFF30344C), RoundedCornerShape(19.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
}

@Composable private fun VoiceSelector(
    voices: List<Voice>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = voices.firstOrNull { it.name == selected }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
            enabled = voices.isNotEmpty(), shape = RoundedCornerShape(12.dp)) {
            Text(if (label == null) "Choose an installed voice" else "${label.name} · ${label.locale.toLanguageTag()}",
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp)); Text("▾", color = Cyan)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            voices.forEach { v ->
                DropdownMenuItem(
                    text = { Column { Text(v.name, maxLines = 1); Text("${v.locale.toLanguageTag()} · ${if (v.isNetworkConnectionRequired) "Network" else "Offline-capable*"}",
                        style = MaterialTheme.typography.labelSmall, color = Muted) } },
                    onClick = { expanded = false; onSelect(v.name) }
                )
            }
        }
    }
}

@Composable private fun ValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
                                    display: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(display, color = Cyan)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable private fun StudioPage(
    title: String, onTitle: (String) -> Unit,
    script: String, onScript: (String) -> Unit,
    voices: List<Voice>, selectedVoice: String, onVoice: (String) -> Unit,
    speed: Float, onSpeed: (Float) -> Unit, pitch: Float, onPitch: (Float) -> Unit,
    gain: Float, onGain: (Float) -> Unit, pauseMs: Float, onPauseMs: (Float) -> Unit,
    trim: Boolean, onTrim: (Boolean) -> Unit,
    playing: Boolean, paused: Boolean, exporting: Boolean, progress: Float,
    onPlay: () -> Unit, onPause: () -> Unit, onStop: () -> Unit,
    onSave: () -> Unit, onExport: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        PageTitle("Text to Speech", "Produce real narration using voices installed on your device.")
        SurfaceCard {
            OutlinedTextField(value = title, onValueChange = onTitle, label = { Text("Project / audio name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = script, onValueChange = onScript, label = { Text("Write or paste your narration") },
                placeholder = { Text("Assalam-o-Alaikum dosto! Aaj hum…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp), minLines = 8)
            val seconds = TextTools.estimatedSeconds(script, speed)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${TextTools.wordCount(script)} words", color = Muted)
                Text("~${seconds / 60}m ${seconds % 60}s estimated", color = Cyan)
            }
            if (script.isNotBlank()) {
                Text("8-second scene: ${if (seconds <= 8) "Likely within estimate" else "Estimated ${seconds - 8}s over"} · approx ${TextTools.wordCount(script)} words", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        SurfaceCard {
            Text("VOICE & DELIVERY", color = Cyan, fontWeight = FontWeight.Bold)
            VoiceSelector(voices, selectedVoice, onVoice)
            Text("Delivery presets change speed/pitch only. They do not create model emotions.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Presets.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (name, s, p) ->
                            OutlinedButton(onClick = { onSpeed(s); onPitch(p) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp)) {
                                Text(name, maxLines = 2, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            ValueSlider("Speed", speed, 0.5f..2f, "${"%.2f".format(Locale.US, speed)}×", onSpeed)
            ValueSlider("Pitch", pitch, 0.5f..2f, "${"%.2f".format(Locale.US, pitch)}×", onPitch)
            ValueSlider("WAV gain", gain, 0.5f..1.8f, "${"%.2f".format(Locale.US, gain)}×", onGain)
            ValueSlider("Paragraph pause (for Voice Studio)", pauseMs, 0f..2000f, "${pauseMs.toInt()}ms", onPauseMs)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = trim, onCheckedChange = onTrim)
                Text("Trim digital silence at ends of generated clips", style = MaterialTheme.typography.bodySmall)
            }
        }
        SurfaceCard {
            Text("AUDIO CONTROLS", color = Cyan, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPlay, enabled = !exporting && script.isNotBlank() && selectedVoice.isNotBlank(), modifier = Modifier.weight(1f)) { Text("▶ Play") }
                OutlinedButton(onClick = onPause, enabled = playing, modifier = Modifier.weight(1f)) { Text(if (paused) "▶ Resume" else "Ⅱ Pause") }
                OutlinedButton(onClick = onStop, enabled = playing, modifier = Modifier.weight(1f)) { Text("■ Stop") }
            }
            if (playing) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save project") }
                Button(onClick = onExport, enabled = !exporting && script.isNotBlank() && selectedVoice.isNotBlank(), modifier = Modifier.weight(1f)) { Text(if (exporting) "Working…" else "Export WAV") }
            }
            Text("The exported audio is validated as PCM WAV. MP3 is not included in this version.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun quality(voice: Voice): String = when (voice.quality) {
    Voice.QUALITY_VERY_HIGH -> "Very high (engine-rated)"
    Voice.QUALITY_HIGH -> "High (engine-rated)"
    Voice.QUALITY_NORMAL -> "Normal (engine-rated)"
    Voice.QUALITY_LOW -> "Low (engine-rated)"
    Voice.QUALITY_VERY_LOW -> "Very low (engine-rated)"
    else -> "Not specified"
}

@Composable private fun VoiceLibraryPage(voices: List<Voice>, selected: String, onSelect: (String) -> Unit,
                                         favorites: Set<String>, recent: List<String>,
                                         onFavorite: (String) -> Unit, onPreview: (Voice) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Favorites", "Recent", "Offline", "English", "Urdu", "Hindi", "Pakistan", "India")
    val results = voices.filter { v ->
        (query.isBlank() || v.name.contains(query, true) || v.locale.toLanguageTag().contains(query, true)) &&
            when (filter) {
                "Favorites" -> v.name in favorites
                "Recent" -> v.name in recent
                "Offline" -> !v.isNetworkConnectionRequired
                "English" -> v.locale.language == "en"
                "Urdu" -> v.locale.language == "ur"
                "Hindi" -> v.locale.language == "hi"
                "Pakistan" -> v.locale.country == "PK"
                "India" -> v.locale.country == "IN"
                else -> true
            }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle("Real Voice Library", "Only voices reported by your selected Android TTS engine appear here.")
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search voice / locale") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { f -> FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }) }
        }
        Text("${results.size} / ${voices.size} voices · Gender and age unknown unless engine supplies reliable metadata.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(results, key = { it.name }) { v ->
                SurfaceCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, fontWeight = FontWeight.SemiBold)
                            Text(TextTools.localeLabel(v.locale.toLanguageTag()), color = Cyan, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (v.name == selected) "SELECTED" else "", color = Cyan, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${quality(v)} · ${if (v.isNetworkConnectionRequired) "Requires network" else "Offline-capable (engine claim)"} · Data size not exposed by Android API", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onFavorite(v.name) }) { Text(if (v.name in favorites) "★ Saved" else "☆ Favorite") }
                        OutlinedButton(onClick = { onPreview(v) }) { Text("▶ Preview") }
                        Button(onClick = { onSelect(v.name) }) { Text("Use voice") }
                    }
                }
            }
            if (results.isEmpty()) item {
                Text("No matching installed voices. In Android Settings > Text-to-speech, install compatible engine/language data; then refresh in Settings.", color = Muted)
            }
        }
    }
}

@Composable private fun VoiceStudioPage(
    lines: SnapshotStateList<SpeakerLine>, voices: List<Voice>, defaultVoice: String, defaultPauseMs: Int,
    onPreview: (SpeakerLine) -> Unit, onExport: () -> Unit, exporting: Boolean
) {
    val allWords = lines.sumOf { TextTools.wordCount(it.content) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        PageTitle("Multi-Speaker Studio", "Assign real installed voices to dialogue lines; export a combined WAV.")
        SurfaceCard {
            Text("${lines.size} speaker lines · $allWords words", color = Cyan)
            Text("Different voices can generate mismatched audio formats. If that happens, export with voices using the same sample rate and channels.",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { lines.add(SpeakerLine(voiceName = defaultVoice, pauseMs = defaultPauseMs)) }, enabled = voices.isNotEmpty()) {
                Text("+ Add dialogue line")
            }
        }
        lines.forEachIndexed { index, line ->
            SurfaceCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("LINE ${index + 1}", color = Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { lines.removeAt(index) }) { Text("Remove") }
                }
                OutlinedTextField(value = line.content, onValueChange = { lines[index] = line.copy(content = it) },
                    label = { Text("Speaker ${index + 1} text") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                VoiceSelector(voices, line.voiceName, onSelect = { lines[index] = line.copy(voiceName = it) })
                ValueSlider("Pause after line", line.pauseMs.toFloat(), 0f..2000f, "${line.pauseMs} ms") {
                    lines[index] = line.copy(pauseMs = it.toInt())
                }
                OutlinedButton(onClick = { onPreview(line) }, enabled = line.content.isNotBlank() && line.voiceName.isNotBlank() && !exporting) {
                    Text("▶ Preview this speaker")
                }
            }
        }
        if (lines.isNotEmpty()) {
            Button(onClick = onExport, enabled = !exporting && lines.any { it.content.isNotBlank() } &&
                lines.filter { it.content.isNotBlank() }.all { it.voiceName.isNotBlank() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (exporting) "Synthesizing…" else "Export all lines to WAV")
            }
        }
    }
}

@Composable private fun PronunciationPage(dictionary: Map<String, String>, onPut: (String, String) -> Unit,
                                           onRemove: (String) -> Unit, onTest: (String) -> Unit,
                                           currentVoiceAvailable: Boolean) {
    var original by remember { mutableStateOf("") }
    var spokenAs by remember { mutableStateOf("") }
    var test by remember { mutableStateOf("Assalam-o-Alaikum dosto! Aaj hum jaanenge ek aisa tareekhi waqia jo aapko hairaan kar dega!") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageTitle("Pronunciation Lab", "Custom dictionary corrections are applied before playback and WAV export.")
        SurfaceCard {
            Text("ADD A CORRECTION", fontWeight = FontWeight.Bold, color = Cyan)
            OutlinedTextField(value = original, onValueChange = { original = it },
                label = { Text("Original word / phrase") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = spokenAs, onValueChange = { spokenAs = it },
                label = { Text("Replacement spelling for TTS") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onPut(original.trim(), spokenAs.trim()); original = ""; spokenAs = "" },
                enabled = original.isNotBlank() && spokenAs.isNotBlank()) {
                Text("Save pronunciation rule")
            }
            Text("Rules replace complete words/phrases, not phonemes. Roman Urdu and Hinglish are not automatically transliterated; quality depends on your installed voice.",
                style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        SurfaceCard {
            Text("COMPARE YOUR CORRECTIONS", fontWeight = FontWeight.Bold, color = Cyan)
            OutlinedTextField(value = test, onValueChange = { test = it }, label = { Text("Test sentence") }, minLines = 3,
                modifier = Modifier.fillMaxWidth())
            Text("After correction:", fontWeight = FontWeight.Bold)
            Text(TextTools.corrected(test, dictionary), color = Cyan)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTest(TextTools.corrected(test, emptyMap())) }, enabled = test.isNotBlank() && currentVoiceAvailable) { Text("▶ Original") }
                Button(onClick = { onTest(TextTools.corrected(test, dictionary)) }, enabled = test.isNotBlank() && currentVoiceAvailable) { Text("▶ Corrected") }
            }
        }
        SurfaceCard {
            Text("YOUR SAVED DICTIONARY · ${dictionary.size}", color = Cyan, fontWeight = FontWeight.Bold)
            if (dictionary.isEmpty()) Text("No custom pronunciations added yet.", color = Muted)
            dictionary.toSortedMap().forEach { (from, to) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(from, fontWeight = FontWeight.Bold)
                        Text("→ $to", color = Muted)
                    }
                    TextButton(onClick = { onRemove(from) }) { Text("Delete") }
                }
                HorizontalDivider(color = Elevated)
            }
        }
    }
}

@Composable private fun ProjectsPage(projects: List<NarrationProject>, onLoad: (NarrationProject) -> Unit,
                                      onDelete: (NarrationProject) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("Saved Projects", "Your narration scripts and settings stay on this phone.")
        Text("${projects.size} saved locally", color = Cyan)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(projects, key = { it.id }) { project ->
                SurfaceCard {
                    Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(project.script.take(200), maxLines = 3, overflow = TextOverflow.Ellipsis, color = Muted)
                    Text("${TextTools.wordCount(project.script)} words · ${dateLabel(project.savedAt)}", style = MaterialTheme.typography.labelSmall, color = Cyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onLoad(project) }) { Text("Open & edit") }
                        OutlinedButton(onClick = { onDelete(project) }) { Text("Delete") }
                    }
                }
            }
            if (projects.isEmpty()) item { Text("Projects you save from Text to Speech will appear here.", color = Muted) }
        }
    }
}

@Composable private fun DownloadsPage(files: List<File>, onShare: (File) -> Unit,
                                       onSave: (File) -> Unit, onDelete: (File) -> Unit, onRefresh: () -> Unit) {
    val player = remember { MediaPlayer() }
    var playingPath by remember { mutableStateOf<String?>(null) }
    var audioError by remember { mutableStateOf("") }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle("Audio Exports", "Your real generated PCM WAV files, ready to save or share.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${files.size} WAV files", color = Cyan, modifier = Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(files, key = { it.absolutePath }) { file ->
                SurfaceCard {
                    Text(file.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = remember(file.path) { runCatching { WavTools.inspect(file) }.getOrNull() }
                    Text("${file.length() / 1024} KB · ${details?.durationMs?.div(1000) ?: "Unknown"} seconds · ${details?.sampleRate ?: "?"} Hz · ${dateLabel(file.lastModified())}",
                        color = Muted, style = MaterialTheme.typography.labelSmall)
                    WaveformBars(file)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = {
                            runCatching {
                                if (playingPath == file.absolutePath) {
                                    player.stop(); player.reset(); playingPath = null
                                } else {
                                    player.reset(); player.setDataSource(file.absolutePath)
                                    player.setOnCompletionListener { playingPath = null }
                                    player.prepare(); player.start(); playingPath = file.absolutePath
                                }
                                audioError = ""
                            }.onFailure { audioError = "Playback failed: ${it.message}" }
                        }) { Text(if (playingPath == file.absolutePath) "■ Stop" else "▶ Play") }
                        Button(onClick = { onSave(file) }) { Text("Download") }
                        OutlinedButton(onClick = { onShare(file) }) { Text("Share") }
                    }
                    TextButton(onClick = {
                        if (playingPath == file.absolutePath) { player.stop(); player.reset(); playingPath = null }
                        onDelete(file)
                    }) { Text("Delete audio") }
                }
            }
            if (files.isEmpty()) item { Text("Generate a WAV in Text to Speech or Voice Studio first.", color = Muted) }
        }
        if (audioError.isNotBlank()) Text(audioError, color = Color(0xFFFFB4AB))
    }
}

@Composable private fun WaveformBars(file: File) {
    val samples = remember(file.absolutePath) { runCatching { WavTools.waveform(file) }.getOrDefault(emptyList()) }
    if (samples.isEmpty()) return
    Canvas(Modifier.fillMaxWidth().height(46.dp)) {
        val gap = size.width / samples.size
        val center = size.height / 2f
        samples.forEachIndexed { i, amplitude ->
            val height = (amplitude * size.height * 0.85f).coerceAtLeast(2f)
            drawLine(color = if (i % 4 == 0) Purple else Cyan,
                start = Offset(i * gap + gap / 2f, center - height / 2f),
                end = Offset(i * gap + gap / 2f, center + height / 2f),
                strokeWidth = (gap * 0.48f).coerceAtLeast(1f), cap = StrokeCap.Round)
        }
    }
}

@Composable private fun SettingsPage(tts: TtsManager, voices: List<Voice>, onRefresh: () -> Unit,
                                      onSystemSettings: () -> Unit) {
    val offline = voices.count { !it.isNetworkConnectionRequired }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PageTitle("Engine & Privacy", "No sign-in, subscriptions, tracking SDKs, or built-in paid APIs.")
        SurfaceCard {
            Text("ACTIVE ANDROID TTS ENGINES", color = Cyan, fontWeight = FontWeight.Bold)
            val names = tts.engines()
            if (names.isEmpty()) Text("No Android TTS engines reported.", color = Muted)
            names.forEach { Text("• $it") }
            Text("${voices.size} voices reported · $offline marked offline-capable by engine", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh) { Text("Refresh voices") }
                OutlinedButton(onClick = onSystemSettings) { Text("Android TTS Settings") }
            }
        }
        SurfaceCard {
            Text("INSTALL / DOWNLOAD VOICE DATA", color = Cyan, fontWeight = FontWeight.Bold)
            Text("1. Open Android TTS Settings.\n2. Select your installed speech engine.\n3. Open its language / voice data menu.\n4. Download a supported language pack if offered.\n5. Return and tap Refresh voices.")
            Text("Urdu (ur-PK), Pakistani English (en-PK), female/male voice availability and offline operation are device-engine-specific. Android does not guarantee these models.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        SurfaceCard {
            Text("IMPLEMENTATION STATUS", color = Cyan, fontWeight = FontWeight.Bold)
            Text("✓ Android TTS playback · actual discovered voices\n✓ Chunked WAV synthesis · multi-speaker WAV merging\n✓ Script autosave, projects, dictionary, favorites\n✓ Filesystem exports + Android share sheet\n— MP3 encoder not included\n— External neural ONNX model download/inference not included\n— Emotion / stability / voice clone controls not available\n— Advanced transliteration and phoneme editor not included")
        }
        SurfaceCard {
            Text("VOICE PRIVACY", color = Cyan, fontWeight = FontWeight.Bold)
            Text("The app sends text to the Android TTS engine you select. Offline-capable voices typically process locally; network-marked voices may send text to the engine provider. AiVoiceGen itself has no INTERNET permission.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Version 0.1.0 · Android 8.0+ · App-level model licenses: no third-party speech models bundled.", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun dateLabel(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
