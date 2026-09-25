package ir.picassooads.boom.twa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class BiometricActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "biometric_url";

    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_biometric);

        webView = findViewById(R.id.biometricWebView);
        setupWebView();

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url != null && !url.isEmpty()) {
            webView.loadUrl(url);
        } else {
            finish();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        // ★ کوکی‌ها را با WebView اصلی به اشتراک بگذار
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme();

                // ★ Deep link برگشت به اپ
                if ("boomapp".equals(scheme)) {
                    goBackToMain(uri);
                    return true;
                }

                // ★ intent:// از داخل Custom Tab / WebView
                if ("intent".equals(scheme)) {
                    String fragment = uri.getFragment();
                    if (fragment != null && fragment.contains("scheme=boomapp")) {
                        goBackToMain(Uri.parse("boomapp://biometric-success"));
                        return true;
                    }
                    // fallback: پارس browser_fallback_url
                    try {
                        String fallback = uri.getQueryParameter("browser_fallback_url");
                        if (fallback != null && !fallback.isEmpty()) {
                            view.loadUrl(fallback);
                            return true;
                        }
                    } catch (Exception ignored) {}
                    return true;
                }

                // ★ لینک‌های داخلی سایت → داخل همین WebView
                String host = uri.getHost();
                if (host != null && host.contains("picassooads.ir")) {
                    return false;
                }

                // لینک‌های خارجی → مرورگر
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {}
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });
    }

    // ═══════════════════════════════════════════════════════════
    // برگشت به MainActivity با deep link
    // ═══════════════════════════════════════════════════════════
    private void goBackToMain(Uri deepLink) {
        try {
            // کوکی‌ها را فوراً ذخیره کن تا MainActivity بعدی آن‌ها را ببیند
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {}

        Intent intent = new Intent(this, MainActivity.class);
        intent.setData(deepLink);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
