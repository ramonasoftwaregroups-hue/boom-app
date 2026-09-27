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
 * v3 — اصلاحات:
 *   • ترتیب اولویت درست:
 *       1) زبان کاربر از سرور  (user_language)
 *       2) زبان انتخاب‌شده محلی (selected_language)
 *       3) زبان سیستم
 *       4) fa (fallback نهایی)
 *   • دو کلید جداگانه برای زبان سرور و زبان محلی
 *   • سیستم فقط وقتی اعمال می‌شود که هیچ‌کدام تنظیم نشده باشد
 * ═══════════════════════════════════════════════════════════════
 */
public class LocaleHelper {

    private static final String PREFS               = "boom_locale";
    private static final String KEY_LOCAL_LANG      = "selected_language";   // انتخاب کاربر از داخل اپ
    private static final String KEY_USER_LANG       = "user_language";       // زبان ذخیره‌شده در سرور

    public static final String[] SUPPORTED = {"fa", "ar", "en", "fr", "it", "de"};
    public static final String DEFAULT = "fa";

    /* ═══════════════════════════════════════════════════════════
       ذخیره — دو نوع جداگانه
       ═══════════════════════════════════════════════════════════ */

    /** زبان محلی — وقتی کاربر از داخل اپ تغییر می‌دهد */
    public static void saveLanguage(Context context, String lang) {
        if (!isValid(lang)) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOCAL_LANG, lang).apply();
    }

    /** زبان کاربر از سرور — هنگام ورود موفق */
    public static void saveUserLanguage(Context context, String lang) {
        if (!isValid(lang)) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_LANG, lang).apply();
    }

    /* ═══════════════════════════════════════════════════════════
       خواندن — با ترتیب اولویت درست
       
       1) زبان کاربر از سرور
       2) زبان انتخاب‌شده محلی
       3) زبان سیستم
       4) fa
       ═══════════════════════════════════════════════════════════ */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // ۱) زبان کاربر از سرور
        String userLang = prefs.getString(KEY_USER_LANG, null);
        if (userLang != null && isValid(userLang)) {
            return userLang;
        }

        // ۲) زبان انتخاب‌شده محلی
        String localLang = prefs.getString(KEY_LOCAL_LANG, null);
        if (localLang != null && isValid(localLang)) {
            return localLang;
        }

        // ۳) زبان سیستم
        String systemLang = getSystemLanguage();
        if (systemLang != null) {
            return systemLang;
        }

        // ۴) fallback نهایی
        return DEFAULT;
    }

    /* ═══════════════════════════════════════════════════════════
       دریافت زبان سیستم (اگر در لیست پشتیبانی‌شده باشد)
       ═══════════════════════════════════════════════════════════ */
    private static String getSystemLanguage() {
        try {
            String code = Locale.getDefault().getLanguage();
            if (code == null) return null;
            code = code.toLowerCase(Locale.US).trim();
            if (isValid(code)) return code;
        } catch (Exception ignored) {}
        return null;
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
       forceLocale — اعمال فوری زبان روی Activity
       ═══════════════════════════════════════════════════════════ */
    public static void forceLocale(Activity activity, String lang) {
        if (activity == null) return;
        if (!isValid(lang)) lang = DEFAULT;

        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);

        activity.getResources().updateConfiguration(
                config,
                activity.getResources().getDisplayMetrics()
        );
    }

    /* ═══════════════════════════════════════════════════════════
       applyLocale — برای استفاده در attachBaseContext
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
       پاک کردن (هنگام logout)
       ═══════════════════════════════════════════════════════════ */
    public static void clearUserLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_USER_LANG).apply();
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
