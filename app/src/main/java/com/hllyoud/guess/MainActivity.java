package com.hllyoud.guess;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String GAME_URL = "https://tmphtxyrpwzwfivsvijz.supabase.co/functions/v1/hllyoud-app";
    private static final String APP_UA = "HllyoudGuessAndroid/1.0.3";

    private WebView webView;
    private FrameLayout root;
    private volatile boolean destroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                    // The edge endpoint currently arrives with a text-oriented response header.
                    // Loading the fetched bytes explicitly as HTML makes Android WebView render
                    // the exact same live game instead of showing the HTML source as plain text.
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
