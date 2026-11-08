# MASTER PROMPT — CINEZEHN AI STUDIO PRO (FLAGSHIP TARGET)

Act as a principal Android/Kotlin engineer, video-engine architect, mobile UX designer, audio DSP engineer, on-device Whisper/LLM engineer, motion-graphics artist, QA lead and release engineer. Build **CineZehn AI Studio PRO** as a production-grade, offline-first Android video and AI creative studio for a Pixel 7 Pro. Use the existing working implementation as an incremental foundation but replace the old long-button/form-style interface. This is a target specification, not an assertion that all features exist yet.

## 1. Required finished product and engineering contract

- Android application with modern CapCut-inspired ORIGINAL CineZehn identity, high-density professional interface, accessible buttons and working features; no fake UI or claims of generated videos without a real generator.
- Kotlin + Jetpack Compose adaptive UI for flagship release; real, non-destructive multitrack timeline domain independent from rendering; Media3/MediaCodec and custom GPU + audio graph for advanced compositing; Room, DataStore and versioned editable project bundle. A WebView prototype is not equivalent to full native flagship acceptance.
- Dark graphite `#111118`, `#181821`, `#1D1D28`; ultraviolet `#A88AFF`/`#8953ED`; white `#F7F5FF`; muted `#A8A5BB`; green status `#60E5AC`; readable full Light theme; System/Dark/Light selector throughout all screens.
- Phone Portrait: preview above transport and time ruler; vertically scrollable multitrack timeline; bottom tools and property sheets. Phone Landscape: media panel LEFT, preview CENTER, properties RIGHT, full multitrack timeline BOTTOM. Tablet and split-screen adaptive. Canvas ratio independent of screen ratio. Ratios 9:16, 16:9, 1:1, 4:5. Correct rotation persistence and accessibility.
- No account, mandatory paid API, cloud service, app watermark or Play Store account for sideloading. Explain any optional provider, network, model/license restrictions. Implement actual source, tests, reproducible CI and downloadable signed installable APK (debug signing acceptable for sideload beta).

## 2. Completely separate workspaces

**A. Video Editor PRO:** full manual video editor from blank/imported media, planner timeline or an explicitly copied Flow sequence. It owns final editing/export of the Planner output. Timeline must permit variable starts and durations, video, stills, PNG overlays, graphics, captions, host, VO, BGM and SFX; non-destructive edits.

**B. AI Auto Planner:** import narration, transcribe, paste ChatGPT master editing prompt, parse exact timestamps, generate shot plan + individual prompts/asset upload forms and create an editable cut. `Open in Manual Editor` is the normal handoff, never flatten. Export must pass through editor review.

**C. Flow Studio / Flow Clip Joiner:** dedicated import of already-rendered Google Flow/Veo clips. Numeric sort, reorder, trim, preview, join, original audio/mute, optional matching color, transitions and independent export. It is NOT the main editor and its project/sequence remains separate until `Open Copy in Editor` is explicitly chosen.

Give independent home navigation to Whisper Transcript, Asset Generator; one-tap access AI Director Hub, Avatar Host, Silence Cutter & Audio Studio, Motion Graphics, Thumbnail Studio, Creative Library, SEO Studio, Batch/Export Queue.

## 3. Narration + Whisper Transcript

- Actual multilingual Whisper local runtime via whisper.cpp + JNI, or a measured approved CPU/WASM runtime. Use real model files: Tiny, Base, optional Small; checksum validation, size/efficiency notes, import/download with progress, resumability and clear storage use. Never mistake an STT placeholder, remote service, Vosk, or demo transcript for offline Whisper.
- Audio/video import; 16 kHz mono inference stream; auto language detect, English, Urdu, Hindi, Arabic and mixed Hinglish/Roman Urdu. Preserve original voice. Cue timestamps, optional per-word timing if actually supported; accurate playback seeking, edit/review text, split/merge, manual timing, uncertain-words glossary; Romanization/translation remain separate from original with time alignment and explicit approximation labels.
- SRT, VTT, TXT, versioned JSON import/export. Copy a timestamped transcript with playback ratio/duration and explicit instructions for ChatGPT to produce VISUAL/MOTION/GRAPHICS/SFX/BGM/TRANSITION timing. Import that returned master brief to the Planner. Add editable, linked caption clips to timeline with source time map and per-word highlight when verified.
- Caption templates: minimum 200 independently renderable templates across downloaded/bundled packs, including kinetic per-word highlighted, typewriter, floating, shaking, pop, bouncy, masked reveal, modern documentary lower thirds, 2-line Hinglish safe-zone layouts, Urdu RTL shaping, outline/shadow, precise font controls. A selector listing future templates without rendering is not sufficient.

