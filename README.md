# CineZehn AI Studio — v0.6.0 Pro Storyboard Editor

**This APK works on an Android phone without a PC, local server, OpenAI API, cloud account, or subscription.**

Native Android app: media files from device storage, video preview, editable time-coded captions, offline speech recognition with a user-installed Vosk model, rule-based motion style planning, animated burned-in captions, optional imported PNG sticker, trim/crop, Media3 Transformer encoding, audio-only narration to captioned video, MP4 saved into Movies/CineZehn.

## v0.6.0 AI Storyboard Planner + independent manual Pro Editor
- **Separate full-featured-by-scope Pro Editor and Flow Clip Merger**. Flow joins Google Flow/Veo video clips independently; the Pro Editor opens scene-based media projects from the Auto Planner.
- Paste an externally generated ChatGPT *timestamped* master edit prompt; deterministic parser reads VISUAL, MOTION, GRAPHICS, SFX and shot times.
- Select 5–100 assets. Every visual has its own generated **text prompt**, editable upload slot and motion style; up to **five additional PNG overlay slots** per shot each with prompt, file chooser, effect and position/size; per-shot optional avatar-host PNG and motion effect. PNG/image creation itself is NOT included offline as a neural generator.
- Open Manual Pro Editor after planning. Preview imported scene images/overlays, split/reorder/duplicate/delete scenes, modify captions, timing, aspect, host and overlay positions/motions, undo one change checkpoint.
- Actual on-device Media3 video compositing draws these layers and effects onto rendered output, mixed with user-imported narration audio; MP4 export saves to Gallery.
- **Honest current limitations:** This beta does not run a local GPT-style reasoning model or a built-in Whisper engine, nor does it perform trained semantic AI scene detection, phoneme-level avatar lip sync, AI image/video generation or automatic stock fetching. Vosk from the legacy editor remains separately available. Editor is scene-layer oriented, not yet a full multi-video NLE.
- Test the app on device, especially rendering, rotated layouts, codec support, live previews and output audio sync.

## v0.5.0 modern studio + new offline tools
- New modern Android **Home Dashboard**, 5-screen navigation (Home, Studios, Offline Director, Creative Library, Settings), persistent dark/light dashboard preference, offline-readiness status and context-aware editing briefs.
- Existing **Flow/Veo scene editor** redesigned with a card layout, media clip thumbnails, reusable editing styles and functional clip trimming, scene-specific timed captions, Vosk offline transcription, SRT exports, presets, crop/fit shape choices, 15-second test render, optional animated ending, project restore and MP4 Gallery export.
- Original **procedural 2D explainer host**, driven by dialogue text keywords to gesture and animate; **not** realistic neural avatar, gesture IK, or genuine viseme lip-sync.
- Original procedural **comic reaction overlays** when scene narration includes comedy/surprise words. These are not imported movie memes.
- New native **Silence Cutter Studio**: Android MediaExtractor/MediaCodec PCM decode, energy-based audio activity segmentation, adjustable sensitivity, optional word padding, non-destructive cuts and MP4/M4A export. Energy is **not** trained speech VAD. Music/noise may cause errors. Test before publishing.
- Single-video and narration studio restyled with white rounded cards, modern typography and controls.
- Offline Director and library offer real keyword/rule-based editing plans and style selection; **no GPT-style LLM, image/video generation or research engine is installed.**
- Android namespace stays \`com.shari.autocut\` (existing Java packages); independent installation ID is \`com.cinezehn.aistudio\`. Install over v0.4.1+; older \`com.shari.autocut.offline\` is a distinct app.
- Caution: GitHub CI compilation is not device acceptance testing. Mixed codecs/HDR, silence cuts, avatar overlays and long videos must still be tested on supported Android hardware.

## v0.4.2 speech model download recovery
- Automatic retry from the public Hugging Face LocalStack model mirror when the official Vosk model host fails DNS/connection.
- Known SHA-256 checks maintained for English and Indian English model ZIPs; archives validated and safety-limited during import.
- Explicit browser shortcut to model mirror and import-ZIP fallback.
- Play/Pause button on the main video preview to avoid confusion over a blank unplayed VideoView.
- This fixes error handling and alternative download routes, not root-cause Internet DNS access; on-device tests remain necessary.

## v0.4.1 independent installation / launcher recovery
- Android `applicationId` changed from `com.shari.autocut.offline` to `com.cinezehn.aistudio`, so the new CineZehn APK installs alongside the older AutoCut package rather than updating it.
- Dedicated visible vector launcher icon is provided; the app retains MAIN/LAUNCHER intent-filter.
- Important: The old package's private projects, imported models, and settings are **not** automatically migrated to the new application ID. The old APK need not be uninstalled. Videos already exported to Movies/CineZehn stay in shared storage.

## New in v0.4.0 — Flow/Veo Creator Studio
- Multi-select videos, reorder, preview, remove and **trim** clips without modifying the originals.
- Burn in **editable per-scene timed captions**, or transcribe a specific clip offline using a separately downloaded Vosk model; export an aligned **SRT** file.
- Choose 12 category styling presets and a local, deterministic editing-recipe director. **Not a local ChatGPT/LLM**.
- Animated procedural scene graphics and captions, toggled independently, rendered with Media3 composition-wide effects.
- Output framing for **9:16 Shorts**, **16:9**, **1:1**, or original frame; choose safe fit or center-crop.
- Save and restore the most recent project on device, retaining user-approved source URI access.
- Technical scene inspection (metadata, source audio, brightness sampling) and optional animated outro.
- 15-second export check, cancel render, MP4 save into Movies/CineZehn, share last export.
- Honest boundaries: This beta DOES NOT implement an offline reasoning model, AI-generated stock footage, semantic object recognition, RNNoise, lip-synced 3D avatars, automatic meme clip discovery, or full timeline editing. Scene analysis is technical, not semantic. Device-specific export behavior still requires testing.

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
