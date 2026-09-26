package ir.picassooads.boom.twa;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ═══════════════════════════════════════════════════════════════
 * ApiClient — ارتباط با panel/api/mobile.php
 * 
 * از HttpURLConnection استفاده می‌کند (بدون کتابخانه‌ی خارجی).
 * همه‌ی متدها async هستند و نتیجه را در callback برمی‌گردانند.
 * ═══════════════════════════════════════════════════════════════
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    /** آدرس پایه‌ی API */
    public static final String API_URL =
            "https://boom.picassooads.ir/panel/api/mobile.php";

    /** زمان انتظار پیش‌فرض */
    private static final int TIMEOUT_CONNECT_MS = 15000;
    private static final int TIMEOUT_READ_MS    = 30000;

    /** نسخه‌ی اپ — اینجا را با هر انتشار به‌روز کن */
    public static final String APP_VERSION = "1.0.0";

    /** Thread pool برای درخواست‌ها */
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    /** Handler برای برگشت به main thread */
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Context appContext;

    public ApiClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /* ═══════════════════════════════════════════════════════════
       CALLBACK
       ═══════════════════════════════════════════════════════════ */
    public interface ApiCallback {
        /**
         * @param success آیا درخواست موفق بود
         * @param response کل پاسخ JSON (در صورت موفقیت یا خطای منطقی)
         * @param errorMessage پیام خطا (اگر success=false)
         */
        void onResult(boolean success, JSONObject response, String errorMessage);
    }

    /* ═══════════════════════════════════════════════════════════
       GENERIC POST — پایه‌ی همه‌ی درخواست‌ها
       ═══════════════════════════════════════════════════════════ */
    private void post(final JSONObject body, final String bearerToken, final ApiCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(API_URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(TIMEOUT_CONNECT_MS);
                    conn.setReadTimeout(TIMEOUT_READ_MS);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                    conn.setRequestProperty("X-App-Version", APP_VERSION);
                    conn.setRequestProperty("X-Device-Name", getDeviceName());
                    conn.setRequestProperty("X-Device-Fingerprint", getFingerprint());

                    if (bearerToken != null && !bearerToken.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
                    }

                    // نوشتن body
                    String bodyStr = body.toString();
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bodyStr.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }

                    int httpCode = conn.getResponseCode();

                    // خواندن پاسخ (اگر خطا 500 باشد از errorStream)
                    InputStream is;
                    if (httpCode >= 200 && httpCode < 400) {
                        is = conn.getInputStream();
                    } else {
                        is = conn.getErrorStream();
                        if (is == null) is = conn.getInputStream();
                    }

                    String responseStr = readStream(is);
                    Log.d(TAG, "HTTP " + httpCode + " ← " + responseStr);

                    JSONObject json = null;
                    try {
                        if (responseStr != null && !responseStr.isEmpty()) {
                            json = new JSONObject(responseStr);
                        }
                    } catch (JSONException je) {
                        Log.e(TAG, "JSON parse error: " + je.getMessage() + " body=" + responseStr);
                        finalError(callback, "پاسخ سرور نامعتبر است");
                        return;
                    }

                    if (json == null) {
                        finalError(callback, "پاسخ خالی از سرور");
                        return;
                    }

                    boolean success = json.optBoolean("success", false);

                    if (success) {
                        finalResult(callback, true, json, null);
                    } else {
                        String err = json.optString("error", "");
                        if (err.isEmpty()) err = json.optString("message", "خطای نامشخص");
                        finalResult(callback, false, json, err);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "IOException: " + e.getMessage());
                    finalError(callback, "خطای ارتباط با سرور: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "Exception: " + e.getMessage());
                    finalError(callback, "خطای غیرمنتظره: " + e.getMessage());
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       خواندن InputStream به String
       ═══════════════════════════════════════════════════════════ */
    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private void finalResult(final ApiCallback cb,
                             final boolean success,
                             final JSONObject json,
                             final String error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (cb != null) cb.onResult(success, json, error);
            }
        });
    }

    private void finalError(final ApiCallback cb, final String error) {
        finalResult(cb, false, null, error);
    }

    /* ═══════════════════════════════════════════════════════════
       اطلاعات دستگاه
       ═══════════════════════════════════════════════════════════ */
    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) return s;
        return Character.toUpperCase(first) + s.substring(1);
    }

    private String getFingerprint() {
        // یک شناسه‌ی سبک از مشخصات دستگاه — نه کامل ولی کافی
        return Build.MANUFACTURER + "/" + Build.MODEL + "/" + Build.DEVICE;
    }

    /* ═══════════════════════════════════════════════════════════
       ═══════════════════════════════════════════════════════════
       PUBLIC METHODS — اکشن‌های API
       ═══════════════════════════════════════════════════════════
       ═══════════════════════════════════════════════════════════ */

    /** PING — تست سلامت API */
    public void ping(ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "ping");
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۱) ورود با رمز عبور
       ───────────────────────────────────────────── */
    public void loginWithPassword(String login, String password, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "login_password");
            body.put("login", login);
            body.put("password", password);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۲) درخواست کد OTP برای ورود
       ───────────────────────────────────────────── */
    public void loginRequestOtp(String login, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "login_request_otp");
            body.put("login", login);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۳) تأیید کد OTP
       ───────────────────────────────────────────── */
    public void loginVerifyOtp(String otpToken, String code, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "login_verify_otp");
            body.put("otp_token", otpToken);
            body.put("code", code);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۴) بررسی نام کاربری
       ───────────────────────────────────────────── */
    public void checkUsername(String username, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "check_username");
            body.put("username", username);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۵) ثبت‌نام — درخواست کد
       ───────────────────────────────────────────── */
    public void registerRequestOtp(String firstName,
                                   String lastName,
                                   String username,
                                   String phone,
                                   String password,
                                   boolean acceptTerms,
                                   ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "register_request_otp");
            body.put("first_name", firstName);
            body.put("last_name", lastName);
            body.put("username", username);
            body.put("phone", phone);
            body.put("password", password);
            body.put("accept_terms", acceptTerms ? 1 : 0);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۶) ثبت‌نام — تأیید کد
       ───────────────────────────────────────────── */
    public void registerVerifyOtp(String otpToken, String code, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "register_verify_otp");
            body.put("otp_token", otpToken);
            body.put("code", code);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۷) فراموشی رمز — درخواست کد
       ───────────────────────────────────────────── */
    public void forgotRequestOtp(String login, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "forgot_request_otp");
            body.put("login", login);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۸) فراموشی رمز — تأیید کد
       ───────────────────────────────────────────── */
    public void forgotVerifyOtp(String otpToken, String code, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "forgot_verify_otp");
            body.put("otp_token", otpToken);
            body.put("code", code);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۹) فراموشی رمز — تنظیم رمز جدید
       ───────────────────────────────────────────── */
    public void forgotReset(String resetToken, String password, String passwordConfirm, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "forgot_reset");
            body.put("reset_token", resetToken);
            body.put("password", password);
            body.put("password_confirm", passwordConfirm);
            post(body, null, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۱۰) ME — اطلاعات کاربر فعلی
       ───────────────────────────────────────────── */
    public void me(String token, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "me");
            post(body, token, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۱۱) REFRESH TOKEN
       ───────────────────────────────────────────── */
    public void refreshToken(String token, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "refresh_token");
            post(body, token, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ─────────────────────────────────────────────
       ۱۲) LOGOUT
       ───────────────────────────────────────────── */
    public void logout(String token, boolean allDevices, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "logout");
            body.put("all_devices", allDevices ? 1 : 0);
            post(body, token, callback);
        } catch (JSONException e) {
            finalError(callback, "خطای ساخت درخواست");
        }
    }

    /* ═══════════════════════════════════════════════════════════
       HELPERS — استخراج اطلاعات از پاسخ
       ═══════════════════════════════════════════════════════════ */

    /** استخراج پیام خطا از پاسخ */
    public static String getErrorMessage(JSONObject response, String fallback) {
        if (response == null) return fallback;
        String msg = response.optString("error", "");
        if (msg.isEmpty()) msg = response.optString("message", "");
        if (msg.isEmpty()) msg = fallback;
        return msg;
    }

    /** ساخت یک رشته‌ی نمایشی از JSON (برای debug) */
    public static String prettyPrint(JSONObject json) {
        if (json == null) return "null";
        try {
            return json.toString(2);
        } catch (JSONException e) {
            return json.toString();
        }
    }
}
