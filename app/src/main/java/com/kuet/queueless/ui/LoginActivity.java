package com.kuet.queueless.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.kuet.queueless.databinding.ActivityLoginBinding;
import com.kuet.queueless.repository.AuthRepository;
import com.kuet.queueless.util.Navigator;
import com.kuet.queueless.util.ResultCallback;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding b;
    private final AuthRepository auth = new AuthRepository();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        b.btnLogin.setOnClickListener(v -> doLogin());
        b.tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));

        // Already signed in? Restore the session and open the right dashboard.
        String uid = auth.currentUid();
        if (uid != null) {
            setLoading(true);
            auth.fetchRole(uid, new ResultCallback<String>() {
                @Override public void onSuccess(String role) { Navigator.goHome(LoginActivity.this, role); }
                @Override public void onError(String message) {
                    setLoading(false);
                    auth.logout();
                }
            });
        }
    }

    private void doLogin() {
        String email = String.valueOf(b.etEmail.getText()).trim();
        String pass = String.valueOf(b.etPassword.getText());
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.etEmail.setError("Enter a valid email");
            return;
        }
        if (pass.isEmpty()) {
            b.etPassword.setError("Enter your password");
            return;
        }
        setLoading(true);
        auth.login(email, pass, new ResultCallback<String>() {
            @Override public void onSuccess(String role) { Navigator.goHome(LoginActivity.this, role); }
            @Override public void onError(String message) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        b.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        b.btnLogin.setEnabled(!loading);
    }
}
