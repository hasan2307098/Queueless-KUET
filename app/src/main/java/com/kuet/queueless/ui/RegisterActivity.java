package com.kuet.queueless.ui;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kuet.queueless.databinding.ActivityRegisterBinding;
import com.kuet.queueless.repository.AuthRepository;
import com.kuet.queueless.util.Constants;
import com.kuet.queueless.util.Navigator;
import com.kuet.queueless.util.ResultCallback;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding b;
    private final AuthRepository auth = new AuthRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Create account");

        b.rgRole.setOnCheckedChangeListener((group, checkedId) ->
                b.tilCode.setVisibility(checkedId == b.rbOperator.getId() ? View.VISIBLE : View.GONE));
        b.btnRegister.setOnClickListener(v -> doRegister());
        b.tvBack.setOnClickListener(v -> finish());
    }

    private void doRegister() {
        String name = String.valueOf(b.etName.getText()).trim();
        String email = String.valueOf(b.etEmail.getText()).trim();
        String pass = String.valueOf(b.etPassword.getText());
        boolean operator = b.rbOperator.isChecked();

        if (name.isEmpty()) { b.etName.setError("Enter your name"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { b.etEmail.setError("Enter a valid email"); return; }
        if (pass.length() < 6) { b.etPassword.setError("At least 6 characters"); return; }
        if (operator && !Constants.OPERATOR_CODE.equals(String.valueOf(b.etCode.getText()).trim())) {
            b.etCode.setError("Wrong operator code");
            return;
        }

        setLoading(true);
        String role = operator ? Constants.ROLE_OPERATOR : Constants.ROLE_STUDENT;
        auth.register(name, email, pass, role, new ResultCallback<String>() {
            @Override public void onSuccess(String r) { Navigator.goHome(RegisterActivity.this, r); }
            @Override public void onError(String message) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnRegister.setEnabled(!loading);
    }
}
