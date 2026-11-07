# Manual voice/device verification (not automatically executed)

AiVoiceGen detects voices from `TextToSpeech.getVoices()`; availability and pronunciation
quality cannot be proven by JVM unit tests. Run these tests on a physical device using
voice data actually installed on the device. Tick only after listening to exported audio.

| Test | Text | What to check |
|---|---|---|
| US English | "The factory transforms raw steel into seamless pipes." | Natural US pronunciation; device offers en-US |
| Indian English | "Welcome to the latest science and history documentary." | Distinct Indian English when en-IN exists |
| Pakistan English | "Assalam-o-Alaikum, welcome to this historical documentary." | en-PK exists at all; don't substitute en-US silently |
| Urdu Pakistan | "السلام علیکم دوستو، آج ہم ایک تاریخی واقعہ سنیں گے۔" | Installed ur-PK, script and stress |
| Urdu India | "السلام علیکم، یہ ایک دلچسپ کہانی ہے۔" | ur-IN existence and intelligibility |
| Hindi India | "नमस्कार दोस्तों, आज हम एक रोचक कहानी सुनेंगे।" | hi-IN works |
| Roman Urdu | "Assalam-o-Alaikum dosto! Aaj hum jaanenge ek aisa tareekhi waqia jo aapko hairaan kar dega!" | Pronunciation of Assalam-o-Alaikum, tareekhi, waqia, hairaan |
| Hinglish | "Hello dosto, aaj hum quantum physics ko simple Hindi mein samjhenge." | Code switching; no forced English-only claim |

## Workflow

1. Install app on a real phone; in Android TTS settings download available voice data.
2. Open Voice Library; verify voice name and locale are genuinely provided by engine.
3. Preview each available language; listen for errors or a network requirement.
4. Use Pronunciation Lab to compare original and corrected spelling in Roman Urdu.
5. Generate a 30-second WAV and replay it in Downloads; check speech isn't silent.
6. Save with Android's file picker and open in CapCut or media player.
7. Export 3 speaker segments with 300-ms pauses; check order/timing.
8. Test offline by enabling airplane mode with an engine-reported offline voice.
9. Paste a 5-minute script; confirm the UI remains responsive and WAV can be played.
10. Verify pause/resume behavior with the selected Android TTS engine.

**Not claimed tested:** no Android emulator, physical device, signed release APK, or neural-model
compatibility testing was available in the source-authoring environment.
