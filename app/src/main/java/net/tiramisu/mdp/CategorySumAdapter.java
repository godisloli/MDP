package net.tiramisu.mdp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import net.tiramisu.mdp.model.CategorySum;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CategorySumAdapter extends RecyclerView.Adapter<CategorySumAdapter.VH> {
    private List<CategorySum> items;

    public CategorySumAdapter(List<CategorySum> items) { this.items = items; }

    public void setItems(List<CategorySum> items) { this.items = items; notifyDataSetChanged(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_sum, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CategorySum s = items.get(position);
        String cat = s.category == null ? "Khác" : s.category;
        holder.tvCategory.setText(cat);
        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        double val = s.total == null ? 0.0 : s.total;
        holder.tvAmount.setText(fmt.format(val));
        // color by sign (use existing color resources)
        int colorRes = (val < 0) ? R.color.red : R.color.green;
        int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);
        holder.tvAmount.setTextColor(color);

        // placeholder percent: we don't have total of all categories here; caller can compute and set if desired
        holder.tvPercent.setText(" ");
    }

    @Override
    public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        android.widget.ImageView ivIcon;
        TextView tvCategory, tvPercent, tvAmount;
        VH(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivIcon);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvPercent = itemView.findViewById(R.id.tvPercent);
            tvAmount = itemView.findViewById(R.id.tvAmount);
        }
    }
}
