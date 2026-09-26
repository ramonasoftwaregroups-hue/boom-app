package ir.picassooads.boom.twa;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * ═══════════════════════════════════════════════════════════════
 * MainActivity — WebView پلتفرم
 * 
 * v2 — اصلاحات:
 *   • چک کامل اعتبار سشن (isSessionValid) در onCreate و onResume
 *   • پاک کردن توکن منقضی قبل از رفتن به Login
 * ═══════════════════════════════════════════════════════════════
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SessionManager session;

    private static final String APP_SCHEME = "boomapp";
    private static final String BRIDGE_BASE =
            "https://boom.picassooads.ir/panel/mobile-bridge.php";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

        // ★ چک کامل: توکن هست + منقضی نشده
        if (!session.isSessionValid()) {
            // اگر توکن بود ولی منقضی شده، پاکش کن
            if (session.isLoggedIn()) {
                session.clear();
            }
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_main);

        try {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        } catch (Exception ignored) {}

        webView = findViewById(R.id.webview);
        setupWebView();

        handleIntent(getIntent());

        if (webView.getUrl() == null) {
            loadWithToken();
        }
    }

    private void loadWithToken() {
        String token = session.getToken();
        if (token == null || token.isEmpty()) {
            goToLogin();
            return;
        }
        String url = BRIDGE_BASE + "?token=" + Uri.encode(token);
        webView.loadUrl(url);
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

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                String scheme = uri.getScheme();

                if (APP_SCHEME.equals(scheme)) {
                    handleDeepLink(uri);
                    return true;
                }

                if (host != null && host.contains("picassooads.ir")) {
                    String path = uri.getPath();
                    if (path != null
                            && !path.contains("dashboard.php")
                            && !path.contains("user/")
                            && !path.contains("mobile-bridge.php")
                            && (path.endsWith("/panel/")
                                || path.endsWith("/panel/index.php")
                                || path.contains("/login")
                                || path.contains("/auth/"))) {
                        // کاربر از پنل logout کرد → سشن را پاک کن
                        session.clear();
                        goToLogin();
                        return true;
                    }
                    return false;
                }

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

        webView.addJavascriptInterface(new AppBridge(), "AndroidApp");
        webView.addJavascriptInterface(new BiometricBridge(), "AndroidBiometric");
    }

    public class AppBridge {

        @JavascriptInterface
        public boolean isApp() { return true; }

        @JavascriptInterface
        public String getPlatform() { return "android-app"; }

        @JavascriptInterface
        public String getAppVersion() { return ApiClient.APP_VERSION; }

        @JavascriptInterface
        public void logout() {
            runOnUiThread(() -> {
                session.clear();
                goToLogin();
            });
        }
    }

    public class BiometricBridge {

        @JavascriptInterface
        public boolean isApp() { return true; }

        @JavascriptInterface
        public String getPlatform() { return "android-app"; }

        @JavascriptInterface
        public void openBiometric(String url) {
            if (url == null || url.isEmpty()) return;
            runOnUiThread(() -> openBiometricInApp(url));
        }
    }

    private void openBiometricInApp(String url) {
        try {
            Intent intent = new Intent(MainActivity.this, BiometricActivity.class);
            intent.putExtra(BiometricActivity.EXTRA_URL, url);
            startActivity(intent);
        } catch (Exception e) {
            if (webView != null) webView.loadUrl(url);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        handleDeepLink(intent.getData());
    }

    private void handleDeepLink(Uri uri) {
        if (uri == null) return;

        String host = uri.getHost();
        String path = uri.getPath();

        if (APP_SCHEME.equals(uri.getScheme())) {
            if ("biometric-success".equals(host)
                    || (path != null && path.contains("biometric-success"))) {
                try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
                if (webView != null) webView.reload();
            }
            return;
        }

        if (host != null && host.contains("picassooads.ir")) {
            String biometric = uri.getQueryParameter("biometric");
            if ("success".equals(biometric)) {
                try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
                if (webView != null) webView.reload();
            }
        }
    }

    private void goToLogin() {
        Intent i = new Intent(MainActivity.this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /* ═══════════════════════════════════════════════════════════
       ★ onResume — چک کامل اعتبار سشن
       ═══════════════════════════════════════════════════════════ */
    @Override
    protected void onResume() {
        super.onResume();

        if (session != null && !session.isSessionValid()) {
            // توکن منقضی یا نامعتبر شد → پاکش کن و برو Login
            session.clear();
            goToLogin();
            return;
        }

        try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
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
