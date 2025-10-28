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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class StatisticsFragment extends Fragment {
    public StatisticsFragment() {}

    private TransactionRepository repository;
    private long currentFrom, currentTo;
    private String currentUserId = "local";
    private CategorySumAdapter categoryAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new TransactionRepository(requireContext());

        RecyclerView rv = view.findViewById(R.id.rvMonthTransactions);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            categoryAdapter = new CategorySumAdapter(new ArrayList<>());
            rv.setAdapter(categoryAdapter);
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

    private void refreshForCurrentMonth(@NonNull View view) {
        TextView tvTotalIncome = view.findViewById(R.id.tvTotalIncome);
        TextView tvTotalExpense = view.findViewById(R.id.tvTotalExpense);
        TextView tvMonthBalance = view.findViewById(R.id.tvMonthBalance);

        // fetch totals
        repository.getSumIncomeInRange(currentUserId, currentFrom, currentTo, (Double income) -> {
            repository.getSumExpenseInRange(currentUserId, currentFrom, currentTo, (Double expense) -> {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
                    double inc = income == null ? 0.0 : income;
                    double exp = expense == null ? 0.0 : expense;
                    tvTotalIncome.setText(fmt.format(inc));
                    tvTotalExpense.setText(fmt.format(Math.abs(exp)));
                    tvMonthBalance.setText(fmt.format(inc + exp));
                });
            });
        });

        // fetch category sums
        repository.getCategorySumsInRange(currentUserId, currentFrom, currentTo, (List<CategorySum> list) -> {
            if (list == null) return;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> categoryAdapter.setItems(list));
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
}
