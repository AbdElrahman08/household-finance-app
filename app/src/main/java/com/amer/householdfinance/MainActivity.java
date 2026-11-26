package com.amer.householdfinance;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Wraps the household-finance page (assets/index.html) in a full-screen WebView. */
public class MainActivity extends Activity {
    private static final int PICK_FILE = 41;
    private WebView web;
    private ValueCallback<Uri[]> pending;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // the app keeps its data in localStorage
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (pending != null) pending.onReceiveValue(null);
                pending = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "اختار ملف النسخة الاحتياطية"), PICK_FILE);
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

    /** Lets the page hand a CSV or backup to Android's share sheet (Drive, WhatsApp, email...). */
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
    }
}
