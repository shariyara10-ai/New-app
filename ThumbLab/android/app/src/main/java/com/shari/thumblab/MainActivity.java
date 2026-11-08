package com.shari.thumblab;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView view;
    private ValueCallback<Uri[]> chooser;
    private static final int PICK_FILE = 1406;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        view = new WebView(this);
        view.setBackgroundColor(0xff0b0d13);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest req) {
                String url = req.getUrl().toString();
                return !url.startsWith("file:///android_asset/");
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                String mime = "image/*";
                for (String accept : p.getAcceptTypes()) {
                    if (accept != null && (accept.contains("json") || accept.equals(".json"))) mime = "application/json";
                }
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mime);
                try { startActivityForResult(Intent.createChooser(intent, "Select file"), PICK_FILE); return true; }
                catch (Exception e) { chooser.onReceiveValue(null); chooser = null; return false; }
            }
        });
        // This bridge is safe only for our bundled local document.
        view.addJavascriptInterface(new NativeBridge(), "NativeApp");
        setContentView(view);
        view.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int code, int result, Intent intent) {
        super.onActivityResult(code, result, intent);
        if (code == PICK_FILE && chooser != null) {
            chooser.onReceiveValue(result == RESULT_OK && intent != null && intent.getData() != null
                ? new Uri[] { intent.getData() } : null);
            chooser = null;
        }
    }

    public final class NativeBridge {
        @JavascriptInterface public void saveImage(String dataUrl, String name) {
            new Thread(() -> {
                Uri created = null;
                try {
                    if (dataUrl == null || !dataUrl.startsWith("data:image/")) throw new IllegalArgumentException("Invalid image");
                    int comma = dataUrl.indexOf(',');
                    if (comma < 0) throw new IllegalArgumentException("Invalid image data");
                    boolean jpeg = dataUrl.startsWith("data:image/jpeg");
                    byte[] bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT);
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, safeName(name, jpeg ? "thumbnail.jpg" : "thumbnail.png"));
                    v.put(MediaStore.Images.Media.MIME_TYPE, jpeg ? "image/jpeg" : "image/png");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ThumbLab");
                    v.put(MediaStore.Images.Media.IS_PENDING, 1);
                    created = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (created == null) throw new IllegalStateException("Gallery unavailable");
                    try (OutputStream stream = getContentResolver().openOutputStream(created)) {
                        if (stream == null) throw new IllegalStateException("Cannot open output");
                        stream.write(bytes);
                    }
                    v.clear();
                    v.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(created, v, null, null);
                    toast("Thumbnail saved to Pictures / ThumbLab");
                } catch (Exception e) {
                    if (created != null) getContentResolver().delete(created, null, null);
                    toast("Export failed: " + e.getMessage());
                }
            }).start();
        }

        @JavascriptInterface public void saveTextFile(String content, String name) {
            new Thread(() -> {
                Uri created = null;
                try {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, safeName(name, "thumblab-project.json"));
                    v.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ThumbLab");
                    v.put(MediaStore.Downloads.IS_PENDING, 1);
                    created = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (created == null) throw new IllegalStateException("Downloads unavailable");
                    try (OutputStream stream = getContentResolver().openOutputStream(created)) {
                        if (stream == null) throw new IllegalStateException("Cannot open output");
                        stream.write(content.getBytes(StandardCharsets.UTF_8));
                    }
                    v.clear();
                    v.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(created, v, null, null);
                    toast("Project saved to Downloads / ThumbLab");
                } catch (Exception e) {
                    if (created != null) getContentResolver().delete(created, null, null);
                    toast("Save failed: " + e.getMessage());
                }
            }).start();
        }
    }
    private static String safeName(String input, String fallback) {
        String value = input == null ? fallback : input.replaceAll("[^A-Za-z0-9._-]", "-");
        return value.isEmpty() || value.equals(".") || value.equals("..") ? fallback : value;
    }
    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }
    @Override public void onBackPressed() {
        if (view != null && view.canGoBack()) view.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (view != null) {
            view.removeJavascriptInterface("NativeApp");
            view.destroy();
            view = null;
        }
        super.onDestroy();
    }
}
