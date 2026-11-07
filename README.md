# ShariCut Studio PRO — Android Video & Voiceover Editor

An editable native Android source project focused on **cutting dead air from both video and its synchronized audio**, cleaning narration and authoring Hindi / Urdu / Hinglish / English subtitles. Kotlin + Jetpack Compose + FFmpeg processing. Designed around an Android phone workflow. No ads, sign-in, or watermark inserted by the app.

> **Build status:** Source implementation + automated Android build workflow are included. In this delivery, the APK was **not** built or tested on a real Android device because the creation environment does not have the Android SDK / Gradle dependencies available. Kotlin timeline and subtitles routines compiled and passed local tests, and the generated processing graphs were exercised with desktop FFmpeg on synthetic video/audio, including Urdu/Hinglish captions. Android build/runtime issues may still require fixing in GitHub Actions. This is an ambitious working starter, **not a verified production release**.

## What this version implements

| Feature | Implementation | Notes |
| --- | --- | --- |
| Import MP4 / MOV / MP3 / M4A / WAV | Android Storage Access Framework | Media format/codec support depends on device/FFmpeg |
| Preview video | Android VideoView | Preview of original local file |
| Find silences | FFmpeg `silencedetect` | Adjustable threshold from -55 to -20 dB, pause duration |
| Auto remove silent video **and** audio | FFmpeg trim/atrim + concat | Includes adjustable context padding and synced cuts |
| Display detected cuts and estimated time saved | Kotlin analysis | Manual per-segment overrides not included |
| Remove steady background hiss | `highpass`, `afftdn`, `lowpass` | Non-neural DSP; won't eliminate all noise |
| Voice compression / normalize | `acompressor`, `loudnorm` | Targets spoken narration |
| Video polish | `eq`, `unsharp` | Mild exposure, contrast and sharpness; not AI upscaling |
| Quality options | Original / max 720p / max 1080p | Keeps original aspect ratio |
| MP4 video export | `h264_mediacodec`, MPEG-4 fallback | Encoder availability varies on Android |
| Extract cleaned audio from a video | M4A/AAC export | Optional video-to-audio mode |
| Urdu, Hindi, English and Roman/Hinglish subtitle editor | Unicode SRT import/edit/export | Multiline text supports native scripts |
| Embed captions into video | FFmpeg libass `subtitles` filter | 3 styles; device fonts can affect Urdu glyph rendering |
| Sync existing captions after silence cuts | SRT timeline remapping | Automatic during FFmpeg export |
| Optional AI timed automatic captions | Groq Whisper-Large-v3-turbo API | **Not offline**; requires user API key, internet, external service. Provider usage may have charges. |
| Roman script approximation | Android ICU transliteration | **Not idiomatic Urdu/Hinglish translation**, edit captions afterward |
| Export / share to other apps | MediaStore / ACTION_SEND | Saved under Movies or Music / ShariCut Studio |

## Build an APK using ONLY an Android phone (Termux + GitHub)

You'll need **Termux**, a GitHub account and internet. You do **not** need a laptop, Android Studio, Google Play Console or a paid AI API to cut silence / clean audio / use existing SRT captions.

1. Download `ShariCutStudioPRO-source.zip` to your Android Downloads folder.
2. Open Termux. Run:

```sh
pkg update -y
pkg install -y git gh unzip
termux-setup-storage
cd ~
unzip /storage/emulated/0/Download/ShariCutStudioPRO-source.zip
cd ShariCutStudioPRO
```

3. Sign into GitHub with the device/browser flow:

```sh
gh auth login
```

4. Commit and create **your own private repository**. Replace the email value with the email you use for Git commits:

```sh
git init -b main
git add .
git -c user.name="Shari" -c user.email="YOUR_EMAIL@example.com" commit -m "Initial ShariCut app"
gh repo create ShariCutStudioPRO --private --source=. --remote=origin --push
```

5. In GitHub (Chrome, desktop view if needed), open **ShariCutStudioPRO → Actions → Build Android APK**. A `push` automatically starts the workflow. If needed click **Run workflow**.
6. Open the completed, successful build and download artifact `ShariCutStudioPRO-debug-APK` (a ZIP). Extract `app-debug.apk`, open it and allow your browser/files app to install apps from this source when Android asks. Run ShariCut Studio PRO.
7. If the build fails, open **Actions → failed run → Build / Unit tests** to inspect logs. The project has not yet been Android-built, so fixes might be necessary.

**Alternative for developers:** Open this source folder in Android Studio with JDK17, SDK35 and Gradle8.13, then run `gradle :app:assembleDebug`.

