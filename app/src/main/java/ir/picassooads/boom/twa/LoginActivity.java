package ir.picassooads.boom.twa;

import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

/**
 * ═══════════════════════════════════════════════════════════════
 * LoginActivity — صفحه‌ی ورود نیتیو
 * 
 * v4 — اصلاحات امنیتی:
 *   • توکن دستگاه دیگر در SharedPreferences ذخیره نمی‌شود
 *   • هر بار که این صفحه باز می‌شود، همه‌ی کوکی‌های WebView پاک می‌شوند
 *   • توکن فقط از طریق Intent به MainActivity منتقل می‌شود
 *   • نتیجه: هر بار کاربر باید دوباره وارد شود (طبق خواسته‌ی کارفرما)
 * ═══════════════════════════════════════════════════════════════
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private static final String PICASSO_FOOTER_URL =
            "https://boom.picassooads.ir/assets/images/logo_motion.gif";

    /* Views */
    private TabLayout loginTabs;
    private LinearLayout passwordMode, otpMode, errorAlert;
    private TextInputLayout loginInputLayout, passwordLayout;
    private TextInputEditText loginInput, passwordInput;
    private MaterialButton loginBtn, verifyOtpBtn, resendBtn, backBtn, forgotBtn, registerCta;
    private TextView errorText, otpSub, langCode;
    private FrameLayout themeBtn, langBtn, biometricBtn;
    private ImageView themeIcon;
    private ImageView picassoLogo;
    private EditText[] otpBoxes = new EditText[6];

    /* State */
    private ApiClient api;
    private SessionManager session;
    private String currentLang;
    private String currentOtpToken = "";
    private boolean otpSent = false;
    private CountDownTimer resendTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ★ اعمال تم قبل از super
        ThemeHelper.applySavedMode(this);

        super.onCreate(savedInstanceState);

        // ★ اعمال زبان
        currentLang = LocaleHelper.getLanguage(this);
        LocaleHelper.applyLocale(this, currentLang);

        // ★ سشن قبلی را پاک کن (توکن ذخیره نمی‌شود، ولی برای اطمینان)
        session = new SessionManager(this);
        if (session.isLoggedIn()) {
            session.clear();
        }

        // ★ کوکی‌های WebView را پاک کن
        // چون کاربر هر بار باید لاگین کند، سشن PHP قبلی هم باید پاک شود
        try {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear cookies", e);
        }

        setContentView(R.layout.activity_login);

        api = new ApiClient(this);

        bindViews();
        setupTopActions();
        setupTabs();
        setupFields();
        setupButtons();
        setupOtpBoxes();

        // اعمال فونت
        LocaleHelper.applyFontToViewTree(this,
                findViewById(R.id.loginRoot), currentLang);

        // به‌روزرسانی دکمه‌ی زبان
        langCode.setText(currentLang.toUpperCase());

        // به‌روزرسانی آیکون تم
        updateThemeIcon();

        // ★ لود لوگوی Picasso در فوتر
        loadPicassoFooter();

        // ★ اگر username قبلی ذخیره شده، پر کن (اختیاری — برای راحتی)
        restoreSavedUsername();
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
        picassoLogo = findViewById(R.id.picassoLogo);

        otpBoxes[0] = findViewById(R.id.otp1);
        otpBoxes[1] = findViewById(R.id.otp2);
        otpBoxes[2] = findViewById(R.id.otp3);
        otpBoxes[3] = findViewById(R.id.otp4);
        otpBoxes[4] = findViewById(R.id.otp5);
        otpBoxes[5] = findViewById(R.id.otp6);
    }

    /* ═══════════════════════════════════════════════════════════
       TOP ACTIONS — Theme + Language
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

        new androidx.appcompat.app.AlertDialog.Builder(this,
                R.style.BoomDialogTheme)
                .setTitle(R.string.language_choose)
                .setItems(names, (dialog, which) -> {
                    String lang = codes[which];
                    if (lang.equals(currentLang)) return;
                    LocaleHelper.saveLanguage(this, lang);
                    recreate();
                })
                .show();
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
       FIELDS — realtime error clear
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
            Intent i = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(i);
        });

        registerCta.setOnClickListener(v -> {
            Intent i = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(i);
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
                    if (getOtpCode().length() == 6 && otpSent) {
                        doVerifyOtp();
                    }
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
        for (EditText e : otpBoxes) {
            sb.append(e.getText().toString());
        }
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

        if (login.isEmpty()) {
            showError(getString(R.string.login_error_empty_login));
            return;
        }
        if (pass.isEmpty()) {
            showError(getString(R.string.login_error_empty_password));
            return;
        }

        setLoading(loginBtn, true, getString(R.string.login_loading));
        hideError();

        api.loginWithPassword(login, pass, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                setLoading(loginBtn, false, getString(R.string.login_button));
                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage :
                            getString(R.string.login_error_invalid_credentials));
                    return;
                }
                handleLoginSuccess(response);
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       OTP — Request code
       ═══════════════════════════════════════════════════════════ */
    private void doRequestOtp() {
        String login = text(loginInput);

        if (login.isEmpty()) {
            showError(getString(R.string.login_error_empty_login));
            return;
        }

        setLoading(verifyOtpBtn, true, getString(R.string.login_sending_code));
        hideError();

        api.loginRequestOtp(login, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                setLoading(verifyOtpBtn, false,
                        otpSent ? getString(R.string.login_verify)
                                : getString(R.string.login_send_code));
                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage :
                            getString(R.string.login_error_sms_failed));
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

    /* ═══════════════════════════════════════════════════════════
       OTP — Verify code
       ═══════════════════════════════════════════════════════════ */
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
                    showError(errorMessage != null ? errorMessage :
                            getString(R.string.login_error_invalid_code));
                    clearOtpBoxes();
                    return;
                }
                handleLoginSuccess(response);
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       RESEND COOLDOWN
       ═══════════════════════════════════════════════════════════ */
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
       ★ LOGIN SUCCESS
       
       - توکن در SharedPreferences ذخیره نمی‌شود
       - فقط username برای راحتی کاربر ذخیره می‌شود
       - توکن از طریق Intent به MainActivity پاس می‌شود
       - زبان و تم کاربر ذخیره می‌شوند (چون تجربه‌ی کاربر بهتر می‌شود)
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
            String firstName = user.optString("first_name", "");
            String lastName = user.optString("last_name", "");
            String fullName = user.optString("full_name", "");
            String phone = user.optString("phone", "");
            String email = user.optString("email", "");
            String lang = user.optString("language", currentLang);
            String theme = user.optString("theme", "auto");

            // ★ فقط تنظیمات پایه را ذخیره می‌کنیم — نه توکن!
            if (LocaleHelper.isValid(lang)) LocaleHelper.saveLanguage(this, lang);
            if (theme != null) ThemeHelper.saveMode(this, theme);

            // ★ ذخیره‌ی username برای پر شدن خودکار در ورود بعدی (اختیاری)
            if (!username.isEmpty()) {
                try {
                    getSharedPreferences("boom_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("last_username", username)
                        .apply();
                } catch (Exception ignored) {}
            }

            // ★★ نکته مهم: دیگر session.saveSession صدا زده نمی‌شود

            Toast.makeText(this,
                    getString(R.string.success_welcome) + " " + fullName,
                    Toast.LENGTH_SHORT).show();

            // ★ توکن را از طریق Intent به MainActivity بفرست
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("session_token", token);
            intent.putExtra("user_id", userId);
            intent.putExtra("user_full_name", fullName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();

        } catch (Exception e) {
            Log.e(TAG, "handleLoginSuccess", e);
            showError(getString(R.string.login_error_server));
        }
    }

    /* ═══════════════════════════════════════════════════════════
       ★ RESTORE SAVED USERNAME
       ═══════════════════════════════════════════════════════════ */
    private void restoreSavedUsername() {
        try {
            String lastUsername = getSharedPreferences("boom_prefs", MODE_PRIVATE)
                    .getString("last_username", "");
            if (lastUsername != null && !lastUsername.isEmpty() && loginInput != null) {
                loginInput.setText(lastUsername);
                // cursor به آخر
                if (loginInput.getText() != null) {
                    loginInput.setSelection(loginInput.getText().length());
                }
            }
        } catch (Exception ignored) {}
    }

    /* ═══════════════════════════════════════════════════════════
       BIOMETRIC
       ═══════════════════════════════════════════════════════════ */
    private void onBiometric() {
        Toast.makeText(this, R.string.biometric_not_available, Toast.LENGTH_SHORT).show();
    }

    /* ═══════════════════════════════════════════════════════════
       LOAD PICASSO FOOTER
       ═══════════════════════════════════════════════════════════ */
    private void loadPicassoFooter() {
        if (picassoLogo == null) return;

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(PICASSO_FOOTER_URL);
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    try (java.io.InputStream is = conn.getInputStream()) {
                        final android.graphics.Bitmap bm =
                                android.graphics.BitmapFactory.decodeStream(is);
                        if (bm != null) {
                            runOnUiThread(() -> picassoLogo.setImageBitmap(bm));
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "picasso footer load failed", e);
            }
        }).start();
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
