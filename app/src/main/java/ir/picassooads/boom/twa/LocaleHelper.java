package ir.picassooads.boom.twa;

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
 * • ذخیره زبان انتخابی در SharedPreferences
 * • تغییر Configuration اپ
 * • اعمال فونت مناسب (Peyda برای fa/ar، Arciform برای بقیه)
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
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, lang).apply();
    }

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_LANG, null);
        if (saved != null && isValid(saved)) {
            return saved;
        }
        // fallback به زبان سیستم اگر پشتیبانی می‌شود
        String sysLang = Locale.getDefault().getLanguage();
        if (isValid(sysLang)) return sysLang;
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
       اعمال زبان به Context
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
            // فونت اصلی را نگه می‌داریم تا اگر bold بود حفظ شود
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
