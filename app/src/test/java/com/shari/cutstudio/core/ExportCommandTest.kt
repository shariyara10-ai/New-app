package com.shari.cutstudio.core

import org.junit.Assert.*
import org.junit.Test

class ExportCommandTest {
    @Test fun properScriptFlagAndSpacesInPaths() {
        val args = ExportCommand.arguments(
            "/cache/my video.mp4", "/cache/my graph.txt", "/cache/final video.mp4",
            hasVideo = true, hasAudio = true
        ).toList()
        assertEquals("/cache/my video.mp4", args[args.indexOf("-i") + 1])
        assertEquals("/cache/my graph.txt", args[args.indexOf("-filter_complex_script") + 1])
        assertTrue(args.containsAll(listOf("[vout]", "[aout]", "-c:v", "mpeg4", "-c:a")))
        assertFalse(args.any { it.startsWith("-/") })
    }

    @Test fun audioOnlyMapsNoVideo() {
        val args = ExportCommand.arguments(
            "/in.mp3", "/graph.txt", "/out.m4a", false, true
        ).toList()
        assertTrue(args.containsAll(listOf("-map", "[aout]", "-vn", "aac")))
        assertFalse(args.contains("[vout]"))
        assertFalse(args.contains("-c:v"))
    }

    @Test fun muteVideoDoesNotMapAudio() {
        val args = ExportCommand.arguments(
            "/in.mp4", "/graph.txt", "/out.mp4", true, false
        ).toList()
        assertTrue(args.contains("[vout]"))
        assertTrue(args.contains("-an"))
        assertFalse(args.contains("[aout]"))
    }
}
