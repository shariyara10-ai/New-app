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
        val args = "-hide_banner -nostdin -i ${quote(info.path)} -vn " +
            "-af silencedetect=n=${thresholdDb}dB:d=${Timeline.seconds(minSilenceSeconds)} -f null -"
        val session = FFmpegKit.execute(args)
        val logs = session.allLogsAsString ?: ""
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
        val graph = createGraph(context, info, ranges, options, sourceSrt)
        val graphFile = File(context.cacheDir, "graph_${System.nanoTime()}.txt")
        graphFile.writeText(graph, Charsets.UTF_8)
        try {
            val base = "-hide_banner -y -nostdin -i ${quote(info.path)} " +
                "-/filter_complex ${quote(graphFile.absolutePath)} "
            val cmd = if (outputVideo) {
                val audioMap = if (info.hasAudio) "-map [aout] -c:a aac -b:a 192k " else "-an "
                base + "-map [vout] $audioMap -c:v h264_mediacodec -b:v 5000k " +
                    "-pix_fmt yuv420p -movflags +faststart ${quote(output.absolutePath)}"
            } else {
                base + "-map [aout] -vn -c:a aac -b:a 192k ${quote(output.absolutePath)}"
            }
            val first = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(first.returnCode) && output.exists() && output.length() > 0) return
            // Android devices and FFmpeg distributions vary in available hardware encoders.
            // An MPEG-4 software encode is a slow but compatible fallback for MP4 exports.
            if (outputVideo) {
                output.delete()
                val fallback = cmd.replace("-c:v h264_mediacodec -b:v 5000k", "-c:v mpeg4 -q:v 3 -r 30")
                val second = FFmpegKit.execute(fallback)
                if (ReturnCode.isSuccess(second.returnCode) && output.length() > 0) return
                throw IllegalStateException("Video export failed. ${second.allLogsAsString?.takeLast(1400) ?: "Unknown error"}")
            }
            throw IllegalStateException("Audio export failed. ${first.allLogsAsString?.takeLast(1400) ?: "Unknown error"}")
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

    private fun quote(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
