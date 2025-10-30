package net.tiramisu.mdp;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

public class ViewDetailsActivity extends AppCompatActivity {

    private TransactionRepository repository;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_detail);

        repository = TransactionRepository.getInstance(this);

        // Setup toolbar with back button
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Use IDs that exist in activity_transaction_detail.xml
        TextView tvDetailTitle = findViewById(R.id.tvDetailTitle);
        TextView tvDetailDate = findViewById(R.id.tvDetailDate);
        TextView tvDetailAmount = findViewById(R.id.tvDetailAmount);
        TextView tvDetailCategory = findViewById(R.id.tvDetailCategory);
        TextView tvDetailNote = findViewById(R.id.tvDetailNote);
        MaterialButton btnDelete = findViewById(R.id.btnDeleteTransaction);

        // Retrieve transaction details from intent extras
        String title = getIntent().getStringExtra("EXTRA_TITLE");
        String date = getIntent().getStringExtra("EXTRA_DATE");
        double amount = getIntent().getDoubleExtra("EXTRA_AMOUNT", 0.0);
        String category = getIntent().getStringExtra("EXTRA_CATEGORY");
        String note = getIntent().getStringExtra("EXTRA_NOTE");
        long timestamp = getIntent().getLongExtra("EXTRA_TIMESTAMP", 0L);

        // Format amount using CurrencyUtils
        String amountStr = CurrencyUtils.formatCurrency(this, amount);

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

        // Setup delete button
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> showDeleteConfirmationDialog(timestamp, amount));
        }
    }

    private void showDeleteConfirmationDialog(long timestamp, double amount) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_transaction_title)
            .setMessage(R.string.delete_transaction_message)
            .setPositiveButton(R.string.delete, (dialog, which) -> deleteTransaction(timestamp, amount))
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void deleteTransaction(long timestamp, double amount) {
        String userId = "local";
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
        } catch (Exception ignored) {}

        final String finalUserId = userId;

        // Find the transaction by userId, timestamp, and amount
        repository.getByUserAndTimestampAndAmount(userId, timestamp, amount, transaction -> {
            if (transaction == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.delete_transaction_error, Toast.LENGTH_SHORT).show();
                });
                return;
            }

            // Delete the transaction and adjust balance
            repository.deleteTransaction(transaction.id, finalUserId, transaction.amount, () -> {
                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.delete_transaction_success, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK); // Notify calling activity that data changed
                    finish(); // Close the detail activity
                });
            });
        });
    }
}
