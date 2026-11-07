package com.shari.ttsstudio;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 16, 30);
    private static final int PANEL = Color.rgb(19, 29, 47);
    private static final int MUTED = Color.rgb(154, 170, 192);
    private static final int WHITE = Color.rgb(242, 246, 252);
    private static final int AQUA = Color.rgb(80, 227, 198);
    private static final String[] LOCALE_LABELS = {
            "Urdu — Pakistan", "Urdu — India", "Hindi — India",
            "English — India", "English — Pakistan", "English — USA",
            "Roman Urdu — try Hindi voice"
    };
    private static final String[] LOCALE_TAGS = {
            "ur-PK", "ur-IN", "hi-IN", "en-IN", "en-PK", "en-US", "hi-IN"
    };
    private static final String URDU_SAMPLE = "خیبر کی وادی میں ایک حیرت انگیز واقعہ پیش آیا۔ قلعے کے سامنے سب کی نظریں ایک اہم فیصلے پر جمی ہوئی تھیں۔";
    private static final String HINDI_SAMPLE = "यह कहानी है एक ऐसे फैसले की, जिसने सबको हैरान कर दिया। अगले आठ सेकंड में जानिए असल बात।";
    private static final String HINGLISH_SAMPLE = "Socho, ek aisa raaz jo sab se chhupaya gaya! Lekin asli twist ab shuru hota hai.";
    private static final String ENGLISH_SAMPLE = "What if the most important clue was hidden in plain sight? Here is the twist nobody expected.";

    private TextToSpeech tts;
    private boolean ready = false;
    private Spinner localeSpinner;
    private Spinner voiceSpinner;
    private EditText scriptBox;
    private EditText fixesBox;
    private CheckBox offlineCheck;
    private SeekBar speedBar;
    private SeekBar pitchBar;
    private TextView speedView;
    private TextView pitchView;
    private TextView countView;
    private TextView voiceDetail;
    private TextView statusView;
    private final List<Voice> matchedVoices = new ArrayList<>();
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Object audioLock = new Object();
    private volatile int session = 0;
    private volatile String job = "idle";
    private ArrayList<String> segments = new ArrayList<>();
    private int segmentIndex = 0;
    private File rawFile;
    private File partFile;
    private FileOutputStream rawStream;
    private int sampleRate = 0;
    private int audioEncoding = 0;
    private int channelCount = 0;
    private long audioBytes = 0;
    private boolean incompatibleFormat = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("shari_tts_prefs", MODE_PRIVATE);
        makeUi();
        setStatus("Loading Android TTS engine…");
        tts = new TextToSpeech(this, code -> ui.post(() -> {
            ready = code == TextToSpeech.SUCCESS;
            if (ready) {
                tts.setOnUtteranceProgressListener(new PlaybackListener());
                refreshVoices();
                setStatus("Ready. Choose an installed voice and start speaking.");
            } else {
                setStatus("Speech engine failed to load. Open Android speech settings below.");
            }
        }));
    }

    private void makeUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(22), dp(18), dp(36));
        scroll.addView(body);
        setContentView(scroll);

        TextView eyebrow = text("FREE  •  PRIVATE  •  NO API KEY", 11, AQUA, true);
        body.addView(eyebrow);
        TextView headline = text("SHARI TTS", 31, WHITE, true);
        headline.setPadding(0, dp(7), 0, dp(2));
        body.addView(headline);
        body.addView(text("Voice Studio  ·  Pakistan & India", 15, MUTED, false));
        body.addView(gap(20));

        LinearLayout scriptCard = card(body);
        scriptCard.addView(text("01  YOUR SCRIPT", 12, AQUA, true));
        scriptCard.addView(gap(10));
        scriptBox = new EditText(this);
        scriptBox.setTextColor(WHITE);
        scriptBox.setHintTextColor(MUTED);
        scriptBox.setTextSize(16);
        scriptBox.setGravity(Gravity.TOP | Gravity.START);
        scriptBox.setMinLines(7);
        scriptBox.setMaxLines(14);
        scriptBox.setPadding(dp(14), dp(13), dp(14), dp(13));
        scriptBox.setBackground(rounded(Color.rgb(11, 20, 36), 13, Color.rgb(43, 62, 82)));
        scriptBox.setHint("Urdu, Hindi, English ya Hinglish script yahan paste karo…");
        scriptBox.setText(prefs.getString("script", URDU_SAMPLE));
        scriptCard.addView(scriptBox, new LinearLayout.LayoutParams(-1, -2));
        scriptCard.addView(gap(10));
        countView = text("", 12, MUTED, false);
        scriptCard.addView(countView);
        scriptCard.addView(gap(11));
        scriptCard.addView(text("Try sample text", 12, MUTED, false));
        HorizontalScrollView sampleScroll = new HorizontalScrollView(this);
        sampleScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout samples = new LinearLayout(this);
        samples.setOrientation(LinearLayout.HORIZONTAL);
        addSmallButton(samples, "Urdu", () -> {scriptBox.setText(URDU_SAMPLE); localeSpinner.setSelection(0);});
        addSmallButton(samples, "Hindi", () -> {scriptBox.setText(HINDI_SAMPLE); localeSpinner.setSelection(2);});
        addSmallButton(samples, "Hinglish", () -> {scriptBox.setText(HINGLISH_SAMPLE); localeSpinner.setSelection(6);});
        addSmallButton(samples, "English", () -> {scriptBox.setText(ENGLISH_SAMPLE); localeSpinner.setSelection(3);});
        sampleScroll.addView(samples);
        scriptCard.addView(sampleScroll);
        scriptBox.addTextChangedListener(watch(() -> {
            prefs.edit().putString("script", scriptBox.getText().toString()).apply();
            updateCount();
        }));
        updateCount();
        body.addView(gap(16));

        LinearLayout voiceCard = card(body);
        voiceCard.addView(text("02  ACCENT & VOICE", 12, AQUA, true));
        voiceCard.addView(gap(12));
        voiceCard.addView(text("Region / language", 13, MUTED, false));
        localeSpinner = new Spinner(this);
        localeSpinner.setAdapter(spinnerAdapter(Arrays.asList(LOCALE_LABELS)));
        voiceCard.addView(localeSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        voiceCard.addView(gap(6));
        offlineCheck = new CheckBox(this);
        offlineCheck.setText("Only installed offline voices");
        offlineCheck.setTextColor(WHITE);
        offlineCheck.setButtonTintList(android.content.res.ColorStateList.valueOf(AQUA));
        offlineCheck.setChecked(prefs.getBoolean("offline", true));
        voiceCard.addView(offlineCheck);
        voiceCard.addView(gap(6));
        voiceCard.addView(text("Available voice", 13, MUTED, false));
        voiceSpinner = new Spinner(this);
        voiceCard.addView(voiceSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        voiceDetail = text("Your device controls which accents are available.", 12, MUTED, false);
        voiceDetail.setPadding(0, dp(5), 0, 0);
        voiceCard.addView(voiceDetail);
        voiceCard.addView(gap(12));
        Button settingsButton = actionButton("Download voices / TTS settings", false);
        settingsButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
            } catch (Exception ignored) {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            }
        });
        voiceCard.addView(settingsButton);
        localeSpinner.setOnItemSelectedListener(new SimpleItemSelected(() -> {
            prefs.edit().putInt("locale", localeSpinner.getSelectedItemPosition()).apply();
            refreshVoices();
        }));
        offlineCheck.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean("offline", checked).apply();
            refreshVoices();
        });
        voiceSpinner.setOnItemSelectedListener(new SimpleItemSelected(this::onVoiceSelected));
        localeSpinner.setSelection(Math.max(0, Math.min(LOCALE_LABELS.length - 1, prefs.getInt("locale", 0))));
        body.addView(gap(16));

        LinearLayout tuneCard = card(body);
        tuneCard.addView(text("03  DELIVERY", 12, AQUA, true));
        tuneCard.addView(gap(14));
        speedView = text("Speed", 14, WHITE, true);
        tuneCard.addView(speedView);
        speedBar = new SeekBar(this);
        speedBar.setMax(120);
        speedBar.setProgress(prefs.getInt("speed", 35)); // 0.70 + 35 / 100 = 1.05x
        tint(speedBar);
        tuneCard.addView(speedBar);
        pitchView = text("Pitch", 14, WHITE, true);
        tuneCard.addView(pitchView);
        pitchBar = new SeekBar(this);
        pitchBar.setMax(80);
        pitchBar.setProgress(prefs.getInt("pitch", 40)); // 0.70 + 40 / 100 = 1.10x
        tint(pitchBar);
        tuneCard.addView(pitchBar);
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        addSmallButton(presets, "Natural", () -> {speedBar.setProgress(30); pitchBar.setProgress(30);});
        addSmallButton(presets, "Fast", () -> {speedBar.setProgress(55); pitchBar.setProgress(25);});
        addSmallButton(presets, "Very fast", () -> {speedBar.setProgress(78); pitchBar.setProgress(23);});
        tuneCard.addView(presets);
        tuneCard.addView(gap(10));
        tuneCard.addView(text("Faster speech can reduce pronunciation clarity.", 12, MUTED, false));
        speedBar.setOnSeekBarChangeListener(progressListener(value -> {
            prefs.edit().putInt("speed", value).apply();
            updateControls();
        }));
        pitchBar.setOnSeekBarChangeListener(progressListener(value -> {
            prefs.edit().putInt("pitch", value).apply();
            updateControls();
        }));
        updateControls();
        body.addView(gap(16));

        LinearLayout dictCard = card(body);
        dictCard.addView(text("04  PRONUNCIATION FIXES  (OPTIONAL)", 12, AQUA, true));
        dictCard.addView(gap(8));
        dictCard.addView(text("Each line: wrong => better spelling. Replacements affect speech, not your original script.", 12, MUTED, false));
        dictCard.addView(gap(10));
        fixesBox = new EditText(this);
        fixesBox.setTextSize(14);
        fixesBox.setTextColor(WHITE);
        fixesBox.setHintTextColor(MUTED);
        fixesBox.setHint("Khyber => Khaybar\nMola Ali => Maula Ali");
        fixesBox.setGravity(Gravity.TOP | Gravity.START);
        fixesBox.setMinLines(2);
        fixesBox.setMaxLines(6);
        fixesBox.setPadding(dp(13), dp(11), dp(13), dp(11));
        fixesBox.setBackground(rounded(Color.rgb(11, 20, 36), 12, Color.rgb(43, 62, 82)));
        fixesBox.setText(prefs.getString("fixes", ""));
        fixesBox.addTextChangedListener(watch(() -> prefs.edit().putString("fixes", fixesBox.getText().toString()).apply()));
        dictCard.addView(fixesBox);
        body.addView(gap(18));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        Button speak = actionButton("▶  Play voice", true);
        Button stop = actionButton("■  Stop", false);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(54), 2f);
        left.setMargins(0, 0, dp(8), 0);
        buttonRow.addView(speak, left);
        buttonRow.addView(stop, new LinearLayout.LayoutParams(0, dp(54), 1f));
        body.addView(buttonRow);
        body.addView(gap(9));
        Button save = actionButton("↓  Export full script as WAV", true);
        body.addView(save, new LinearLayout.LayoutParams(-1, dp(54)));
        speak.setOnClickListener(v -> beginSpeak());
        stop.setOnClickListener(v -> stopCurrent(true));
        save.setOnClickListener(v -> beginExport());
        body.addView(gap(12));
        statusView = text("", 13, MUTED, false);
        statusView.setPadding(dp(11), dp(10), dp(11), dp(10));
        statusView.setBackground(rounded(PANEL, 10, Color.rgb(37, 57, 77)));
        body.addView(statusView);
        body.addView(gap(18));
        body.addView(text("No subscription • No ads • No account • No app-owned Internet access", 12, MUTED, false));
        TextView caveat = text("Accent quality depends on your installed Android TTS voice. 100% accuracy isn't guaranteed. WAV export requires audio buffers from the engine.", 12, MUTED, false);
        caveat.setPadding(0, dp(9), 0, 0);
        body.addView(caveat);
    }

    private void refreshVoices() {
        if (!ready || voiceSpinner == null || localeSpinner == null) return;
        matchedVoices.clear();
        int i = localeSpinner.getSelectedItemPosition();
        if (i < 0) i = 0;
        Locale target = Locale.forLanguageTag(LOCALE_TAGS[i]);
        Set<Voice> engineVoices = tts.getVoices();
        if (engineVoices != null) {
            for (Voice v : engineVoices) {
                Locale own = v.getLocale();
                if (own == null || !own.getLanguage().equals(target.getLanguage())) continue;
                if (offlineCheck.isChecked() && v.isNetworkConnectionRequired()) continue;
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                matchedVoices.add(v);
            }
        }
        Collections.sort(matchedVoices, (a, b) -> {
            int ar = rankVoice(a, target), br = rankVoice(b, target);
            if (ar != br) return Integer.compare(ar, br);
            return a.getName().compareToIgnoreCase(b.getName());
        });
        ArrayList<String> labels = new ArrayList<>();
        for (Voice v : matchedVoices) {
            labels.add(v.getName() + "   [" + v.getLocale().toLanguageTag() + "]" +
                    (v.isNetworkConnectionRequired() ? "  • Online" : "  • Offline"));
        }
        if (labels.isEmpty()) labels.add("No matching installed voices");
        voiceSpinner.setAdapter(spinnerAdapter(labels));
        String saved = prefs.getString("voice_" + i, "");
        int best = 0;
        for (int k = 0; k < matchedVoices.size(); k++) {
            if (matchedVoices.get(k).getName().equals(saved)) {best = k; break;}
        }
        voiceSpinner.setSelection(best);
        onVoiceSelected();
        if (matchedVoices.isEmpty()) setStatus("No " + LOCALE_LABELS[i] + " voice. Try voice settings or switch off offline-only.");
    }

    private int rankVoice(Voice v, Locale target) {
        int rank = 0;
        if (!v.getLocale().getCountry().equalsIgnoreCase(target.getCountry())) rank += 10;
        if (v.isNetworkConnectionRequired()) rank += 2;
        // A lower quality number is not assumed to be better. Let user test installed voices.
        return rank;
    }

    private void onVoiceSelected() {
        if (!ready || matchedVoices.isEmpty() || voiceSpinner.getSelectedItemPosition() < 0) {
            if (voiceDetail != null) voiceDetail.setText("No matching voice installed. Open speech settings to add one.");
            return;
        }
        int index = voiceSpinner.getSelectedItemPosition();
        if (index >= matchedVoices.size()) return;
        Voice v = matchedVoices.get(index);
        int result = tts.setVoice(v);
        if (result == TextToSpeech.ERROR) {
            voiceDetail.setText("Voice failed to load. Try another voice or install its data.");
        } else {
            prefs.edit().putString("voice_" + localeSpinner.getSelectedItemPosition(), v.getName()).apply();
            voiceDetail.setText("Actual region: " + v.getLocale().toLanguageTag() + "  •  " +
                    (v.isNetworkConnectionRequired() ? "Network required" : "Offline-capable") +
                    "  •  Engine: " + tts.getDefaultEngine());
        }
    }

    private boolean isReadyToTalk() {
        if (!ready) {setStatus("Android speech engine isn't ready."); return false;}
        if (matchedVoices.isEmpty()) {setStatus("Select an installed voice first."); return false;}
        if (scriptBox.getText().toString().trim().isEmpty()) {setStatus("Please enter a script first."); return false;}
        onVoiceSelected();
        tts.setSpeechRate(speed());
        tts.setPitch(pitch());
        return true;
    }

    private void beginSpeak() {
        if (!isReadyToTalk()) return;
        stopCurrent(false);
        segments = splitScript(processText(scriptBox.getText().toString()), 2400);
        if (segments.isEmpty()) return;
        job = "play";
        int id = ++session;
        for (int k = 0; k < segments.size(); k++) {
            int queued = tts.speak(segments.get(k), k == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                    new Bundle(), "PLAY_" + id + "_" + k);
            if (queued == TextToSpeech.ERROR) {stopCurrent(false); setStatus("This TTS engine couldn't play the script."); return;}
        }
        setStatus("Playing " + segments.size() + " segment(s)…");
    }

    private void beginExport() {
        if (!isReadyToTalk()) return;
        stopCurrent(false);
        segments = splitScript(processText(scriptBox.getText().toString()), 2400);
        if (segments.isEmpty()) return;
        try {
            synchronized (audioLock) {
                rawFile = File.createTempFile("shari_raw_", ".pcm", getCacheDir());
                rawStream = new FileOutputStream(rawFile);
                sampleRate = 0;
                audioEncoding = 0;
                channelCount = 0;
                audioBytes = 0;
                incompatibleFormat = false;
            }
            job = "export";
            int id = ++session;
            segmentIndex = 0;
            exportOne(id, 0);
        } catch (IOException e) {
            stopCurrent(false);
            setStatus("Can't open temporary file: " + e.getMessage());
        }
    }

    private void exportOne(int id, int index) {
        if (id != session || !"export".equals(job)) return;
        segmentIndex = index;
        partFile = new File(getCacheDir(), "shari_part_" + id + ".wav");
        int queued = tts.synthesizeToFile(segments.get(index), new Bundle(), partFile,
                "EXP_" + id + "_" + index);
        if (queued == TextToSpeech.ERROR) {
            failExport("The selected TTS voice doesn't support file synthesis.");
            return;
        }
        setStatus("Creating WAV audio: segment " + (index + 1) + "/" + segments.size() + "…");
    }

    private final class PlaybackListener extends UtteranceProgressListener {
        @Override public void onStart(String id) { }

        @Override public void onBeginSynthesis(String id, int rate, int format, int channels) {
            if (!isActiveExportId(id)) return;
            synchronized (audioLock) {
                if (sampleRate == 0) {
                    sampleRate = rate;
                    audioEncoding = format;
                    channelCount = channels;
                } else if (sampleRate != rate || audioEncoding != format || channelCount != channels) {
                    incompatibleFormat = true;
                }
            }
        }

        @Override public void onAudioAvailable(String id, byte[] audio) {
            if (!isActiveExportId(id)) return;
            synchronized (audioLock) {
                if (rawStream != null && !incompatibleFormat) {
                    try {
                        rawStream.write(audio);
                        audioBytes += audio.length;
                    } catch (IOException e) {
                        incompatibleFormat = true;
                    }
                }
            }
        }

        @Override public void onDone(String id) {
            ui.post(() -> {
                if (id.equals("PLAY_" + session + "_" + (segments.size() - 1)) && "play".equals(job)) {
                    job = "idle";
                    setStatus("Playback complete. You can export the full script to WAV.");
                } else if (isActiveExportId(id)) {
                    if (partFile != null) partFile.delete();
                    if (incompatibleFormat) {failExport("Voice changed audio format mid-script, or couldn't stream PCM audio."); return;}
                    int next = segmentIndex + 1;
                    if (next < segments.size()) exportOne(session, next);
                    else finishExport(session);
                }
            });
        }

        @Override public void onError(String id) {
            ui.post(() -> {
                if (isActiveExportId(id)) failExport("TTS engine couldn't synthesize this text. Try another voice.");
                else if (id.startsWith("PLAY_" + session + "_")) {job = "idle"; setStatus("Playback failed for the selected voice.");}
            });
        }
        @Override public void onError(String id, int errorCode) {onError(id);}
        @Override public void onStop(String id, boolean interrupted) { }
    }

    private boolean isActiveExportId(String utterance) {
        return "export".equals(job) && utterance.startsWith("EXP_" + session + "_");
    }

    private void finishExport(int id) {
        job = "saving";
        setStatus("Saving your full WAV to Downloads/ShariTTS…");
        new Thread(() -> {
            try {
                final File source;
                final int rate, enc, channels;
                final long size;
                synchronized (audioLock) {
                    if (rawStream != null) {rawStream.flush(); rawStream.close(); rawStream = null;}
                    source = rawFile;
                    rate = sampleRate;
                    enc = audioEncoding;
                    channels = channelCount;
                    size = audioBytes;
                }
                if (source == null || size == 0 || rate == 0) throw new IOException("Engine returned no PCM buffers for export.");
                if (size > Integer.MAX_VALUE - 44) throw new IOException("Audio is too large for a single WAV file.");
                int bits;
                int wavType;
                if (enc == AudioFormat.ENCODING_PCM_8BIT) {bits = 8; wavType = 1;}
                else if (enc == AudioFormat.ENCODING_PCM_16BIT) {bits = 16; wavType = 1;}
                else if (enc == AudioFormat.ENCODING_PCM_FLOAT) {bits = 32; wavType = 3;}
                else throw new IOException("Unsupported audio PCM format: " + enc);
                String title = "ShariTTS_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".wav";
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, title);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ShariTTS");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Could not create Downloads file.");
                try {
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "w");
                         FileInputStream in = new FileInputStream(source)) {
                        if (out == null) throw new IOException("Cannot write Downloads file.");
                        writeWavHeader(out, (int) size, rate, channels, bits, wavType);
                        byte[] buffer = new byte[32768];
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                } catch (Exception e) {
                    getContentResolver().delete(uri, null, null);
                    throw e;
                }
                source.delete();
                ui.post(() -> {
                    if (id == session) {job = "idle"; setStatus("✓ SAVED: Downloads/ShariTTS/" + title);}
                });
            } catch (Exception e) {
                ui.post(() -> {if (id == session) failExport("Export failed: " + e.getMessage());});
            }
        }, "ShariTTS-WavWriter").start();
    }

    private static void writeWavHeader(OutputStream out, int size, int rate, int channels, int bits, int wavType) throws IOException {
        out.write(new byte[]{'R','I','F','F'}); writeLE(out, 36 + size, 4);
        out.write(new byte[]{'W','A','V','E','f','m','t',' '}); writeLE(out, 16, 4);
        writeLE(out, wavType, 2); writeLE(out, channels, 2); writeLE(out, rate, 4);
        writeLE(out, rate * channels * (bits / 8), 4);
        writeLE(out, channels * (bits / 8), 2); writeLE(out, bits, 2);
        out.write(new byte[]{'d','a','t','a'}); writeLE(out, size, 4);
    }

    private static void writeLE(OutputStream out, int value, int bytes) throws IOException {
        for (int k = 0; k < bytes; k++) out.write((value >>> (k * 8)) & 0xff);
    }

    private void failExport(String reason) {
        stopCurrent(false);
        setStatus(reason);
    }

    private void stopCurrent(boolean notify) {
        session++;
        job = "idle";
        if (tts != null) tts.stop();
        synchronized (audioLock) {
            if (rawStream != null) {
                try {rawStream.close();} catch (IOException ignored) { }
                rawStream = null;
            }
            if (rawFile != null) {rawFile.delete(); rawFile = null;}
            if (partFile != null) {partFile.delete(); partFile = null;}
        }
        if (notify) setStatus("Stopped.");
    }

    private String processText(String original) {
        String s = original;
        for (String line : fixesBox.getText().toString().split("\\r?\\n")) {
            int separator = line.indexOf("=>");
            if (separator <= 0) continue;
            String from = line.substring(0, separator).trim();
            String to = line.substring(separator + 2).trim();
            if (!from.isEmpty() && !to.isEmpty()) s = s.replace(from, to);
        }
        return s.trim();
    }

    private ArrayList<String> splitScript(String text, int limit) {
        ArrayList<String> result = new ArrayList<>();
        text = text.replace("\r\n", "\n").trim();
        while (!text.isEmpty()) {
            if (text.length() <= limit) {result.add(text); break;}
            int at = limit;
            for (int k = limit; k > (int)(limit * .65f); k--) {
                char c = text.charAt(k - 1);
                if (c == '\n' || c == '।' || c == '۔' || c == '.' || c == '!' || c == '?' || c == '،' || c == '،') {at = k; break;}
            }
            if (at == limit) {
                for (int k = limit; k > (int)(limit * .70f); k--) {
                    if (Character.isWhitespace(text.charAt(k - 1))) {at = k; break;}
                }
            }
            result.add(text.substring(0, at).trim());
            text = text.substring(at).trim();
        }
        return result;
    }

    private float speed() {return 0.70f + (speedBar.getProgress() / 100.0f);}
    private float pitch() {return 0.70f + (pitchBar.getProgress() / 100.0f);}
    private void updateControls() {
        if (speedView != null && speedBar != null) speedView.setText(String.format(Locale.US, "Speed   %.2fx", speed()));
        if (pitchView != null && pitchBar != null) pitchView.setText(String.format(Locale.US, "Pitch   %.2fx", pitch()));
        updateCount();
    }
    private void updateCount() {
        if (scriptBox == null || countView == null) return;
        String s = scriptBox.getText().toString().trim();
        int words = s.isEmpty() ? 0 : s.split("\\s+").length;
        float rate = speedBar == null ? 1.05f : speed();
        int seconds = (int) Math.round(words * 60.0 / (135.0 * rate));
        countView.setText(words + " words   •   " + s.length() + " characters   •   ≈ " + (seconds / 60) + "m " + (seconds % 60) + "s (rough estimate)");
    }
    private void setStatus(String message) {
        if (statusView != null) statusView.setText(message);
    }

    private LinearLayout card(LinearLayout body) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(17));
        panel.setBackground(rounded(PANEL, 18, Color.rgb(36, 53, 74)));
        body.addView(panel, new LinearLayout.LayoutParams(-1, -2));
        return panel;
    }
    private TextView text(String string, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(string);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private GradientDrawable rounded(int color, int radius, int outline) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), outline);
        return shape;
    }
    private Button actionButton(String title, boolean accent) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(accent ? BG : WHITE);
        b.setBackground(rounded(accent ? AQUA : Color.rgb(30, 46, 68), 12,
                accent ? AQUA : Color.rgb(49, 73, 97)));
        return b;
    }
    private void addSmallButton(LinearLayout holder, String caption, Runnable action) {
        Button b = actionButton(caption, false);
        b.setTextSize(12);
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(38));
        p.setMargins(0, dp(6), dp(6), 0);
        holder.addView(b, p);
        b.setOnClickListener(v -> action.run());
    }
    private View gap(int size) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(size)));
        return v;
    }
    private int dp(int x) {return Math.round(getResources().getDisplayMetrics().density * x);}
    private ArrayAdapter<String> spinnerAdapter(List<String> values) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convert, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convert, parent);
                tv.setTextColor(WHITE); tv.setTextSize(14); tv.setSingleLine(true);
                return tv;
            }
            @Override public View getDropDownView(int position, View convert, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convert, parent);
                tv.setTextColor(Color.BLACK); tv.setPadding(dp(14), dp(13), dp(8), dp(13));
                return tv;
            }
        };
    }
    private void tint(SeekBar bar) {
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(AQUA));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(AQUA));
    }
    private TextWatcher watch(Runnable after) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int afterCount) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) {after.run();}
        };
    }
    private interface OnProgress {void update(int value);}
    private SeekBar.OnSeekBarChangeListener progressListener(OnProgress callback) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {callback.update(progress);}
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        };
    }
    private static class SimpleItemSelected implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable callback;
        SimpleItemSelected(Runnable callback) {this.callback = callback;}
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {callback.run();}
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
    @Override protected void onDestroy() {
        stopCurrent(false);
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
