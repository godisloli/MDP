package net.tiramisu.mdp;

import android.content.Intent;
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

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class RegisterActivity extends AppCompatActivity {
    private EditText edtEmail, edtPassword, edtConfirmPassword;
    private Button btnRegister;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private TextView tvBackLogin; // added field

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Ensure Firebase is initialized
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        initViews();

        progressBar = findViewById(R.id.progressBar);

        btnRegister.setOnClickListener(v -> registerUser());

        // Wire the back-to-login text view (if present)
        if (tvBackLogin != null) {
            tvBackLogin.setOnClickListener(v -> {
                // Simply finish this activity to return to the previous (LoginActivity)
                finish();
            });
        }
    }

    private void initViews() {
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvBackLogin = findViewById(R.id.tvBackLogin); // initialize the text view
    }

    private boolean validateInput() {
        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString();
        String confirm = edtConfirmPassword.getText().toString();

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
        if (password.length() < 6) {
            edtPassword.setError("Password must be at least 6 characters");
            edtPassword.requestFocus();
            return false;
        }
        if (!password.equals(confirm)) {
            edtConfirmPassword.setError("Passwords do not match");
            edtConfirmPassword.requestFocus();
            return false;
        }
        return true;
    }

    private void showLoading(boolean show) {
        if (progressBar == null) return;
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        edtEmail.setEnabled(!show);
        edtPassword.setEnabled(!show);
        edtConfirmPassword.setEnabled(!show);
        btnRegister.setEnabled(!show);
    }

    private void registerUser() {
        if (!validateInput()) return;

        String email = edtEmail.getText().toString().trim();
        String password = edtPassword.getText().toString();

        showLoading(true);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(RegisterActivity.this, "Account created", Toast.LENGTH_SHORT).show();
                        // Get the newly created user
                        com.google.firebase.auth.FirebaseUser user = mAuth.getCurrentUser();
                        String userEmail = user != null ? user.getEmail() : "";
                        String userUid = user != null ? user.getUid() : "";

                        // Navigate to main app with extras
                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        intent.putExtra("EXTRA_USER_EMAIL", userEmail);
                        intent.putExtra("EXTRA_USER_UID", userUid);
                        // Start MainActivity normally (no NEW_TASK / CLEAR_TOP flags)
                        try {
                            startActivity(intent);
                            finish();
                        } catch (Exception ex) {
                            // Log the exception to an internal crash log so you can pull it for analysis
                            try {
                                File f = new File(getFilesDir(), "register_start_error.txt");
                                FileWriter fw = new FileWriter(f, true);
                                fw.write("\n---- Start MainActivity error on " + System.currentTimeMillis() + " ----\n");
                                PrintWriter pw = new PrintWriter(fw);
                                ex.printStackTrace(pw);
                                pw.flush();
                                pw.close();
                                fw.close();
                            } catch (Exception ignored) {}
                            // Show a toast so user knows something went wrong instead of a crash.
                            Toast.makeText(RegisterActivity.this, "Failed to open app: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        String msg = "Registration failed.";
                        if (task.getException() != null) msg = task.getException().getMessage();
                        Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
