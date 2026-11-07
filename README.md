# Shari AutoCut AI Studio Android Client (v0.1)
This is an installable Android client for the earlier local Python-based editor, **not** a standalone on-device AI video rendering app. It displays the existing local editor inside a native Android WebView, integrates Android file selection for importing media, and handles edited video downloads.

## Install APK
Open the latest successful **Build Shari AutoCut Android APK** GitHub Actions run for this branch, download the **Shari-AutoCut-AI-Android-APK** artifact ZIP, extract **app-debug.apk**, and install it on your Android phone. The package is debug-signed, suitable for personal sideloading; not intended for Play Store distribution.

## Run the backend on a trusted Wi-Fi
1. On a Windows/macOS/Linux computer, unzip **Shari_AutoCut_AI_Studio_v0_1.zip**, install Python 3.11+, FFmpeg and the `requirements-ai.txt` packages.
2. From the extracted project folder run `python run.py --host 0.0.0.0`.
3. Find your computer's Wi-Fi/LAN IP address (e.g. 192.168.1.5).
4. Connect your Android phone to the SAME trusted local Wi-Fi.
5. In the Android app enter `http://192.168.1.5:8000` using your computer's actual LAN IP.

The Android client does **not** itself contain the Python/FFmpeg/Whisper/Ollama backend. It cannot generate edits or transcribe audio unless the connected computer server is running. Never expose the server directly to the public Internet. Mixed Urdu/Hindi/Hinglish results should be manually checked.

## Project files
Android Java app with programmatic native UI, WebView and Android DownloadManager; no extra dependencies. GitHub Actions workflow compiles the APK using Gradle and Android SDK.
