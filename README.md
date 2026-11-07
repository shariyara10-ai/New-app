# AiVoiceGen · Kotlin / Jetpack Compose Android TTS

**Status: source implementation (v0.1.0); device build and physical voice QA still required.**

Free, original, local-first Android speech narration studio. This project is source code,
not a precompiled APK. No mandatory sign-in, ads, watermarks, commercial APIs or embedded secrets.
It uses the user's installed **Android TextToSpeech engine**, not a bundled ElevenLabs model.
The voice catalog contains only **names, locales and quality metadata reported by that engine**.

## Implemented

- Seven-screen dark-themed Jetpack Compose app: Text to Speech, Voice Library, Voice Studio,
  Pronunciation, Projects, Downloads, Settings.
- Actual runtime Android TTS initialization and discovered voice list, filter by locale,
  declared offline status, engine-rated quality, favorites, listening preview.
- Long script chunking below Android's per-request input limit. Real playback, stop,
  best-effort pause/resume (resume repeats interrupted chunk; some engines behave differently).
- Script editor, live word count, approximate time estimator, 8-second scene estimate,
  10 delivery presets (speed/pitch changes only), manual speed, pitch and WAV gain.
- Local projects, autosaved script draft, permanent pronunciation replacement dictionary,
  before/after comparison.
- Multi-speaker line editor with individually selected engine voices and per-line pause,
  sequential real synthesis, exact PCM format validation and WAV concatenation.
- WAV 16-bit PCM inspection; optional end-silence trim; memory-efficient audio merging,
  actual audio amplitude waveform and MediaPlayer preview.
- Export via Android's system document picker to Downloads/user-selected location,
  share WAV through FileProvider to compatible apps such as CapCut.
- No app INTERNET permission. A selected Android TTS engine may still use its own networking.
- GitHub Actions workflow to compile, run JVM unit tests and publish the debug APK artifact.

## Not implemented (do not present as working)

- Native downloaded ONNX/Piper neural voices and a personal inference server. These need
  a verified inference runtime, licensed per-language models, quantization and real device QA.
- Guaranteed en-PK, ur-PK, ur-IN, male/female voice availability, voice gender/age identification,
  custom emotion/stability/similarity sliders, cloning, SSML support, real phoneme editor,
  native Roman-Urdu transliteration, forced proper code-switching, automatic word timestamps.
- MP3 export: a licensed encoder integration is still required. **WAV works only if the selected
  device TTS engine outputs compatible 16-bit PCM WAV.** Incompatible outputs produce an error.
- Voice model download sizes: Android TextToSpeech doesn't expose per-voice installed size.
- Synthesizer audio feedback on every physical device is not verified in this environment.

## Free GitHub build: install APK without a Play Console account

1. Unzip `AiVoiceGen-source.zip` (the parent folder contains `settings.gradle.kts`, `app`, `.github`).
2. In GitHub (browser on your phone), make a new **private or public** repository named `AiVoiceGen`.
3. Upload the *contents* of the folder to repository root, including **hidden** `.github/workflows/android.yml`.
   GitHub's browser uploader may not preserve whole directory structures reliably on mobile;
   easiest alternative is Android `Termux` or a desktop `git push`. A GitHub-connected coding
   tool can also upload the project files.
4. On GitHub tap **Actions → Build AiVoiceGen APK → Run workflow** (or push to `main`).
5. When all steps are green, open the run → **Artifacts → AiVoiceGen-debug-apk**.
6. Download artifact ZIP, extract `app-debug.apk`, and open it on your Android phone.
7. Enable **Install unknown apps** for the browser/file manager only when prompted.
8. A debug APK is for personal/testing use, not a Play Store-signed release.

Build command if you have Android Studio and Gradle 8.11.1:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

The GitHub workflow explicitly installs Gradle via `gradle/actions/setup-gradle` because
Gradle Wrapper JAR is not bundled (this execution environment could not access Gradle servers).
SDK 35 and JDK 17 are specified. Default package/applicationId is `com.shari.aivoicegen`.

## Getting voices on Android

1. Settings tab → Android TTS Settings (or phone Settings → Accessibility → Text-to-speech output).
2. Select a speech engine, such as Speech Services by Google if present.
3. Find **Install voice data / Languages** in the engine settings and download a supported pack.
4. Restart AiVoiceGen if necessary → Settings → Refresh voices → Voice Library.
5. Choose an actually reported voice and tap **Preview** to verify sound and accent.

Not all systems have Urdu, Pakistani English, Hindi or offline voices. No fabricated
voice names or gender labels are used; absence is shown honestly.

## Narration to CapCut

1. On Text to Speech, paste your script and choose an installed voice.
2. Set Speed / Pitch; tap **Play** to hear a live preview.
3. For Roman Urdu use Pronunciation Lab to add spelling substitutions where helpful.
4. Tap **Export WAV**; wait for segment generation and WAV validation.
5. On Downloads tap **Play** to hear real exported file. Tap **Download** to save a
   user-selected location, or **Share** to open Android's share sheet.
6. Import the WAV in CapCut using CapCut's own audio/file import workflow.

**Privacy:** app does not request INTERNET or broad storage access. Selected OS TTS engines
may require network and independently process narration. A voice's reported "offline-capable"
status is the engine's claim, not an independent guarantee.

**Model / code licenses:** all original source code is MIT licensed (see `LICENSE`).
No open-source neural speech models or restricted model weights are included. Android
system TTS engines and downloaded voice data have their own terms/licenses, and are not
licensed by this repository. Kotlin/AndroidX artifacts retain their respective licenses.

**Tests:** JVM unit tests cover chunking, dictionary replacement, name sanitation, WAV parsing
and joining. They are compiled/run by GitHub Actions, but have not been executed as an Android
app here. See `docs/voice-tests.md` for physical-device voice and accent verification.

## Phone-only workflow (easiest; two uploads/actions)

Instead of uploading all 19 files separately, you can use the separately supplied
`AiVoiceGen-GitHub-build-from-ZIP.yml` workflow:

1. Create a new empty repository on GitHub called `AiVoiceGen`.
2. Upload just `AiVoiceGen-source.zip` to the root of that repo and commit it.
3. GitHub → **Add file → Create new file**, enter the new filename
   `.github/workflows/build.yml` and paste the exact contents of
   `AiVoiceGen-GitHub-build-from-ZIP.yml`. Commit the new file.
4. Actions → **Build AiVoiceGen APK from uploaded ZIP** → Run workflow.
5. Download/extract the `AiVoiceGen-debug-apk` artifact. Its `app-debug.apk` is
   the installation file, if and only if the build succeeds.

This is a build-from-ZIP convenience workflow, not a claim of prior compilation.
