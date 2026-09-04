package com.hllyoud.guess;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String GAME_URL = "https://tmphtxyrpwzwfivsvijz.supabase.co/functions/v1/hllyoud-app";
    private WebView webView;
    private FrameLayout root;

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
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(7, 9, 19));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        root.addView(webView, params);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        try {
            String ua = settings.getUserAgentString();
            if (ua != null && !ua.contains("HllyoudGuessAndroid")) {
                settings.setUserAgentString(ua + " HllyoudGuessAndroid/1.0.1");
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

        webView.loadUrl(GAME_URL);
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
        if (isFinishing() || root == null) return;
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
            root.removeAllViews();
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
        if (root == null) return;
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
