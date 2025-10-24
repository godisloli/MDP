package net.tiramisu.mdp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_ADD_TRANSACTION = 1001;

    private TextView tvUserEmail;
    private TextView tvBalance;
    private TextView tvIncome;
    private TextView tvExpense;
    private RecyclerView rvTransactions;
    private TransactionAdapter transactionAdapter;
    private FloatingActionButton fabAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvBalance = findViewById(R.id.tvBalance);
        tvIncome = findViewById(R.id.tvIncome);
        tvExpense = findViewById(R.id.tvExpense);
        rvTransactions = findViewById(R.id.rvTransactions);
        fabAdd = findViewById(R.id.fabAdd);

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
                tvUserEmail.setText(getString(R.string.welcome_user, email));
            } else {
                tvUserEmail.setText(R.string.welcome);
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

        // --- Setup transactions RecyclerView with sample data ---
        if (rvTransactions != null) {
            rvTransactions.setLayoutManager(new LinearLayoutManager(this));

            List<Transaction> samples = new ArrayList<>();
            // Sample items (amount positive = income, negative = expense)
            samples.add(new Transaction("Lương", "20-10-2025", 17000000, R.drawable.ic_wallet));
            samples.add(new Transaction("Hóa đơn", "06-10-2025", -1500000, R.drawable.ic_bill));
            samples.add(new Transaction("Di chuyển", "05-10-2025", -300000, R.drawable.ic_transport));
            samples.add(new Transaction("Tiền thưởng", "01-10-2025", 500000, R.drawable.ic_wallet));

            transactionAdapter = new TransactionAdapter(samples);
            rvTransactions.setAdapter(transactionAdapter);
            rvTransactions.setNestedScrollingEnabled(false);
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, AddTransactionActivity.class);
                startActivityForResult(i, REQ_ADD_TRANSACTION);
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ADD_TRANSACTION && resultCode == RESULT_OK && data != null) {
            String title = data.getStringExtra(AddTransactionActivity.EXTRA_TITLE);
            String date = data.getStringExtra(AddTransactionActivity.EXTRA_DATE);
            double amount = data.getDoubleExtra(AddTransactionActivity.EXTRA_AMOUNT, 0.0);
            boolean isIncome = data.getBooleanExtra(AddTransactionActivity.EXTRA_IS_INCOME, false);

            // choose icon by category/simple heuristics
            int icon = R.drawable.ic_wallet;
            String category = data.getStringExtra(AddTransactionActivity.EXTRA_CATEGORY);
            if (category != null && category.contains("Đi lại")) icon = R.drawable.ic_transport;
            else if (category != null && category.contains("Hóa đơn")) icon = R.drawable.ic_bill;

            Transaction tx = new Transaction(category != null ? category : title, date != null ? date : "", amount, icon);
            if (transactionAdapter != null) {
                transactionAdapter.addTransaction(tx);
                if (rvTransactions != null) rvTransactions.scrollToPosition(0);
            }
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
                    NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));

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
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        monthKey = cal.get(java.util.Calendar.YEAR) + "-" + String.format(Locale.US, "%02d", cal.get(java.util.Calendar.MONTH) + 1);
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
            s = s.replaceAll("[^0-9.-]", "");
            if (s.isEmpty()) return 0.0;
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private void setDefaults() {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        if (tvBalance != null) tvBalance.setText(fmt.format(0));
        if (tvIncome != null) tvIncome.setText(fmt.format(0));
        if (tvExpense != null) tvExpense.setText(fmt.format(0));
    }
}