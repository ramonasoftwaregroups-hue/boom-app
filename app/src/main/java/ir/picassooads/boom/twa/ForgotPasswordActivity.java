package ir.picassooads.boom.twa;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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

public class ForgotPasswordActivity extends AppCompatActivity {

    private FrameLayout backBtn;
    private LinearLayout step1Container, step2Container, step3Container, errorAlert;
    private TextView errorText, otpSub;
    private TextInputLayout loginInputLayout, newPasswordLayout, confirmPasswordLayout;
    private TextInputEditText loginInput, newPasswordInput, confirmPasswordInput;
    private MaterialButton sendCodeBtn, verifyBtn, resetBtn;
    private EditText[] otpBoxes = new EditText[6];

    private ApiClient api;
    private int currentStep = 1;
    private String currentOtpToken = "";
    private String currentResetToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applySavedMode(this);
        super.onCreate(savedInstanceState);

        String lang = LocaleHelper.getLanguage(this);
        LocaleHelper.applyLocale(this, lang);

        setContentView(R.layout.activity_forgot_password);
        api = new ApiClient(this);

        bindViews();
        setupButtons();
        setupOtpBoxes();

        LocaleHelper.applyFontToViewTree(this,
                findViewById(R.id.forgotRoot), lang);
    }

    private void bindViews() {
        backBtn = findViewById(R.id.backBtn);
        step1Container = findViewById(R.id.step1Container);
        step2Container = findViewById(R.id.step2Container);
        step3Container = findViewById(R.id.step3Container);
        errorAlert = findViewById(R.id.errorAlert);
        errorText = findViewById(R.id.errorText);
        otpSub = findViewById(R.id.otpSub);
        loginInputLayout = findViewById(R.id.loginInputLayout);
        newPasswordLayout = findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        loginInput = findViewById(R.id.loginInput);
        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        sendCodeBtn = findViewById(R.id.sendCodeBtn);
        verifyBtn = findViewById(R.id.verifyBtn);
        resetBtn = findViewById(R.id.resetBtn);

        otpBoxes[0] = findViewById(R.id.otp1);
        otpBoxes[1] = findViewById(R.id.otp2);
        otpBoxes[2] = findViewById(R.id.otp3);
        otpBoxes[3] = findViewById(R.id.otp4);
        otpBoxes[4] = findViewById(R.id.otp5);
        otpBoxes[5] = findViewById(R.id.otp6);
    }

    private void setupButtons() {
        backBtn.setOnClickListener(v -> {
            if (currentStep > 1) goToStep(currentStep - 1);
            else finish();
        });

        sendCodeBtn.setOnClickListener(v -> doRequestCode());
        verifyBtn.setOnClickListener(v -> doVerifyCode());
        resetBtn.setOnClickListener(v -> doResetPassword());
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
    }

    private void doRequestCode() {
        hideError();
        String login = text(loginInput);
        if (login.isEmpty()) {
            showError(getString(R.string.login_error_empty_login));
            return;
        }

        sendCodeBtn.setEnabled(false);
        sendCodeBtn.setText(R.string.forgot_loading);

        api.forgotRequestOtp(login, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                sendCodeBtn.setEnabled(true);
                sendCodeBtn.setText(R.string.forgot_send_code);
                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage :
                            getString(R.string.login_error_generic));
                    return;
                }

                currentOtpToken = response.optString("otp_token", "");
                String masked = response.optString("phone_masked", "");
                otpSub.setText(getString(R.string.otp_sub) + " " + masked);
                goToStep(2);
            }
        });
    }

    private void doVerifyCode() {
        hideError();
        StringBuilder sb = new StringBuilder();
        for (EditText e : otpBoxes) sb.append(e.getText().toString());
        String code = sb.toString();
        if (code.length() != 6) {
            showError(getString(R.string.register_error_otp_invalid));
            return;
        }

        verifyBtn.setEnabled(false);
        verifyBtn.setText(R.string.login_verifying);

        api.forgotVerifyOtp(currentOtpToken, code, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                verifyBtn.setEnabled(true);
                verifyBtn.setText(R.string.forgot_verify);
                if (!success || response == null) {
                    showError(errorMessage != null ? errorMessage :
                            getString(R.string.register_error_otp_invalid));
                    return;
                }
                currentResetToken = response.optString("reset_token", "");
                goToStep(3);
            }
        });
    }

    private void doResetPassword() {
        hideError();
        String pw = text(newPasswordInput);
        String cp = text(confirmPasswordInput);

        if (pw.isEmpty() || cp.isEmpty()) {
            showError(getString(R.string.register_error_fill_required));
            return;
        }
        if (!pw.equals(cp)) {
            showError(getString(R.string.register_error_password_mismatch));
            return;
        }

        resetBtn.setEnabled(false);
        resetBtn.setText(R.string.forgot_resetting);

        api.forgotReset(currentResetToken, pw, cp, new ApiClient.ApiCallback() {
            @Override
            public void onResult(boolean success, JSONObject response, String errorMessage) {
                resetBtn.setEnabled(true);
                resetBtn.setText(R.string.forgot_reset);
                if (!success) {
                    showError(errorMessage != null ? errorMessage :
                            getString(R.string.login_error_generic));
                    return;
                }
                Toast.makeText(ForgotPasswordActivity.this,
                        R.string.forgot_success, Toast.LENGTH_LONG).show();
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
