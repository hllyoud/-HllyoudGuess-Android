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
        handler.postDelayed(() -> evalE2E("(function(){const b=document.getElementById('chooseAr');if(!b)return 'missing_language';b.click();return 'arabic_clicked'})()", "LANGUAGE", () -> handler.postDelayed(this::e2eLogin, 1600)), 1800);
    }

    private void e2eLogin() {
        String u = JSONObject.quote(e2eUsername);
        String p = JSONObject.quote(e2ePassword);
        String js = "(function(){"+
                "const un=document.getElementById('loginIdentifier'),pw=document.getElementById('loginPassword'),b=document.getElementById('loginBtn');"+
                "if(!un||!pw||!b)return 'missing_login_controls';"+
                "const set=(e,v)=>{const d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d&&d.set?d.set.call(e,v):e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))};"+
                "set(un,"+u+");set(pw,"+p+");b.click();return 'login_clicked';})()";
        evalE2E(js, "LOGIN", () -> handler.postDelayed(() -> logScreen("AFTER_LOGIN_SCREEN", () -> e2eTutorialStep(0)), 6500));
    }

    private void e2eTutorialStep(int step) {
        if (step >= 3) {
            handler.postDelayed(() -> logScreen("AFTER_TUTORIAL_SCREEN", () -> handler.postDelayed(this::e2eOpenComputer, 900)), 2200);
            return;
        }
        String js = "(function(){const s=document.querySelector('.screen.active')?.id||'';if(s!=='tutorial')return 'skip:'+s;const b=document.getElementById('tutorialNext');if(!b)return 'missing_tutorial_next';b.click();return 'tutorial_next_' + " + (step + 1) + ";})()";
        evalE2E(js, "TUTORIAL_" + (step + 1), () -> handler.postDelayed(() -> e2eTutorialStep(step + 1), 1200));
    }

    private void e2eOpenComputer() {
        String js = "(function(){try{if(typeof ensureComputerUI==='function')ensureComputerUI()}catch(_){}const b=document.getElementById('computerRoomBtn');if(!b)return 'missing_computer_button:'+((document.querySelector('.screen.active')||{}).id||'');b.click();return 'computer_clicked'})()";
        evalE2E(js, "COMPUTER", () -> handler.postDelayed(() -> logState("AFTER_COMPUTER_STATE", () -> handler.postDelayed(this::e2eLockSecret, 800)), 6000));
    }

    private void e2eLockSecret() {
        String js = "(function(){const n=document.getElementById('secretInput'),b=document.getElementById('lockSecretBtn');if(!n||!b)return 'missing_secret_controls:'+((document.querySelector('.screen.active')||{}).id||'');const d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d&&d.set?d.set.call(n,'50'):n.value='50';n.dispatchEvent(new Event('input',{bubbles:true}));n.dispatchEvent(new Event('change',{bubbles:true}));b.click();return 'secret_50_clicked'})()";
        evalE2E(js, "SECRET", () -> handler.postDelayed(() -> logState("AFTER_SECRET_STATE", () -> handler.postDelayed(this::e2ePlayAction, 1000)), 6500));
    }

    private void e2ePlayAction() {
        String js = "(function(){"+
                "if(!window.state||!window.currentUser)return 'state_missing';const room=state.room||{},round=state.round||{},p=state.pending;"+
                "if(room.status!=='playing')return 'not_playing:'+room.status;"+
                "if(p&&p.guessed_against===currentUser.id){const id=Number(p.guessed_number)<50?'higherBtn':'lowerBtn',b=document.getElementById(id);if(!b)return 'answer_button_missing';b.click();return 'answered_cpu_guess:'+id+':'+p.guessed_number;}"+
                "if(!p&&round.turn_user_id===currentUser.id){const n=document.getElementById('guessInput'),b=document.getElementById('guessBtn');if(!n||!b)return 'guess_controls_missing';const d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d&&d.set?d.set.call(n,'50'):n.value='50';n.dispatchEvent(new Event('input',{bubbles:true}));b.click();return 'human_guess_clicked:50';}"+
                "return 'waiting_for_cpu';})()";
        evalE2E(js, "PLAY_ACTION", () -> handler.postDelayed(() -> logState("FINAL_STATE", () -> logBody("FINAL_BODY", null)), 7500));
    }

    private void logScreen(String label, Runnable after) {
        evalE2E("(function(){return document.querySelector('.screen.active')?.id||'none'})()", label, after);
    }

    private void logState(String label, Runnable after) {
        String js = "(function(){try{return JSON.stringify({screen:document.querySelector('.screen.active')?.id||'',room:window.state?.room?.status||'',mode:window.state?.room?.game_mode||'',round:window.state?.room?.current_round||0,turn:window.state?.round?.turn_user_id||'',pending:!!window.state?.pending})}catch(e){return 'state_error:'+e.message}})()";
        evalE2E(js, label, after);
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
