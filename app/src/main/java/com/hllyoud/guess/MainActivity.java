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
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private FrameLayout root;
    private volatile boolean destroyed;
    private String e2eUsername;
    private String e2ePassword;
    private boolean e2eStarted;

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
        try { createAndLoadWebView(); } catch (Throwable e) { showSafeFallback(); }
    }

    private void createAndLoadWebView() {
        root.removeAllViews();
        showLoading();
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 9, 19));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        try {
            String ua = s.getUserAgentString();
            if (ua != null && !ua.contains("HllyoudGuessAndroid")) s.setUserAgentString(ua + " " + APP_UA);
        } catch (Throwable ignored) {}
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable ignored) {}

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("HllyoudConsole", m.messageLevel() + " " + m.sourceId() + ":" + m.lineNumber() + " " + m.message());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Throwable ignored) {}
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) showConnectionError();
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                maybeStartE2E();
            }
        });
        fetchAndRenderGame();
    }

    private void fetchAndRenderGame() {
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(GAME_URL).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(25000);
                c.setInstanceFollowRedirects(true);
                c.setRequestMethod("GET");
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                c.setRequestProperty("User-Agent", APP_UA);
                int status = c.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
                String html;
                try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                    html = out.toString(StandardCharsets.UTF_8.name());
                }
                if (html.length() < 1000 || !html.toLowerCase().contains("<html")) throw new IllegalStateException("Invalid game HTML");
                String finalHtml = html;
                runOnUiThread(() -> {
                    if (destroyed || isFinishing() || webView == null) return;
                    root.removeAllViews();
                    root.addView(webView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                    webView.loadDataWithBaseURL(GAME_URL, finalHtml, "text/html", "UTF-8", GAME_URL);
                });
            } catch (Throwable e) {
                Log.e("HllyoudApp", "Game load failed", e);
                runOnUiThread(() -> { if (!destroyed && !isFinishing()) showConnectionError(); });
            } finally { if (c != null) c.disconnect(); }
        }, "HllyoudGameLoader").start();
    }

    private void maybeStartE2E() {
        if (e2eStarted || webView == null || e2eUsername == null || e2ePassword == null || e2eUsername.isEmpty() || e2ePassword.isEmpty()) return;
        e2eStarted = true;
        Log.i(E2E_TAG, "E2E_START");
        handler.postDelayed(() -> evalE2E(clickTextScript("العربية", "Arabic"), "LANGUAGE", () ->
                handler.postDelayed(this::e2eLogin, 1800)), 1800);
    }

    private void e2eLogin() {
        String u = JSONObject.quote(e2eUsername);
        String p = JSONObject.quote(e2ePassword);
        String js = "(function(){"+
                "const vis=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>2&&r.height>2&&s.display!='none'&&s.visibility!='hidden'};"+
                "const ins=[...document.querySelectorAll('input')].filter(vis),pw=ins.find(e=>(e.type||'').toLowerCase()==='password'),un=ins.find(e=>e!==pw&&!['hidden','checkbox','radio','submit','button'].includes((e.type||'text').toLowerCase()));"+
                "if(!un||!pw)return 'missing_inputs:'+document.body.innerText.slice(0,500);"+
                "const set=(e,v)=>{const d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d&&d.set?d.set.call(e,v):e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))};"+
                "set(un,"+u+");set(pw,"+p+");"+
                "const form=pw.closest('form');let b=form&&form.querySelector('button[type=submit],input[type=submit]');"+
                "if(!b){const bs=[...document.querySelectorAll('button,[role=button]')].filter(vis);b=bs.reverse().find(x=>{const t=(x.innerText||x.textContent||'').trim().toLowerCase();return t==='دخول'||t==='login'})}"+
                "if(!b)return 'missing_login_button';b.click();return 'login_clicked:user='+un.value+',plen='+pw.value.length;})()";
        evalE2E(js, "LOGIN", () -> handler.postDelayed(() -> logBody("AFTER_LOGIN", () ->
                handler.postDelayed(this::e2eOpenComputer, 1500)), 7000));
    }

    private void e2eOpenComputer() {
        evalE2E(clickTextScript("الكمبيوتر", "كمبيوتر", "computer", "bot"), "COMPUTER", () ->
                handler.postDelayed(() -> logBody("AFTER_COMPUTER_CLICK", () -> handler.postDelayed(this::e2eTrySecret, 1200)), 4500));
    }

    private void e2eTrySecret() {
        String js = "(function(){"+
                "const vis=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>2&&r.height>2&&s.display!='none'&&s.visibility!='hidden'};"+
                "const ins=[...document.querySelectorAll('input')].filter(vis),n=ins.find(e=>['number','tel'].includes((e.type||'').toLowerCase())||/secret|رقم/i.test((e.placeholder||'')+' '+(e.name||'')));"+
                "if(!n)return 'no_number_input:'+document.body.innerText.slice(0,800);"+
                "const d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d&&d.set?d.set.call(n,'50'):n.value='50';n.dispatchEvent(new Event('input',{bubbles:true}));n.dispatchEvent(new Event('change',{bubbles:true}));"+
                "const bs=[...document.querySelectorAll('button,[role=button]')].filter(vis),b=bs.find(x=>/تأكيد|ثبت|جاهز|ابدأ|confirm|ready|start/i.test((x.innerText||x.textContent||'')));"+
                "if(b){b.click();return 'secret_clicked:'+((b.innerText||'').trim())}return 'secret_filled_no_button';})()";
        evalE2E(js, "SECRET", () -> handler.postDelayed(() -> logBody("AFTER_SECRET", null), 5000));
    }

    private String clickTextScript(String... needles) {
        StringBuilder a = new StringBuilder("[");
        for (int i=0;i<needles.length;i++) { if (i>0) a.append(','); a.append(JSONObject.quote(needles[i].toLowerCase())); }
        a.append(']');
        return "(function(){const ns="+a+",vis=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>2&&r.height>2&&s.display!='none'&&s.visibility!='hidden'};const es=[...document.querySelectorAll('button,[role=button],a')].filter(vis),e=es.find(x=>{const t=(x.innerText||x.textContent||'').trim().toLowerCase();return ns.some(n=>t.includes(n))});if(!e)return 'not_found:'+document.body.innerText.slice(0,900);const t=(e.innerText||e.textContent||'').trim();e.click();return 'clicked:'+t;})()";
    }

    private void evalE2E(String js, String label, Runnable after) {
        if (webView == null || destroyed) return;
        try {
            webView.evaluateJavascript(js, value -> {
                Log.i(E2E_TAG, label + "=" + value);
                if (after != null && !destroyed) after.run();
            });
        } catch (Throwable e) { Log.e(E2E_TAG, label + "_ERROR", e); }
    }

    private void logBody(String label, Runnable after) {
        evalE2E("(function(){return (document.body&&document.body.innerText||'').slice(0,12000)})()", label, after);
    }

    private void showLoading() {
        TextView t = new TextView(this); t.setText("Hllyoud guess\nجاري تحميل اللعبة..."); t.setTextColor(Color.WHITE); t.setTextSize(20f); t.setGravity(Gravity.CENTER);
        root.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private TextView makeButton(String text) {
        TextView b = new TextView(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(17f); b.setGravity(Gravity.CENTER); b.setPadding(28,24,28,24); b.setBackgroundColor(Color.rgb(92,66,190)); return b;
    }

    private void showConnectionError() {
        if (destroyed || isFinishing() || root == null) return;
        root.removeAllViews();
        LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setGravity(Gravity.CENTER); p.setPadding(48,48,48,48);
        TextView m = new TextView(this); m.setText("تعذر الاتصال باللعبة\nتأكد من الإنترنت وحاول مرة أخرى"); m.setTextColor(Color.WHITE); m.setTextSize(19f); m.setGravity(Gravity.CENTER); m.setPadding(0,0,0,36); p.addView(m);
        TextView r = makeButton("إعادة المحاولة"); r.setOnClickListener(v -> { try { createAndLoadWebView(); } catch (Throwable e) { showSafeFallback(); } }); p.addView(r);
        root.addView(p, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void showSafeFallback() {
        if (destroyed || root == null) return;
        root.removeAllViews();
        LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setGravity(Gravity.CENTER); p.setPadding(48,48,48,48);
        TextView t = new TextView(this); t.setText("Hllyoud guess"); t.setTextColor(Color.WHITE); t.setTextSize(27f); t.setGravity(Gravity.CENTER); t.setPadding(0,0,0,24); p.addView(t);
        TextView m = new TextView(this); m.setText("مكوّن عرض الويب على الجهاز يحتاج تحديث.\nيمكنك فتح اللعبة الآن من المتصفح."); m.setTextColor(Color.LTGRAY); m.setTextSize(17f); m.setGravity(Gravity.CENTER); m.setPadding(0,0,0,36); p.addView(m);
        TextView o = makeButton("فتح اللعبة"); o.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GAME_URL))); } catch (Throwable ignored) {} }); p.addView(o);
        root.addView(p, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }

    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacksAndMessages(null);
        try { if (webView != null) { webView.stopLoading(); webView.loadUrl("about:blank"); webView.setWebViewClient(null); webView.setWebChromeClient(null); webView.destroy(); webView = null; } } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