## 4. Auto Planner workflow (strict screen order)

Inputs: project name, ratio, duration, original VO, transcript/SRT, optional reference video, chosen style, and large editable **MASTER PROMPT / EDITING BRIEF** input. Natural-language parser accepts 00:01.5, 00:01.500, SRT comma milliseconds, brackets, en dash, arrows, SCENE and subshot headings, `VISUAL:`, `MOTION:`, `GRAPHICS:`, `SFX:`, `BGM:`, `TRANSITION:` and typed versioned JSON. Deterministic timecode parsing first, optional LLM semantic parsing second. Show conflicts, unsupported operations and actual word alignment. Preserve user's original prompt and manual corrections.

Styles: Cinematic Documentary, Fast Retention, Kinetic Explainer, Motion Graphic Explainer, Clean Minimal, Product Commercial, Islamic History, Tech, Gaming, News, Finance, Tutorial, Custom. Each style controls pacing, motion, color, captions, emphasis, audio activity, transitions. User selects style **BEFORE** asset count.

Asset count: `10,20,30,40,50,60,80,100`, plus custom `1–100`. **Exactly N unique primary asset cards** when N is selected; independent shot count is allowed (e.g., 27 cuts and 20 source assets). Map assets to individual/editable shots. An asset generated and uploaded to card #7 stays attached after regeneration when card identity stays the same.

Each numbered card must appear in THIS specific order:

1. Shot/scene timecode, narration anchor, source-usage map and copyable **individual** photoreal image/video/transparent PNG prompt with ratio, framing, lighting, realistic accuracy, safe-zone and continuity.
2. **Directly underneath that prompt:** separate upload box for that generated image/video, with preview, replace/relink, validation of PNG alpha and formats, fit/fill/crop, non-destructive source retention. Offer direct model generation only if a real provider/local generator is present. Otherwise copy prompt -> user generates outside -> import.
3. Under asset upload: **Avatar Host PNG upload**; global default and per-asset override; show/hide for each asset; specify exactly which asset cards show it. Per-host x/y, size, alpha, rotation, zoom, point, swipe, shake, hover, bounce, slide, keyframes, anchor, in/out timing. Lip-sync requires real verified engine and explicit permission; still-host motion must function by itself.
4. Per-asset primary camera motion (zoom 100→108%, 115%, 125% impact where suitable, pan, speed, shake 0.08–0.18s), transition, caption/text animations and font/outline/shadow/highlight/template/position/safe zones.
5. **Three independent EXTRA ASSETS** beneath each primary asset. Each has its own PNG/video prompt, upload, opacity, blend, mask, scale, transform, motion, transition, enter/exit, timeline time range, and tracking/layer order controls. Each primary asset ALSO has **five separately enabled overlay slots**. Each extra asset ALSO has **five overlay slots**. Every slot has its own generation prompt, PNG/logo/pointer/diagram upload, opacity, timing, animation, transition, transform and preview. Disabled slots never appear in render.

After preparing and uploading required media, create typed editable operations in Video Editor PRO. Do not flatten. Show missing source report, preview, and explicit confirmation before allowing placeholder export.

## 5. Manual Editor PRO — acceptance rather than button count

- Real timebase, 30/60fps timeline ruler, scrubbing, seek, frame-step, waveforms, thumbnail strips, selected-clip inspector and proxy preview. Video, overlays, text/captions, avatars, graphics, VO, music and SFX on multiple reorderable tracks, with compositing order and audio routing consistent with export.
- Pointer/touch clip drag, trim handles, split, duplicate, replace, delete, multi-select, lock, mute, hide, track reorder, ripple/overwrite, snapping, zoom, markers, undo/redo, per-item in/out, speed ramps/reverse/freeze when verified, audio/caption linked time remapping.
- Clip properties: x/y/scale/rotation/alpha, crop/reframe, source in/out, keyframes/easings/animation presets, masking, blending, chroma, tracked labels with explicit actual tracking indicator, color/contrast/LUT, opacity/fade, VFX budget. Realtime preview and final encode must use same effect spec.
- Kinetic typography, pointers, arrows, process labels, progress bars/counters/charts, match cuts, whip, zoom, brief shake, motion blur, parallax, slide, dissolve/blur/liquid/mask transitions with actual rendering and capability registry.
- Source media does not get overwritten. Saving a project and rotation must preserve all layers, attachments, timeline decisions and edits. Export H.264/AAC MP4 in 720/1080, 30 FPS, selected bitrate and true ratio with SRT/JSON sidecar; never substitute a WebM file renamed to .mp4. Show real codec and fallback if unsupported; 4K only after verification.

## 6. Audio Studio & voice sync