## Quick creator workflow

1. **Editor → Import media**; choose a video or voiceover.
2. Enable **Remove silent sections**, adjust sensitivity and pause length; tap **Scan for silences**. Start around -38 dB and 0.48s, then tune for your recording.
3. Enable **Background hiss reduction** / **Voice boost** as needed; use conservative settings first. For video, optionally enable enhancement. Select audio-only if you want cleaned narration without picture.
4. Tap **Export MP4 video** or **Export M4A audio**. Your original recording is never overwritten.
5. To add **automatic subtitles after silence cuts**, tap **Continue editing this export → captions**. Generate automatic captions (optional online API) or import/edit your own `.srt`, enable **Burn captions**, and export again. Doing it in this order avoids transcript/video timing drift.
6. You can also import SRT *before* the first cut: the subtitle timing is remapped automatically during export.

## Automatic captions: privacy, billing and limitations

The app's default editing functions are processed on the device with FFmpeg. AI transcription is not implemented as an offline speech model in this version. If you choose **Generate timed auto-captions**:

- Enter **your own Groq API key** in the Captions screen. It is held in current app memory; never commit or paste secrets into source control.
- The app extracts a 16 kHz mono MP3 and sends **that audio to Groq over HTTPS** after you explicitly trigger generation. Your audio is handled by that provider under its own policies.
- Uses Whisper `whisper-large-v3-turbo` with segment timestamps. Supports selecting Auto / Urdu / Hindi / English. Automatically generated spelling, timings and language detection are not infallible.
- Free-tier and other provider limits **can change**; there may be billing, quotas and rate limits. The app checks a 25MB payload limit and asks you to split longer audio.
- When **Convert into Roman** is enabled, Android ICU creates a phonetic transliteration. This is an approximation; fix idiomatic Hinglish and Roman Urdu manually in the SRT editor.

For **fully offline captions** in a future release, add a verified on-device speech model (e.g. whisper.cpp or sherpa-onnx), its native bindings and downloadable multilingual model, with realistic model size / memory / licensing expectations.

## Source layout

```text
ShariCutStudioPRO/
├── .github/workflows/android-apk.yml  # GitHub Actions APK builder
├── app/build.gradle.kts                 # Android dependencies
├── app/src/main/AndroidManifest.xml
├── app/src/main/java/com/shari/cutstudio/
│   ├── MainActivity.kt                  # Jetpack Compose UI + phone integration
│   └── core/
│       ├── Timeline.kt                  # Silence detection parsing, kept-time ranges
│       ├── Subtitles.kt                 # Unicode SRT editing and cut-time remapping
│       ├── VideoEngine.kt               # FFmpeg synchronized cutting and media filters
│       └── GroqCaptioner.kt             # Optional, explicit online speech recognition
└── app/src/test/...                     # Timeline & subtitle unit tests
```

## Reproducibility, technical risks and licenses

- **Android compile has not been validated in the delivery environment.** A passing desktop FFmpeg filtergraph test does not guarantee identical Android behavior. Read CI failures and test on the target phone with short non-sensitive clips first.
- Uses the third-party Android `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7` binary fork published on Maven Central. Review its source, releases, provenance, and LGPL license before production deployment. Do not treat a community fork as an official Google/FFmpeg-supported library.
- The app uses FFmpeg filter graphs. Heavy workloads, unorthodox file codecs, frame rate or color profiles, long media, dozens/hundreds of cuts, and fonts not available to libass may affect output quality or stability.
- Video H.264 hardware encoding is attempted first and MPEG-4 software encoding is a compatibility fallback. It may produce larger output and fixed 30 fps. No promise of lossless picture/audio processing is made.
- Subtitle rendering for Urdu/Devanagari depends on the subtitle library and installed system font fallback; inspect output before publishing.
- This project does not include source media files, downloaded third-party libraries, API keys, sign-in credentials, an offline AI speech model, or a prebuilt APK.

## Development smoke tests performed

- Compiled `Timeline.kt` and `Subtitles.kt` with Kotlin CLI, confirming Unicode SRT parsing and correct output timing after cutting.
- Compiled `VideoEngine.kt` against temporary test stubs, generated FFmpeg graphs for 3-segment audio/video cut and audio-only extraction.
- Executed those graphs with local FFmpeg 7.1.5 against synthetic 5s video/audio; produced 4s sample exports including sound enhancement and mixed Urdu/Hinglish SRT burn-in.
- **Still required:** Gradle `testDebugUnitTest`, Android APK assemble, Pixel playback/export/permissions and multilingual end-to-end transcription tests.
