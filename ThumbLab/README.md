# ThumbLab — free unlimited thumbnail designer

This folder is **isolated** from the pre-existing Shari TTS project in the same repository.
The existing repository default branch is untouched.

## What this does
- Generate unlimited procedural thumbnail layouts with no paid API.
- 16:9 YouTube, 9:16 Shorts, and 1:1 formats.
- Upload photos, edit titles, change colors, and export HD JPG/PNG.
- Save editable designs locally and export/import project JSON.
- The Android app is a native WebView shell with bundled offline HTML.
- **No photorealistic text-to-image AI model** is included.

## APK from GitHub Actions
1. In GitHub, switch to branch **thumblab-android-build**.
2. Open **Actions → Build ThumbLab Android APK**.
3. After a successful run, download artifact **ThumbLab-Android-debug-APK**.
4. Extract and install **app-debug.apk** on Android (Android 10+).
5. Image exports are saved under **Pictures/ThumbLab**; project JSON under **Downloads/ThumbLab**.

The APK is a debug-signed test build (not a Play Store release). Build success requires GitHub Actions to be enabled and working Gradle/Android SDK downloads.

## Developing
Open **ThumbLab/android/** as an Android Studio project. To update UI, edit **ThumbLab/index.html** then copy it to **ThumbLab/android/app/src/main/assets/index.html** before building.

This application does not use any paid API, server, ads or login.
