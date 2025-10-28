package net.tiramisu.mdp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.model.CategorySum;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import android.graphics.Color;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class StatisticsFragment extends Fragment {
    public StatisticsFragment() {}

    private TransactionRepository repository;
    private long currentFrom, currentTo;
    private String currentUserId = "local";
    private CategorySumAdapter categoryAdapter;
    // keep last fetched sums so we can recompute display when base changes
    private double lastIncome = 0.0;
    private double lastExpense = 0.0;
    private PieChart pieChartExpense;
    private PieChart pieChartIncome;

    // listener to be notified when transactions change
    private final Runnable repoListener = () -> {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            View v = getView();
            if (v != null) refreshForCurrentMonth(v);
        });
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = net.tiramisu.mdp.repo.TransactionRepository.getInstance(requireContext());

        RecyclerView rv = view.findViewById(R.id.rvMonthTransactions);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            categoryAdapter = new CategorySumAdapter(new ArrayList<>());
            rv.setAdapter(categoryAdapter);
        }

        // ensure rv respects system windows so last item isn't hidden
        if (rv != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rv, (v, insets) -> {
                int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), Math.max(v.getPaddingBottom(), bottom + 16));
                return insets;
            });
        }

        // determine user id
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}

        // determine initial month: current month
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        LocalDate end = now.withDayOfMonth(now.lengthOfMonth());
        currentFrom = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        currentTo = end.atTime(23,59,59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        TextView tvMonthTitle = view.findViewById(R.id.tvMonthTitle);
        if (tvMonthTitle != null) {
            tvMonthTitle.setOnClickListener(v -> showMonthPicker());
            tvMonthTitle.setText(now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi-VN"))));
        }

        // find pie charts and configure basic appearance
        pieChartExpense = view.findViewById(R.id.pieChartExpense);
        pieChartIncome = view.findViewById(R.id.pieChartIncome);
        try {
            if (pieChartExpense != null) {
                pieChartExpense.getDescription().setEnabled(false);
                pieChartExpense.setUsePercentValues(true);
                pieChartExpense.setDrawHoleEnabled(true);
                pieChartExpense.setHoleColor(Color.TRANSPARENT);
                pieChartExpense.setHoleRadius(48f);
                pieChartExpense.setTransparentCircleRadius(54f);
                pieChartExpense.setCenterText("Chi tiêu\n(%)");
                pieChartExpense.setCenterTextSize(14f);
                pieChartExpense.setEntryLabelColor(Color.DKGRAY);
                pieChartExpense.setEntryLabelTextSize(12f);
                pieChartExpense.getLegend().setEnabled(true);
                pieChartExpense.getLegend().setVerticalAlignment(com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM);
                pieChartExpense.getLegend().setHorizontalAlignment(com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
                pieChartExpense.getLegend().setOrientation(com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL);
                pieChartExpense.getLegend().setDrawInside(false);
                pieChartExpense.setDrawEntryLabels(false);
                pieChartExpense.setRotationEnabled(false);
                pieChartExpense.setEntryLabelColor(Color.WHITE);
                pieChartExpense.setCenterTextColor(Color.DKGRAY);
                pieChartExpense.animateY(700);
            }
            if (pieChartIncome != null) {
                pieChartIncome.getDescription().setEnabled(false);
                pieChartIncome.setUsePercentValues(true);
                pieChartIncome.setDrawHoleEnabled(true);
                pieChartIncome.setHoleColor(Color.TRANSPARENT);
                pieChartIncome.setHoleRadius(48f);
                pieChartIncome.setTransparentCircleRadius(54f);
                pieChartIncome.setCenterText("Thu nhập\n(%)");
                pieChartIncome.setCenterTextSize(14f);
                pieChartIncome.setEntryLabelColor(Color.DKGRAY);
                pieChartIncome.setEntryLabelTextSize(12f);
                pieChartIncome.getLegend().setEnabled(true);
                pieChartIncome.getLegend().setVerticalAlignment(com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.BOTTOM);
                pieChartIncome.getLegend().setHorizontalAlignment(com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
                pieChartIncome.getLegend().setOrientation(com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL);
                pieChartIncome.getLegend().setDrawInside(false);
                pieChartIncome.setDrawEntryLabels(false);
                pieChartIncome.setRotationEnabled(false);
                pieChartIncome.setEntryLabelColor(Color.WHITE);
                pieChartIncome.setCenterTextColor(Color.DKGRAY);
                pieChartIncome.animateY(700);
            }
        } catch (Exception ignored) {}

        // observe base balance LiveData so displayed balance updates immediately when base changes
        try {
            String userId = "local";
            try { if (FirebaseAuth.getInstance().getCurrentUser() != null) userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
            final String uidForLive = userId;
            repository.getBaseBalanceLive(uidForLive).observe(getViewLifecycleOwner(), (Double base) -> {
                android.util.Log.d("StatisticsFrag", "baseLive changed for " + uidForLive + " -> " + base);
                View root = getView();
                if (root != null) refreshForCurrentMonth(root);
            });
        } catch (Exception ignored) {}

        // load sums and category breakdown
        refreshForCurrentMonth(view);

        // button still opens full month view
        Button btn = view.findViewById(R.id.btnOpenMonthView);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), ViewOneMonthActivity.class);
                if (tvMonthTitle != null) i.putExtra("EXTRA_MONTH_LABEL", tvMonthTitle.getText().toString());
                startActivity(i);
            });
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (repository != null) repository.registerChangeListener(repoListener);
        View v = getView();
        if (v != null) refreshForCurrentMonth(v);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (repository != null) repository.unregisterChangeListener(repoListener);
    }

    private void refreshForCurrentMonth(@NonNull View view) {
        TextView tvTotalIncome = view.findViewById(R.id.tvTotalIncome);
        TextView tvTotalExpense = view.findViewById(R.id.tvTotalExpense);
        TextView tvMonthBalance = view.findViewById(R.id.tvMonthBalance);

        // fetch totals
        String userId = "local";
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
        final String effectiveUser = userId;
        repository.getSumIncomeInRange(effectiveUser, currentFrom, currentTo, (Double income) -> {
            repository.getSumExpenseInRange(effectiveUser, currentFrom, currentTo, (Double expense) -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
                    double inc = income == null ? 0.0 : income;
                    double exp = expense == null ? 0.0 : expense;
                    // prefer cached LiveData base to avoid race with remote fetch
                    Double cached = null;
                    try { cached = repository.getBaseBalanceLive(effectiveUser).getValue(); } catch (Exception ignored) {}
                    // update last-known sums
                    lastIncome = inc;
                    lastExpense = exp;
                    if (cached != null) {
                        double b = cached;
                        android.util.Log.d("StatisticsFrag", "Using cached base for " + effectiveUser + " = " + b);
                        if (tvTotalIncome != null) tvTotalIncome.setText(fmt.format(inc));
                        if (tvTotalExpense != null) tvTotalExpense.setText(fmt.format(Math.abs(exp)));
                        if (tvMonthBalance != null) tvMonthBalance.setText(fmt.format(b + inc + exp));
                    } else {
                        android.util.Log.d("StatisticsFrag", "Cached base not available for " + effectiveUser + "; falling back to storage");
                        repository.getUserBaseBalance(effectiveUser, (Double base) -> {
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

        // fetch category sums
        repository.getCategorySumsInRange(effectiveUser, currentFrom, currentTo, (List<CategorySum> list) -> {
            if (list == null) list = new ArrayList<>();
            if (getActivity() == null) return;
            List<CategorySum> finalList = list;
            getActivity().runOnUiThread(() -> {
                // default categories (keep in sync with AddTransactionActivity)
                List<String> defaults = new ArrayList<>();
                defaults.add("Ăn uống"); defaults.add("Đi lại"); defaults.add("Mua sắm"); defaults.add("Học tập"); defaults.add("Giải trí"); defaults.add("Khác"); defaults.add("Thu nhập");

                // map returned sums by category
                java.util.Map<String, Double> map = new java.util.HashMap<>();
                for (CategorySum cs : finalList) {
                    if (cs == null) continue;
                    String k = cs.category == null ? "Khác" : cs.category;
                    map.put(k, cs.total == null ? 0.0 : cs.total);
                }

                // build merged list preserving default order
                List<CategorySum> merged = new ArrayList<>();
                for (String d : defaults) {
                    Double v = map.remove(d);
                    merged.add(new CategorySum(d, v == null ? 0.0 : v));
                }
                // add any extra categories returned from DB (not in defaults), sorted by total desc
                List<CategorySum> extras = new ArrayList<>();
                for (java.util.Map.Entry<String, Double> e : map.entrySet()) {
                    extras.add(new CategorySum(e.getKey(), e.getValue()));
                }
                extras.sort((a, b) -> Double.compare(Math.abs(b.total == null ? 0.0 : b.total), Math.abs(a.total == null ? 0.0 : a.total)));
                merged.addAll(extras);

                categoryAdapter.setItems(merged);

                // build maps for expense and income breakdown
                Map<String, Double> expenseMap = new HashMap<>();
                Map<String, Double> incomeMap = new HashMap<>();
                for (CategorySum cs : merged) {
                    if (cs == null) continue;
                    double val = cs.total == null ? 0.0 : cs.total;
                    String cat = cs.category == null ? "Khác" : cs.category;
                    if (val < 0) {
                        double a = Math.abs(val);
                        if (a > 0.0) expenseMap.put(cat, expenseMap.getOrDefault(cat, 0.0) + a);
                    } else if (val > 0) {
                        if (val > 0.0) incomeMap.put(cat, incomeMap.getOrDefault(cat, 0.0) + val);
                    }
                }

                // populate expense pie
                try {
                    populatePieChart(pieChartExpense, expenseMap);
                } catch (Exception ex) { android.util.Log.d("StatisticsFrag","populate expense pie failed: "+ex.getMessage()); }

                // populate income pie
                try {
                    populatePieChart(pieChartIncome, incomeMap);
                } catch (Exception ex) { android.util.Log.d("StatisticsFrag","populate income pie failed: "+ex.getMessage()); }
            });
        });
    }

    private void showMonthPicker() {
        // compute earliest month from DB; ensure at least current month is present
        repository.getMinTimestampForUser(currentUserId, (Long minTs) -> {
            long startTs = minTs == null ? System.currentTimeMillis() : minTs.longValue();
            // build list of months from startTs to now
            List<String> labels = new ArrayList<>();
            List<Long> monthFrom = new ArrayList<>();
            List<Long> monthTo = new ArrayList<>();

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(startTs);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND,0); cal.set(Calendar.MILLISECOND,0);
            long s = cal.getTimeInMillis();
            Calendar now = Calendar.getInstance();
            now.set(Calendar.DAY_OF_MONTH, 1);
            now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0); now.set(Calendar.SECOND,0); now.set(Calendar.MILLISECOND,0);
            long endStart = now.getTimeInMillis();

            // Ensure at least one month
            if (s > endStart) s = endStart;

            Calendar iter = Calendar.getInstance();
            iter.setTimeInMillis(s);
            while (iter.getTimeInMillis() <= endStart) {
                int y = iter.get(Calendar.YEAR);
                int m = iter.get(Calendar.MONTH); // 0-based
                LocalDate ld = LocalDate.of(y, m+1, 1);
                String label = ld.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi-VN")));
                // compute from/to
                long f = iter.getTimeInMillis();
                Calendar endCal = (Calendar) iter.clone();
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
                endCal.set(Calendar.HOUR_OF_DAY,23); endCal.set(Calendar.MINUTE,59); endCal.set(Calendar.SECOND,59);
                long t = endCal.getTimeInMillis();
                labels.add(label);
                monthFrom.add(f);
                monthTo.add(t);
                // next month
                iter.add(Calendar.MONTH, 1);
            }

            if (labels.isEmpty()) {
                labels.add(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("vi-VN"))));
                monthFrom.add(currentFrom);
                monthTo.add(currentTo);
            }

            // reverse labels so newest first
            java.util.Collections.reverse(labels);
            java.util.Collections.reverse(monthFrom);
            java.util.Collections.reverse(monthTo);

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                CharSequence[] items = labels.toArray(new CharSequence[0]);
                androidx.appcompat.app.AlertDialog.Builder b = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
                b.setTitle("Chọn tháng");
                b.setSingleChoiceItems(items, 0, (dialog, which) -> {
                    // since array reversed, pick monthFrom.get(which)
                    currentFrom = monthFrom.get(which);
                    currentTo = monthTo.get(which);
                    TextView tvMonthTitle = getView() == null ? null : getView().findViewById(R.id.tvMonthTitle);
                    if (tvMonthTitle != null) tvMonthTitle.setText(labels.get(which));
                    // refresh content
                    if (getView() != null) refreshForCurrentMonth(getView());
                    dialog.dismiss();
                });
                b.setNegativeButton("Hủy", (d, w) -> d.dismiss());
                b.show();
            });
        });
    }

    private void updateDisplayedMonthBalanceForUser(String userId) {
        View v = getView();
        if (v == null) return;
        TextView tvMonthBalance = v.findViewById(R.id.tvMonthBalance);
        TextView tvTotalIncome = v.findViewById(R.id.tvTotalIncome);
        TextView tvTotalExpense = v.findViewById(R.id.tvTotalExpense);
        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        Double cached = null;
        try { cached = repository.getBaseBalanceLive(userId).getValue(); } catch (Exception ignored) {}
        if (cached != null) {
            double b = cached;
            if (tvTotalIncome != null) tvTotalIncome.setText(fmt.format(lastIncome));
            if (tvTotalExpense != null) tvTotalExpense.setText(fmt.format(Math.abs(lastExpense)));
            if (tvMonthBalance != null) tvMonthBalance.setText(fmt.format(b + lastIncome + lastExpense));
        } else {
            repository.getUserBaseBalance(userId, (Double base) -> {
                double b = base == null ? 0.0 : base;
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (tvTotalIncome != null) tvTotalIncome.setText(fmt.format(lastIncome));
                    if (tvTotalExpense != null) tvTotalExpense.setText(fmt.format(Math.abs(lastExpense)));
                    if (tvMonthBalance != null) tvMonthBalance.setText(fmt.format(b + lastIncome + lastExpense));
                });
            });
        }
    }

    private void populatePieChart(PieChart chart, Map<String, Double> data) {
        if (chart == null) return;
        if (data == null || data.isEmpty()) {
            chart.clear();
            chart.setCenterText("Không có dữ liệu");
            chart.invalidate();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        double total = 0.0;
        for (Double v : data.values()) total += (v == null ? 0.0 : v);
        for (Map.Entry<String, Double> e : data.entrySet()) {
            double v = e.getValue() == null ? 0.0 : e.getValue();
            if (v <= 0.0) continue;
            float perc = total <= 0.0 ? 0f : (float)(v / total * 100.0);
            entries.add(new PieEntry((float)v, e.getKey()));
        }

        PieDataSet set = new PieDataSet(entries, "");
        ArrayList<Integer> colors = new ArrayList<>();
        // Choose themed colors: red hues for expense, green hues for income
        if (chart == pieChartExpense) {
            colors.add(Color.rgb(233, 30, 99)); // pink
            colors.add(Color.rgb(244, 67, 54)); // red
            colors.add(Color.rgb(229, 57, 53));
            colors.add(Color.rgb(198, 40, 40));
            colors.add(Color.rgb(244, 143, 177));
        } else if (chart == pieChartIncome) {
            colors.add(Color.rgb(76, 175, 80)); // green
            colors.add(Color.rgb(139, 195, 74));
            colors.add(Color.rgb(56, 142, 60));
            colors.add(Color.rgb(102, 187, 106));
            colors.add(Color.rgb(200, 230, 201));
        } else {
            for (int c : ColorTemplate.MATERIAL_COLORS) colors.add(c);
            for (int c : ColorTemplate.VORDIPLOM_COLORS) colors.add(c);
        }
        set.setColors(colors);
        // value color: white on dark slices (better contrast), otherwise black
        if (chart == pieChartExpense || chart == pieChartIncome) set.setValueTextColor(Color.WHITE); else set.setValueTextColor(Color.BLACK);
        set.setValueTextSize(12f);

        PieData dataObj = new PieData(set);
        dataObj.setValueFormatter(new com.github.mikephil.charting.formatter.PercentFormatter(chart));
        chart.setData(dataObj);
        chart.setDrawEntryLabels(false);
        chart.invalidate();
    }

}
