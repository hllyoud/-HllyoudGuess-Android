package com.hllyoud.guess;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String GAME_URL = "https://tmphtxyrpwzwfivsvijz.supabase.co/functions/v1/hllyoud-app";
    private static final String APP_UA = "HllyoudGuessAndroid/1.0.3";
    private static final String E2E_TAG = "HllyoudE2E";

    private WebView webView;
    private FrameLayout root;
    private volatile boolean destroyed = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String e2eUsername;
    private String e2ePassword;
    private boolean e2eStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("hllyoud_e2e", false)) {
            e2eUsername = intent.getStringExtra("e2e_username");
            e2ePassword = intent.getStringExtra("e2e_password");
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 9, 19));
        setContentView(root);

        try {
            createAndLoadWebView();
        } catch (Throwable error) {
            showSafeFallback();
        }
    }

    private void createAndLoadWebView() {
        root.removeAllViews();
        showLoading();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 9, 19));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        try {
            String ua = settings.getUserAgentString();
            if (ua != null && !ua.contains("HllyoudGuessAndroid")) {
                settings.setUserAgentString(ua + " " + APP_UA);
            }
        } catch (Throwable ignored) {
        }

        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable ignored) {
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Throwable ignored) {
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    showConnectionError();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                maybeStartE2E();
            }
        });

        fetchAndRenderGame();
    }

    private void showLoading() {
        TextView loading = new TextView(this);
        loading.setText("Hllyoud guess\nجاري تحميل اللعبة...");
        loading.setTextColor(Color.WHITE);
        loading.setTextSize(20f);
        loading.setGravity(Gravity.CENTER);
        root.addView(loading, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    private void fetchAndRenderGame() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(GAME_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(25000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                connection.setRequestProperty("User-Agent", APP_UA);

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException("HTTP " + status);
                }

                String html;
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    html = output.toString(StandardCharsets.UTF_8.name());
                }

                if (html.length() < 1000 || !html.toLowerCase().contains("<html")) {
                    throw new IllegalStateException("Invalid game HTML");
                }

                final String gameHtml = html;
                runOnUiThread(() -> {
                    if (destroyed || isFinishing() || webView == null) return;
                    root.removeAllViews();
                    root.addView(webView, new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    ));
                    webView.loadDataWithBaseURL(GAME_URL, gameHtml, "text/html", "UTF-8", GAME_URL);
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing()) showConnectionError();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "HllyoudGameLoader").start();
    }

    private void maybeStartE2E() {
        if (e2eStarted || webView == null || e2eUsername == null || e2ePassword == null) return;
        if (e2eUsername.isEmpty() || e2ePassword.isEmpty()) return;
        e2eStarted = true;
        Log.i(E2E_TAG, "E2E_START");

        handler.postDelayed(() -> evalE2E(clickTextScript("العربية", "Arabic"), "LANGUAGE"), 1200);
        handler.postDelayed(this::e2eLogin, 3000);
        handler.postDelayed(() -> logBody("AFTER_LOGIN"), 9000);
        handler.postDelayed(this::e2eOpenComputer, 10500);
        handler.postDelayed(() -> logBody("AFTER_COMPUTER_CLICK"), 15000);
        handler.postDelayed(this::e2eTrySecret, 16500);
        handler.postDelayed(() -> logBody("AFTER_SECRET"), 22000);
    }

    private void e2eLogin() {
        if (webView == null) return;
        String u = JSONObject.quote(e2eUsername);
        String p = JSONObject.quote(e2ePassword);
        String js = "(function(){" +
                "const vis=e=>{const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>2&&r.height>2&&s.display!='none'&&s.visibility!='hidden'};" +
                "const ins=[...document.querySelectorAll('input')].filter(vis);" +
                "const pw=ins.find(e=>(e.type||'').toLowerCase()==='password');" +
                "const un=ins.find(e=>e!==pw&&!['hidden','checkbox','radio','submit','button'].includes((e.type||'text').toLowerCase()));" +
                "if(!un||!pw)return 'missing_inputs:'+document.body.innerText.slice(0,600);" +
                "const set=(e,v)=>{const d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d&&d.set?d.set.call(e,v):e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))};" +
                "set(un,"+u+");set(pw,"+p+");" +
                "const form=pw.closest('form');let b=form&&form.querySelector('button[type=submit],input[type=submit]');" +
                "if(!b){const bs=[...document.querySelectorAll('button,[role=button]')].filter(vis);b=bs.reverse().find(x=>{const t=(x.innerText||x.textContent||'').trim().toLowerCase();return t==='دخول'||t==='login'||t.includes('دخول')})}" +
                "if(!b)return 'missing_login_button';b.click();return 'login_clicked';})()";
        evalE2E(js, "LOGIN");
    }

    private void e2eOpenComputer() {
        evalE2E(clickTextScript("الكمبيوتر", "كمبيوتر", "computer", "bot"), "COMPUTER");
    }

    private void e2eTrySecret() {
        String js = "(function(){" +
                "const vis=e=>{const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>2&&r.height>2&&s.display!='none'&&s.visibility!='hidden'};" +
                "const ins=[...document.querySelectorAll('input')].filter(vis);" +
                "const n=ins.find(e=>['number','tel'].includes((e.type||'').toLowerCase())||/secret|رقم/i.test((e.placeholder||'')+' '+(e.name||'')));" +
                "if(!n)return 'no_number_input:'+document.body.innerText.slice(0,800);" +
                "const d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d&&d.set?d.set.call(n,'50'):n.value='50';n.dispatchEvent(new Event('input',{bubbles:true}));n.dispatchEvent(new Event('change',{bubbles:true}));" +
                "const bs=[...document.querySelectorAll('button,[role=button]')].filter(vis);" +
                "const b=bs.find(x=>/تأكيد|ثبت|جاهز|ابدأ|confirm|ready|start/i.test((x.innerText||x.textContent||'')));" +
                "if(b){b.click();return 'secret_clicked:'+((b.innerText||'').trim())}return 'secret_filled_no_button';})()";
        evalE2E(js, "SECRET");
    }

    private String clickTextScript(String... needles) {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < needles.length; i++) {
            if (i > 0) arr.append(',');
            arr.append(JSONObject.quote(needles[i].toLowerCase()));
        }
        arr.append(']');
        return "(function(){const ns="+arr+";const vis=e=>{const r=e.getBoundingClientRect();const s=getComputedStyle(e);return r.width>2&&r.height>2&&s.display!='none'&&s.visibility!='hidden'};const es=[...document.querySelectorAll('button,[role=button],a')].filter(vis);const e=es.find(x=>{const t=(x.innerText||x.textContent||'').trim().toLowerCase();return ns.some(n=>t.includes(n))});if(!e)return 'not_found:'+document.body.innerText.slice(0,800);const t=(e.innerText||e.textContent||'').trim();e.click();return 'clicked:'+t;})()";
    }

    private void evalE2E(String js, String label) {
        if (webView == null || destroyed) return;
        try {
            webView.evaluateJavascript(js, value -> Log.i(E2E_TAG, label + "=" + value));
        } catch (Throwable e) {
            Log.e(E2E_TAG, label + "_ERROR", e);
        }
    }

    private void logBody(String label) {
        evalE2E("(function(){return (document.body&&document.body.innerText||'').slice(0,12000)})()", label);
    }

    private TextView makeButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(17f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(28, 24, 28, 24);
        button.setBackgroundColor(Color.rgb(92, 66, 190));
        return button;
    }

    private void showConnectionError() {
        if (destroyed || isFinishing() || root == null) return;
        root.removeAllViews();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(48, 48, 48, 48);

        TextView message = new TextView(this);
        message.setText("تعذر الاتصال باللعبة\nتأكد من الإنترنت وحاول مرة أخرى");
        message.setTextColor(Color.WHITE);
        message.setTextSize(19f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, 36);
        panel.addView(message);

        TextView retry = makeButton("إعادة المحاولة");
        retry.setOnClickListener(v -> {
            try {
                createAndLoadWebView();
            } catch (Throwable error) {
                showSafeFallback();
            }
        });
        panel.addView(retry);

        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    private void showSafeFallback() {
        if (destroyed || root == null) return;
        root.removeAllViews();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("Hllyoud guess");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 24);
        panel.addView(title);

        TextView message = new TextView(this);
        message.setText("مكوّن عرض الويب على الجهاز يحتاج تحديث.\nيمكنك فتح اللعبة الآن من المتصفح.");
        message.setTextColor(Color.LTGRAY);
        message.setTextSize(17f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 0, 0, 36);
        panel.addView(message);

        TextView open = makeButton("فتح اللعبة");
        open.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GAME_URL)));
            } catch (Throwable ignored) {
            }
        });
        panel.addView(open);

        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        try {
            if (webView != null) {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.setWebViewClient(null);
                webView.destroy();
                webView = null;
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }
}
