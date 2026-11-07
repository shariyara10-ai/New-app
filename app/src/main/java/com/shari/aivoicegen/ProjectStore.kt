package com.shari.aivoicegen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class NarrationProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val script: String,
    val voiceName: String,
    val speed: Float,
    val pitch: Float,
    val savedAt: Long = System.currentTimeMillis()
)

data class SpeakerLine(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val voiceName: String = "",
    val pauseMs: Int = 300
)

class ProjectStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("aivoicegen", Context.MODE_PRIVATE)
    private val projectFile = File(ctx.filesDir, "projects.json")

    fun listProjects(): List<NarrationProject> = runCatching {
        val array = JSONArray(projectFile.takeIf { it.exists() }?.readText() ?: "[]")
        (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            NarrationProject(
                id = obj.getString("id"), title = obj.getString("title"),
                script = obj.getString("script"), voiceName = obj.optString("voice"),
                speed = obj.optDouble("speed", 1.0).toFloat(),
                pitch = obj.optDouble("pitch", 1.0).toFloat(),
                savedAt = obj.optLong("savedAt")
            )
        }.sortedByDescending { it.savedAt }
    }.getOrDefault(emptyList())

    @Synchronized fun save(project: NarrationProject) {
        val all = listProjects().filterNot { it.id == project.id } + project
        val arr = JSONArray()
        all.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("title", p.title).put("script", p.script)
                .put("voice", p.voiceName).put("speed", p.speed).put("pitch", p.pitch)
                .put("savedAt", p.savedAt))
        }
        val temp = File(ctx.filesDir, "projects.tmp")
        temp.writeText(arr.toString(2))
        if (!temp.renameTo(projectFile)) {
            temp.copyTo(projectFile, overwrite = true)
            temp.delete()
        }
    }

    @Synchronized fun delete(id: String) {
        val all = listProjects().filterNot { it.id == id }
        val arr = JSONArray()
        all.forEach { p -> arr.put(JSONObject().put("id", p.id).put("title", p.title)
            .put("script", p.script).put("voice", p.voiceName).put("speed", p.speed)
            .put("pitch", p.pitch).put("savedAt", p.savedAt)) }
        projectFile.writeText(arr.toString())
    }

    fun draft(): String = prefs.getString("draft", "") ?: ""
    fun saveDraft(value: String) { prefs.edit().putString("draft", value).apply() }
    fun voiceName(): String = prefs.getString("voice", "") ?: ""
    fun saveVoice(name: String) { prefs.edit().putString("voice", name).apply() }
    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    fun toggleFavorite(name: String): Set<String> {
        val result = favorites().toMutableSet()
        if (!result.add(name)) result.remove(name)
        prefs.edit().putStringSet("favorites", result).apply()
        return result
    }
    fun pronunciation(): Map<String, String> = runCatching {
        val json = JSONObject(prefs.getString("dictionary", "{}") ?: "{}")
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())
    fun savePronunciation(map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        prefs.edit().putString("dictionary", obj.toString()).apply()
    }
    fun recentVoices(): List<String> = runCatching {
        val array = JSONArray(prefs.getString("recentVoices", "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    fun recordVoiceUsed(name: String) {
        if (name.isBlank()) return
        val next = (listOf(name) + recentVoices().filter { it != name }).take(20)
        prefs.edit().putString("recentVoices", JSONArray(next).toString()).apply()
    }

    fun speakerDraft(): List<SpeakerLine> = runCatching {
        val arr = JSONArray(prefs.getString("speakerDraft", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SpeakerLine(id = o.optString("id", UUID.randomUUID().toString()),
                content = o.optString("content"), voiceName = o.optString("voice"),
                pauseMs = o.optInt("pause", 300))
        }
    }.getOrDefault(emptyList())

    fun saveSpeakerDraft(lines: List<SpeakerLine>) {
        val array = JSONArray()
        lines.forEach { l -> array.put(JSONObject().put("id", l.id).put("content", l.content)
            .put("voice", l.voiceName).put("pause", l.pauseMs)) }
        prefs.edit().putString("speakerDraft", array.toString()).apply()
    }

    fun audioFiles(): List<File> {
        val dir = File(ctx.filesDir, "audio").apply { mkdirs() }
        return dir.listFiles()?.filter { it.isFile && it.extension.equals("wav", true) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
