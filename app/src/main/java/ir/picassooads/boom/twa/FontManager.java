package ir.picassooads.boom.twa;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ═══════════════════════════════════════════════════════════════
 * FontManager — دانلود و مدیریت فونت‌ها از CDN
 * 
 * • بار اول: دانلود از CDN و ذخیره در filesDir
 * • بارهای بعد: لود از filesDir (بدون اینترنت)
 * • در صورت شکست: fallback به Typeface.DEFAULT
 * ═══════════════════════════════════════════════════════════════
 */
public class FontManager {

    private static final String TAG = "FontManager";

    /* ★ این URLها را بعد از دیدن CSS پپیدا آپدیت کن */
    public static final String PEYDA_URL =
            "https://cdn.jsdelivr.net/gh/AmirAbbasVafaee/persian-fonts-cdn@main/fonts/Peyda-Regular.ttf";

    public static final String ARCIFORM_URL =
            "https://cdn.jsdelivr.net/npm/aks-fonts-2@1.0.0/Arciform/Arciform.otf";

    public static final String PEYDA_FILE = "peyda.ttf";
    public static final String ARCIFORM_FILE = "arciform.otf";

    /* Timeout */
    private static final int TIMEOUT_CONNECT_MS = 15000;
    private static final int TIMEOUT_READ_MS = 30000;

    /* Singleton */
    private static FontManager instance;

    private final Context appContext;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /* Cache */
    private Typeface peydaTypeface;
    private Typeface arciformTypeface;
    private boolean peydaReady = false;
    private boolean arciformReady = false;

    private FontManager(Context ctx) {
        this.appContext = ctx.getApplicationContext();
    }

    public static synchronized FontManager getInstance(Context ctx) {
        if (instance == null) {
            instance = new FontManager(ctx);
        }
        return instance;
    }

    /* ═══════════════════════════════════════════════════════════
       INIT — دانلود در پس‌زمینه (اگر نبود)
       ═══════════════════════════════════════════════════════════ */
    public void ensureFonts(final OnFontsReady callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ensureFontSync(PEYDA_URL, PEYDA_FILE);
                ensureFontSync(ARCIFORM_URL, ARCIFORM_FILE);
                loadTypefaces();

                if (callback != null) {
                    mainHandler.post(new Runnable() {
                        @Override public void run() { callback.onReady(); }
                    });
                }
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       دانلود همگام یک فونت
       ═══════════════════════════════════════════════════════════ */
    private void ensureFontSync(String url, String fileName) {
        File target = new File(appContext.getFilesDir(), fileName);

        // اگر هست و حجم دارد، از دانلود صرف‌نظر کن
        if (target.exists() && target.length() > 1024) {
            Log.d(TAG, fileName + " already exists (" + target.length() + " bytes)");
            return;
        }

        Log.d(TAG, "Downloading " + fileName + " from " + url);

        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;

        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(TIMEOUT_CONNECT_MS);
            conn.setReadTimeout(TIMEOUT_READ_MS);
            conn.setInstanceFollowRedirects(true);
            conn.connect();

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.e(TAG, "HTTP " + code + " downloading " + fileName);
                return;
            }

            is = conn.getInputStream();
            fos = new FileOutputStream(target);

            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
                total += n;
            }
            fos.flush();

            Log.d(TAG, "Downloaded " + fileName + " — " + total + " bytes");

        } catch (Exception e) {
            Log.e(TAG, "Download failed: " + fileName, e);
            // اگر فایل ناقص مانده، پاکش کن
            if (target.exists()) target.delete();
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    /* ═══════════════════════════════════════════════════════════
       لود کردن Typeface از فایل‌های ذخیره‌شده
       ═══════════════════════════════════════════════════════════ */
    private void loadTypefaces() {
        try {
            File peydaFile = new File(appContext.getFilesDir(), PEYDA_FILE);
            if (peydaFile.exists() && peydaFile.length() > 1024) {
                peydaTypeface = Typeface.createFromFile(peydaFile);
                peydaReady = true;
                Log.d(TAG, "Peyda Typeface loaded");
            } else {
                Log.w(TAG, "Peyda file missing — fallback to default");
                peydaTypeface = Typeface.DEFAULT;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Peyda", e);
            peydaTypeface = Typeface.DEFAULT;
        }

        try {
            File arcFile = new File(appContext.getFilesDir(), ARCIFORM_FILE);
            if (arcFile.exists() && arcFile.length() > 1024) {
                arciformTypeface = Typeface.createFromFile(arcFile);
                arciformReady = true;
                Log.d(TAG, "Arciform Typeface loaded");
            } else {
                Log.w(TAG, "Arciform file missing — fallback to default");
                arciformTypeface = Typeface.DEFAULT;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Arciform", e);
            arciformTypeface = Typeface.DEFAULT;
        }
    }

    /* ═══════════════════════════════════════════════════════════
       PUBLIC API — گرفتن Typeface
       ═══════════════════════════════════════════════════════════ */
    public Typeface getTypeface(String lang) {
        if ("fa".equals(lang) || "ar".equals(lang)) {
            return getPeyda();
        }
        return getArciform();
    }

    public Typeface getPeyda() {
        if (!peydaReady) loadTypefaces();
        return peydaTypeface != null ? peydaTypeface : Typeface.DEFAULT;
    }

    public Typeface getArciform() {
        if (!arciformReady) loadTypefaces();
        return arciformTypeface != null ? arciformTypeface : Typeface.DEFAULT;
    }

    public boolean isReady(String lang) {
        if ("fa".equals(lang) || "ar".equals(lang)) return peydaReady;
        return arciformReady;
    }

    /* ═══════════════════════════════════════════════════════════
       CALLBACK
       ═══════════════════════════════════════════════════════════ */
    public interface OnFontsReady {
        void onReady();
    }
}
