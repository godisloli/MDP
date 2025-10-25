package net.tiramisu.mdp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {
    private TextView tvUserEmail, tvBalance, tvIncome, tvExpense;

    public HomeFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvBalance = view.findViewById(R.id.tvBalance);
        tvIncome = view.findViewById(R.id.tvIncome);
        tvExpense = view.findViewById(R.id.tvExpense);

        // Fill user email and load financial data
        String email = null;
        String uid = null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            email = user.getEmail();
            uid = user.getUid();
        }

        if (tvUserEmail != null) {
            if (email != null && !email.isEmpty()) {
                tvUserEmail.setText(getString(R.string.welcome_user, email));
            } else {
                tvUserEmail.setText(R.string.welcome);
            }
        }

        if (uid != null && !uid.isEmpty()) {
            loadUserFinancialData(uid);
        } else {
            setDefaults();
        }
    }

    private void loadUserFinancialData(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc == null || !doc.exists()) {
                        setDefaults();
                        return;
                    }

                    NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));

                    Object balanceObj = doc.get("balance");
                    double balance = parseNumber(balanceObj);
                    if (tvBalance != null) tvBalance.setText(fmt.format(balance));

                    String monthKey;
                    try {
                        LocalDate now = LocalDate.now();
                        monthKey = now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                    } catch (Exception ex) {
                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        monthKey = cal.get(java.util.Calendar.YEAR) + "-" + String.format(Locale.US, "%02d", cal.get(java.util.Calendar.MONTH) + 1);
                    }

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

                    if (tvIncome != null) tvIncome.setText(fmt.format(0));
                    if (tvExpense != null) tvExpense.setText(fmt.format(0));
                })
                .addOnFailureListener(e -> setDefaults());
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
