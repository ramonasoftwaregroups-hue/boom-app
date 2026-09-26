package ir.picassooads.boom.twa;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * ═══════════════════════════════════════════════════════════════
 * ThemeHelper — مدیریت تم (روشن/تیره/خودکار)
 * 
 * v2 — اصلاحات:
 *   • اضافه شدن applyModeAndRecreate که تغییر تم را فوری روی Activity اعمال می‌کند
 *   • پشتیبانی از تنظیمات کاربر (اگر theme از سرور آمده باشد)
 *   • toggleLightDark حالا Activity می‌گیرد تا فوراً اعمال شود
 * ═══════════════════════════════════════════════════════════════
 */
public class ThemeHelper {

    private static final String PREFS = "boom_theme";
    private static final String KEY_MODE = "theme_mode";
    private static final String KEY_USER_THEME = "user_theme";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_AUTO = "auto";

    /* ═══════════════════════════════════════════════════════════
       ذخیره و خواندن
       ═══════════════════════════════════════════════════════════ */
    public static void saveMode(Context context, String mode) {
        if (mode == null) mode = MODE_AUTO;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MODE, mode).apply();
    }

    public static String getMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String mode = prefs.getString(KEY_MODE, MODE_AUTO);
        if (mode == null) mode = MODE_AUTO;
        return mode;
    }

    /* ═══════════════════════════════════════════════════════════
       ★ ذخیره/خواندن تم کاربر (از سرور آمده)
       
       این جدا از تم محلی است — کاربر می‌تواند از پنل وب
       تم دلخواهش را انتخاب کند و اپ هم از همان تبعیت کند.
       ═══════════════════════════════════════════════════════════ */
    public static void saveUserTheme(Context context, String theme) {
        if (theme == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_THEME, theme).apply();

        // ★ تم کاربر را به عنوان تم محلی هم ذخیره می‌کنیم
        saveMode(context, theme);
    }

    public static String getUserTheme(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_THEME, MODE_AUTO);
    }

    /* ═══════════════════════════════════════════════════════════
       اعمال تم روی کل اپ
       ═══════════════════════════════════════════════════════════ */
    public static void applyMode(Context context, String mode) {
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
       ★ applyModeAndRecreate — اعمال فوری روی Activity
       
       اگر Activity تغییر کرد، recreate می‌کند.
       برای دکمه‌ی toggle در Login و بقیه صفحات.
       ═══════════════════════════════════════════════════════════ */
    public static void applyModeAndRecreate(Activity activity, String mode) {
        if (activity == null) return;

        if (mode == null) mode = MODE_AUTO;

        // ذخیره
        saveMode(activity, mode);

        // اعمال روی AppCompatDelegate
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

        // ★ اگر AppCompatDelegate خودش recreate نکرد، خودمان می‌کنیم
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
       ★ toggleLightDark — با Activity (اعمال فوری)
       
       چرخه: dark → light → dark → ...
       (auto را در toggle استفاده نمی‌کنیم چون کاربر روی دکمه کلیک می‌کند)
       ═══════════════════════════════════════════════════════════ */
    public static String toggleLightDark(Activity activity) {
        if (activity == null) return MODE_LIGHT;

        boolean isDark = isDark(activity);
        String next = isDark ? MODE_LIGHT : MODE_DARK;

        applyModeAndRecreate(activity, next);
        return next;
    }

    /* ═══════════════════════════════════════════════════════════
       toggle — نسخه‌ی قدیمی (بدون Activity) برای سازگاری
       ═══════════════════════════════════════════════════════════ */
    public static String toggle(Context context) {
        String current = getMode(context);
        String next;
        if (MODE_DARK.equals(current)) {
            next = MODE_LIGHT;
        } else if (MODE_LIGHT.equals(current)) {
            next = MODE_DARK;
        } else {
            next = MODE_DARK;
        }
        saveMode(context, next);
        applyMode(context, next);
        return next;
    }
}
