package ir.picassooads.boom.twa;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

/**
 * ═══════════════════════════════════════════════════════════════
 * LoginActivity — صفحه ورود نیتیو
 * 
 * دو حالت دارد:
 *  1. ورود با رمز عبور (loginWithPassword)
 *  2. ورود با کد OTP (loginRequestOtp → loginVerifyOtp)
 * 
 * بعد از ورود موفق، توکن را در SessionManager ذخیره می‌کند و
 * به MainActivity می‌رود.
 * ═══════════════════════════════════════════════════════════════
 */
public class LoginActivity extends AppCompatActivity {

    /* ═══════════════════════════════════════════════════════════
       VIEWS
       ═══════════════════════════════════════════════════════════ */
    private TabLayout          loginTabs;
    private TextInputLayout    loginInputLayout;
    private TextInputLayout    passwordLayout;
    private TextInputEditText  loginInput;
    private TextInputEditText  passwordInput;
    private MaterialButton     loginButton;
    private MaterialButton     registerButton;
    private TextView           errorMessage;
    private TextView           forgotPasswordLink;
    private ProgressBar        loadingIndicator;

    /* ═══════════════════════════════════════════════════════════
       STATE
       ═══════════════════════════════════════════════════════════ */
    private enum Mode { PASSWORD, OTP }
    private Mode currentMode = Mode.PASSWORD;

    // اگر در حالت OTP هستیم، توکن جلسه را نگه می‌داریم
    private String currentOtpToken = "";
    private boolean otpSent = false;

    /* ═══════════════════════════════════════════════════════════
       CORE
       ═══════════════════════════════════════════════════════════ */
    private ApiClient      api;
    private SessionManager session;

    /* ═══════════════════════════════════════════════════════════
       LIFECYCLE
       ═══════════════════════════════════════════════════════════ */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

        // اگر از قبل لاگین است، برو به MainActivity
        if (session.isLoggedIn()) {
            goToMain();
            return;
        }

        setContentView(R.layout.activity_login);

        api = new ApiClient(this);

