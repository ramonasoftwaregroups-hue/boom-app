package ir.picassooads.boom.twa;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    // ═══════════════════════════════════════════════════════════
    // Constants
    // ═══════════════════════════════════════════════════════════
    private static final String APP_URL = "https://boom.picassooads.ir/panel/pwa/splash.html?source=pwa";
    private static final String CHROME_PACKAGE = "com.android.chrome";
    private static final String CHROME_BETA_PACKAGE = "com.chrome.beta";
    private static final String CHROME_DEV_PACKAGE = "com.chrome.dev";

    // ═══════════════════════════════════════════════════════════
    // Custom Tabs State
    // ═══════════════════════════════════════════════════════════
    private CustomTabsClient customTabsClient;
    private CustomTabsSession customTabsSession;
    private CustomTabsServiceConnection connection;
    private boolean urlOpened = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // رنگ نوار وضعیت
        try {
            getWindow().setStatusBarColor(
                ContextCompat.getColor(this, R.color.colorPrimary)
            );
        } catch (Exception ignored) {}

        // اگه اپ توی Back Stack برگشته بود، دوباره باز کن
        if (savedInstanceState != null && !urlOpened) {
            openCustomTab();
        } else {
            connectAndOpen();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // اتصال به Chrome سپس باز کردن Custom Tab
    // ═══════════════════════════════════════════════════════════
    private void connectAndOpen() {
        connection = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client) {
                customTabsClient = client;
                try {
                    customTabsClient.warmup(0L);
                    customTabsSession = customTabsClient.newSession(null);
                } catch (Exception ignored) {}
                openCustomTab();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                customTabsClient = null;
                customTabsSession = null;
            }
        };

        // تلاش برای اتصال به Chrome (پایدار → بتا → dev → پیش‌فرض)
        boolean bound = false;
        try {
            bound = CustomTabsClient.bindCustomTabsService(this, CHROME_PACKAGE, connection);
            if (!bound) {
                bound = CustomTabsClient.bindCustomTabsService(this, CHROME_BETA_PACKAGE, connection);
            }
            if (!bound) {
                bound = CustomTabsClient.bindCustomTabsService(this, CHROME_DEV_PACKAGE, connection);
            }
        } catch (Exception ignored) {}

        // اگه اتصال موفق نبود، مستقیم باز کن
        if (!bound) {
            openCustomTab();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // باز کردن Custom Tab
    // ═══════════════════════════════════════════════════════════
    private void openCustomTab() {
        if (urlOpened) return;
        urlOpened = true;

        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();

        // رنگ نوار ابزار
        try {
            builder.setToolbarColor(
                ContextCompat.getColor(this, R.color.colorPrimary)
            );
            builder.setSecondaryToolbarColor(
                ContextCompat.getColor(this, R.color.colorPrimaryDark)
            );
        } catch (Exception ignored) {}

        // مخفی کردن عنوان (برای ظاهر native)
        builder.setShowTitle(false);

        // انیمیشن‌ها
        builder.setStartAnimations(this, android.R.anim.fade_in, android.R.anim.fade_out);
        builder.setExitAnimations(this, android.R.anim.fade_in, android.R.anim.fade_out);

        // Instant Apps غیرفعال
        try {
            builder.setInstantAppsEnabled(false);
        } catch (Exception ignored) {}

        // اتصال به session اگه موجود
        if (customTabsSession != null) {
            try {
                builder.setSession(customTabsSession);
            } catch (Exception ignored) {}
        }

        CustomTabsIntent customTabsIntent = builder.build();

        // اول با Chrome امتحان کن
        customTabsIntent.intent.setPackage(CHROME_PACKAGE);

        try {
            customTabsIntent.launchUrl(this, Uri.parse(APP_URL));
            finish();
            return;
        } catch (ActivityNotFoundException e) {
            // Chrome نبود → با مرورگر پیش‌فرض
        } catch (Exception e) {
            // هر خطای دیگه
        }

        // استفاده از مرورگر پیش‌فرض
        customTabsIntent.intent.setPackage(null);
        try {
            customTabsIntent.launchUrl(this, Uri.parse(APP_URL));
        } catch (Exception e) {
            // اگه همه چی شکست خورد → با Intent معمولی
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(APP_URL));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ignored) {}
        }

        finish();
    }

    // ═══════════════════════════════════════════════════════════
    // Cleanup
    // ═══════════════════════════════════════════════════════════
    @Override
    protected void onDestroy() {
        if (connection != null) {
            try {
                unbindService(connection);
            } catch (Exception ignored) {}
            connection = null;
        }
        super.onDestroy();
    }
}
