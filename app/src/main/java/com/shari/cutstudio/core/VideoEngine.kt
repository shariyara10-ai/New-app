package com.shari.cutstudio.core

import android.content.Context
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File


data class MediaInfo(val path: String, val length: Double, val hasVideo: Boolean, val hasAudio: Boolean)

data class ExportOptions(
    val removeSilence: Boolean = true,
    val padSeconds: Double = 0.12,
    val denoise: Boolean = true,
    val voiceBoost: Boolean = true,
    val enhanceVideo: Boolean = false,
    val resolution: Int = 0, // 0 = original, 1280 or 1920 = max longest side
    val burnCaptions: Boolean = false,
    val captionStyle: String = "Modern",
    val audioOnly: Boolean = false,
)

object VideoEngine {
    fun probe(path: String): MediaInfo {
        val meta = MediaMetadataRetriever()
        return try {
            meta.setDataSource(path)
            val millis = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val video = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            val audio = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            MediaInfo(path, millis / 1000.0, video, audio)
        } finally { meta.release() }
    }

    fun detect(info: MediaInfo, thresholdDb: Int, minSilenceSeconds: Double): List<TimeRange> {
        require(info.hasAudio) { "No audio track found: speech-based silence detection is unavailable." }
        // Passing an argument array avoids FFmpegKit command-line re-parsing of file paths.
        val args = arrayOf(
            "-hide_banner", "-nostdin", "-i", info.path, "-vn",
            "-af", "silencedetect=n=${thresholdDb}dB:d=${Timeline.seconds(minSilenceSeconds)}",
            "-f", "null", "-"
        )
        val session = FFmpegKit.executeWithArguments(args)
        val logs = session.allLogsAsString.orEmpty()
        if (!ReturnCode.isSuccess(session.returnCode)) {
            throw IllegalStateException("Silence scan failed: ${logs.takeLast(1100)}")
        }
        return Timeline.detectSilences(logs, info.length)
    }

    fun export(
        context: Context,
        info: MediaInfo,
        ranges: List<TimeRange>,
        options: ExportOptions,
        sourceSrt: String,
        output: File
    ) {
        require(ranges.isNotEmpty()) { "No audible segment remains. Lower the threshold or turn off silence removal." }
        val outputVideo = info.hasVideo && !options.audioOnly
        require(outputVideo || info.hasAudio) { "Selected file has no audio to export." }
        // A single massive split graph can exhaust native FFmpeg memory on mobile.
        require(ranges.size <= 120) {
            "Too many speech segments (${ranges.size}) for a single-pass export. " +
                "Try a shorter input or export it in sections."
        }
        require(ranges.all { it.start >= 0 && it.end <= info.length + 0.1 && it.end > it.start }) {
            "Invalid cut positions: rescan the imported media."
        }
        val graph = createGraph(context, info, ranges, options, sourceSrt)
        val graphFile = File.createTempFile("sharicut_graph_", ".txt", context.cacheDir)
        try {
            graphFile.writeText(graph, Charsets.UTF_8)
            output.parentFile?.mkdirs()
            // Software MPEG4 export avoids device-specific MediaCodec encoder crashes.
            // It is slower but more portable across Android chips.
            val args = ExportCommand.arguments(
                info.path, graphFile.absolutePath, output.absolutePath, outputVideo, info.hasAudio
            )
            val session = FFmpegKit.executeWithArguments(args)
            if (ReturnCode.isSuccess(session.returnCode) && output.isFile && output.length() > 0L) return
            output.delete()
            val logs = session.allLogsAsString.orEmpty().takeLast(2000)
            throw IllegalStateException(
                "Export engine failed (code ${session.returnCode}). ${logs.ifBlank { "No FFmpeg diagnostic was returned." }}"
            )
        } finally {
            graphFile.delete()
        }
    }

