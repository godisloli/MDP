package net.tiramisu.mdp;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class ForgetPasswordActivity extends AppCompatActivity {
    private EditText etEmail;
    private Button btnReset;
    private ProgressBar progressBar;
    private TextView tvBackLogin;
    private FirebaseAuth mAuth;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgetpassword);

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        etEmail = findViewById(R.id.et_email);
        btnReset = findViewById(R.id.btn_reset_password);
        progressBar = findViewById(R.id.progress_loading);
        tvBackLogin = findViewById(R.id.tvBackLogin);

        if (tvBackLogin != null) {
            tvBackLogin.setOnClickListener(v -> {
                // Go back to LoginActivity (clear this activity)
                Intent intent = new Intent(ForgetPasswordActivity.this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        }

        if (btnReset != null) {
            btnReset.setOnClickListener(v -> sendResetLink());
        }
    }

    private void sendResetLink() {
        if (etEmail == null) return;
        String email = etEmail.getText() == null ? "" : etEmail.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError(getString(R.string.enter_valid_email));
            etEmail.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.enter_valid_email));
            etEmail.requestFocus();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.no_network), Toast.LENGTH_LONG).show();
            return;
        }

        if (mAuth == null) {
            Toast.makeText(this, getString(R.string.firebase_not_initialized), Toast.LENGTH_LONG).show();
            return;
        }

        showLoading(true);
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(ForgetPasswordActivity.this, getString(R.string.check_email_reset), Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        String msg = getString(R.string.failed_send_reset);
                        if (task.getException() != null && task.getException().getMessage() != null)
                            msg = task.getException().getMessage();
                        Toast.makeText(ForgetPasswordActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    String msg = getString(R.string.error_prefix, e.getMessage() == null ? "" : e.getMessage());
                    Toast.makeText(ForgetPasswordActivity.this, msg, Toast.LENGTH_LONG).show();
                });
    }

    private void showLoading(boolean show) {
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (etEmail != null) etEmail.setEnabled(!show);
        if (btnReset != null) btnReset.setEnabled(!show);
        if (tvBackLogin != null) tvBackLogin.setEnabled(!show);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(network);
        if (nc == null) return false;
        return nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH);
    }
}
