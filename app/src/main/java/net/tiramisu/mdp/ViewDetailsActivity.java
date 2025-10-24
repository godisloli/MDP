package net.tiramisu.mdp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ViewDetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_details);

        TextView tvTransactionTitle = findViewById(R.id.tvTransactionTitle);
        TextView tvTransactionDate = findViewById(R.id.tvTransactionDate);
        TextView tvTransactionAmount = findViewById(R.id.tvTransactionAmount);
        TextView tvTransactionCategory = findViewById(R.id.tvTransactionCategory);
        TextView tvTransactionNote = findViewById(R.id.tvTransactionNote);

        // Retrieve transaction details from intent extras
        String title = getIntent().getStringExtra("EXTRA_TITLE");
        String date = getIntent().getStringExtra("EXTRA_DATE");
        double amount = getIntent().getDoubleExtra("EXTRA_AMOUNT", 0.0);
        String category = getIntent().getStringExtra("EXTRA_CATEGORY");
        String note = getIntent().getStringExtra("EXTRA_NOTE");

        // Set the transaction details to the TextViews
        tvTransactionTitle.setText(title != null ? title : "Transaction Title");
        tvTransactionDate.setText(date != null ? "Date: " + date : "Date: N/A");
        tvTransactionAmount.setText("Amount: " + amount + " ₫");
        tvTransactionCategory.setText(category != null ? "Category: " + category : "Category: General");
        tvTransactionNote.setText(note != null ? "Note: " + note : "Note: No additional details");
    }
}
