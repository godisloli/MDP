package net.tiramisu.mdp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {
    private final List<Transaction> items;
    private final NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));

    public TransactionAdapter(List<Transaction> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Use the provided item_transaction_mini layout which exists in the project
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction_mini, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Transaction t = items.get(position);
        holder.title.setText(t.title);
        holder.sub.setText(t.date);
        // Format amount
        holder.amount.setText(fmt.format(t.amount));
        // color negative in red, positive in green
        if (t.amount < 0) {
            holder.amount.setTextColor(ContextCompat.getColor(holder.amount.getContext(), android.R.color.holo_red_dark));
        } else {
            holder.amount.setTextColor(ContextCompat.getColor(holder.amount.getContext(), android.R.color.holo_green_dark));
        }
        if (t.iconResId != 0) holder.icon.setImageResource(t.iconResId);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Allow adding a new transaction at the top
    public void addTransaction(Transaction tx) {
        items.add(0, tx);
        notifyItemInserted(0);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView sub;
        TextView amount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.ivIcon);
            title = itemView.findViewById(R.id.tvTitle);
            sub = itemView.findViewById(R.id.tvSub);
            amount = itemView.findViewById(R.id.tvAmount);
        }
    }
}
