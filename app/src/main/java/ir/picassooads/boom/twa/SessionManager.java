package ir.picassooads.boom.twa;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ═══════════════════════════════════════════════════════════════
 * SessionManager — ذخیره و بازیابی توکن دستگاه + اطلاعات کاربر
 * 
 * از SharedPreferences استفاده می‌کند (بدون نیاز به دیتابیس یا فایل).
 * 
 * v2 — اضافه شدن چک انقضای توکن (isTokenExpired + isSessionValid)
 * ═══════════════════════════════════════════════════════════════
 */
public class SessionManager {

    private static final String TAG = "SessionManager";

    private static final String PREF_NAME        = "boom_session";
    private static final String KEY_TOKEN        = "device_token";
    private static final String KEY_EXPIRES_AT   = "token_expires_at";
    private static final String KEY_USER_ID      = "user_id";
    private static final String KEY_USERNAME     = "username";
    private static final String KEY_FIRST_NAME   = "first_name";
    private static final String KEY_LAST_NAME    = "last_name";
    private static final String KEY_FULL_NAME    = "full_name";
    private static final String KEY_PHONE        = "phone";
    private static final String KEY_EMAIL        = "email";
    private static final String KEY_LANGUAGE     = "language";
    private static final String KEY_THEME        = "theme";
    private static final String KEY_DEVICE_NAME  = "device_name";
    private static final String KEY_LAST_LOGIN   = "last_login_at";

    /** فرمت تاریخی که سرور برمی‌گرداند */
    private static final String SERVER_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    /* ═══════════════════════════════════════════════════════════
       ذخیره اطلاعات پس از ورود موفق
       ═══════════════════════════════════════════════════════════ */
    public void saveSession(String token,
                            String expiresAt,
                            int userId,
                            String username,
                            String firstName,
                            String lastName,
                            String fullName,
                            String phone,
                            String email,
                            String language,
                            String theme,
                            String deviceName) {

        editor.putString(KEY_TOKEN,       token == null ? "" : token);
        editor.putString(KEY_EXPIRES_AT,  expiresAt == null ? "" : expiresAt);
        editor.putInt(KEY_USER_ID,        userId);
        editor.putString(KEY_USERNAME,    username == null ? "" : username);
        editor.putString(KEY_FIRST_NAME,  firstName == null ? "" : firstName);
        editor.putString(KEY_LAST_NAME,   lastName == null ? "" : lastName);
        editor.putString(KEY_FULL_NAME,   fullName == null ? "" : fullName);
        editor.putString(KEY_PHONE,       phone == null ? "" : phone);
        editor.putString(KEY_EMAIL,       email == null ? "" : email);
        editor.putString(KEY_LANGUAGE,    language == null ? "fa" : language);
        editor.putString(KEY_THEME,       theme == null ? "auto" : theme);
        editor.putString(KEY_DEVICE_NAME, deviceName == null ? "" : deviceName);
        editor.putLong(KEY_LAST_LOGIN,    System.currentTimeMillis());
        editor.apply();
    }

    /* ═══════════════════════════════════════════════════════════
       به‌روزرسانی توکن (پس از refresh)
       ═══════════════════════════════════════════════════════════ */
    public void updateToken(String token, String expiresAt) {
        editor.putString(KEY_TOKEN, token == null ? "" : token);
        editor.putString(KEY_EXPIRES_AT, expiresAt == null ? "" : expiresAt);
        editor.apply();
    }

    /* ═══════════════════════════════════════════════════════════
       به‌روزرسانی اطلاعات پایه کاربر
       ═══════════════════════════════════════════════════════════ */
    public void updateUserInfo(String fullName, String language, String theme) {
        if (fullName != null) editor.putString(KEY_FULL_NAME, fullName);
        if (language != null) editor.putString(KEY_LANGUAGE, language);
        if (theme != null)    editor.putString(KEY_THEME, theme);
        editor.apply();
    }

    /* ═══════════════════════════════════════════════════════════
       خواندن اطلاعات
       ═══════════════════════════════════════════════════════════ */
    public String  getToken()       { return prefs.getString(KEY_TOKEN, ""); }
    public String  getExpiresAt()   { return prefs.getString(KEY_EXPIRES_AT, ""); }
    public int     getUserId()      { return prefs.getInt(KEY_USER_ID, 0); }
    public String  getUsername()    { return prefs.getString(KEY_USERNAME, ""); }
    public String  getFirstName()   { return prefs.getString(KEY_FIRST_NAME, ""); }
    public String  getLastName()    { return prefs.getString(KEY_LAST_NAME, ""); }
    public String  getFullName()    { return prefs.getString(KEY_FULL_NAME, ""); }
    public String  getPhone()       { return prefs.getString(KEY_PHONE, ""); }
    public String  getEmail()       { return prefs.getString(KEY_EMAIL, ""); }
    public String  getLanguage()    { return prefs.getString(KEY_LANGUAGE, "fa"); }
    public String  getTheme()       { return prefs.getString(KEY_THEME, "auto"); }
    public String  getDeviceName()  { return prefs.getString(KEY_DEVICE_NAME, ""); }
    public long    getLastLogin()   { return prefs.getLong(KEY_LAST_LOGIN, 0L); }

    /* ═══════════════════════════════════════════════════════════
       چک لاگین بودن (فقط وجود توکن)
       ═══════════════════════════════════════════════════════════ */
    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && token.length() >= 32 && getUserId() > 0;
    }

    /* ═══════════════════════════════════════════════════════════
       ★ NEW: چک انقضای توکن
       
       تاریخ انقضا از سرور با فرمت "yyyy-MM-dd HH:mm:ss" می‌آید.
       اگر تاریخ خالی یا نامعتبر بود، محافظه‌کارانه "منقضی" در نظر می‌گیریم.
       ═══════════════════════════════════════════════════════════ */
    public boolean isTokenExpired() {
        String expiresAt = getExpiresAt();
        if (expiresAt == null || expiresAt.isEmpty()) {
            // تاریخ انقضا نداریم → محافظه‌کارانه منقضی فرض کن
            return true;
        }

        try {
            SimpleDateFormat fmt = new SimpleDateFormat(SERVER_DATE_FORMAT, Locale.US);
            fmt.setLenient(false);
            Date exp = fmt.parse(expiresAt);
            if (exp == null) return true;

            // ۶۰ ثانیه حاشیه‌ی امن قبل از انقضا
            long safetyMarginMs = 60_000L;
            return exp.getTime() <= (System.currentTimeMillis() + safetyMarginMs);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse expires_at: " + expiresAt, e);
            return true;
        }
    }

    /* ═══════════════════════════════════════════════════════════
       ★ NEW: چک کامل اعتبار سشن
       
       توکن هست + user_id > 0 + توکن منقضی نشده
       این متد را در SplashActivity، LoginActivity و MainActivity استفاده کن.
       ═══════════════════════════════════════════════════════════ */
    public boolean isSessionValid() {
        return isLoggedIn() && !isTokenExpired();
    }

    /* ═══════════════════════════════════════════════════════════
       پاک کردن سشن (logout)
       ═══════════════════════════════════════════════════════════ */
    public void clear() {
        editor.clear();
        editor.apply();
    }
}