- Import original VO, optional BGM and multiple SFX; align cue timestamps to spoken words with source->timeline time map.
- Adjustable silence threshold, min pause length, padding, audition and ripple preview; real removal must preserve sync to editable captions/assets.
- Real noise cleanup, EQ, compressor, de-ess, highpass, gain, fades, ducking and peak/loudness meters with capability/performance labels. A simple volume slider must NOT be called noise reduction. Optional enhancement/downstream GPU upscaler require verified models/device support.
- SFX/VO/BGM mix is editable after auto planning. Sound effects applied only to meaningful movement/cuts; no narration masking.

## 7. AI Director, image generators, thumbnails, SEO and libraries

- AI Director workflow: topic/category → hook → accurate script → scenes → timed prompt packs for Flow/Veo → optional VO/captions/SFX/BGM → thumbnail and SEO → Auto Planner. User preset: eight 8-second independent prompts for 64-second Shorts, voiceover fast but understandable; long form with visual continuity. Adapters must disclose API/credits/network limits. Do not invent live YouTube search-volume or ranking information.
- Optional offline lightweight AI and advanced local model; explicit Fast/Ultra/Hybrid modes with real model evidence. Preserve offline deterministic planner even with no AI model.
- Asset Generator: copyable image, transparent PNG, video, motion, B-roll, avatar prompts. Local procedural transparent PNG tools actually draw pointers/charts/highlights/labels and let users attach directly. Providers only if configured and working; keep secrets out of saved projects.
- Thumbnail Studio: landscape 16:9 and vertical 9:16, kinetic typography, background/crop/text, local PNG/JPEG export and clarity preview. Creative Library includes educational, process/factory, documentary, Islamic history, finance, science/what-if, gaming, tech and original presets. 200+ templates must render, not merely have names.
- SEO Studio: titles/descriptions/tags/hashtags/chapters, optional CTA, thumbnails; optional real research if source is connected. Islamic historical presets use respectful non-graphic visuals, no Prophet ﷺ or Ali (RA) faces, historic accuracy review, 7th-century wardrobe/props.
- Batch & Export Queue must persist real jobs and support pause/cancel/retry/resume where genuinely feasible, not use fake progress.

## 8. Chocolate Factory acceptance fixture

User provides a one-minute approved Hindi/Urdu/Hinglish chocolate narration, 9:16, 1080×1920, 30fps. Parse the 9-scene / 27-subshot master prompt and map to 20 individually generated PNG/upload cards. Cocoa pod → white beans → chocolate snap (0–5s), plantation/harvesting (5–11s), fermentation (11–18s), drying (18–24s), roasting/winnowing (24–31s), grinding/mixing (31–39s), refining/conching (39–47s), tempering/molding/cooling (47–55s), packaging/CTA (55–60s). Keep exact timecode VISUAL/MOTION/GRAPHICS/SFX/TRANSITION fields. Assign realistic prompts for EACH primary asset; host, extras and overlays independently timed. Captions max two lines, 2–5 words per chunk in Roman Hinglish, bold white with black outline and selectively highlighted `#FFD735` words, above Shorts UI. Meaningful changes ~1.5–3 s rather than arbitrary shakes. Industrial ambiance, VO ducking and clean rich cocoa/amber color grading. Optional "AI-generated educational visualization; not actual factory footage" disclosure.

## 9. Release gates that must pass before saying COMPLETE

1. Verify portrait and landscape UI on Pixel 7 Pro, with theme propagation, large fonts and rotation, no old cyan-stacked design.
2. Parse 27 timed shots and generate exactly 20 separate prompts and upload boxes. Verify 10/20/30/40/50/custom; no lost uploads on regen.
3. Add alpha host on selected slots, three extras and five overlays on an extra; render their layer ordering, motion and transparency correctly in preview and encoded output.
4. Airplane-mode Whisper inference with installed model, multilingual transcription and SRT; correct transcript, copy ChatGPT brief, roundtrip to Auto Planner and timeline; test language handling.
5. Edit timeline in/out/split/undo/redo, linked VO and captions, real waveforms; export MP4/H.264 with verified 30fps frame dimensions, audio sync and zero unrequested placeholders.
6. Import/join/reorder/export eight Flow videos independently and copy only on explicit editor transfer.
7. Perform thermal/low-memory/storage/permission/interruption tests and build/install/test target APK. Distinguish Passing, Partially implemented, and Not yet implemented; never claim full flagship acceptance on a test-free build.

**Output requested:** full source, build instructions, screenshots, edit/test evidence, GitHub Actions config, APK, README capability matrix, architecture decisions and a clearly prioritized TODO list for unfinished engines. Ship small working vertical slices rather than a giant list of dead buttons.
