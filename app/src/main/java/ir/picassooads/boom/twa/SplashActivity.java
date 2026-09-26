package ir.picassooads.boom.twa;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ═══════════════════════════════════════════════════════════════
 * SplashActivity — صفحه‌ی شروع سینمایی
 * 
 * دو صحنه:
 *   Scene 1 (۰-۲.۶s): لوگوی BOOM
 *   Scene 2 (۲.۶-۵.۲s): لوگوی Picasso
 * 
 * v2 — اصلاحات:
 *   • لود لوگوی BOOM از URL + فیلتر سفید
 *   • چک کامل اعتبار سشن (isSessionValid) قبل از رفتن به MainActivity
 *   • پاک کردن سشن منقضی قبل از رفتن به LoginActivity
 * ═══════════════════════════════════════════════════════════════
 */
public class SplashActivity extends AppCompatActivity {

    private static final String BOOM_LOGO_URL =
            "https://boom.picassooads.ir/assets/images/boom.png";

    private static final String PICASSO_LOGO_URL =
            "https://picassooads.ir/shared/assets/images/logoW.png";

    /* ★ Timings */
    private static final long SCENE1_DURATION = 2600;
    private static final long TOTAL_DURATION  = 5200; // SCENE1 + SCENE2

    private LinearLayout scene1, scene2;
    private View scene1Glow, scene2Glow;
    private ImageView scene1Logo, scene2Logo;
    private TextView scene1Title, scene1Subtitle, scene2Caption, scene2Accent;
    private View versionBadge, versionDot;
    private TextView versionText;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean navigated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ★ تمام‌صفحه واقعی
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setContentView(R.layout.activity_splash);

        bindViews();
        setupScene1();
        setupScene2();

        // ★ شروع انیمیشن scene 1
        runScene1Animation();

        // ★ Version check در پس‌زمینه (بدون بلاک)
        checkVersionAsync();

