package net.tiramisu.mdp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.graphics.Color;

public class ViewOneMonthActivity extends AppCompatActivity {

    private TextView tvMonthTitle;
    private TextView tvTotalIncome;
    private TextView tvTotalExpense;
    private TextView tvMonthBalance;
    private RecyclerView rvMonthTransactions;
    private TransactionAdapter adapter;
    private BarChart barChartMonthExpenses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_one_month);

        tvMonthTitle = findViewById(R.id.tvMonthTitle);
        tvTotalIncome = findViewById(R.id.tvTotalIncome);
        tvTotalExpense = findViewById(R.id.tvTotalExpense);
        tvMonthBalance = findViewById(R.id.tvMonthBalance);
        rvMonthTransactions = findViewById(R.id.rvMonthTransactions);
        barChartMonthExpenses = findViewById(R.id.barChartMonthExpenses);

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
        adapter = new TransactionAdapter(samples);
        rvMonthTransactions.setAdapter(adapter);

        setupExpenseChart(samples);
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

    private void setupExpenseChart(List<Transaction> transactions) {
        // Giả sử chia expense theo tuần (4 tuần), bạn có thể thay đổi logic này theo dữ liệu thực tế
        float[] weekExpense = new float[4];
        for (Transaction t : transactions) {
            if (t.amount < 0) {
                // Phân bổ tuần dựa trên ngày (giả định dd-MM-yyyy)
                int day = 1;
                try {
                    String[] parts = t.date.split("-");
                    day = Integer.parseInt(parts[0]);
                } catch (Exception ignored) {}
                int weekIdx = Math.min((day - 1) / 7, 3);
                weekExpense[weekIdx] += -t.amount;
            }
        }
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            entries.add(new BarEntry(i, weekExpense[i]));
        }
        BarDataSet set = new BarDataSet(entries, "Chi tiêu (₫)");
        set.setColor(Color.parseColor("#FF7043"));
        set.setValueTextColor(Color.DKGRAY);
        set.setValueTextSize(12f);
        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        barChartMonthExpenses.setData(data);
        List<String> labels = new ArrayList<>();
        labels.add("Tuần 1");
        labels.add("Tuần 2");
        labels.add("Tuần 3");
        labels.add("Tuần 4");
        XAxis xAxis = barChartMonthExpenses.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        barChartMonthExpenses.getDescription().setEnabled(false);
        barChartMonthExpenses.setDrawGridBackground(false);
        barChartMonthExpenses.setFitBars(true);
        barChartMonthExpenses.animateY(700);
        barChartMonthExpenses.invalidate();
    }
}
