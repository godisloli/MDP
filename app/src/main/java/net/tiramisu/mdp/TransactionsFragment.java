package net.tiramisu.mdp;

import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView;
import android.text.TextWatcher;
import android.text.Editable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionsFragment extends Fragment {
    private TransactionAdapter adapter;
    private TransactionRepository repository;

    // cached full list from DB (used for filtering/sorting)
    private final List<TransactionEntity> allEntities = new ArrayList<>();

    // UI controls
    private EditText edtSearch;
    private Spinner spinnerSort;
    private ChipGroup chips;

    // listener - called by repository when data changes
    private final Runnable repoListener = () -> {
        String userId = "local";
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
        repository.getByUser(userId, entities -> {
            if (entities == null) entities = new ArrayList<>();
            synchronized (allEntities) {
                allEntities.clear();
                allEntities.addAll(entities);
            }
            if (getActivity() != null) getActivity().runOnUiThread(this::applyFilters);
        });
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

        repository = TransactionRepository.getInstance(requireContext());

        // find UI
        edtSearch = view.findViewById(R.id.edtSearch);
        spinnerSort = view.findViewById(R.id.spinnerSort);
        chips = view.findViewById(R.id.chips);
        RecyclerView rv = view.findViewById(R.id.rvTransactions);

        // setup RecyclerView
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new TransactionAdapter(new ArrayList<>());
            rv.setAdapter(adapter);
            ViewCompat.setOnApplyWindowInsetsListener(rv, (v, insets) -> {
                int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), Math.max(v.getPaddingBottom(), bottom + 16));
                return insets;
            });
        }

        // wire search to filter live
        if (edtSearch != null) {
            edtSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // wire spinner change
        if (spinnerSort != null) {
            spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) { applyFilters(); }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        // wire chips change
        if (chips != null) {
            // singleSelection is enabled in layout; simply reapply filters
            chips.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilters());
        }

        // load cache from DB initially
        String userId = "local";
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
        repository.getByUser(userId, entities -> {
            if (entities == null) entities = new ArrayList<>();
            synchronized (allEntities) {
                allEntities.clear();
                allEntities.addAll(entities);
            }
            if (getActivity() != null) getActivity().runOnUiThread(this::applyFilters);
        });

        // also refresh sums (month totals)
        refreshSums(view);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (repository != null) repository.registerChangeListener(repoListener);
        View v = getView(); if (v != null) refreshSums(v);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (repository != null) repository.unregisterChangeListener(repoListener);
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
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}

        long finalFrom = from, finalTo = to;
        String finalUserId = userId;
        repository.getSumIncomeInRange(userId, finalFrom, finalTo, income -> {
            repository.getSumExpenseInRange(finalUserId, finalFrom, finalTo, expense -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
                    double inc = income == null ? 0.0 : income;
                    double exp = expense == null ? 0.0 : expense;
                    Double cached = null;
                    try { cached = repository.getBaseBalanceLive(finalUserId).getValue(); } catch (Exception ignored) {}
                    if (cached != null) {
                        double b = cached;
                        if (tvTotalIncome != null) tvTotalIncome.setText(fmt.format(inc));
                        if (tvTotalExpense != null) tvTotalExpense.setText(fmt.format(Math.abs(exp)));
                        if (tvMonthBalance != null) tvMonthBalance.setText(fmt.format(b + inc + exp));
                    } else {
                        repository.getUserBaseBalance(finalUserId, (Double base) -> {
                            double b = base == null ? 0.0 : base;
                            if (getActivity() == null) return;
                            getActivity().runOnUiThread(() -> {
                                if (tvTotalIncome != null) tvTotalIncome.setText(fmt.format(inc));
                                if (tvTotalExpense != null) tvTotalExpense.setText(fmt.format(Math.abs(exp)));
                                if (tvMonthBalance != null) tvMonthBalance.setText(fmt.format(b + inc + exp));
                            });
                        });
                    }
                });
            });
        });
    }

    // Apply current search, chip-filter and sort to allEntities and update adapter
    private void applyFilters() {
        if (adapter == null) return;
        View view = getView(); if (view == null) return;

        String q = "";
        if (edtSearch != null) q = edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT);

        // chip filter
        String typeFilter = null; // null=all, "income" or "expense"
        if (chips != null) {
            int checked = chips.getCheckedChipId();
            if (checked == R.id.chIncome) typeFilter = "income";
            else if (checked == R.id.chExpense) typeFilter = "expense";
        }

        // build filtered pairs (Transaction + timestamp) so we can sort by timestamp if needed
        List<Pair<Transaction, Long>> pairs = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));

        synchronized (allEntities) {
            for (TransactionEntity te : allEntities) {
                if (te == null) continue;
                if (typeFilter != null && !typeFilter.equalsIgnoreCase(te.type == null ? "" : te.type)) continue;

                String dateStr = "";
                try { dateStr = df.format(new Date(te.timestamp)); } catch (Exception ignored) {}

                boolean match = q.isEmpty();
                if (!match) {
                    String note = te.note == null ? "" : te.note.toLowerCase(Locale.ROOT);
                    String cat = te.category == null ? "" : te.category.toLowerCase(Locale.ROOT);
                    if (note.contains(q) || cat.contains(q) || dateStr.toLowerCase(Locale.ROOT).contains(q)) match = true;
                }
                if (!match) continue;

                int icon = R.drawable.ic_transaction;
                if ("Ăn uống".equals(te.category)) icon = R.drawable.ic_food;
                if ("Đi lại".equals(te.category)) icon = R.drawable.ic_transport;
                if ("Mua sắm".equals(te.category)) icon = R.drawable.ic_shopping;
                if ("Giải trí".equals(te.category)) icon = R.drawable.ic_entertainment;

                String title = te.category != null && !te.category.isEmpty() ? te.category : (te.note != null && !te.note.isEmpty() ? te.note : "Giao dịch");
                Transaction t = new Transaction(title, dateStr, te.amount, icon);
                pairs.add(new Pair<>(t, te.timestamp));
            }
        }

        int sortPos = spinnerSort == null ? 0 : spinnerSort.getSelectedItemPosition();
        switch (sortPos) {
            case 0: // Newest
                Collections.sort(pairs, (p1, p2) -> Long.compare(p2.second, p1.second));
                break;
            case 1: // Oldest
                Collections.sort(pairs, (p1, p2) -> Long.compare(p1.second, p2.second));
                break;
            case 2: // Amount asc
                Collections.sort(pairs, (p1, p2) -> Double.compare(p1.first.amount, p2.first.amount));
                break;
            case 3: // Amount desc
                Collections.sort(pairs, (p1, p2) -> Double.compare(p2.first.amount, p1.first.amount));
                break;
            default:
                break;
        }

        List<Transaction> out = new ArrayList<>();
        for (Pair<Transaction, Long> p : pairs) out.add(p.first);
        adapter.setItems(out);
    }

    // Called externally (e.g., MainActivity) to optimistically show a newly added transaction.
    // It updates the adapter immediately and refreshes the cached list from DB to stay authoritative.
    public void addTransaction(Transaction tx) {
        if (tx == null) return;
        // optimistic UI update
        if (adapter != null) adapter.addTransaction(tx);

        // Refresh authoritative data from DB in background
        try {
            String userId = "local";
            try { if (FirebaseAuth.getInstance().getCurrentUser() != null) userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
            repository.getByUser(userId, entities -> {
                if (entities == null) entities = new ArrayList<>();
                synchronized (allEntities) {
                    allEntities.clear();
                    allEntities.addAll(entities);
                }
                if (getActivity() != null) getActivity().runOnUiThread(this::applyFilters);
            });
        } catch (Exception ignored) {}
    }
}
