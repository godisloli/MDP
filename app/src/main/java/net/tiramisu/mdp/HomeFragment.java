package net.tiramisu.mdp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {
    private static final int REQUEST_VIEW_DETAILS = 1001;

    private TextView tvBalance;
    private TextView tvIncome;
    private TextView tvExpense;
    private TextView tvIncomeComparison;
    private TextView tvExpenseComparison;
    private TextView tvIncomeChange;
    private TextView tvExpenseChange;
    private TransactionRepository repository;
    private double currentBaseBalance = 0.0;
    private TransactionAdapter recentAdapter;

    public HomeFragment() {}

    // listener to refresh data when transactions change
    private final Runnable repoListener = () -> {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(this::refreshData);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        tvBalance = view.findViewById(R.id.tvBalance);
        tvIncome = view.findViewById(R.id.tvIncome);
        tvExpense = view.findViewById(R.id.tvExpense);
        tvIncomeComparison = view.findViewById(R.id.tvIncomeComparison);
        tvExpenseComparison = view.findViewById(R.id.tvExpenseComparison);
        tvIncomeChange = view.findViewById(R.id.tvIncomeChange);
        tvExpenseChange = view.findViewById(R.id.tvExpenseChange);
        RecyclerView rvRecent = view.findViewById(R.id.rvRecent);
        com.google.android.material.button.MaterialButton btnGenerateTestData = view.findViewById(R.id.btnGenerateTestData);

        repository = net.tiramisu.mdp.repo.TransactionRepository.getInstance(requireContext());

        // prepare recent recycler
        if (rvRecent != null) {
            rvRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
            recentAdapter = new TransactionAdapter(new ArrayList<>());

            // Set click listener to open transaction details
            recentAdapter.setOnItemClickListener(transaction -> {
                android.content.Intent intent = new android.content.Intent(requireContext(), ViewDetailsActivity.class);
                intent.putExtra("EXTRA_TITLE", transaction.title);
                intent.putExtra("EXTRA_DATE", transaction.date);
                intent.putExtra("EXTRA_AMOUNT", transaction.amount);
                intent.putExtra("EXTRA_CATEGORY", transaction.category);
                intent.putExtra("EXTRA_NOTE", transaction.note);
                intent.putExtra("EXTRA_TIMESTAMP", transaction.extraLong); // Pass timestamp
                startActivityForResult(intent, REQUEST_VIEW_DETAILS);
            });

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
        // capture uid as effectively final for use in lambdas
        final String targetUid = uid;

        if (tvUserEmail != null) {
            if (email != null && !email.isEmpty()) {
                tvUserEmail.setText(getString(R.string.welcome_user, email));
            } else {
                tvUserEmail.setText(R.string.welcome);
            }
        }

        // make balance editable on tap
        if (tvBalance != null) {
            tvBalance.setOnClickListener(v -> showEditBalanceDialog(targetUid));
        }

        // Setup test data generation button
        if (btnGenerateTestData != null) {
            btnGenerateTestData.setOnClickListener(v -> generateTestData());
        }

        if (targetUid != null && !targetUid.isEmpty()) {
            // migrate any local transactions into the user account so sums and recent list are preserved
            repository.migrateUserId("local", targetUid, () -> {
                // after migration, load base balance and then load sums and recent
                loadUserBaseBalance(targetUid, () -> {
                    loadMonthlySums(targetUid);
                    loadRecent(targetUid);
                    loadMonthComparison(targetUid);
                });
            });
        } else {
            // load local balance from SharedPreferences
            SharedPreferences sp = requireContext().getSharedPreferences("mdp_local", Context.MODE_PRIVATE);
            currentBaseBalance = Double.longBitsToDouble(sp.getLong("local_balance_bits", Double.doubleToLongBits(0.0)));
            updateBalanceDisplay(currentBaseBalance);
            loadMonthlySums("local");
            loadRecent("local");
            loadMonthComparison("local");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (repository != null) repository.registerChangeListener(repoListener);
        // ensure we refresh when returning to this fragment (catch missed changes)
        refreshData();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (repository != null) repository.unregisterChangeListener(repoListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIEW_DETAILS && resultCode == android.app.Activity.RESULT_OK) {
            // Transaction was deleted or modified, refresh all data
            refreshData();
        }
    }

    public void refreshData() {
        String uid = null;
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
        } catch (Exception ignored) {}

        final String targetUid = uid;
        Log.d("HomeFrag", "refreshData() called, uid=" + targetUid + ", currentBaseBalance=" + currentBaseBalance);

        if (uid != null && !uid.isEmpty()) {
            // Prefer cached base (LiveData) or recently-manually-set base to avoid a race where
            // a remote fetch (that may still be propagating) overwrites the user's entered value.
            try {
                Double cached = null;
                try { cached = repository.getBaseBalanceLive(targetUid).getValue(); } catch (Exception ignored) {}
                if (cached != null) {
                    currentBaseBalance = cached;
                    updateBalanceDisplay(currentBaseBalance);
                    loadMonthlySums(targetUid);
                    loadRecent(targetUid);
                    loadMonthComparison(targetUid);
                } else if (repository != null && repository.wasManuallyUpdatedRecently(targetUid, 5000)) {
                    // use the in-memory currentBaseBalance (set when user saved) and refresh UI
                    updateBalanceDisplay(currentBaseBalance);
                    loadMonthlySums(targetUid);
                    loadRecent(targetUid);
                    loadMonthComparison(targetUid);
                } else {
                    // no cached value and no recent manual update -> fetch from Firestore
                    loadUserBaseBalance(targetUid, () -> {
                        loadMonthlySums(targetUid);
                        loadRecent(targetUid);
                        loadMonthComparison(targetUid);
                    });
                }
            } catch (Exception ex) {
                // fallback to the previous behavior on any error
                loadUserBaseBalance(targetUid, () -> {
                    loadMonthlySums(targetUid);
                    loadRecent(targetUid);
                    loadMonthComparison(targetUid);
                });
            }
        } else {
            // load local balance from SharedPreferences
            SharedPreferences sp = requireContext().getSharedPreferences("mdp_local", Context.MODE_PRIVATE);
            currentBaseBalance = Double.longBitsToDouble(sp.getLong("local_balance_bits", Double.doubleToLongBits(0.0)));
            updateBalanceDisplay(currentBaseBalance);
            loadMonthlySums("local");
            loadRecent("local");
            loadMonthComparison("local");
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
                    Transaction t = getTransaction(te);
                    temp.add(t);
                }
                recentAdapter.setItems(temp);
            });
        });
    }

    @NonNull
    private Transaction getTransaction(TransactionEntity te) {
        int icon = R.drawable.ic_transaction;
        if (CategoryHelper.KEY_FOOD.equals(te.category)) icon = R.drawable.ic_food;
        if (CategoryHelper.KEY_TRANSPORT.equals(te.category)) icon = R.drawable.ic_transport;
        if (CategoryHelper.KEY_SHOPPING.equals(te.category)) icon = R.drawable.ic_shopping;
        if (CategoryHelper.KEY_ENTERTAINMENT.equals(te.category)) icon = R.drawable.ic_entertainment;

        // Use title if available, otherwise use localized category as title
        String title;
        if (te.title != null && !te.title.isEmpty()) {
            title = te.title;
        } else if (te.category != null) {
            title = CategoryHelper.getLocalizedCategory(getContext(), te.category);
        } else {
            title = getString(R.string.transaction_title_default);
        }

        // Format date string from timestamp
        String dateStr = "now";
        try {
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
            dateStr = df.format(new java.util.Date(te.timestamp));
        } catch (Exception ignored) {}

        Transaction t = new Transaction(title, dateStr, te.amount, icon);
        t.extraLong = te.timestamp; // Store timestamp for deletion
        t.category = te.category;
        t.note = te.note;
        return t;
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
        repository.getSumIncomeInRange(uid, from, to, (Double income) -> repository.getSumExpenseInRange(uid, finalFrom, finalTo, (Double expense) -> {
            // update UI on main thread
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                Log.d("HomeFrag", "loadMonthlySums results: income=" + income + " expense=" + expense + " base=" + currentBaseBalance);
                if (tvIncome != null) tvIncome.setText(CurrencyUtils.formatCurrency(getContext(), income == null ? 0.0 : income));
                if (tvExpense != null) tvExpense.setText(CurrencyUtils.formatCurrency(getContext(), Math.abs(expense == null ? 0.0 : expense)));
                // also update balance display: base + net change this month
                double net = (income == null ? 0.0 : income) + (expense == null ? 0.0 : expense); // expense is negative sum
                updateBalanceDisplay(currentBaseBalance + net);
            });
        }));
    }

    // Load base balance for a user from Firestore. If 'after' is non-null, call it after loading (success or failure).
    private void loadUserBaseBalance(String uid, Runnable after) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(uid).get()
                .addOnSuccessListener((DocumentSnapshot doc) -> {
                    // if the user recently manually updated the base, prefer that and do not overwrite
                    if (repository != null && repository.wasManuallyUpdatedRecently(uid, 5000)) {
                        Log.d("HomeFrag", "loadUserBaseBalance: skipping overwrite because manual update recent for uid=" + uid);
                        if (after != null) after.run();
                        return;
                    }

                    if (doc == null || !doc.exists()) {
                        currentBaseBalance = 0.0;
                    } else {
                        Object balanceObj = doc.get("balance");
                        currentBaseBalance = parseNumber(balanceObj);
                    }
                    updateBalanceDisplay(currentBaseBalance);
                    Log.d("HomeFrag", "loadUserBaseBalance success: base=" + currentBaseBalance + " uid=" + uid);
                    if (after != null) after.run();
                })
                .addOnFailureListener(e -> {
                    if (repository != null && repository.wasManuallyUpdatedRecently(uid, 5000)) {
                        Log.d("HomeFrag", "loadUserBaseBalance failed but manual update recent, skipping overwrite for uid=" + uid);
                        if (after != null) after.run();
                        return;
                    }
                    currentBaseBalance = 0.0;
                    updateBalanceDisplay(currentBaseBalance);
                    Log.d("HomeFrag", "loadUserBaseBalance failed for uid=" + uid + ", err=" + e.getMessage());
                    if (after != null) after.run();
                });
    }

    private void updateBalanceDisplay(double value) {
        if (tvBalance == null) return;
        tvBalance.setText(CurrencyUtils.formatCurrency(getContext(), value));
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
        // Show the displayed total (base + current month net) as hint to the user, because the user
        // expects to edit the visible total. Compute net asynchronously and update the hint when ready.
        final String targetUserForHint = (uid != null && !uid.isEmpty()) ? uid : "local";
        input.setHint(CurrencyUtils.formatCurrency(getContext(), currentBaseBalance));
        computeCurrentMonthNet(targetUserForHint, (Double net) -> {
            double netVal = net == null ? 0.0 : net;
            final double displayed = currentBaseBalance + netVal;
            if (getActivity() != null) getActivity().runOnUiThread(() -> input.setHint(CurrencyUtils.formatCurrency(getContext(), displayed)));
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
                    if (repository != null) { repository.setCachedBaseBalance("local", baseToStore); repository.notifyChange(); }
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        loadMonthlySums("local");
                        loadRecent("local");
                    });
                } else {
                    // Immediately update cached base so UI shows the user's entered value while Firestore write runs
                    currentBaseBalance = baseToStore;
                    if (repository != null) { repository.setCachedBaseBalance(targetUser, baseToStore); repository.notifyChange(); }

                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                    Map<String, Object> update = new HashMap<>();
                    update.put("balance", baseToStore);
                    db.collection("users").document(targetUser).set(update, SetOptions.merge())
                            .addOnSuccessListener(aVoid -> {
                                // already cached and currentBaseBalance set; just refresh UI
                                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                                    loadMonthlySums(targetUser);
                                    loadRecent(targetUser);
                                });
                            })
                            .addOnFailureListener(e -> {
                                // fallback: persist locally but still keep cached value
                                saveLocalBalance(baseToStore);
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
        repository.getSumIncomeInRange(userId, finalFrom, finalTo, (Double income) -> repository.getSumExpenseInRange(userId, finalFrom, finalTo, (Double expense) -> {
            double inc = income == null ? 0.0 : income;
            double exp = expense == null ? 0.0 : expense; // expense may be negative in DB
            double net = inc + exp;
            if (callback != null) callback.accept(net);
        }));
    }

    // Load month-over-month comparison
    private void loadMonthComparison(String userId) {
        // Get current month range
        long currentFrom, currentTo;
        try {
            LocalDate now = LocalDate.now();
            LocalDate start = now.withDayOfMonth(1);
            LocalDate end = now.withDayOfMonth(now.lengthOfMonth());
            currentFrom = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            currentTo = end.atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ex) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
            currentFrom = cal.getTimeInMillis();
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            cal.set(Calendar.HOUR_OF_DAY,23); cal.set(Calendar.MINUTE,59); cal.set(Calendar.SECOND,59);
            currentTo = cal.getTimeInMillis();
        }

        // Get previous month range
        long previousFrom, previousTo;
        try {
            LocalDate now = LocalDate.now();
            LocalDate previousMonth = now.minusMonths(1);
            LocalDate start = previousMonth.withDayOfMonth(1);
            LocalDate end = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth());
            previousFrom = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            previousTo = end.atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ex) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MONTH, -1);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
            previousFrom = cal.getTimeInMillis();
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            cal.set(Calendar.HOUR_OF_DAY,23); cal.set(Calendar.MINUTE,59); cal.set(Calendar.SECOND,59);
            previousTo = cal.getTimeInMillis();
        }

        final String uid = userId;

        // Fetch current month income
        long finalPreviousFrom = previousFrom;
        long finalPreviousTo = previousTo;
        repository.getSumIncomeInRange(uid, currentFrom, currentTo, currentIncome -> {
            // Fetch previous month income
            repository.getSumIncomeInRange(uid, finalPreviousFrom, finalPreviousTo, previousIncome -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    updateIncomeComparison(currentIncome, previousIncome);
                });
            });
        });

        // Fetch current month expense
        long finalPreviousFrom1 = previousFrom;
        long finalPreviousTo1 = previousTo;
        repository.getSumExpenseInRange(uid, currentFrom, currentTo, currentExpense -> {
            // Fetch previous month expense
            repository.getSumExpenseInRange(uid, finalPreviousFrom1, finalPreviousTo1, previousExpense -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    updateExpenseComparison(currentExpense, previousExpense);
                });
            });
        });
    }

    private void updateIncomeComparison(Double current, Double previous) {
        if (tvIncomeComparison == null || tvIncomeChange == null) return;

        double currentVal = current == null ? 0.0 : current;
        double previousVal = previous == null ? 0.0 : previous;

        if (previousVal == 0.0) {
            tvIncomeComparison.setText(R.string.comparison_no_data);
            tvIncomeChange.setText("—");
            tvIncomeChange.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
            return;
        }

        String previousFormatted = CurrencyUtils.formatCurrency(getContext(), previousVal);
        tvIncomeComparison.setText(getString(R.string.comparison_vs_last_month, previousFormatted));

        double change = currentVal - previousVal;
        double percentChange = (change / previousVal) * 100;

        String changeText;
        int color;
        if (Math.abs(percentChange) < 0.01) {
            changeText = getString(R.string.comparison_no_change);
            color = getResources().getColor(android.R.color.darker_gray, null);
        } else if (percentChange > 0) {
            changeText = String.format("+%.1f%%", percentChange);
            color = getResources().getColor(android.R.color.holo_green_dark, null);
        } else {
            changeText = String.format("%.1f%%", percentChange);
            color = getResources().getColor(android.R.color.holo_red_dark, null);
        }

        tvIncomeChange.setText(changeText);
        tvIncomeChange.setTextColor(color);
    }

    private void updateExpenseComparison(Double current, Double previous) {
        if (tvExpenseComparison == null || tvExpenseChange == null) return;

        double currentVal = Math.abs(current == null ? 0.0 : current);
        double previousVal = Math.abs(previous == null ? 0.0 : previous);

        if (previousVal == 0.0) {
            tvExpenseComparison.setText(R.string.comparison_no_data);
            tvExpenseChange.setText("—");
            tvExpenseChange.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
            return;
        }

        String previousFormatted = CurrencyUtils.formatCurrency(getContext(), previousVal);
        tvExpenseComparison.setText(getString(R.string.comparison_vs_last_month, previousFormatted));

        double change = currentVal - previousVal;
        double percentChange = (change / previousVal) * 100;

        String changeText;
        int color;
        if (Math.abs(percentChange) < 0.01) {
            changeText = getString(R.string.comparison_no_change);
            color = getResources().getColor(android.R.color.darker_gray, null);
        } else if (percentChange > 0) {
            // For expenses, increase is bad (red)
            changeText = String.format("+%.1f%%", percentChange);
            color = getResources().getColor(android.R.color.holo_red_dark, null);
        } else {
            // For expenses, decrease is good (green)
            changeText = String.format("%.1f%%", percentChange);
            color = getResources().getColor(android.R.color.holo_green_dark, null);
        }

        tvExpenseChange.setText(changeText);
        tvExpenseChange.setTextColor(color);
    }

    private void generateTestData() {
        if (getContext() == null) return;

        // Show loading toast
        Toast.makeText(getContext(), R.string.test_data_generating, Toast.LENGTH_SHORT).show();

        TestDataGenerator generator = new TestDataGenerator(requireContext());
        generator.generateTestData(() -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), R.string.test_data_generated, Toast.LENGTH_LONG).show();
                    // Refresh all data
                    refreshData();
                });
            }
        });
    }
}
