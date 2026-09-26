package ir.picassooads.boom.twa;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * ═══════════════════════════════════════════════════════════════
 * RegisterActivity — صفحه ثبت‌نام (نسخه مینیمال)
 * 
 * فعلاً فقط باز می‌شود. بعد از تست ورود، کامل پُرش می‌کنیم.
 * ═══════════════════════════════════════════════════════════════
 */
public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        MaterialButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                finish();
            }
        });
    }
}
