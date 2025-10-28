package net.tiramisu.mdp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TransactionsFragment extends Fragment {
    private TransactionAdapter adapter;
    private TransactionRepository repository;

    // listener to refresh sums when DB changes
    private final Runnable repoListener = () -> {
        // repository now posts listeners on the main thread; perform UI updates directly
        View v = getView();
        if (v != null) {
            refreshSums(v);
            // reload latest transactions for the current user
            String userId = "local";
            try { if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
            repository.getByUser(userId, entities -> {
                if (entities == null) return;
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (adapter == null) return;
                    adapter.clear();
                    for (TransactionEntity te : entities) {
                        int icon = R.drawable.ic_transaction;
                        if ("Ăn uống".equals(te.category)) icon = R.drawable.ic_food;
                        if ("Đi lại".equals(te.category)) icon = R.drawable.ic_transport;
                        if ("Mua sắm".equals(te.category)) icon = R.drawable.ic_shopping;
                        if ("Giải trí".equals(te.category)) icon = R.drawable.ic_entertainment;
                        Transaction t = new Transaction(te.category != null ? te.category : "Giao dịch", "now", te.amount, icon);
                        adapter.addTransaction(t);
                    }
                });
            });
        }
    };

    public TransactionsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transactions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Ensure the month title is shown (the header is included in layouts for both tabs)
        try {
            LocalDate now = LocalDate.now();
            TextView tvMonthTitle = view.findViewById(R.id.tvMonthTitle);
            if (tvMonthTitle != null) {
                tvMonthTitle.setText(now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi-VN"))));
                // not clickable in transactions screen, but ensure visible
                tvMonthTitle.setVisibility(View.VISIBLE);
            }
        } catch (Exception ignored) {}

        repository = net.tiramisu.mdp.repo.TransactionRepository.getInstance(requireContext());

        RecyclerView rv = view.findViewById(R.id.rvTransactions);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));

            // start with empty list; we'll populate from DB
            List<Transaction> list = new ArrayList<>();
            adapter = new TransactionAdapter(list);
            rv.setAdapter(adapter);

            // determine user id (use FirebaseAuth if available)
            String userId = "local";
            try {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                }
            } catch (Exception ignored) {}

            // load from repository
            repository.getByUser(userId, entities -> {
                Handler h = new Handler(Looper.getMainLooper());
                h.post(() -> {
                    // create Transaction objects in reverse order to keep newest first
                    for (TransactionEntity te : entities) {
                        int icon = R.drawable.ic_transaction;
                        if ("Ăn uống".equals(te.category)) icon = R.drawable.ic_food;
                        if ("Đi lại".equals(te.category)) icon = R.drawable.ic_transport;
                        if ("Mua sắm".equals(te.category)) icon = R.drawable.ic_shopping;
                        if ("Giải trí".equals(te.category)) icon = R.drawable.ic_entertainment;

                        Transaction t = new Transaction(te.category != null ? te.category : "Giao dịch", "now", te.amount, icon);
                        adapter.addTransaction(t);
                    }
                });
            });

            // observe base balance LiveData so UI updates immediately when base changes
            try {
                final String uidForLive = userId;
                repository.getBaseBalanceLive(uidForLive).observe(getViewLifecycleOwner(), (Double base) -> {
                    Log.d("TransactionsFrag", "baseLive changed for " + uidForLive + " -> " + base);
                    View root = getView();
                    if (root != null) refreshSums(root);
                });
            } catch (Exception ignored) {}

            // initial sums load
            refreshSums(view);

            // make RV respect system window insets so last item is not hidden by bottom nav
            ViewCompat.setOnApplyWindowInsetsListener(rv, (v, insets) -> {
                int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), Math.max(v.getPaddingBottom(), bottom + 16));
                return insets;
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (repository != null) repository.registerChangeListener(repoListener);
        // ensure we refresh totals when the fragment becomes visible again (catch missed changes)
        View v = getView();
        if (v != null) refreshSums(v);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (repository != null) repository.unregisterChangeListener(repoListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v != null) refreshSums(v);
    }

    private void refreshSums(@NonNull View view) {
        TextView tvTotalIncome = view.findViewById(R.id.tvTotalIncome);
        TextView tvTotalExpense = view.findViewById(R.id.tvTotalExpense);
        TextView tvMonthBalance = view.findViewById(R.id.tvMonthBalance);

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

        String userId = "local";
        try { if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}

        long finalFrom = from, finalTo = to;
        String finalUserId = userId;
        repository.getSumIncomeInRange(userId, finalFrom, finalTo, income -> {
            repository.getSumExpenseInRange(finalUserId, finalFrom, finalTo, expense -> {
                 if (getActivity() == null) return;
                 // Prefer cached base balance from LiveData to avoid overwriting user-entered value
                 getActivity().runOnUiThread(() -> {
                     NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
                     double inc = income == null ? 0.0 : income;
                     double exp = expense == null ? 0.0 : expense;
                     Double cached = null;
                     try {
                         cached = repository.getBaseBalanceLive(finalUserId).getValue();
                     } catch (Exception ignored) {}
                     if (cached != null) {
                         double b = cached;
                         Log.d("TransactionsFrag", "Using cached base for " + finalUserId + " = " + b);
                         if (tvTotalIncome != null) tvTotalIncome.setText(fmt.format(inc));
                         if (tvTotalExpense != null) tvTotalExpense.setText(fmt.format(Math.abs(exp)));
                         if (tvMonthBalance != null) tvMonthBalance.setText(fmt.format(b + inc + exp));
                         // also set activity-level shared summaries if present
                         if (getActivity() != null) {
                             try {
                                 TextView actInc = getActivity().findViewById(R.id.tvTotalIncome);
                                 TextView actExp = getActivity().findViewById(R.id.tvTotalExpense);
                                 TextView actBal = getActivity().findViewById(R.id.tvMonthBalance);
                                 if (actInc != null) actInc.setText(fmt.format(inc));
                                 if (actExp != null) actExp.setText(fmt.format(Math.abs(exp)));
                                 if (actBal != null) actBal.setText(fmt.format(b + inc + exp));
                             } catch (Exception ignored) {}
                         }
                     } else {
                         Log.d("TransactionsFrag", "Cached base not available for " + finalUserId + "; falling back to storage");
                         // fallback to reading storage if LiveData not yet initialized
                         repository.getUserBaseBalance(finalUserId, (Double base) -> {
                            double b = base == null ? 0.0 : base;
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                if (tvTotalIncome != null) tvTotalIncome.setText(fmt.format(inc));
                                if (tvTotalExpense != null) tvTotalExpense.setText(fmt.format(Math.abs(exp)));
                                if (tvMonthBalance != null) tvMonthBalance.setText(fmt.format(b + inc + exp));
                                // also update activity-level shared summaries
                                try {
                                    TextView actInc = getActivity().findViewById(R.id.tvTotalIncome);
                                    TextView actExp = getActivity().findViewById(R.id.tvTotalExpense);
                                    TextView actBal = getActivity().findViewById(R.id.tvMonthBalance);
                                    if (actInc != null) actInc.setText(fmt.format(inc));
                                    if (actExp != null) actExp.setText(fmt.format(Math.abs(exp)));
                                    if (actBal != null) actBal.setText(fmt.format(b + inc + exp));
                                } catch (Exception ignored) {}
                            });
                        });
                    }
                });
             });
         });
    }

    // Allow external code to add a transaction to the UI
    public void addTransaction(Transaction tx) {
        if (adapter != null) adapter.addTransaction(tx);
    }
}
