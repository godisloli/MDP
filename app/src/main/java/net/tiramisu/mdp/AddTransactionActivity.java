package net.tiramisu.mdp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class AddTransactionActivity extends AppCompatActivity {
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_DATE = "EXTRA_DATE";
    public static final String EXTRA_AMOUNT = "EXTRA_AMOUNT";
    public static final String EXTRA_IS_INCOME = "EXTRA_IS_INCOME";
    public static final String EXTRA_CATEGORY = "EXTRA_CATEGORY";
    public static final String EXTRA_NOTE = "EXTRA_NOTE";

    private RadioGroup rgType;
    private EditText etAmount;
    private EditText etNote;
    private Spinner spinnerCategory;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        rgType = findViewById(R.id.rgType);
        etAmount = findViewById(R.id.etAmount);
        etNote = findViewById(R.id.etNote);
        spinnerCategory = findViewById(R.id.spinnerCategory);
        Button btnSave = findViewById(R.id.btnSaveTransaction);
        Button btnCancel = findViewById(R.id.btnCancelTransaction);

        // simple categories
        String[] categories = new String[]{"Ăn uống", "Đi lại", "Mua sắm", "Học tập", "Giải trí", "Khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        btnSave.setOnClickListener(v -> onSave());
        btnCancel.setOnClickListener(v -> { setResult(Activity.RESULT_CANCELED); finish(); });
    }

    private void onSave() {
        String amountStr = etAmount.getText() == null ? "" : etAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            etAmount.setError(getString(R.string.error_input));
            etAmount.requestFocus();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr.replaceAll("[^0-9.-]", ""));
        } catch (Exception ex) {
            etAmount.setError(getString(R.string.error_input));
            etAmount.requestFocus();
            return;
        }

        int checked = rgType.getCheckedRadioButtonId();
        boolean isIncome = checked == R.id.rbIncome;
        if (!isIncome) amount = -Math.abs(amount);

        String note = etNote.getText() == null ? "" : etNote.getText().toString().trim();
        String category = (String) spinnerCategory.getSelectedItem();

        // Prepare result intent
        Intent out = new Intent();
        out.putExtra(EXTRA_TITLE, category);
        out.putExtra(EXTRA_DATE, "now");
        out.putExtra(EXTRA_AMOUNT, amount);
        out.putExtra(EXTRA_IS_INCOME, isIncome);
        out.putExtra(EXTRA_CATEGORY, category);
        out.putExtra(EXTRA_NOTE, note);

        setResult(Activity.RESULT_OK, out);
        finish();
    }
}
