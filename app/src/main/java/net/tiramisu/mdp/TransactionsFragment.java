package net.tiramisu.mdp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.util.ArrayList;
import java.util.List;

public class TransactionsFragment extends Fragment {
    private TransactionAdapter adapter;
    private TransactionRepository repository;

    public TransactionsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transactions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new TransactionRepository(requireContext());

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
                    if (entities == null || entities.isEmpty()) {
                        // keep sample data if none
                        adapter.addTransaction(new Transaction("Lương", "20-10-2025", 17000000, R.drawable.ic_wallet));
                        adapter.addTransaction(new Transaction("Hóa đơn", "06-10-2025", -1500000, R.drawable.ic_bill));
                        adapter.addTransaction(new Transaction("Di chuyển", "05-10-2025", -300000, R.drawable.ic_transport));
                        adapter.addTransaction(new Transaction("Mua sắm", "03-10-2025", -750000, R.drawable.ic_shopping));
                        return;
                    }

                    // clear default if any
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
        }
    }

    // Allow external code to add a transaction to the UI
    public void addTransaction(Transaction tx) {
        if (adapter != null) adapter.addTransaction(tx);
    }
}
