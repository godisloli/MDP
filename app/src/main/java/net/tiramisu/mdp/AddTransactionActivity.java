package net.tiramisu.mdp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

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

    private TransactionRepository repository;

    private TextView tvSuggest1;
    private TextView tvSuggest2;
    private TextView tvSuggest3;
    private View llAmountSuggestions;

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

        repository = net.tiramisu.mdp.repo.TransactionRepository.getInstance(getApplicationContext());

        // find suggestion views
        tvSuggest1 = findViewById(R.id.tvSuggest1);
        tvSuggest2 = findViewById(R.id.tvSuggest2);
        tvSuggest3 = findViewById(R.id.tvSuggest3);
        llAmountSuggestions = findViewById(R.id.llAmountSuggestions);

        // simple categories
        String[] categories = new String[]{"Ăn uống", "Đi lại", "Mua sắm", "Học tập", "Giải trí", "Khác"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);

        // Toggle category visibility when type changes: hide for income
        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isIncome = checkedId == R.id.rbIncome;
            spinnerCategory.setVisibility(isIncome ? View.GONE : View.VISIBLE);
        });

        // initialize spinner visibility based on default selection
        int initialChecked = rgType.getCheckedRadioButtonId();
        spinnerCategory.setVisibility((initialChecked == R.id.rbIncome) ? View.GONE : View.VISIBLE);

        // wire amount text changes to update suggestions
        if (etAmount != null) {
            etAmount.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateSuggestions(s == null ? "" : s.toString()); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }

        // set suggestion click listeners
        if (tvSuggest1 != null) tvSuggest1.setOnClickListener(v -> applySuggestedAmount(tvSuggest1.getTag()));
        if (tvSuggest2 != null) tvSuggest2.setOnClickListener(v -> applySuggestedAmount(tvSuggest2.getTag()));
        if (tvSuggest3 != null) tvSuggest3.setOnClickListener(v -> applySuggestedAmount(tvSuggest3.getTag()));

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
        if (isIncome) {
            // incomes don't need category; set a default title/category
            category = "Thu nhập";
        }

        // Prepare result intent
        Intent out = new Intent();
        out.putExtra(EXTRA_TITLE, category);
        out.putExtra(EXTRA_DATE, "now");
        out.putExtra(EXTRA_AMOUNT, amount);
        out.putExtra(EXTRA_IS_INCOME, isIncome);
        out.putExtra(EXTRA_CATEGORY, category);
        out.putExtra(EXTRA_NOTE, note);

        // Persist to local Room DB asynchronously, then finish with result
        long ts = System.currentTimeMillis();
        String type = isIncome ? "income" : "expense";
        String userId = "local";
        try {
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
        } catch (Exception ignored) {}
        TransactionEntity t = new TransactionEntity(userId, type, amount, note, category, ts);

        // insert and on callback finish activity on UI thread with result
        repository.insert(t, () -> {
            Handler h = new Handler(Looper.getMainLooper());
            h.post(() -> {
                setResult(Activity.RESULT_OK, out);
                finish();
            });
        });
    }

    // parse typed base and update the three suggestion chips
    private void updateSuggestions(String typed) {
        if (typed == null) typed = "";
        String cleaned = typed.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) {
            if (llAmountSuggestions != null) llAmountSuggestions.setVisibility(View.GONE);
            return;
        }

        // parse base number (use up to 7 digits to avoid overflow when multiplied)
        long base = 0L;
        try {
            String take = cleaned.length() > 7 ? cleaned.substring(0, 7) : cleaned;
            base = Long.parseLong(take);
        } catch (Exception ignored) {}

        final long MAX = 10_000_000L; // 10^7 cap for suggestions

        // generate candidates using multipliers 10^3 .. 10^6 (can be adjusted)
        List<Long> candidates = new ArrayList<>();
        long[] exps = new long[]{3,4,5,6};
        for (long e : exps) {
            try {
                long mul = (long) Math.pow(10, e);
                long v = base * mul;
                if (v <= 0) continue;
                if (v > MAX) continue; // enforce cap
                candidates.add(v);
            } catch (Exception ignored) {}
            if (candidates.size() >= 3) break;
        }

        // If no candidate (base too large), hide suggestions
        if (candidates.isEmpty()) {
            if (llAmountSuggestions != null) llAmountSuggestions.setVisibility(View.GONE);
            return;
        }

        NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
        nf.setGroupingUsed(true);
        DecimalFormat df = (DecimalFormat) nf;
        df.setMaximumFractionDigits(0);

        // populate up to 3 suggestion views
        if (tvSuggest1 != null) {
            if (candidates.size() > 0) {
                tvSuggest1.setText(df.format(candidates.get(0)));
                tvSuggest1.setTag(Long.valueOf(candidates.get(0)));
                tvSuggest1.setVisibility(View.VISIBLE);
            } else tvSuggest1.setVisibility(View.GONE);
        }
        if (tvSuggest2 != null) {
            if (candidates.size() > 1) {
                tvSuggest2.setText(df.format(candidates.get(1)));
                tvSuggest2.setTag(Long.valueOf(candidates.get(1)));
                tvSuggest2.setVisibility(View.VISIBLE);
            } else tvSuggest2.setVisibility(View.GONE);
        }
        if (tvSuggest3 != null) {
            if (candidates.size() > 2) {
                tvSuggest3.setText(df.format(candidates.get(2)));
                tvSuggest3.setTag(Long.valueOf(candidates.get(2)));
                tvSuggest3.setVisibility(View.VISIBLE);
            } else tvSuggest3.setVisibility(View.GONE);
        }

        if (llAmountSuggestions != null) llAmountSuggestions.setVisibility(View.VISIBLE);
    }

    private void applySuggestedAmount(Object tag) {
        if (tag == null || etAmount == null) return;
        try {
            long v = (tag instanceof Long) ? (Long) tag : Long.parseLong(tag.toString());
            etAmount.setText(String.valueOf(v));
            // move cursor to end
            etAmount.setSelection(etAmount.getText().length());
            // hide suggestions after selection
            if (llAmountSuggestions != null) llAmountSuggestions.setVisibility(View.GONE);
        } catch (Exception ignored) {}
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }
}
