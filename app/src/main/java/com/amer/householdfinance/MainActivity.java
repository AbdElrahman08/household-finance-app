package com.amer.householdfinance;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Wraps the household-finance page (assets/index.html) in a full-screen WebView. */
public class MainActivity extends Activity {
    private static final int PICK_FILE = 41;
    private static final int ASK_NOTIFY = 42;
    private WebView web;
    private ValueCallback<Uri[]> pending;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // the app keeps its data in localStorage and IndexedDB
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pending != null) pending.onReceiveValue(null);
                pending = cb;
                Intent i;
                String[] types = p.getAcceptTypes();
                boolean image = types != null && types.length > 0 && types[0] != null && types[0].startsWith("image");
                if (image) {
                    i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                } else {
                    i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("*/*");
                }
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(Intent.createChooser(i, image ? "اختار صورة" : "اختار ملف"), PICK_FILE);
                } catch (Exception e) {
                    pending = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        if (state != null) web.restoreState(state);
        else web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == PICK_FILE && pending != null) {
            Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
            pending.onReceiveValue(u == null ? null : new Uri[]{u});
            pending = null;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private void js(final String code) {
        web.post(() -> web.evaluateJavascript(code, null));
    }

    /** Native features the page can call through window.AndroidBridge. */
    public class Bridge {
        @JavascriptInterface
        public void share(String text, String title) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            i.putExtra(Intent.EXTRA_SUBJECT, title);
            startActivity(Intent.createChooser(i, title));
        }

        @JavascriptInterface
        public boolean canPickFiles() {
            return true;
        }

        /** Saves a text file to Downloads/HouseholdFinance and returns where it went, or "" on failure. */
        @JavascriptInterface
        public String saveFile(String name, String mime, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver cr = getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HouseholdFinance");
                    Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u == null) return "";
                    try (OutputStream os = cr.openOutputStream(u)) {
                        if (os == null) return "";
                        os.write(bytes);
                    }
                    return "Downloads/HouseholdFinance/" + name;
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) return "";
                    File f = new File(dir, name);
                    try (FileOutputStream os = new FileOutputStream(f)) {
                        os.write(bytes);
                    }
                    return f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void setReminders(String json) {
            getSharedPreferences(Reminders.PREFS, MODE_PRIVATE).edit().putString("rem", json).apply();
            Reminders.schedule(MainActivity.this);
        }

        @JavascriptInterface
        public void markLogged(String date) {
            getSharedPreferences(Reminders.PREFS, MODE_PRIVATE).edit().putString("logged", date).apply();
        }

        @JavascriptInterface
        public void requestNotify() {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, ASK_NOTIFY));
            }
        }

        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public boolean canBio() {
            if (Build.VERSION.SDK_INT < 29) return false;
            BiometricManager bm = getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        }

        @JavascriptInterface
        public void bio() {
            if (Build.VERSION.SDK_INT < 29) return;
            runOnUiThread(() -> {
                BiometricPrompt p = new BiometricPrompt.Builder(MainActivity.this)
                        .setTitle("افتح مالية البيت")
                        .setNegativeButton("الرقم السري", getMainExecutor(), (d, w) -> js("window.onBio&&window.onBio(false)"))
                        .build();
                p.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        js("window.onBio&&window.onBio(true)");
                    }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        js("window.onBio&&window.onBio(false)");
                    }
                });
            });
        }
    }
}
