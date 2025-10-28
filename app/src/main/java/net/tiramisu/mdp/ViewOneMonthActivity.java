package net.tiramisu.mdp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.graphics.Color;

public class ViewOneMonthActivity extends AppCompatActivity {

    private TextView tvTotalIncome;
    private TextView tvTotalExpense;
    private TextView tvMonthBalance;
    private PieChart pieChartExpense;
    private PieChart pieChartIncome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistic_overview);

        TextView tvMonthTitle = findViewById(R.id.tvMonthTitle);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpense = findViewById(R.id.tvTotalExpense);
        tvMonthBalance = findViewById(R.id.tvMonthBalance);
        RecyclerView rvMonthTransactions = findViewById(R.id.rvMonthTransactions);
        pieChartExpense = findViewById(R.id.pieChartExpense);
        pieChartIncome = findViewById(R.id.pieChartIncome);

        String monthLabel = getIntent().getStringExtra("EXTRA_MONTH_LABEL");
        if (monthLabel == null || monthLabel.isEmpty()) {
            monthLabel = getString(R.string.current_month);
        }
        tvMonthTitle.setText(monthLabel);

        // Sample transactions for the month (positive = income, negative = expense)
        List<Transaction> samples = new ArrayList<>();
        samples.add(new Transaction("Lương", "20-10-2025", 17000000, R.drawable.ic_wallet));
        samples.add(new Transaction("Hóa đơn", "06-10-2025", -1500000, R.drawable.ic_bill));
        samples.add(new Transaction("Di chuyển", "05-10-2025", -300000, R.drawable.ic_transport));
        samples.add(new Transaction("Tiền thưởng", "01-10-2025", 500000, R.drawable.ic_wallet));

        computeAndShowTotals(samples);

        // Setup RecyclerView
        rvMonthTransactions.setLayoutManager(new LinearLayoutManager(this));
        TransactionAdapter adapter = new TransactionAdapter(samples);
        rvMonthTransactions.setAdapter(adapter);

        setupPieCharts(samples);
    }

    private void computeAndShowTotals(List<Transaction> list) {
        double income = 0.0;
        double expense = 0.0;
        for (Transaction t : list) {
            if (t.amount >= 0) income += t.amount;
            else expense += Math.abs(t.amount);
        }

        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        tvTotalIncome.setText(fmt.format(income));
        tvTotalExpense.setText(fmt.format(expense));
        tvMonthBalance.setText(fmt.format(income - expense));
    }

    private void setupPieCharts(List<Transaction> transactions) {
        // Aggregate by category into expense and income maps
        java.util.Map<String, Double> expenseMap = new java.util.HashMap<>();
        java.util.Map<String, Double> incomeMap = new java.util.HashMap<>();
        for (Transaction t : transactions) {
            String cat = t.title == null ? "Khác" : t.title;
            double v = t.amount;
            if (v < 0) {
                expenseMap.put(cat, expenseMap.getOrDefault(cat, 0.0) + Math.abs(v));
            } else if (v > 0) {
                incomeMap.put(cat, incomeMap.getOrDefault(cat, 0.0) + v);
            }
        }

        // populate both charts
        populatePie(pieChartExpense, expenseMap, "Chi tiêu");
        populatePie(pieChartIncome, incomeMap, "Thu nhập");
    }

    private void populatePie(PieChart chart, java.util.Map<String, Double> map, String centerText) {
        if (chart == null) return;
        if (map == null || map.isEmpty()) {
            chart.clear();
            chart.setCenterText("Không có dữ liệu");
            chart.invalidate();
            return;
        }
        List<PieEntry> entries = new ArrayList<>();
        double total = 0.0;
        for (Double v : map.values()) total += (v == null ? 0.0 : v);
        for (java.util.Map.Entry<String, Double> e : map.entrySet()) {
            double val = e.getValue() == null ? 0.0 : e.getValue();
            if (val <= 0) continue;
            entries.add(new PieEntry((float) val, e.getKey()));
        }

        PieDataSet set = new PieDataSet(entries, "");
        ArrayList<Integer> colors = new ArrayList<>();
        for (int c : ColorTemplate.MATERIAL_COLORS) colors.add(c);
        for (int c : ColorTemplate.VORDIPLOM_COLORS) colors.add(c);
        set.setColors(colors);
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(12f);

        PieData data = new PieData(set);
        data.setValueFormatter(new PercentFormatter(chart));
        chart.setData(data);
        chart.setCenterText(centerText + "\n(%)");
        chart.setUsePercentValues(true);
        chart.setDrawEntryLabels(false);
        chart.getDescription().setEnabled(false);
        chart.animateY(700);
        chart.invalidate();
    }
}
