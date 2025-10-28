package net.tiramisu.mdp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class HomeFragment extends Fragment {
    private TextView tvUserEmail, tvBalance, tvIncome, tvExpense;
    private TransactionRepository repository;
    private double currentBaseBalance = 0.0;
    private RecyclerView rvRecent;
    private TransactionAdapter recentAdapter;

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
        rvRecent = view.findViewById(R.id.rvRecent);

        repository = new TransactionRepository(requireContext());

        // prepare recent recycler
        if (rvRecent != null) {
            rvRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
            recentAdapter = new TransactionAdapter(new ArrayList<>());
            rvRecent.setAdapter(recentAdapter);
        }

        // Fill user email and load financial data
        String email = null;
        String uid;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            email = user.getEmail();
            uid = user.getUid();
        } else {
            uid = null;
        }

        if (tvUserEmail != null) {
            if (email != null && !email.isEmpty()) {
                tvUserEmail.setText(getString(R.string.welcome_user, email));
            } else {
                tvUserEmail.setText(R.string.welcome);
            }
        }

        // make balance editable on tap
        if (tvBalance != null) {
            tvBalance.setOnClickListener(v -> showEditBalanceDialog(uid));
        }

        if (uid != null && !uid.isEmpty()) {
            // migrate any local transactions into the user account so sums and recent list are preserved
            repository.migrateUserId("local", uid, () -> {
                // after migration, load base balance and sums and recent
                loadUserBaseBalance(uid);
                loadMonthlySums(uid);
                loadRecent(uid);
            });
        } else {
            // load local balance from SharedPreferences
            SharedPreferences sp = requireContext().getSharedPreferences("mdp_local", Context.MODE_PRIVATE);
            currentBaseBalance = Double.longBitsToDouble(sp.getLong("local_balance_bits", Double.doubleToLongBits(0.0)));
            updateBalanceDisplay(currentBaseBalance);
            loadMonthlySums("local");
            loadRecent("local");
        }
    }

    public void refreshData() {
        String uid = null;
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
        } catch (Exception ignored) {}

        if (uid != null && !uid.isEmpty()) {
            loadUserBaseBalance(uid);
            loadMonthlySums(uid);
            loadRecent(uid);
        } else {
            SharedPreferences sp = requireContext().getSharedPreferences("mdp_local", Context.MODE_PRIVATE);
            currentBaseBalance = Double.longBitsToDouble(sp.getLong("local_balance_bits", Double.doubleToLongBits(0.0)));
            updateBalanceDisplay(currentBaseBalance);
            loadMonthlySums("local");
            loadRecent("local");
        }
    }

    private void loadRecent(String userId) {
        repository.getNewestTransactions(userId, 5, (List<TransactionEntity> list) -> {
            if (list == null) return;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (recentAdapter == null) return;
                java.util.List<Transaction> temp = new java.util.ArrayList<>();
                // convert each TransactionEntity -> Transaction model and add to temp list
                for (TransactionEntity te : list) {
                    int icon = R.drawable.ic_transaction;
                    if ("Ăn uống".equals(te.category)) icon = R.drawable.ic_food;
                    if ("Đi lại".equals(te.category)) icon = R.drawable.ic_transport;
                    if ("Mua sắm".equals(te.category)) icon = R.drawable.ic_shopping;
                    if ("Giải trí".equals(te.category)) icon = R.drawable.ic_entertainment;
                    String title = te.category != null ? te.category : "Giao dịch";
                    Transaction t = new Transaction(title, "now", te.amount, icon);
                    temp.add(t);
                }
                recentAdapter.setItems(temp);
            });
        });
    }

    private void loadMonthlySums(String userId) {
        // compute start and end of current month in millis
        long from, to;
        try {
            LocalDate now = LocalDate.now();
            LocalDate start = now.withDayOfMonth(1);
            LocalDate end = now.withDayOfMonth(now.lengthOfMonth());
            from = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            to = end.atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ex) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
            from = cal.getTimeInMillis();
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            cal.set(Calendar.HOUR_OF_DAY,23); cal.set(Calendar.MINUTE,59); cal.set(Calendar.SECOND,59);
            to = cal.getTimeInMillis();
        }

        final String uid = userId;
        // fetch sums asynchronously
        long finalFrom = from;
        long finalTo = to;
        repository.getSumIncomeInRange(uid, from, to, (Double income) -> {
            repository.getSumExpenseInRange(uid, finalFrom, finalTo, (Double expense) -> {
                // update UI on main thread
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
                    if (tvIncome != null) tvIncome.setText(fmt.format(income == null ? 0.0 : income));
                    if (tvExpense != null) tvExpense.setText(fmt.format(Math.abs(expense == null ? 0.0 : expense)));
                    // also update balance display: base + net change this month
                    double net = (income == null ? 0.0 : income) + (expense == null ? 0.0 : expense); // expense is negative sum
                    updateBalanceDisplay(currentBaseBalance + net);
                });
            });
        });
    }

    private void loadUserBaseBalance(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    if (doc == null || !doc.exists()) {
                        currentBaseBalance = 0.0;
                    } else {
                        Object balanceObj = doc.get("balance");
                        currentBaseBalance = parseNumber(balanceObj);
                    }
                    updateBalanceDisplay(currentBaseBalance);
                })
                .addOnFailureListener(e -> {
                    currentBaseBalance = 0.0;
                    updateBalanceDisplay(currentBaseBalance);
                });
    }

    private void updateBalanceDisplay(double value) {
        if (tvBalance == null) return;
        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        tvBalance.setText(fmt.format(value));
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

    private void showEditBalanceDialog(String uid) {
        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setTitle("Chỉnh sửa số dư");
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        // Show the displayed total (base + current month net) as hint to the user, because the user
        // expects to edit the visible total. Compute net asynchronously and update the hint when ready.
        final String targetUserForHint = (uid != null && !uid.isEmpty()) ? uid : "local";
        input.setHint(fmt.format(currentBaseBalance));
        computeCurrentMonthNet(targetUserForHint, (Double net) -> {
            double netVal = net == null ? 0.0 : net;
            final double displayed = currentBaseBalance + netVal;
            if (getActivity() != null) getActivity().runOnUiThread(() -> input.setHint(fmt.format(displayed)));
        });
        b.setView(input);
        b.setPositiveButton("Lưu", (DialogInterface dialog, int which) -> {
            String s = input.getText() == null ? "" : input.getText().toString().trim();
            double v = 0.0;
            try { v = Double.parseDouble(s.replaceAll("[^0-9.-]", "")); } catch (Exception ignored) {}
            // update either Firestore or SharedPreferences
            // We interpret the user's input `v` as the desired displayed total balance (base + net_month).
            // To persist properly we need to store the base balance = v - net_month, so later when sums are
            // added the displayed value equals what the user entered.
            final double enteredDisplayed = v;
            final String targetUser = (uid != null && !uid.isEmpty()) ? uid : "local";

            computeCurrentMonthNet(targetUser, (Double net) -> {
                double netVal = net == null ? 0.0 : net;
                double baseToStore = enteredDisplayed - netVal;

                if (targetUser.equals("local")) {
                    saveLocalBalance(baseToStore);
                    currentBaseBalance = baseToStore;
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        loadMonthlySums("local");
                        loadRecent("local");
                    });
                } else {
                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    Map<String, Object> update = new HashMap<>();
                    update.put("balance", baseToStore);
                    db.collection("users").document(targetUser).set(update, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                currentBaseBalance = baseToStore;
                                // refresh UI on main thread
                                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                                    loadMonthlySums(targetUser);
                                    loadRecent(targetUser);
                                });
                            })
                            .addOnFailureListener(e -> {
                                // fallback: persist locally but still update UI
                                saveLocalBalance(baseToStore);
                                currentBaseBalance = baseToStore;
                                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                                    loadMonthlySums(targetUser);
                                    loadRecent(targetUser);
                                });
                            });
                }
            });
        });
        b.setNegativeButton("Hủy", (DialogInterface dialog, int which) -> dialog.dismiss());
        b.show();
    }

    private void saveLocalBalance(double v) {
        SharedPreferences sp = requireContext().getSharedPreferences("mdp_local", Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putLong("local_balance_bits", Double.doubleToLongBits(v));
        ed.apply();
    }

    // Explanation: add helper to compute the net change (income + expense) for the current month
    // so we can correctly compute displayed balance = base balance + net-of-month when user edits base.
    private void computeCurrentMonthNet(String userId, java.util.function.Consumer<Double> callback) {
        long from, to;
        try {
            LocalDate now = LocalDate.now();
            LocalDate start = now.withDayOfMonth(1);
            LocalDate end = now.withDayOfMonth(now.lengthOfMonth());
            from = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            to = end.atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ex) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
            from = cal.getTimeInMillis();
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            cal.set(Calendar.HOUR_OF_DAY,23); cal.set(Calendar.MINUTE,59); cal.set(Calendar.SECOND,59);
            to = cal.getTimeInMillis();
        }

        final long finalFrom = from;
        final long finalTo = to;
        // use repository methods to fetch income and expense and combine them
        repository.getSumIncomeInRange(userId, finalFrom, finalTo, (Double income) -> {
            repository.getSumExpenseInRange(userId, finalFrom, finalTo, (Double expense) -> {
                double inc = income == null ? 0.0 : income;
                double exp = expense == null ? 0.0 : expense; // expense may be negative in DB
                double net = inc + exp;
                if (callback != null) callback.accept(net);
            });
        });
    }
}
