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

import java.util.ArrayList;
import java.util.List;

public class StatisticsFragment extends Fragment {
    public StatisticsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_statistics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.rvMonthTransactions);
        if (rv != null) {
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            List<Transaction> samples = new ArrayList<>();
            samples.add(new Transaction("Lương", "20-10-2025", 17000000, R.drawable.ic_wallet));
            samples.add(new Transaction("Hóa đơn", "06-10-2025", -1500000, R.drawable.ic_bill));
            samples.add(new Transaction("Di chuyển", "05-10-2025", -300000, R.drawable.ic_transport));

            TransactionAdapter adapter = new TransactionAdapter(samples);
            rv.setAdapter(adapter);
        }

        // Wire the button to open the full-screen activity view
        Button btn = view.findViewById(R.id.btnOpenMonthView);
        TextView tvMonthTitle = view.findViewById(R.id.tvMonthTitle);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                Intent i = new Intent(requireContext(), ViewOneMonthActivity.class);
                String label = (tvMonthTitle != null) ? tvMonthTitle.getText().toString() : null;
                if (label != null) i.putExtra("EXTRA_MONTH_LABEL", label);
                startActivity(i);
            });
        }
    }
}
