package com.shari.cutstudio.core

/**
 * Builds stable FFmpeg argv arrays (not shell command strings).
 * The filter graph is loaded from a temporary script to avoid quoting bugs.
 */
internal object ExportCommand {
    fun arguments(
        inputPath: String,
        filterScriptPath: String,
        outputPath: String,
        hasVideo: Boolean,
        hasAudio: Boolean
    ): Array<String> {
        require(inputPath.isNotBlank() && filterScriptPath.isNotBlank() && outputPath.isNotBlank())
        require(hasVideo || hasAudio) { "No media stream is available for export." }
        val args = mutableListOf(
            "-hide_banner", "-y", "-nostdin",
            "-i", inputPath,
            "-filter_complex_script", filterScriptPath
        )
        if (hasVideo) args += listOf("-map", "[vout]")
        if (hasAudio) args += listOf("-map", "[aout]")
        if (hasVideo) {
            // MPEG4 Part 2 is implemented in FFmpeg itself; it does not rely on
            // device-specific Android hardware encoder initialization.
            args += listOf(
                "-c:v", "mpeg4", "-q:v", "3", "-r", "30",
                "-pix_fmt", "yuv420p", "-movflags", "+faststart"
            )
        }
        if (hasAudio) args += listOf("-c:a", "aac", "-b:a", "192k")
        else args += "-an"
        if (!hasVideo) args += "-vn"
        args += outputPath
        return args.toTypedArray()
    }
}
