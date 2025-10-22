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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private EditText edtEmail, edtPassword;
    private Button btnLogin, btnRegister;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Ensure Firebase is initialized
        FirebaseApp.initializeApp(this);

        mAuth = FirebaseAuth.getInstance();

        // Bind progress bar early
        progressBar = findViewById(R.id.progressBar);

        // Initialize views and listeners
        initViews();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If user already signed in, go straight to MainActivity
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startMain();
        }
    }

    private void initViews() {
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        if (btnLogin != null) {
            btnLogin.setOnClickListener(v -> login());
        }

        if (btnRegister != null) {
            btnRegister.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            });
        }
    }

    private boolean validateInput() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString();

        if (email.isEmpty()) {
            edtEmail.setError("Email is required");
            edtEmail.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            edtEmail.setError("Enter a valid email");
            edtEmail.requestFocus();
            return false;
        }
        if (password.isEmpty()) {
            edtPassword.setError("Password is required");
            edtPassword.requestFocus();
            return false;
        }
        return true;
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

    private void showLoading(boolean show) {
        if (progressBar == null) return;
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (edtEmail != null) edtEmail.setEnabled(!show);
        if (edtPassword != null) edtPassword.setEnabled(!show);
        if (btnLogin != null) btnLogin.setEnabled(!show);
        if (btnRegister != null) btnRegister.setEnabled(!show);
    }

    private void login() {
        if (!validateInput()) return;

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "No network connection", Toast.LENGTH_LONG).show();
            return;
        }

        if (mAuth == null) {
            Toast.makeText(this, "Firebase not initialized", Toast.LENGTH_LONG).show();
            return;
        }

        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString();

        showLoading(true);
        try {
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(LoginActivity.this, task -> {
                        showLoading(false);
                        if (task.isSuccessful()) {
                            // Get signed-in user and pass credentials to MainActivity
                            FirebaseUser user = mAuth.getCurrentUser();
                            String userEmail = user != null ? user.getEmail() : "";
                            String userUid = user != null ? user.getUid() : "";

                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            intent.putExtra("EXTRA_USER_EMAIL", userEmail);
                            intent.putExtra("EXTRA_USER_UID", userUid);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            String msg = "Authentication failed.";
                            if (task.getException() != null)
                                msg = task.getException().getMessage();
                            Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    })
                    .addOnFailureListener(LoginActivity.this, e -> {
                        showLoading(false);
                        Toast.makeText(LoginActivity.this, "Login error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        } catch (Exception ex) {
            showLoading(false);
            Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void startMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}
