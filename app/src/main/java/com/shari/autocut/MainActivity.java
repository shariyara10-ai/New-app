package com.shari.autocut;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Environment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.View;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int CHOOSE_FILE_REQUEST = 4219;
    private static final String PREFS = "autocut_client";
    private static final int BACKGROUND = Color.rgb(10, 16, 28);
    private static final int CARD = Color.rgb(19, 28, 43);
    private static final int ACCENT = Color.rgb(69, 188, 244);
    private FrameLayout root;
    private LinearLayout home;
    private LinearLayout editorContainer;
    private EditText address;
    private TextView status;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> uploadCallback;
    private String endpoint = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        root = new FrameLayout(this);
        root.setBackgroundColor(BACKGROUND);
        setContentView(root);
        showHome();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String str, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(str);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String title, int color) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackground(bg(color, 14));
        return b;
    }

    private void showHome() {
        if (webView != null) webView.setVisibility(View.GONE);
        root.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(38), dp(22), dp(24));
        scroll.addView(content);
        root.addView(scroll);
        home = content;

        TextView tag = text("SHARI CREATOR SUITE  /  ANDROID CLIENT", 11, ACCENT, true);
        content.addView(tag);
        TextView heading = text("AutoCut AI Studio", 29, Color.WHITE, true);
        LinearLayout.LayoutParams h = new LinearLayout.LayoutParams(-1, -2);
        h.topMargin = dp(18);
        content.addView(heading, h);
        TextView summary = text("Edit your YouTube videos using speech-aware captions, motion graphics and an AI-assisted timeline.", 16, Color.rgb(180,195,212), false);
        LinearLayout.LayoutParams s = new LinearLayout.LayoutParams(-1, -2);
        s.topMargin = dp(8);
        content.addView(summary, s);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(22), dp(20), dp(22));
        card.setBackground(bg(CARD, 20));
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(-1, -2);
        c.topMargin = dp(26);
        content.addView(card, c);
        card.addView(text("CONNECT YOUR EDITOR", 14, Color.WHITE, true));
        TextView explain = text("Your Windows/Mac/Linux computer runs the editor engine. Your phone must be on the same trusted Wi-Fi.", 14, Color.rgb(183,196,213), false);
        LinearLayout.LayoutParams e = new LinearLayout.LayoutParams(-1, -2);
        e.topMargin = dp(8);
        card.addView(explain, e);

        address = new EditText(this);
        address.setSingleLine(true);
        address.setSelectAllOnFocus(true);
        address.setTextSize(15);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(128,148,166));
        address.setHint("http://192.168.1.5:8000");
        address.setPadding(dp(13), 0, dp(13), 0);
        address.setBackground(bg(Color.rgb(33, 45, 64), 10));
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("endpoint", ""));
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(-1, dp(55));
        a.topMargin = dp(16);
        card.addView(address, a);
        Button connect = button("Open AI Editor", Color.rgb(31, 130, 189));
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(-1, dp(54));
        b.topMargin = dp(14);
        card.addView(connect, b);
        connect.setOnClickListener(v -> connect());

        status = text("Ready to connect", 13, Color.rgb(160, 182, 205), false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(14);
        card.addView(status, sp);

        TextView steps = text("1. Start the desktop AutoCut AI Studio server.\n2. Run python run.py --host 0.0.0.0\n3. Enter your computer's LAN IP and port 8000 above.\n4. Import video/audio, transcribe, edit and export.", 14, Color.rgb(196, 205, 219), false);
        steps.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams stepsLp = new LinearLayout.LayoutParams(-1, -2);
        stepsLp.topMargin = dp(24);
        content.addView(steps, stepsLp);
        TextView limits = text("Requires the desktop engine. This APK does not do on-device Whisper transcription, local Ollama or FFmpeg video rendering.", 12, Color.rgb(150,165,183), false);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2);
        l.topMargin = dp(21);
        content.addView(limits, l);
    }

    private void connect() {
        String url = address.getText().toString().trim();
        if (url.isEmpty()) {
            status.setText("Enter your computer's IP address first.");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        Uri parsed = Uri.parse(url);
        if (parsed.getHost() == null ||
            !("http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme()))) {
            status.setText("Invalid address. Example: http://192.168.1.5:8000");
            return;
        }
        endpoint = url;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("endpoint", endpoint).apply();
        openEditor();
    }

    private void openEditor() {
        root.removeAllViews();
        editorContainer = new LinearLayout(this);
        editorContainer.setOrientation(LinearLayout.VERTICAL);
        editorContainer.setBackgroundColor(BACKGROUND);
        root.addView(editorContainer, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(4), dp(12), dp(4));
        bar.setBackgroundColor(CARD);
        editorContainer.addView(bar, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView brand = text("AUTOCUT  AI", 16, Color.WHITE, true);
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        Button settings = button("Server", Color.rgb(49, 63, 84));
        bar.addView(settings, new LinearLayout.LayoutParams(dp(90), dp(42)));
        settings.setOnClickListener(v -> showHome());

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        editorContainer.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        webView = new WebView(this);
        editorContainer.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        WebSettings w = webView.getSettings();
        w.setJavaScriptEnabled(true);
        w.setDomStorageEnabled(true);
        w.setAllowFileAccess(true);
        w.setMediaPlaybackRequiresUserGesture(false);
        w.setBuiltInZoomControls(false);
        w.setDisplayZoomControls(false);
        w.setUseWideViewPort(true);
        w.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Editor connection failed")
                        .setMessage("Start the desktop server and check your Wi-Fi/IP address.\n\n" + error.getDescription())
                        .setPositiveButton("Server settings", (d, w) -> showHome())
                        .setNegativeButton("Retry", (d, w) -> view.reload())
                        .show();
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                try {
                    Intent chooser = params.createIntent();
                    startActivityForResult(chooser, CHOOSE_FILE_REQUEST);
                    return true;
                } catch (Exception ex) {
                    uploadCallback = null;
                    Toast.makeText(MainActivity.this, "Unable to open file picker", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.setDownloadListener((url, agent, disposition, mime, length) -> download(url, agent, disposition, mime));
        webView.loadUrl(endpoint);
    }

    private void download(String url, String userAgent, String disposition, String mime) {
        try {
            String filename = URLUtil.guessFileName(url, disposition, mime);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("Shari AutoCut AI Studio export");
            request.setMimeType(mime);
            request.addRequestHeader("User-Agent", userAgent);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
            Toast.makeText(this, "Saving to Downloads: " + filename, Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, "Download failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (request == CHOOSE_FILE_REQUEST && uploadCallback != null) {
            uploadCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, intent));
            uploadCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.getParent() != null && webView.canGoBack()) {
            webView.goBack();
        } else if (webView != null && webView.getParent() != null) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
