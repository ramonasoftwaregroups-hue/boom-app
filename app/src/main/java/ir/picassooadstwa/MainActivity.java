package ir.picassooads.boom.twa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final String BASE_URL = "https://boom.picassooads.ir/panel/pwa/splash.html?source=pwa";
    private static final String CHROME_PACKAGE = "com.android.chrome";
    private static final String APP_SCHEME = "boomapp";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        } catch (Exception ignored) {}

        webView = findViewById(R.id.webview);
        setupWebView();

        // اگه با deep link باز شد
        handleIntent(getIntent());

        // بارگذاری اولیه
        if (webView.getUrl() == null) {
            webView.loadUrl(BASE_URL);
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
        settings.setGeolocationEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                String scheme = uri.getScheme();

                // Deep link به اپ
                if (APP_SCHEME.equals(scheme)) {
                    handleDeepLink(uri);
                    return true;
                }

                // لینک‌های داخلی سایت → داخل WebView
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

        // JS Bridge
        webView.addJavascriptInterface(new BiometricBridge(), "AndroidBiometric");
    }

    // ═══════════════════════════════════════════════════════════
    // JS Bridge — از داخل WebView صدا زده می‌شه
    // ═══════════════════════════════════════════════════════════
    public class BiometricBridge {

        @JavascriptInterface
        public boolean isApp() {
            return true;
        }

        @JavascriptInterface
        public String getPlatform() {
            return "android-app";
        }

        @JavascriptInterface
        public void openBiometric(String url) {
            if (url == null || url.isEmpty()) return;
            runOnUiThread(() -> openBiometricCustomTab(url));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // باز کردن Custom Tab فقط برای بیومتریک
    // ═══════════════════════════════════════════════════════════
    private void openBiometricCustomTab(String url) {
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary));
            builder.setShowTitle(false);
            builder.setUrlBarHidingEnabled(true);
            builder.setStartAnimations(this, android.R.anim.fade_in, android.R.anim.fade_out);
            builder.setExitAnimations(this, android.R.anim.fade_in, android.R.anim.fade_out);

            CustomTabsIntent intent = builder.build();
            intent.intent.setPackage(CHROME_PACKAGE);

            try {
                intent.launchUrl(this, Uri.parse(url));
            } catch (Exception e) {
                intent.intent.setPackage(null);
                intent.launchUrl(this, Uri.parse(url));
            }
        } catch (Exception e) {
            // اگه هیچی نشد، داخل WebView باز کن
            if (webView != null) webView.loadUrl(url);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Deep Link handling
    // ═══════════════════════════════════════════════════════════
    private void handleIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        handleDeepLink(intent.getData());
    }

    private void handleDeepLink(Uri uri) {
        if (uri == null) return;

        String host = uri.getHost();
        String path = uri.getPath();

        // boost://biometric-success
        if (APP_SCHEME.equals(uri.getScheme())) {
            if ("biometric-success".equals(host) || (path != null && path.contains("biometric-success"))) {
                // برگرد به اپ و reload کن
                if (webView != null) {
                    webView.reload();
                }
            }
            return;
        }

        // https://boom.picassooads.ir/... با پارامتر biometric=success
        if (host != null && host.contains("picassooads.ir")) {
            String biometric = uri.getQueryParameter("biometric");
            if ("success".equals(biometric)) {
                if (webView != null) webView.reload();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
