package ir.picassooads.boom.twa;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    private FrameLayout backBtn;
    private TextView step1Dot, step2Dot, step3Dot, errorText, regOtpSub;
    private View stepLine1, stepLine2;
    private LinearLayout step1Container, step2Container, step3Container, errorAlert;
    private TextInputLayout firstNameLayout, lastNameLayout, usernameLayout,
            regPasswordLayout, confirmPasswordLayout, phoneLayout;
    private TextInputEditText firstNameInput, lastNameInput, usernameInput,
            regPasswordInput, confirmPasswordInput, phoneInput;
    private CheckBox privacyCheck;
    private MaterialButton nextBtn, prevBtn, sendCodeBtn, createAccountBtn;
    private EditText[] otpBoxes = new EditText[6];

    private ApiClient api;
    private SessionManager session;
    private int currentStep = 1;
    private String currentOtpToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applySavedMode(this);
        super.onCreate(savedInstanceState);

        String lang = LocaleHelper.getLanguage(this);
        LocaleHelper.applyLocale(this, lang);
        session = new SessionManager(this);

        setContentView(R.layout.activity_register);
        api = new ApiClient(this);

        bindViews();
        setupButtons();
        setupOtpBoxes();

        LocaleHelper.applyFontToViewTree(this,
                findViewById(R.id.registerRoot), lang);
    }

    private void bindViews() {
        backBtn = findViewById(R.id.backBtn);
        step1Dot = findViewById(R.id.step1Dot);
        step2Dot = findViewById(R.id.step2Dot);
        step3Dot = findViewById(R.id.step3Dot);
        stepLine1 = findViewById(R.id.stepLine1);
        stepLine2 = findViewById(R.id.stepLine2);
        step1Container = findViewById(R.id.step1Container);
        step2Container = findViewById(R.id.step2Container);
        step3Container = findViewById(R.id.step3Container);
        errorAlert = findViewById(R.id.errorAlert);
        errorText = findViewById(R.id.errorText);
        regOtpSub = findViewById(R.id.regOtpSub);
        firstNameLayout = findViewById(R.id.firstNameLayout);
        lastNameLayout = findViewById(R.id.lastNameLayout);
        usernameLayout = findViewById(R.id.usernameLayout);
        regPasswordLayout = findViewById(R.id.regPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        phoneLayout = findViewById(R.id.phoneLayout);
        firstNameInput = findViewById(R.id.firstNameInput);
        lastNameInput = findViewById(R.id.lastNameInput);
        usernameInput = findViewById(R.id.usernameInput);
        regPasswordInput = findViewById(R.id.regPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        phoneInput = findViewById(R.id.phoneInput);
        privacyCheck = findViewById(R.id.privacyCheck);
        nextBtn = findViewById(R.id.nextBtn);
        prevBtn = findViewById(R.id.prevBtn);
        sendCodeBtn = findViewById(R.id.sendCodeBtn);
        createAccountBtn = findViewById(R.id.createAccountBtn);
        otpBoxes[0] = findViewById(R.id.regOtp1);
        otpBoxes[1] = findViewById(R.id.regOtp2);
        otpBoxes[2] = findViewById(R.id.regOtp3);
        otpBoxes[3] = findViewById(R.id.regOtp4);
        otpBoxes[4] = findViewById(R.id.regOtp5);
        otpBoxes[5] = findViewById(R.id.regOtp6);
    }

    private void setupButtons() {
        backBtn.setOnClickListener(v -> {
            if (currentStep == 1) finish();
            else goToStep(currentStep - 1);
        });

        nextBtn.setOnClickListener(v -> validateAndGoStep2());
        prevBtn.setOnClickListener(v -> goToStep(1));
        sendCodeBtn.setOnClickListener(v -> doRegisterRequest());
        createAccountBtn.setOnClickListener(v -> doVerifyRegister());
    }

    private void setupOtpBoxes() {
        for (int i = 0; i < otpBoxes.length; i++) {
            final int idx = i;
            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    if (s.length() == 1 && idx < otpBoxes.length - 1) {
                        otpBoxes[idx + 1].requestFocus();
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

    private void goToStep(int step) {
        currentStep = step;
        step1Container.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        step2Container.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        step3Container.setVisibility(step == 3 ? View.VISIBLE : View.GONE);

        // Update indicators
        step1Dot.setBackgroundResource(step > 1 ?
                R.drawable.bg_step_circle_done : R.drawable.bg_step_circle_active);
        step2Dot.setBackgroundResource(step > 2 ?
                R.drawable.bg_step_circle_done :
                (step == 2 ? R.drawable.bg_step_circle_active : R.drawable.bg_step_circle));
        step3Dot.setBackgroundResource(step == 3 ?
                R.drawable.bg_step_circle_active : R.drawable.bg_step_circle);

        stepLine1.setBackgroundResource(step > 1 ?
                R.drawable.bg_step_line_done : R.drawable.bg_step_line);
        stepLine2.setBackgroundResource(step > 2 ?
                R.drawable.bg_step_line_done : R.drawable.bg_step_line);
    }

    private void validateAndGoStep2() {
        hideError();
        String fn = text(firstNameInput);
        String ln = text(lastNameInput);
        String un = text(usernameInput);
        String pw = text(regPasswordInput);
        String cp = text(confirmPasswordInput);

        if (fn.isEmpty() || ln.isEmpty() || un.isEmpty() || pw.isEmpty() || cp.isEmpty()) {
            showError(getString(R.string.register_error_fill_required));
            return;
        }
        if (fn.length() < 2 || ln.length() < 2) {
            showError(getString(R.string.register_error_name_short));
            return;
        }
        if (!un.matches("^[a-zA-Z0-9_]{3,50}$")) {
            showError(getString(R.string.register_error_username_format));
            return;
        }
        if (pw.length() < 8 || !pw.matches(".*[A-Z].*") ||
                !pw.matches(".*[a-z].*") || !pw.matches(".*[0-9].*")) {
            showError(getString(R.string.register_error_password_weak));
            return;
        }
        if (!pw.equals(cp)) {
            showError(getString(R.string.register_error_password_mismatch));
            return;
        }
        if (!privacyCheck.isChecked()) {
            showError(getString(R.string.register_error_accept_terms));
            return;
        }
        goToStep(2);
    }

    private void doRegisterRequest() {
        hideError();
        String phone = text(phoneInput).replaceAll("\\s", "");
        if (!phone.matches("^09[0-9]{9}$")) {
            showError(getString(R.string.register_error_phone_invalid));
            return;
        }

        sendCodeBtn.setEnabled(false);
        sendCodeBtn.setText(R.string.register_loading);

        api.registerRequestOtp(
                text(firstNameInput), text(lastNameInput), text(usernameInput),
                phone, text(regPasswordInput), true,
                new ApiClient.ApiCallback() {
                    @Override
                    public void onResult(boolean success, JSONObject response, String errorMessage) {
                        sendCodeBtn.setEnabled(true);
                        sendCodeBtn.setText(R.string.register_send_code);

                        if (!success || response == null) {
                            showError(errorMessage != null ? errorMessage :
                                    getString(R.string.login_error_generic));
                            return;
                        }

                        currentOtpToken = response.optString("otp_token", "");
                        String masked = response.optString("phone_masked", phone);

                        regOtpSub.setText(getString(R.string.register_otp_sent_to) + " " + masked);
                        goToStep(3);
                    }
                });
    }

    private void doVerifyRegister() {
        hideError();
        StringBuilder sb = new StringBuilder();
        for (EditText e : otpBoxes) sb.append(e.getText().toString());
        String code = sb.toString();
        if (code.length() != 6) {
            showError(getString(R.string.register_error_otp_invalid));
            return;
        }

        createAccountBtn.setEnabled(false);
        createAccountBtn.setText(R.string.register_creating);

        api.registerVerifyOtp(currentOtpToken, code, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                createAccountBtn.setEnabled(true);
                createAccountBtn.setText(R.string.register_create_account);

                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage :
                            getString(R.string.register_error_otp_invalid));
                    return;
                }

                // ذخیره توکن
                try {
                    String token = response.optString("token", "");
                    String expiresAt = response.optString("expires_at", "");
                    JSONObject user = response.optJSONObject("user");
                    if (user != null && !token.isEmpty()) {
                        session.saveSession(token, expiresAt,
                                user.optInt("id", 0),
                                user.optString("username", ""),
                                user.optString("first_name", ""),
                                user.optString("last_name", ""),
                                user.optString("full_name", ""),
                                user.optString("phone", ""),
                                user.optString("email", ""),
                                user.optString("language", "fa"),
                                user.optString("theme", "auto"),
                                android.os.Build.MODEL);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "save session", e);
                }

                Toast.makeText(RegisterActivity.this,
                        R.string.success_account_created, Toast.LENGTH_LONG).show();

                Intent i = new Intent(RegisterActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            }
        });
    }

    private void showError(String msg) {
        if (msg == null || msg.isEmpty()) msg = getString(R.string.login_error_generic);
        errorText.setText(msg);
        errorAlert.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorAlert.setVisibility(View.GONE);
    }

    private String text(TextInputEditText e) {
        if (e == null || e.getText() == null) return "";
        return e.getText().toString().trim();
    }
}
