package ir.picassooads.boom.twa;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════
 * LocaleHelper — مدیریت زبان و اعمال آن به اپ
 * 
 * v2 — اصلاحات:
 *   • اضافه شدن forceLocale() که زبان را فوراً روی Activity اعمال می‌کند
 *   • fallback به فارسی (نه به زبان سیستم)
 *   • تغییر زبان بدون نیاز به recreate پیچیده
 * ═══════════════════════════════════════════════════════════════
 */
public class LocaleHelper {

    private static final String PREFS = "boom_locale";
    private static final String KEY_LANG = "selected_language";

    public static final String[] SUPPORTED = {"fa", "ar", "en", "fr", "it", "de"};
    public static final String DEFAULT = "fa";

    /* ═══════════════════════════════════════════════════════════
       ذخیره و خواندن زبان
       ═══════════════════════════════════════════════════════════ */
    public static void saveLanguage(Context context, String lang) {
        if (!isValid(lang)) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, lang).apply();
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_LANG, null);
        if (saved != null && isValid(saved)) {
            return saved;
        }
        // ★ fallback به فارسی (نه به زبان سیستم)
        return DEFAULT;
    }

    public static boolean isValid(String lang) {
        if (lang == null) return false;
        for (String s : SUPPORTED) {
            if (s.equals(lang)) return true;
        }
        return false;
    }

    public static boolean isRtl(String lang) {
        return "fa".equals(lang) || "ar".equals(lang);
    }

    /* ═══════════════════════════════════════════════════════════
       ★ forceLocale — اعمال فوری زبان روی Activity
       
       این متد زبان را بلافاصله روی منابع Activity اعمال می‌کند
       و نیازی به attachBaseContext یا recreate ندارد.
       ═══════════════════════════════════════════════════════════ */
    public static void forceLocale(Activity activity, String lang) {
        if (activity == null) return;
        if (!isValid(lang)) lang = DEFAULT;

        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        // ★ روش مستقیم — روی همه‌ی API ها کار می‌کند
        activity.getResources().updateConfiguration(
                config,
                activity.getResources().getDisplayMetrics()
        );
    }

    /* ═══════════════════════════════════════════════════════════
       applyLocale — برای استفاده در attachBaseContext
       (روش پیشنهادی برای اپ‌هایی که می‌خواهند در همه Activity ها اعمال شود)
       ═══════════════════════════════════════════════════════════ */
    public static Context applyLocale(Context context, String lang) {
        if (!isValid(lang)) lang = DEFAULT;

        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.createConfigurationContext(config);
        } else {
            context.getResources().updateConfiguration(config,
                    context.getResources().getDisplayMetrics());
            return context;
        }
    }

    public static Context applySavedLocale(Context context) {
        return applyLocale(context, getLanguage(context));
    }

    /* ═══════════════════════════════════════════════════════════
       اعمال فونت به یک View و همه‌ی فرزندانش
       ═══════════════════════════════════════════════════════════ */
    public static void applyFontToViewTree(Context context, View root, String lang) {
        if (root == null) return;

        android.graphics.Typeface tf =
                FontManager.getInstance(context).getTypeface(lang);

        applyFontRecursive(root, tf);
    }

    private static void applyFontRecursive(View view, android.graphics.Typeface tf) {
        if (view == null) return;

        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            android.graphics.Typeface original = tv.getTypeface();
            if (original != null && original.isBold()) {
                tv.setTypeface(tf, android.graphics.Typeface.BOLD);
            } else {
                tv.setTypeface(tf);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyFontRecursive(vg.getChildAt(i), tf);
            }
        }
    }
}
