package ir.picassooads.boom.twa;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.concurrent.Executor;

/**
 * ═══════════════════════════════════════════════════════════════
 * LoginActivity — صفحه‌ی ورود نیتیو
 * 
 * v7 — منطق کامل بیومتریک:
 *   • Auto-fill username از بار قبل
 *   • Auto-trigger بیومتریک اگر username پر بود و بیومتریک فعال
 *   • دکمه بیومتریک با چک‌های کامل:
 *       - بدون username → پیام
 *       - بدون device_key → پیام «فعال نیست»
 *       - با device_key → BiometricPrompt → سرور → ورود
 *   • بعد از ورود با رمز/OTP:
 *       - اگر بیومتریک فعال نیست → دیالوگ «فعال کن؟»
 *       - اگر بله → BiometricPrompt → سرور → ذخیره device_key
 *   • ذخیره‌ی جداگانه‌ی زبان/تم از سرور
 * ═══════════════════════════════════════════════════════════════
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private static final String PREFS_LOCAL       = "boom_prefs";
    private static final String KEY_LAST_USERNAME = "last_username";

    /* Views */
    private TabLayout loginTabs;
    private LinearLayout passwordMode, otpMode, errorAlert;
    private TextInputLayout loginInputLayout, passwordLayout;
    private TextInputEditText loginInput, passwordInput;
    private MaterialButton loginBtn, verifyOtpBtn, resendBtn, backBtn, forgotBtn, registerCta;
    private TextView errorText, otpSub, langCode;
    private FrameLayout themeBtn, langBtn, biometricBtn;
    private ImageView themeIcon, brandLogo, picassoLogo;
    private EditText[] otpBoxes = new EditText[6];

    /* State */
    private ApiClient api;
    private SessionManager session;
    private BiometricStorage bioStorage;
    private String currentLang;
    private String currentOtpToken = "";
    private boolean otpSent = false;
    private CountDownTimer resendTimer;

    /* Biometric */
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo biometricPromptInfo;
    private enum BioMode { LOGIN, ENABLE, DISABLED }
    private BioMode pendingBioMode = BioMode.DISABLED;
    private String pendingLoginUsername = "";
    private String pendingToken = "";
    private String pendingFullName = "";
    private int pendingUserId = 0;

    /* ═══════════════════════════════════════════════════════════
       attachBaseContext
       ═══════════════════════════════════════════════════════════ */
    @Override
    protected void attachBaseContext(Context newBase) {
        String lang = LocaleHelper.getLanguage(newBase);
        Context ctx = LocaleHelper.applyLocale(newBase, lang);
        super.attachBaseContext(ctx);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applySavedMode(this);
        super.onCreate(savedInstanceState);

        currentLang = LocaleHelper.getLanguage(this);

        session = new SessionManager(this);
        if (session.isLoggedIn()) session.clear();

        bioStorage = new BiometricStorage(this);

        try {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } catch (Exception e) { Log.w(TAG, "clear cookies", e); }

        setContentView(R.layout.activity_login);

        api = new ApiClient(this);

        bindViews();
        setupTopActions();
        setupTabs();
        setupFields();
        setupButtons();
        setupOtpBoxes();
        setupBiometric();

        LocaleHelper.applyFontToViewTree(this,
                findViewById(R.id.loginRoot), currentLang);

        langCode.setText(currentLang.toUpperCase());
        updateThemeIcon();
        loadBrandLogo();
        loadPicassoFooter();

        // ★ پر کردن username و auto-trigger بیومتریک
        restoreSavedUsername();
        maybeAutoBiometric();
    }

    /* ═══════════════════════════════════════════════════════════
       BIND
       ═══════════════════════════════════════════════════════════ */
    private void bindViews() {
        loginTabs = findViewById(R.id.loginTabs);
        passwordMode = findViewById(R.id.passwordMode);
        otpMode = findViewById(R.id.otpMode);
        errorAlert = findViewById(R.id.errorAlert);
        errorText = findViewById(R.id.errorText);
        loginInputLayout = findViewById(R.id.loginInputLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        loginInput = findViewById(R.id.loginInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);
        verifyOtpBtn = findViewById(R.id.verifyOtpBtn);
        resendBtn = findViewById(R.id.resendBtn);
        backBtn = findViewById(R.id.backBtn);
        forgotBtn = findViewById(R.id.forgotBtn);
        registerCta = findViewById(R.id.registerCta);
        otpSub = findViewById(R.id.otpSub);
        themeBtn = findViewById(R.id.themeBtn);
        langBtn = findViewById(R.id.langBtn);
        biometricBtn = findViewById(R.id.biometricBtn);
        themeIcon = findViewById(R.id.themeIcon);
        langCode = findViewById(R.id.langCode);
        brandLogo = findViewById(R.id.brandLogo);
        picassoLogo = findViewById(R.id.picassoLogo);

        otpBoxes[0] = findViewById(R.id.otp1);
        otpBoxes[1] = findViewById(R.id.otp2);
        otpBoxes[2] = findViewById(R.id.otp3);
        otpBoxes[3] = findViewById(R.id.otp4);
        otpBoxes[4] = findViewById(R.id.otp5);
        otpBoxes[5] = findViewById(R.id.otp6);
    }

    /* ═══════════════════════════════════════════════════════════
       TOP ACTIONS
       ═══════════════════════════════════════════════════════════ */
    private void setupTopActions() {
        themeBtn.setOnClickListener(v -> {
            ThemeHelper.toggleLightDark(this);
            updateThemeIcon();
        });
        langBtn.setOnClickListener(v -> showLanguageDialog());
    }

    private void updateThemeIcon() {
        boolean dark = ThemeHelper.isDark(this);
        themeIcon.setImageResource(dark ? R.drawable.ic_sun : R.drawable.ic_moon);
    }

    private void showLanguageDialog() {
        final String[] names = {
                "فارسی", "العربية", "English", "Français", "Italiano", "Deutsch"
        };
        final String[] codes = LocaleHelper.SUPPORTED;

        new AlertDialog.Builder(this, R.style.BoomDialogTheme)
                .setTitle(R.string.language_choose)
                .setItems(names, (d, which) -> {
                    String lang = codes[which];
                    if (lang.equals(currentLang)) return;
                    LocaleHelper.saveLanguage(this, lang);
                    LocaleHelper.forceLocale(this, lang);
                    recreate();
                })
                .show();
    }

    /* ═══════════════════════════════════════════════════════════
       BIOMETRIC — Setup
       ═══════════════════════════════════════════════════════════ */
    private void setupBiometric() {
        Executor executor = ContextCompat.getMainExecutor(this);

        biometricPrompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        Log.w(TAG, "Bio error " + errorCode + ": " + errString);

                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || errorCode == BiometricPrompt.ERROR_CANCELED) {
                            // لغو شد
                            if (pendingBioMode == BioMode.ENABLE) {
                                // کاربر لغو کرد → ورود عادی ادامه
                                pendingBioMode = BioMode.DISABLED;
                                proceedAfterLogin();
                            }
                            return;
                        }
                        // خطای دیگر
                        if (pendingBioMode == BioMode.LOGIN) {
                            showError(getString(R.string.biometric_error));
                        } else if (pendingBioMode == BioMode.ENABLE) {
                            pendingBioMode = BioMode.DISABLED;
                            proceedAfterLogin();
                        }
                        pendingBioMode = BioMode.DISABLED;
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        Log.d(TAG, "Bio success, mode=" + pendingBioMode);

                        if (pendingBioMode == BioMode.LOGIN) {
                            performBiometricServerLogin();
                        } else if (pendingBioMode == BioMode.ENABLE) {
                            performBiometricServerRegister();
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        Log.d(TAG, "Bio failed (retry)");
                    }
                });
    }

    /* ═══════════════════════════════════════════════════════════
       BIOMETRIC — چک پشتیبانی
       ═══════════════════════════════════════════════════════════ */
    private boolean isBiometricSupported() {
        BiometricManager bm = BiometricManager.from(this);
        int canAuth = bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private String getBiometricUnavailableMessage(int canAuth) {
        switch (canAuth) {
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                return getString(R.string.biometric_not_available);
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                return getString(R.string.biometric_error);
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                return getString(R.string.biometric_not_enrolled);
            default:
                return getString(R.string.biometric_not_available);
        }
    }

    /* ═══════════════════════════════════════════════════════════
       BIOMETRIC — دکمه (کلیک دستی)
       ═══════════════════════════════════════════════════════════ */
    private void onBiometric() {
        // ۱) چک پشتیبانی
        if (!isBiometricSupported()) {
            int canAuth = BiometricManager.from(this).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                            | BiometricManager.Authenticators.BIOMETRIC_WEAK);
            Toast.makeText(this, getBiometricUnavailableMessage(canAuth),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // ۲) چک username
        String username = text(loginInput);
        if (username.isEmpty()) {
            showError(getString(R.string.bio_enter_username_first));
            loginInput.requestFocus();
            return;
        }

        // ۳) چک فعال بودن بیومتریک برای این کاربر
        if (!bioStorage.isEnabledFor(username)) {
            // username هست ولی بیومتریک قبلاً فعال نشده
            showError(getString(R.string.bio_not_enabled_for_user));
            return;
        }

        // ۴) همه‌چیز OK → prompt
        pendingBioMode = BioMode.LOGIN;
        pendingLoginUsername = username;
        showBiometricPrompt(
                getString(R.string.biometric_title),
                getString(R.string.biometric_subtitle)
        );
    }

    /* ═══════════════════════════════════════════════════════════
       BIOMETRIC — نمایش Prompt
       ═══════════════════════════════════════════════════════════ */
    private void showBiometricPrompt(String title, String subtitle) {
        biometricPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(getString(R.string.biometric_cancel))
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setConfirmationRequired(false)
                .build();

        try {
            biometricPrompt.authenticate(biometricPromptInfo);
        } catch (Exception e) {
            Log.e(TAG, "authenticate failed", e);
            Toast.makeText(this, R.string.biometric_error, Toast.LENGTH_SHORT).show();
            pendingBioMode = BioMode.DISABLED;
        }
    }

    /* ═══════════════════════════════════════════════════════════
       BIOMETRIC — auto-trigger هنگام باز شدن
       ═══════════════════════════════════════════════════════════ */
    private void maybeAutoBiometric() {
        String username = text(loginInput);
        if (username.isEmpty()) return;
        if (!bioStorage.isEnabledFor(username)) return;
        if (!isBiometricSupported()) return;

        // کمی تأخیر تا صفحه انیمیشنش تمام شود
        loginInput.postDelayed(() -> {
            if (isFinishing()) return;
            pendingBioMode = BioMode.LOGIN;
            pendingLoginUsername = username;
            showBiometricPrompt(
                    getString(R.string.biometric_title),
                    getString(R.string.bio_auto_login_hint)
            );
        }, 600);
    }

    /* ═══════════════════════════════════════════════════════════
       BIOMETRIC — درخواست ورود با device_key
       ═══════════════════════════════════════════════════════════ */
    private void performBiometricServerLogin() {
        String deviceKey = bioStorage.getDeviceKey();
        if (deviceKey == null || deviceKey.isEmpty()) {
            bioStorage.clear();
            showError(getString(R.string.bio_not_enabled_for_user));
            pendingBioMode = BioMode.DISABLED;
            return;
        }

        setLoading(loginBtn, true, getString(R.string.bio_signing_in));
        hideError();

        api.biometricLogin(deviceKey, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                setLoading(loginBtn, false, getString(R.string.login_button));
                pendingBioMode = BioMode.DISABLED;

                if (!success || response == null) {
                    // device_key نامعتبر → پاکش کن
                    bioStorage.clear();
                    showError(errorMessage != null ? errorMessage
                            : getString(R.string.bio_invalid_key));
                    return;
                }
                // ورود موفق
                handleLoginSuccess(response);
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       BIOMETRIC — درخواست فعال‌سازی روی سرور
       ═══════════════════════════════════════════════════════════ */
    private void performBiometricServerRegister() {
        if (pendingToken.isEmpty() || pendingLoginUsername.isEmpty()) {
            pendingBioMode = BioMode.DISABLED;
            proceedAfterLogin();
            return;
        }

        api.biometricRegister(pendingToken, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                if (success && response != null) {
                    String deviceKey = response.optString("device_key", "");
                    if (!deviceKey.isEmpty()) {
                        bioStorage.save(
                                deviceKey,
                                pendingLoginUsername,
                                pendingUserId,
                                android.os.Build.MODEL
                        );
                        Toast.makeText(LoginActivity.this,
                                R.string.bio_enabled_toast,
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // شکست سرور → بی‌سروصدا رد شو
                    Log.w(TAG, "biometric_register failed: " + errorMessage);
                }
                pendingBioMode = BioMode.DISABLED;
                proceedAfterLogin();
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       TABS
       ═══════════════════════════════════════════════════════════ */
    private void setupTabs() {
        loginTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    passwordMode.setVisibility(View.VISIBLE);
                    otpMode.setVisibility(View.GONE);
                    otpSent = false;
                } else {
                    passwordMode.setVisibility(View.GONE);
                    otpMode.setVisibility(View.VISIBLE);
                }
                hideError();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    /* ═══════════════════════════════════════════════════════════
       FIELDS
       ═══════════════════════════════════════════════════════════ */
    private void setupFields() {
        TextWatcher clearError = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                hideError();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        loginInput.addTextChangedListener(clearError);
        passwordInput.addTextChangedListener(clearError);
    }

    /* ═══════════════════════════════════════════════════════════
       BUTTONS
       ═══════════════════════════════════════════════════════════ */
    private void setupButtons() {
        loginBtn.setOnClickListener(v -> doPasswordLogin());

        verifyOtpBtn.setOnClickListener(v -> {
            if (otpSent) doVerifyOtp();
            else doRequestOtp();
        });

        resendBtn.setOnClickListener(v -> doRequestOtp());

        backBtn.setOnClickListener(v -> {
            otpSent = false;
            clearOtpBoxes();
            doRequestOtp();
        });

        forgotBtn.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
        });

        registerCta.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        biometricBtn.setOnClickListener(v -> onBiometric());
    }

    /* ═══════════════════════════════════════════════════════════
       OTP BOXES
       ═══════════════════════════════════════════════════════════ */
    private void setupOtpBoxes() {
        for (int i = 0; i < otpBoxes.length; i++) {
            final int idx = i;
            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    if (s.length() == 1 && idx < otpBoxes.length - 1) {
                        otpBoxes[idx + 1].requestFocus();
                    }
                    if (getOtpCode().length() == 6 && otpSent) doVerifyOtp();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            otpBoxes[i].setOnKeyListener((view, keyCode, event) -> {
                if (keyCode == android.view.KeyEvent.KEYCODE_DEL
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && otpBoxes[idx].getText().length() == 0
                        && idx > 0) {
                    otpBoxes[idx - 1].requestFocus();
                    otpBoxes[idx - 1].setText("");
                    return true;
                }
                return false;
            });
        }
    }

    private String getOtpCode() {
        StringBuilder sb = new StringBuilder();
        for (EditText e : otpBoxes) sb.append(e.getText().toString());
        return sb.toString();
    }

    private void clearOtpBoxes() {
        for (EditText e : otpBoxes) e.setText("");
        otpBoxes[0].requestFocus();
    }

    /* ═══════════════════════════════════════════════════════════
       PASSWORD LOGIN
       ═══════════════════════════════════════════════════════════ */
    private void doPasswordLogin() {
        String login = text(loginInput);
        String pass = text(passwordInput);

        if (login.isEmpty()) { showError(getString(R.string.login_error_empty_login)); return; }
        if (pass.isEmpty())  { showError(getString(R.string.login_error_empty_password)); return; }

        setLoading(loginBtn, true, getString(R.string.login_loading));
        hideError();

        api.loginWithPassword(login, pass, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                setLoading(loginBtn, false, getString(R.string.login_button));
                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage
                            : getString(R.string.login_error_invalid_credentials));
                    return;
                }
                handleLoginSuccess(response);
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       OTP
       ═══════════════════════════════════════════════════════════ */
    private void doRequestOtp() {
        String login = text(loginInput);
        if (login.isEmpty()) { showError(getString(R.string.login_error_empty_login)); return; }

        setLoading(verifyOtpBtn, true, getString(R.string.login_sending_code));
        hideError();

        api.loginRequestOtp(login, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                setLoading(verifyOtpBtn, false,
                        otpSent ? getString(R.string.login_verify)
                                : getString(R.string.login_send_code));
                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage
                            : getString(R.string.login_error_sms_failed));
                    return;
                }
                currentOtpToken = response.optString("otp_token", "");
                if (currentOtpToken.isEmpty()) {
                    showError(getString(R.string.login_info_generic_sent));
                    return;
                }
                otpSent = true;
                String masked = response.optString("phone_masked", "");
                otpSub.setText(getString(R.string.login_info_otp_sent, masked));
                verifyOtpBtn.setText(R.string.login_verify);
                clearOtpBoxes();
                startResendCooldown(60);
            }
        });
    }

    private void doVerifyOtp() {
        String code = getOtpCode();
        if (code.length() != 6) {
            showError(getString(R.string.login_error_empty_code));
            return;
        }

        setLoading(verifyOtpBtn, true, getString(R.string.login_verifying));
        hideError();

        api.loginVerifyOtp(currentOtpToken, code, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                setLoading(verifyOtpBtn, false, getString(R.string.login_verify));
                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage
                            : getString(R.string.login_error_invalid_code));
                    clearOtpBoxes();
                    return;
                }
                handleLoginSuccess(response);
            }
        });
    }

    private void startResendCooldown(int seconds) {
        if (resendTimer != null) resendTimer.cancel();
        resendBtn.setEnabled(false);
        resendTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override public void onTick(long ms) {
                int left = (int) (ms / 1000);
                resendBtn.setText(getString(R.string.login_resend) + " (" + left + ")");
            }
            @Override public void onFinish() {
                resendBtn.setEnabled(true);
                resendBtn.setText(R.string.login_resend);
            }
        }.start();
    }

    /* ═══════════════════════════════════════════════════════════
       LOGIN SUCCESS — با چک بیومتریک
       ═══════════════════════════════════════════════════════════ */
    private void handleLoginSuccess(JSONObject response) {
        try {
            String token = response.optString("token", "");
            String expiresAt = response.optString("expires_at", "");
            JSONObject user = response.optJSONObject("user");

            if (user == null || token.isEmpty()) {
                showError(getString(R.string.login_error_server));
                return;
            }

            int userId = user.optInt("id", 0);
            String username = user.optString("username", "");
            String fullName = user.optString("full_name", "");
            String lang = user.optString("language", currentLang);
            String theme = user.optString("theme", "auto");

            // ★ ذخیره‌ی جداگانه‌ی زبان و تم از سرور
            if (LocaleHelper.isValid(lang)) {
                LocaleHelper.saveUserLanguage(this, lang);
                LocaleHelper.forceLocale(this, lang);
            }
            if (theme != null && !theme.isEmpty()) {
                ThemeHelper.saveUserTheme(this, theme);
            }

            // ★ ذخیره‌ی username برای auto-fill
            if (!username.isEmpty()) {
                try {
                    getSharedPreferences(PREFS_LOCAL, MODE_PRIVATE)
                            .edit()
                            .putString(KEY_LAST_USERNAME, username)
                            .apply();
                } catch (Exception ignored) {}
            }

            // ★ اطلاعات برای ادامه‌ی جریان
            pendingToken = token;
            pendingFullName = fullName;
            pendingUserId = userId;
            pendingLoginUsername = username;

            // ★ آیا بیومتریک از سرور آمده؟
            boolean serverHasBio = response.optBoolean("biometric_enabled", false);
            boolean localHasBio = bioStorage.isEnabledFor(username);

            // اگر سرور می‌گوید کاربر بیومتریک دارد ولی ما محلی نداریم (مثلاً دستگاه جدید):
            // نیازی به فعال‌سازی نیست، مستقیم برو
            if (serverHasBio && !localHasBio) {
                proceedAfterLogin();
                return;
            }

            // اگر محلی داریم → برو (بیومتریک قبلاً فعال شده)
            if (localHasBio) {
                proceedAfterLogin();
                return;
            }

            // ★ بیومتریک فعال نیست → دیالوگ فعال‌سازی
            if (isBiometricSupported()) {
                askEnableBiometric();
            } else {
                proceedAfterLogin();
            }

        } catch (Exception e) {
            Log.e(TAG, "handleLoginSuccess", e);
            showError(getString(R.string.login_error_server));
        }
    }

    /* ═══════════════════════════════════════════════════════════
       ASK ENABLE — دیالوگ فعال‌سازی بیومتریک
       ═══════════════════════════════════════════════════════════ */
    private void askEnableBiometric() {
        new AlertDialog.Builder(this, R.style.BoomDialogTheme)
                .setTitle(R.string.bio_enable_prompt_title)
                .setMessage(R.string.bio_enable_prompt_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.bio_enable_yes, (d, w) -> {
                    pendingBioMode = BioMode.ENABLE;
                    showBiometricPrompt(
                            getString(R.string.bio_enable_prompt_title),
                            getString(R.string.bio_enable_prompt_msg)
                    );
                })
                .setNegativeButton(R.string.bio_enable_later, (d, w) -> {
                    pendingBioMode = BioMode.DISABLED;
                    proceedAfterLogin();
                })
                .show();
    }

    /* ═══════════════════════════════════════════════════════════
       PROCEED — رفتن به MainActivity
       ═══════════════════════════════════════════════════════════ */
    private void proceedAfterLogin() {
        if (pendingToken.isEmpty()) return;

        Toast.makeText(this,
                getString(R.string.success_welcome) + " " + pendingFullName,
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("session_token", pendingToken);
        intent.putExtra("user_id", pendingUserId);
        intent.putExtra("user_full_name", pendingFullName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /* ═══════════════════════════════════════════════════════════
       RESTORE SAVED USERNAME
       ═══════════════════════════════════════════════════════════ */
    private void restoreSavedUsername() {
        try {
            String lastUsername = getSharedPreferences(PREFS_LOCAL, MODE_PRIVATE)
                    .getString(KEY_LAST_USERNAME, "");
            if (lastUsername != null && !lastUsername.isEmpty() && loginInput != null) {
                loginInput.setText(lastUsername);
                if (loginInput.getText() != null) {
                    loginInput.setSelection(loginInput.getText().length());
                }
            }
        } catch (Exception ignored) {}
    }

    /* ═══════════════════════════════════════════════════════════
       LOAD LOGOS
       ═══════════════════════════════════════════════════════════ */
    private void loadBrandLogo() {
        if (brandLogo == null) return;
        boolean isDark = ThemeHelper.isDark(this);
        if (isDark) {
            brandLogo.setColorFilter(
                    new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        } else {
            brandLogo.clearColorFilter();
        }
        brandLogo.setImageResource(R.drawable.boom);
    }

    private void loadPicassoFooter() {
        if (picassoLogo == null) return;
        picassoLogo.setImageResource(R.drawable.logo_motion);
    }

    /* ═══════════════════════════════════════════════════════════
       UI HELPERS
       ═══════════════════════════════════════════════════════════ */
    private void showError(String msg) {
        if (msg == null || msg.isEmpty()) msg = getString(R.string.login_error_generic);
        errorText.setText(msg);
        errorAlert.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorAlert.setVisibility(View.GONE);
    }

    private void setLoading(MaterialButton btn, boolean loading, String text) {
        if (btn == null) return;
        btn.setEnabled(!loading);
        btn.setText(text);
    }

    private String text(TextInputEditText e) {
        if (e == null || e.getText() == null) return "";
        return e.getText().toString().trim();
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) resendTimer.cancel();
        super.onDestroy();
    }
}
