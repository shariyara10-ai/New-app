# CineZehn AI Studio — Standalone Android v0.3.2

**This APK works on an Android phone without a PC, local server, OpenAI API, cloud account, or subscription.**

Native Android app: media files from device storage, video preview, editable time-coded captions, offline speech recognition with a user-installed Vosk model, rule-based motion style planning, animated burned-in captions, optional imported PNG sticker, trim/crop, Media3 Transformer encoding, audio-only narration to captioned video, MP4 saved into Movies/CineZehn.

## Build
GitHub Actions branch: `shari-autocut-standalone`. Download the latest successful run artifact named `CineZehn-AI-Studio-Android-APK` and install `app-debug.apk` after extracting. Target: Android 10+ (API 29+). No Play Store needed.

## First-run offline workflow
1. Open the APK and tap **Choose Video / Audio**; select an MP4, M4A, MP3, etc. supported by Android codecs.
2. For automatic transcription: tap **Open official Vosk models page** (opens your browser) and download either `vosk-model-small-en-us-0.15.zip` (English, ~40 MB) or `vosk-model-small-hi-0.22.zip` (Hindi, ~42 MB). Source: https://alphacephei.com/vosk/models — read each model license before use. For easier setup tap **Download & install English model** or **Download & install Hindi model** within the app. English downloads are SHA-256 checked, other models have ZIP decompression integrity checks. Or import a downloaded original ZIP manually. If an imported file says 'invalid code lengths set', it is a corrupt/incomplete ZIP; delete the download and retry on stable Wi-Fi. Old installed model is retained if new ZIP installation fails. This only needs to be done once; recognition then runs offline.
3. Tap **Transcribe selected audio OFFLINE**. The app uses Android MediaExtractor/MediaCodec to decode the selected recording, converts PCM to mono 16 kHz for Vosk, and receives word-timestamped JSON results. Long recordings may be slow; keep app open.
4. Review and correct the captions. If no model is installed, paste your own script manually; timestamped format is `[00:00.00-00:02.20] Your sentence`. Plain text also works and gets evenly spaced segments.
5. Tap **Plan caption motion** (local keyword rules: mystery, warning, numbers, titles and pop). Pick a style preset. You can generate text prompts for optional image assets; no images are generated or downloaded automatically.
6. Select trim start/end and 9:16, 16:9 or square. Tap **Render and save MP4 to Gallery**. The app uses AndroidX Media3 Transformer to encode MP4 on your device; output appears in `Movies/CineZehn`. Audio-only inputs are paired with a generated background image to produce video.
7. Share video through Android sharing sheet.

## Honest feature boundaries
- Transcription is Vosk, **not Whisper**. You choose the language by choosing a model; language is NOT automatically identified. A small Hindi model is not a multilingual Hinglish/Urdu recognizer. Accuracy depends on accents, recordings and vocabulary.
- **Approx. Romanize** uses Android ICU transliteration and will require human correction for Urdu/Hinglish pronunciation.
- Editing planning is **rule-based rather than generative AI**. It does not fact-check speech, search the web, identify arbitrary video objects, automatically fetch PNGs, generate B-roll, support multi-layer keyframe timelines, or apply After Effects-grade 2.5D parallax.
- Subtitle burn-in uses Media3 CanvasOverlay and native GPU transformations. Export support depends on the media codec/Android version.
- No remote server is contacted by the editing app. The optional Models button opens the external Vosk website in your browser.
- The APK is debug-signed and intended for personal sideloading; device testing still required.
- Large videos, HDR, unusual soundtracks or some device encoders can fail, and no progress/cancel control is implemented for Transformer rendering yet.

## License / dependencies
App sources are provided in this branch. AndroidX Media3 libraries: Apache 2.0; Vosk SDK: Apache 2.0, individual model licenses may differ. See their official project/license pages.

## v0.2.1 repair
- In-app official model download (English US, Hindi, Indian English) with size guard; checksum verification for English/Indian English; extraction CRC validation.
- Corrupt/incomplete ZIP gives a readable recovery message instead of the Java inflater message.
- Transactional model install: an invalid import cannot erase a working installed model.
- Fixed dark-on-dark preset spinner labels and added system-bar insets for modern Pixel phones.
- Internet permission is only needed for optional first-time model downloads; the speech recognition/editor stays local.
