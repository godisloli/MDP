package net.tiramisu.mdp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView tvUserEmail;
    private TextView tvBalance;
    private TextView tvIncome;
    private TextView tvExpense;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvBalance = findViewById(R.id.tvBalance);
        tvIncome = findViewById(R.id.tvIncome);
        tvExpense = findViewById(R.id.tvExpense);

        // Try to read extras passed from Login/Register
        String email = getIntent().getStringExtra("EXTRA_USER_EMAIL");
        String uid = getIntent().getStringExtra("EXTRA_USER_UID");

        if (email == null || email.isEmpty()) {
            // Fallback to FirebaseAuth current user
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                email = user.getEmail();
                uid = user.getUid();
            }
        }

        if (tvUserEmail != null) {
            if (email != null && !email.isEmpty()) {
                tvUserEmail.setText("Welcome, " + email);
            } else {
                tvUserEmail.setText("Welcome");
            }
        }

        // If we have a uid, load financial data from Firestore
        if (uid != null && !uid.isEmpty()) {
            loadUserFinancialData(uid);
        } else {
            // Clear or show defaults
            if (tvBalance != null) tvBalance.setText("0 ₫");
            if (tvIncome != null) tvIncome.setText("0 ₫");
            if (tvExpense != null) tvExpense.setText("0 ₫");
        }
    }

    private void loadUserFinancialData(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc == null || !doc.exists()) {
                        // no data
                        setDefaults();
                        return;
                    }

                    // Format currency for Vietnam
                    NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

                    // balance field
                    Object balanceObj = doc.get("balance");
                    double balance = parseNumber(balanceObj);
                    if (tvBalance != null) tvBalance.setText(fmt.format(balance));

                    // Determine current month key in format YYYY-MM
                    String monthKey;
                    try {
                        LocalDate now = LocalDate.now();
                        monthKey = now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    } catch (Exception ex) {
                        // fallback to simple year-month
                        monthKey = String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
                                + "-" + String.format(Locale.US, "%02d", java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1);
                    }

                    // monthData expected as map: { "2025-10": { income: number, expense: number }, ... }
                    Object monthDataObj = doc.get("monthData");
                    if (monthDataObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> monthDataMap = (Map<String, Object>) monthDataObj;
                        Object thisMonthObj = monthDataMap.get(monthKey);
                        if (thisMonthObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> m = (Map<String, Object>) thisMonthObj;
                            double income = parseNumber(m.get("income"));
                            double expense = parseNumber(m.get("expense"));
                            if (tvIncome != null) tvIncome.setText(fmt.format(income));
                            if (tvExpense != null) tvExpense.setText(fmt.format(expense));
                            return;
                        }
                    }

                    // If monthData not present or not in expected format, set defaults
                    if (tvIncome != null) tvIncome.setText(fmt.format(0));
                    if (tvExpense != null) tvExpense.setText(fmt.format(0));
                })
                .addOnFailureListener(e -> {
                    // on error, set defaults
                    setDefaults();
                });
    }

    private double parseNumber(Object obj) {
        if (obj == null) return 0.0;
        try {
            if (obj instanceof Number) return ((Number) obj).doubleValue();
            String s = obj.toString();
            s = s.replaceAll("[^0-9\\.-]", "");
            if (s.isEmpty()) return 0.0;
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private void setDefaults() {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        if (tvBalance != null) tvBalance.setText(fmt.format(0));
        if (tvIncome != null) tvIncome.setText(fmt.format(0));
        if (tvExpense != null) tvExpense.setText(fmt.format(0));
    }
}