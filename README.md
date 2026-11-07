# Shari TTS Studio — Android

Free, API-key-free Android text-to-speech creator app, for Urdu, Hindi, Roman Urdu and English. Native Android TTS via the selected system speech engine. No account, tracking, paid subscription, or server API. Offline playback/export works **only if the selected installed voice is offline-capable**.

## Features
- Locale presets: Urdu Pakistan (ur-PK), Urdu India (ur-IN), Hindi India (hi-IN), English India (en-IN), English Pakistan (en-PK), English US (en-US), Roman Urdu (hi-IN fallback).
- Shows installed voice names, accents/locales, and offline vs network requirements. Can filter to offline voices.
- Preview/stop, speed and pitch controls, YouTube-style energetic/fast presets, script count and approximate duration.
- Long-script support via 2,400-character chunks, playback queue and WAV export in `Downloads/ShariTTS` using Android TTS `onAudioAvailable` PCM callbacks.
- Optional spelling/pronunciation replacements: one `original => replacement` per line.
- Sample Urdu, Hindi, Hinglish, and English scripts; local persistence.

## Build APK for FREE using GitHub Actions (no computer)
1. Upload all files to your GitHub repo (include `.github/workflows/android.yml`).
2. Open **Actions → Build Shari TTS Android APK**. The push should start a build; or tap **Run workflow**.
3. Open the successful workflow run, download `ShariTTS-Android-APK` artifact ZIP, extract `app-debug.apk`, then install it on your Android phone.
4. Open the app, choose language, voice, and create audio. For offline voices, download voice data in Android Text-to-speech settings if necessary.

Alternatively open this project in Android Studio and build `assembleDebug` (AGP 8.7.3 / Gradle 8.9 / SDK 35, Java 17).

## Limitations and troubleshooting
- **100% Indian/Pakistani accent accuracy is not possible to guarantee.** Voice availability and pronunciation depend on the Android TTS engine and installed language packs. `ur-PK` may not be available. Select the nearest language and use Urdu script or spelling replacements.
- If voice list is empty, uncheck offline-only, install language data, or change your Android speech engine.
- The user may use the app free; installing/download of voice packs may use data. Some Android engines offer online voices and might transmit text. This app itself requests no Internet permission, but an independently installed TTS engine may have its own permissions and privacy terms.
- WAV export requires the selected TTS engine to provide audio buffers via Android `UtteranceProgressListener.onAudioAvailable`. Engines that don't provide samples will show a clear error.
- Android 10+ (API 29+). Debug APK is signed with standard debug key, not intended for Play Store publishing.
- No Urdu/Hindi neural model bundled, and no voice cloning. Roman Urdu quality varies considerably; Unicode Urdu/Hindi scripts are recommended.

## Privacy
Script, preferences and pronunciation rules remain locally on your device. No app-owned network API, ads, analytics or tracking.
