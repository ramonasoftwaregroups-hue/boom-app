package ir.picassooads.boom.twa;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * ═══════════════════════════════════════════════════════════════
 * ThemeHelper — مدیریت تم (روشن/تیره/خودکار)
 * ═══════════════════════════════════════════════════════════════
 */
public class ThemeHelper {

    private static final String PREFS = "boom_theme";
    private static final String KEY_MODE = "theme_mode";

    public static final String MODE_LIGHT = "light";
    public static final String MODE_DARK = "dark";
    public static final String MODE_AUTO = "auto";

    /* ═══════════════════════════════════════════════════════════
       ذخیره و خواندن
       ═══════════════════════════════════════════════════════════ */
    public static void saveMode(Context context, String mode) {
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
       اعمال تم
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
       چک کردن تم فعلی
       ═══════════════════════════════════════════════════════════ */
    public static boolean isDark(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static String toggle(Context context) {
        String current = getMode(context);
        String next;
        if (MODE_DARK.equals(current)) {
            next = MODE_LIGHT;
        } else if (MODE_LIGHT.equals(current)) {
            next = MODE_AUTO;
        } else {
            next = MODE_DARK;
        }
        saveMode(context, next);
        applyMode(context, next);
        return next;
    }

    /* ═══════════════════════════════════════════════════════════
       چرخه‌ی ساده‌ی دو‌حالته (برای دکمه ماه/خورشید)
       ═══════════════════════════════════════════════════════════ */
    public static String toggleLightDark(Context context) {
        boolean isDark = isDark(context);
        String next = isDark ? MODE_LIGHT : MODE_DARK;
        saveMode(context, next);
        applyMode(context, next);
        return next;
    }
}
