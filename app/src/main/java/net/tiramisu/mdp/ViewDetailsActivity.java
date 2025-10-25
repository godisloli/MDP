package net.tiramisu.mdp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.NumberFormat;
import java.util.Locale;

public class ViewDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_detail);

        // Use IDs that exist in activity_transaction_detail.xml
        TextView tvDetailTitle = findViewById(R.id.tvDetailTitle);
        TextView tvDetailDate = findViewById(R.id.tvDetailDate);
        TextView tvDetailAmount = findViewById(R.id.tvDetailAmount);
        TextView tvDetailCategory = findViewById(R.id.tvDetailCategory);
        TextView tvDetailNote = findViewById(R.id.tvDetailNote);

        // Retrieve transaction details from intent extras
        String title = getIntent().getStringExtra("EXTRA_TITLE");
        String date = getIntent().getStringExtra("EXTRA_DATE");
        double amount = getIntent().getDoubleExtra("EXTRA_AMOUNT", 0.0);
        String category = getIntent().getStringExtra("EXTRA_CATEGORY");
        String note = getIntent().getStringExtra("EXTRA_NOTE");

        // Format amount using locale-aware currency formatting
        NumberFormat fmt = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN"));
        String amountStr = fmt.format(amount);

        // Set the transaction details to the TextViews using string resources
        tvDetailTitle.setText(title != null ? title : getString(R.string.transaction_title_default));

        if (date != null && !date.isEmpty()) {
            tvDetailDate.setText(getString(R.string.transaction_date_label, date));
        } else {
            tvDetailDate.setText(getString(R.string.transaction_date_label, getString(R.string.transaction_date_default)));
        }

        tvDetailAmount.setText(getString(R.string.transaction_amount_label, amountStr));

        if (category != null && !category.isEmpty()) {
            tvDetailCategory.setText(getString(R.string.transaction_category_label, category));
        } else {
            tvDetailCategory.setText(getString(R.string.transaction_category_label, getString(R.string.transaction_category_default)));
        }

        if (note != null && !note.isEmpty()) {
            tvDetailNote.setText(getString(R.string.transaction_note_label, note));
        } else {
            tvDetailNote.setText(getString(R.string.transaction_note_label, getString(R.string.transaction_note_default)));
        }
    }
}