    internal fun createGraph(
        context: Context,
        info: MediaInfo,
        ranges: List<TimeRange>,
        options: ExportOptions,
        sourceSrt: String
    ): String {
        val outputVideo = info.hasVideo && !options.audioOnly
        val n = ranges.size
        val parts = mutableListOf<String>()
        if (n > 1) {
            if (outputVideo) {
                parts += "[0:v]split=$n${(0 until n).joinToString("") { "[vs$it]" }}"
            }
            if (info.hasAudio) {
                parts += "[0:a]asplit=$n${(0 until n).joinToString("") { "[as$it]" }}"
            }
        }
        ranges.forEachIndexed { i, item ->
            val start = Timeline.seconds(item.start)
            val end = Timeline.seconds(item.end)
            if (outputVideo) {
                val vIn = if (n == 1) "0:v" else "vs$i"
                parts += "[$vIn]trim=start=$start:end=$end,setpts=PTS-STARTPTS[v$i]"
            }
            if (info.hasAudio) {
                val aIn = if (n == 1) "0:a" else "as$i"
                parts += "[$aIn]atrim=start=$start:end=$end,asetpts=PTS-STARTPTS[a$i]"
            }
        }
        if (n > 1) {
            val inputs = (0 until n).joinToString("") { i ->
                (if (outputVideo) "[v$i]" else "") + (if (info.hasAudio) "[a$i]" else "")
            }
            val outs = (if (outputVideo) "[joinedv]" else "") +
                (if (info.hasAudio) "[joineda]" else "")
            parts += "${inputs}concat=n=$n:v=${if (outputVideo) 1 else 0}:a=${if (info.hasAudio) 1 else 0}$outs"
        }
        if (outputVideo) {
            val inLabel = if (n == 1) "v0" else "joinedv"
            val videoFilters = mutableListOf<String>()
            if (options.resolution > 0) {
                val cap = options.resolution
                videoFilters += "scale=w='if(gte(iw,ih),min(iw,$cap),-2)':h='if(gte(iw,ih),-2,min(ih,$cap))'"
            }
            if (options.enhanceVideo) {
                videoFilters += "eq=contrast=1.045:saturation=1.09"
                videoFilters += "unsharp=5:5:0.42"
            }
            if (options.burnCaptions && sourceSrt.isNotBlank()) {
                val items = Subtitles.parse(sourceSrt)
                if (items.isNotEmpty()) {
                    val remapped = Subtitles.remap(items, ranges)
                    val srtFile = File(context.cacheDir, "render_captions.srt")
                    srtFile.writeText(Subtitles.asSrt(remapped), Charsets.UTF_8)
                    val style = when (options.captionStyle) {
                        "Cinematic" -> "FontName=Noto Sans,FontSize=20,Bold=1,Outline=3,Shadow=2,Alignment=2,MarginV=92"
                        "Minimal" -> "FontName=Noto Sans,FontSize=18,Bold=0,Outline=1,Shadow=0,Alignment=2,MarginV=65"
                        else -> "FontName=Noto Sans,FontSize=23,Bold=1,Outline=2,Shadow=1,Alignment=2,MarginV=75"
                    }
                    videoFilters += "subtitles='${srtFile.absolutePath}':force_style='$style'"
                }
            }
            if (videoFilters.isEmpty()) videoFilters += "null"
            parts += "[$inLabel]${videoFilters.joinToString(",")}[vout]"
        }
        if (info.hasAudio) {
            val inLabel = if (n == 1) "a0" else "joineda"
            val audioFilters = mutableListOf<String>()
            if (options.denoise) {
                audioFilters += "highpass=f=80"
                audioFilters += "afftdn=nr=10:nf=-27"
                audioFilters += "lowpass=f=14000"
            }
            if (options.voiceBoost) {
                audioFilters += "acompressor=threshold=-20dB:ratio=2.2:attack=12:release=150"
                audioFilters += "loudnorm=I=-16:TP=-1.5:LRA=11"
            }
            if (audioFilters.isEmpty()) audioFilters += "anull"
            parts += "[$inLabel]${audioFilters.joinToString(",")}[aout]"
        }
        return parts.joinToString(";\n")
    }


}
