# CineZehn AI Studio PRO · 0.9.0 (Android/Web beta)

This is a **working beta foundation**, not the final flagship implementation. Source, Android WebView app packaging, local project model, prompt parsing, asset cards, waveform-looking placeholder UI (not a measured waveform), video/image canvas compositing, local import/export, and unit tests are present. It replaces the old form-style app with the graphite/violet responsive studio UI.

## Three separate workflows

- **AI Auto Planner**: Paste ChatGPT's SCENE/timestamp/VISUAL/MOTION/GRAPHICS/SFX brief. Choose editing style **first**, then 10, 20, 30, 40, 50, 60, 80, 100 or custom asset count. Exactly N primary prompt/upload cards are produced; each card also has a host PNG + on/off, motion/caption style, 3 extra assets, 5 primary overlays and 5 per-extra overlays. Loaded Chocolate project has **27 editable subshots and 20 unique primary slots**. Explicit handoff creates a real JSON multitrack cut in Manual Editor.
- **Video Editor PRO**: Multi-track timeline, local imported image/video/audio, selectable text, source videos/images/avatars, caption clip timeline, motion presets (selected effects renderer), x/y/rotation/scale/opacity, split, duplicate, remove, drag, resize/trim handles, undo/redo, aspect ratio and preview. Export is a realtime canvas capture + MediaRecorder, which outputs **MP4 only when supported** by the browser/device, otherwise WebM. Android interface saves to `Downloads/CineZehn`. No misleading rename of WebM to MP4.
- **Flow Studio**: Its own ordered already-rendered clip list, numeric sort, manual reorder, preview, independent export. Copy to Editor is only via explicit action; separate Flow project stays intact.

## Other features

- **Whisper Transcript**: Experimental real Transformers.js Whisper Tiny local WASM inference, not fabricated output. First-time download and working internet needed to fetch model/runtime. On-device speed/memory/Android WebView compatibility have NOT been device verified. Editable cue list, SRT import/export, VTT, copy accurate timestamped text to ChatGPT, add caption clips to Editor. Automatic Roman transliteration/word-accurate Whisper timestamps are not yet shipped.
- **Asset Studio**: Real local transparent PNG graphics (arrows, lower thirds, labels, rings), prompt copy/import workflow, thumbnail PNG generator.
- **AI Director**: Offline deterministic 8x8-second/long-form **draft** generation (not LLM research). Separate styles, categories and prompt output.
- **Dark/Light themes** and responsive portrait/landscape browser UI. Local media is stored using IndexedDB; project edit decisions in localStorage; exported JSON does not embed media blobs.

## What is NOT finished

Jetpack Compose native editor, native Media3 GPU compositing, native offline whisper.cpp/arm64 Tiny/Base, exact word-level sync, advanced motion-tracking/masks/liquid transitions, accurate audio waveforms, noise reduction/enhancement, 200 independently-renderable graphics presets, 4K upscale, facial/lip-sync host, advanced offline LLM, reusable persisted batch export queue, crossfade render and validation of Pixel 7 Pro APK install/export. These are in `docs/MASTER_PROMPT.md` and **must not be claimed as working**. Some UI property selectors, such as transition choice, are editable metadata that currently map only to available renderer behavior.

## Build Android APK with GitHub Actions (phone-only)

1. Repository branch: `cinezehn-pro-rebuild` (keep prior TTS project default branch unchanged).
2. Go to **GitHub → Actions → Build CineZehn PRO Android APK → Run workflow**. Push to that branch also triggers CI.
3. Download artifact `CineZehn-AI-Studio-PRO-APK`, unzip it, and install `app-debug.apk` on Android. If Android reports a signature conflict, install this as a separate package `com.cinezehn.pro` (or back up data before replacing).
4. Import media through file picker. To test layering, create/attach transparent PNGs, open Manual Editor, preview and check which export MIME is supported on the device. First Whisper transcription needs a model download.

GitHub CI uses Gradle `8.10.2`, Android Gradle Plugin `8.7.3`, JDK 17, SDK 35. Compiling a local Android APK requires the corresponding Android SDK and Gradle; not included in ZIP. Web version can be previewed on a local server via `python -m http.server 8787 --directory web` (or `npm run preview`). Opening raw HTML by `file://` is unreliable due JavaScript module origin restrictions.

## Automated test

```bash
node --test tests/*.test.mjs
```

All 6 deterministic tests for parser, SRT, 27 shots, 20 assets, persistence and separated workflows pass. Web browser rendering and Android installation/export still require real device verification.

## Important privacy / license / accuracy

The editor loads imported media and procedural graphics locally; by default it never sends user media to external image/video providers. Clicking Whisper Tiny loads the external JS runtime/weights before inference; clearly disclosed in the UI. Exported video is an illustration if your source media is generated, not actual factory footage. Copyright, historical accuracy and content-specific disclosures remain the creator's responsibility.