        // ★ Timeline
        handler.postDelayed(this::transitionToScene2, SCENE1_DURATION);
        handler.postDelayed(this::navigateNext, TOTAL_DURATION);
    }

    private void bindViews() {
        scene1        = findViewById(R.id.scene1);
        scene2        = findViewById(R.id.scene2);
        scene1Glow    = findViewById(R.id.scene1Glow);
        scene2Glow    = findViewById(R.id.scene2Glow);
        scene1Logo    = findViewById(R.id.scene1Logo);
        scene2Logo    = findViewById(R.id.scene2Logo);
        scene1Title   = findViewById(R.id.scene1Title);
        scene1Subtitle= findViewById(R.id.scene1Subtitle);
        scene2Caption = findViewById(R.id.scene2Caption);
        scene2Accent  = findViewById(R.id.scene2Accent);
        versionBadge  = findViewById(R.id.versionBadge);
        versionDot    = findViewById(R.id.versionDot);
        versionText   = findViewById(R.id.versionText);
    }

    /* ═══════════════════════════════════════════════════════════
       SCENE 1 — لوگوی BOOM
       ═══════════════════════════════════════════════════════════ */
    private void setupScene1() {
        // ★ ۱) فیلتر سفید — مثل filter: brightness(0) invert(1) در وب
        //    (فقط پیکسل‌های غیرشفاف سفید می‌شوند، پس‌زمینه شفاف می‌ماند)
        scene1Logo.setColorFilter(
                new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));

        // ★ ۲) لوگوی BOOM را از URL لود کن
        loadImageFromUrl(BOOM_LOGO_URL, scene1Logo);
    }

    /* ═══════════════════════════════════════════════════════════
       SCENE 2 — لوگوی Picasso
       ═══════════════════════════════════════════════════════════ */
    private void setupScene2() {
        // Picasso logo از URL دانلود می‌شود
        loadImageFromUrl(PICASSO_LOGO_URL, scene2Logo);
    }

    /* ═══════════════════════════════════════════════════════════
       SCENE 1 — انیمیشن ورود Boom
       ═══════════════════════════════════════════════════════════ */
    private void runScene1Animation() {
        scene1.animate()
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        scene1Glow.setScaleX(0.5f);
        scene1Glow.setScaleY(0.5f);
        scene1Glow.animate()
                .scaleX(1.1f).scaleY(1.1f)
                .alpha(0.9f)
                .setDuration(1600)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        scene1Logo.setScaleX(0.3f);
        scene1Logo.setScaleY(0.3f);
        scene1Logo.setRotation(-10f);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(
                ObjectAnimator.ofFloat(scene1Logo, "scaleX", 0.3f, 1.08f, 1f),
                ObjectAnimator.ofFloat(scene1Logo, "scaleY", 0.3f, 1.08f, 1f),
                ObjectAnimator.ofFloat(scene1Logo, "rotation", -10f, 2f, 0f),
                ObjectAnimator.ofFloat(scene1Logo, "alpha", 0f, 1f)
        );
        logoSet.setDuration(1100);
        logoSet.setStartDelay(200);
        logoSet.setInterpolator(new OvershootInterpolator(1.4f));
        logoSet.start();

        scene1Title.setTranslationY(60f);
        scene1Title.animate()
                .translationY(0f).alpha(1f)
                .setDuration(700).setStartDelay(900)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        scene1Subtitle.setTranslationY(30f);
        scene1Subtitle.animate()
                .translationY(0f).alpha(1f)
                .setDuration(700).setStartDelay(1300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /* ═══════════════════════════════════════════════════════════
       Scene 1 → Scene 2
       ═══════════════════════════════════════════════════════════ */
    private void transitionToScene2() {
        scene1.animate()
                .alpha(0f)
                .setDuration(350)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        scene2.setVisibility(View.VISIBLE);
        scene2.animate()
                .alpha(1f)
                .setDuration(400)
                .setStartDelay(200)
                .start();

        handler.postDelayed(this::runScene2Animation, 400);
    }

    /* ═══════════════════════════════════════════════════════════
       SCENE 2 — انیمیشن ورود Picasso
       ═══════════════════════════════════════════════════════════ */
    private void runScene2Animation() {
        scene2Glow.setScaleX(0.5f);
        scene2Glow.setScaleY(0.5f);
        scene2Glow.animate()
                .scaleX(1.05f).scaleY(1.05f)
                .alpha(0.75f)
                .setDuration(1400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        scene2Logo.setScaleX(0.4f);
        scene2Logo.setScaleY(0.4f);
        scene2Logo.setRotation(-8f);

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(
                ObjectAnimator.ofFloat(scene2Logo, "scaleX", 0.4f, 1.06f, 1f),
                ObjectAnimator.ofFloat(scene2Logo, "scaleY", 0.4f, 1.06f, 1f),
                ObjectAnimator.ofFloat(scene2Logo, "rotation", -8f, 1f, 0f),
                ObjectAnimator.ofFloat(scene2Logo, "alpha", 0f, 1f)
        );
        logoSet.setDuration(1100);
        logoSet.setInterpolator(new OvershootInterpolator(1.3f));
        logoSet.start();

        scene2Caption.setTranslationY(40f);
        scene2Caption.animate()
                .translationY(0f).alpha(1f)
                .setDuration(600).setStartDelay(900)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        scene2Accent.setTranslationY(30f);
        scene2Accent.animate()
                .translationY(0f).alpha(1f)
                .setDuration(600).setStartDelay(1100)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /* ═══════════════════════════════════════════════════════════
       Version check (پس‌زمینه)
       ═══════════════════════════════════════════════════════════ */
    private void checkVersionAsync() {
        new Thread(() -> {
            try {
                URL url = new URL("https://boom.picassooads.ir/panel/pwa/version.json?_="
                        + System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    try (InputStream is = conn.getInputStream()) {
                        byte[] buf = new byte[4096];
                        int n = is.read(buf);
                        if (n > 0) {
                            String json = new String(buf, 0, n, "UTF-8");
                            int idx = json.indexOf("\"version\"");
                            if (idx >= 0) {
                                int q1 = json.indexOf('"', idx + 9);
                                if (q1 > 0) {
                                    int q2 = json.indexOf('"', q1 + 1);
                                    if (q2 > q1) {
                                        final String v = json.substring(q1 + 1, q2);
                                        handler.post(() -> showVersion(v));
                                    }
                                }
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private void showVersion(String version) {
        versionText.setText("v" + version);
        versionDot.animate().alpha(1f).setDuration(400).start();
        versionBadge.animate().alpha(1f).setDuration(500).start();
    }

    /* ═══════════════════════════════════════════════════════════
       Load image from URL
       ═══════════════════════════════════════════════════════════ */
    @SuppressWarnings("deprecation")
    private void loadImageFromUrl(String urlStr, ImageView target) {
        new AsyncTask<String, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(String... params) {
                try {
                    URL url = new URL(params[0]);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();
                    if (conn.getResponseCode() == 200) {
                        try (InputStream is = conn.getInputStream()) {
                            return BitmapFactory.decodeStream(is);
                        }
                    }
                } catch (Exception ignored) {}
                return null;
            }
            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null && target != null) {
                    target.setImageBitmap(bitmap);
                }
            }
        }.execute(urlStr);
    }

    /* ═══════════════════════════════════════════════════════════
       ★ NAVIGATE — با چک کامل اعتبار سشن
       ═══════════════════════════════════════════════════════════ */
    private void navigateNext() {
        if (navigated) return;
        navigated = true;

        SessionManager session = new SessionManager(this);

        // ★ چک کامل: توکن هست + منقضی نشده
        if (session.isSessionValid()) {
            // توکن معتبر → داشبورد
            goToMain();
        } else {
            // توکن نیست یا منقضی است → پاکش کن و برو به Login
            if (session.isLoggedIn()) {
                session.clear();
            }
            goToLogin();
        }
    }

    private void goToMain() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        getWindow().getDecorView().animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                })
                .start();
    }

    private void goToLogin() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        getWindow().getDecorView().animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    startActivity(intent);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                })
                .start();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
