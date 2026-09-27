package ir.picassooads.boom.twa;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * ═══════════════════════════════════════════════════════════════
 * ThemeHelper — مدیریت تم (روشن/تیره/خودکار)
 * 
 * v3 — اصلاحات:
 *   • ترتیب اولویت درست:
 *       1) تم کاربر از سرور  (user_theme)
 *       2) تم انتخاب‌شده محلی (theme_mode)
 *       3) تم سیستم
 *       4) auto (fallback نهایی)
 *   • دو کلید جداگانه برای تم سرور و تم محلی
 *   • سیستم فقط وقتی اعمال می‌شود که هیچ‌کدام تنظیم نشده باشد
 * ═══════════════════════════════════════════════════════════════
 */
public class ThemeHelper {

    private static final String PREFS           = "boom_theme";
    private static final String KEY_LOCAL_MODE  = "theme_mode";     // انتخاب کاربر از داخل اپ
    private static final String KEY_USER_THEME  = "user_theme";     // تم ذخیره‌شده در سرور

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK  = "dark";
    public static final String MODE_AUTO  = "auto";

    /* ═══════════════════════════════════════════════════════════
       ذخیره — دو نوع جداگانه
       ═══════════════════════════════════════════════════════════ */

    /** تم محلی — وقتی کاربر از داخل اپ تغییر می‌دهد */
    public static void saveMode(Context context, String mode) {
        if (!isValid(mode)) mode = MODE_AUTO;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LOCAL_MODE, mode).apply();
    }

    /** تم کاربر از سرور — هنگام ورود موفق */
    public static void saveUserTheme(Context context, String theme) {
        if (!isValid(theme)) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_THEME, theme).apply();
    }

    /* ═══════════════════════════════════════════════════════════
       خواندن — با ترتیب اولویت درست
       
       1) تم کاربر از سرور
       2) تم انتخاب‌شده محلی
       3) تم سیستم
       4) auto
       ═══════════════════════════════════════════════════════════ */
    public static String getMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // ۱) تم کاربر از سرور
        String userTheme = prefs.getString(KEY_USER_THEME, null);
        if (userTheme != null && isValid(userTheme)) {
            return userTheme;
        }

        // ۲) تم انتخاب‌شده محلی
        String localMode = prefs.getString(KEY_LOCAL_MODE, null);
        if (localMode != null && isValid(localMode)) {
            return localMode;
        }

        // ۳) تم سیستم
        String systemTheme = getSystemTheme(context);
        if (systemTheme != null) {
            return systemTheme;
        }

        // ۴) fallback نهایی
        return MODE_AUTO;
    }

    public static String getUserTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String t = prefs.getString(KEY_USER_THEME, null);
        return isValid(t) ? t : MODE_AUTO;
    }

    /* ═══════════════════════════════════════════════════════════
       تشخیص تم سیستم
       ═══════════════════════════════════════════════════════════ */
    private static String getSystemTheme(Context context) {
        try {
            int uiMode = context.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                return MODE_DARK;
            } else if (uiMode == android.content.res.Configuration.UI_MODE_NIGHT_NO) {
                return MODE_LIGHT;
            }
        } catch (Exception ignored) {}
        return null; // سیستم تنظیم خاصی ندارد → auto
    }

    public static boolean isValid(String mode) {
        if (mode == null) return false;
        return MODE_LIGHT.equals(mode) || MODE_DARK.equals(mode) || MODE_AUTO.equals(mode);
    }

    /* ═══════════════════════════════════════════════════════════
       اعمال تم روی کل اپ
       ═══════════════════════════════════════════════════════════ */
    public static void applyMode(Context context, String mode) {
        if (!isValid(mode)) mode = MODE_AUTO;

        int nightMode;
        switch (mode) {
            case MODE_LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case MODE_DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case MODE_AUTO:
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    public static void applySavedMode(Context context) {
        applyMode(context, getMode(context));
    }

    /* ═══════════════════════════════════════════════════════════
       applyModeAndRecreate — اعمال فوری روی Activity
       ═══════════════════════════════════════════════════════════ */
    public static void applyModeAndRecreate(Activity activity, String mode) {
        if (activity == null) return;
        if (!isValid(mode)) mode = MODE_AUTO;

        // ذخیره به عنوان تم محلی
        saveMode(activity, mode);

        int nightMode;
        switch (mode) {
            case MODE_LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case MODE_DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case MODE_AUTO:
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
        activity.recreate();
    }

    /* ═══════════════════════════════════════════════════════════
       چک کردن تم فعلی
       ═══════════════════════════════════════════════════════════ */
    public static boolean isDark(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /* ═══════════════════════════════════════════════════════════
       toggleLightDark — با Activity (اعمال فوری)
       ═══════════════════════════════════════════════════════════ */
    public static String toggleLightDark(Activity activity) {
        if (activity == null) return MODE_LIGHT;

        boolean isDark = isDark(activity);
        String next = isDark ? MODE_LIGHT : MODE_DARK;

        // ★ چون کاربر خودش تغییر داده، تم سرور را هم override می‌کنیم
        // تا در بار بعد، تم جدید کاربر اعمال شود
        saveUserTheme(activity, next);

        applyModeAndRecreate(activity, next);
        return next;
    }

    /* ═══════════════════════════════════════════════════════════
       پاک کردن (هنگام logout)
       ═══════════════════════════════════════════════════════════ */
    public static void clearUserTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_USER_THEME).apply();
    }
}