        bindViews();
        setupTabs();
        setupButtons();
    }

    /* ═══════════════════════════════════════════════════════════
       BIND
       ═══════════════════════════════════════════════════════════ */
    private void bindViews() {
        loginTabs         = findViewById(R.id.loginTabs);
        loginInputLayout  = findViewById(R.id.loginInputLayout);
        passwordLayout    = findViewById(R.id.passwordLayout);
        loginInput        = findViewById(R.id.loginInput);
        passwordInput     = findViewById(R.id.passwordInput);
        loginButton       = findViewById(R.id.loginButton);
        registerButton    = findViewById(R.id.registerButton);
        errorMessage      = findViewById(R.id.errorMessage);
        forgotPasswordLink = findViewById(R.id.forgotPasswordLink);
        loadingIndicator  = findViewById(R.id.loadingIndicator);
    }

    /* ═══════════════════════════════════════════════════════════
       TABS — تغییر بین حالت رمز و حالت OTP
       ═══════════════════════════════════════════════════════════ */
    private void setupTabs() {
        loginTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                if (pos == 0) {
                    setMode(Mode.PASSWORD);
                } else {
                    setMode(Mode.OTP);
                }
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void setMode(Mode mode) {
        currentMode = mode;
        hideError();
        otpSent = false;
        currentOtpToken = "";

        if (mode == Mode.PASSWORD) {
            passwordLayout.setVisibility(View.VISIBLE);
            loginButton.setText(R.string.login_button);
        } else {
            passwordLayout.setVisibility(View.GONE);
            loginButton.setText(R.string.login_send_code);
        }
    }

    /* ═══════════════════════════════════════════════════════════
       BUTTONS
       ═══════════════════════════════════════════════════════════ */
    private void setupButtons() {
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                hideKeyboard();
                if (currentMode == Mode.PASSWORD) {
                    doPasswordLogin();
                } else {
                    doOtpFlow();
                }
            }
        });

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(i);
            }
        });

        forgotPasswordLink.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
                startActivity(i);
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       LOGIN — حالت رمز عبور
       ═══════════════════════════════════════════════════════════ */
    private void doPasswordLogin() {
        String login = text(loginInput);
        String password = text(passwordInput);

        if (login.isEmpty()) {
            showError(getString(R.string.login_error_empty_login));
            return;
        }
        if (password.isEmpty()) {
            showError(getString(R.string.login_error_empty_password));
            return;
        }

        setLoading(true, getString(R.string.login_loading));

        api.loginWithPassword(login, password, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                setLoading(false, null);

                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage
                            : "ورود ناموفق بود");
                    return;
                }

                handleLoginSuccess(response);
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       LOGIN — حالت OTP
       ═══════════════════════════════════════════════════════════ */
    private void doOtpFlow() {
        String login = text(loginInput);

        if (login.isEmpty()) {
            showError(getString(R.string.login_error_empty_login));
            return;
        }

        // مرحله ۱: درخواست کد
        if (!otpSent) {
            setLoading(true, getString(R.string.login_sending_code));

            api.loginRequestOtp(login, new ApiClient.ApiCallback() {
                @Override
                public void onResult(boolean success, JSONObject response, String errorMessage) {
                    setLoading(false, null);

                    if (!success || response == null) {
                        showError(errorMessage != null ? errorMessage
                                : "خطا در ارسال کد");
                        return;
                    }

                    currentOtpToken = response.optString("otp_token", "");
                    if (currentOtpToken.isEmpty()) {
                        // ضد user-enumeration — پاسخ generic برگشته
                        showError("اگر اطلاعات وارد شده صحیح باشد، کد ارسال خواهد شد");
                        return;
                    }

                    otpSent = true;

                    // تغییر UI به مرحله‌ی کد
                    passwordLayout.setVisibility(View.VISIBLE);
                    passwordLayout.setHint("کد ۶ رقمی");
                    passwordInput.setInputType(
                            android.text.InputType.TYPE_CLASS_NUMBER);
                    passwordInput.setText("");
                    passwordInput.requestFocus();

                    loginButton.setText("تأیید و ورود");

                    String masked = response.optString("phone_masked", "");
                    showSuccess("کد به " + masked + " ارسال شد");
                }
            });
            return;
        }

        // مرحله ۲: تأیید کد
        String code = text(passwordInput);
        if (code.isEmpty() || code.length() != 6) {
            showError("کد ۶ رقمی را وارد کنید");
            return;
        }

        setLoading(true, "در حال بررسی کد...");

        api.loginVerifyOtp(currentOtpToken, code, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                setLoading(false, null);

                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage
                            : "کد اشتباه است");
                    return;
                }

                handleLoginSuccess(response);
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       LOGIN SUCCESS — ذخیره‌ی سشن + رفتن به Main
       ═══════════════════════════════════════════════════════════ */
    private void handleLoginSuccess(JSONObject response) {
        try {
            String token       = response.optString("token", "");
            String expiresAt   = response.optString("expires_at", "");
            String method      = response.optString("method", "password");

            JSONObject user = response.optJSONObject("user");
            if (user == null) {
                showError("پاسخ سرور ناقص است");
                return;
            }

            int    userId     = user.optInt("id", 0);
            String username   = user.optString("username", "");
            String firstName  = user.optString("first_name", "");
            String lastName   = user.optString("last_name", "");
            String fullName   = user.optString("full_name", "");
            String phone      = user.optString("phone", "");
            String email      = user.optString("email", "");
            String language   = user.optString("language", "fa");
            String theme      = user.optString("theme", "auto");

            if (token.isEmpty() || userId <= 0) {
                showError("پاسخ سرور ناقص است (توکن نبود)");
                return;
            }

            // ذخیره در SharedPreferences
            session.saveSession(
                    token,
                    expiresAt,
                    userId,
                    username,
                    firstName,
                    lastName,
                    fullName,
                    phone,
                    email,
                    language,
                    theme,
                    android.os.Build.MODEL
            );

            // رفتن به MainActivity
            goToMain();

        } catch (Exception e) {
            showError("خطای غیرمنتظره: " + e.getMessage());
        }
    }

    /* ═══════════════════════════════════════════════════════════
       NAVIGATION
       ═══════════════════════════════════════════════════════════ */
    private void goToMain() {
        Intent i = new Intent(LoginActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    /* ═══════════════════════════════════════════════════════════
       UI HELPERS
       ═══════════════════════════════════════════════════════════ */
    private void showError(String message) {
        if (TextUtils.isEmpty(message)) message = "خطایی رخ داد";
        errorMessage.setText(message);
        errorMessage.setTextColor(ContextCompat.getColor(this, R.color.boomError));
        errorMessage.setVisibility(View.VISIBLE);
    }

    private void showSuccess(String message) {
        if (TextUtils.isEmpty(message)) return;
        errorMessage.setText(message);
        errorMessage.setTextColor(ContextCompat.getColor(this, R.color.boomPrimary));
        errorMessage.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorMessage.setVisibility(View.GONE);
        errorMessage.setText("");
    }

    private void setLoading(boolean loading, String buttonText) {
        if (loading) {
            loadingIndicator.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            registerButton.setEnabled(false);
            if (buttonText != null) loginButton.setText(buttonText);
        } else {
            loadingIndicator.setVisibility(View.GONE);
            loginButton.setEnabled(true);
            registerButton.setEnabled(true);
            // بازگرداندن متن دکمه به حالت اولیه
            if (currentMode == Mode.PASSWORD) {
                loginButton.setText(R.string.login_button);
            } else {
                loginButton.setText(otpSent ? "تأیید و ورود" : getString(R.string.login_send_code));
            }
        }
    }

    private String text(TextInputEditText editText) {
        if (editText == null || editText.getText() == null) return "";
        return editText.getText().toString().trim();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception ignored) { }
    }
}
