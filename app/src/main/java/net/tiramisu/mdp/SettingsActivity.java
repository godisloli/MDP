package net.tiramisu.mdp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {
    private SharedPreferences sp;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sp = getSharedPreferences("mdp_prefs", MODE_PRIVATE);

        TextView tvCurrency = findViewById(R.id.tvCurrency);
        TextView tvWeekStart = findViewById(R.id.tvWeekStart);
        TextView tvAutoDetect = findViewById(R.id.tvAutoDetect);
        TextView tvReminderSummary = findViewById(R.id.tvReminderSummary);
        SwitchCompat switchDaily = findViewById(R.id.switchDaily);
        TextView tvUserEmail = findViewById(R.id.tvUserEmail);
        TextView tvVersion = findViewById(R.id.tvVersion);

        // load prefs
        String cur = sp.getString("pref_currency", "VND");
        String weekStart = sp.getString("pref_week_start", "Monday");
        String reminder = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
        boolean dailyOn = sp.getBoolean("pref_reminder_daily", true);

        tvCurrency.setText(mapCurrencyLabel(cur));
        tvWeekStart.setText(mapWeekLabel(weekStart));
        updateAutoDetectSummary(tvAutoDetect);
        // use formatted string resource for reminder summary
        String extra = dailyOn ? (" " + getString(R.string.reminder_daily)) : "";
        tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), reminder, extra));
        switchDaily.setChecked(dailyOn);

        // user email
        String email = "";
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) email = FirebaseAuth.getInstance().getCurrentUser().getEmail(); } catch (Exception ignored) {}
        if (email == null || email.isEmpty()) email = getString(R.string.email_hint);
        tvUserEmail.setText(email);

        // version (use PackageManager to fetch versionName)
        try {
            PackageManager pm = getPackageManager();
            PackageInfo pi = pm.getPackageInfo(getPackageName(), 0);
            tvVersion.setText(pi.versionName == null ? "1.0" : pi.versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
            tvVersion.setText("1.0");
        }

        // clicks
        findViewById(R.id.tvCurrency).setOnClickListener(v -> showCurrencyDialog(tvCurrency));
        findViewById(R.id.card_auto_detect).setOnClickListener(v -> showAutoDetectDialog(tvAutoDetect));
        findViewById(R.id.tvWeekStart).setOnClickListener(v -> showWeekStartDialog(tvWeekStart));

        switchDaily.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("pref_reminder_daily", isChecked).apply();
            String time = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
            String ex = isChecked ? (" " + getString(R.string.reminder_daily)) : "";
            tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), time, ex));
            Toast.makeText(this, isChecked ? "Bật nhắc hàng ngày" : "Tắt nhắc hàng ngày", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignored) {}
            Intent i = new Intent(SettingsActivity.this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });

        findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportAndSendEmail());
    }

    private void showCurrencyDialog(TextView tvCurrency) {
        final String[] items = new String[]{"Auto", "VND", "USD", "EUR", "CNY"};
        int checked = 0;
        String cur = sp.getString("pref_currency", "auto");
        for (int i = 0; i < items.length; i++) if (items[i].equalsIgnoreCase(cur)) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(R.string.currency)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    String sel = items[which];
                    sp.edit().putString("pref_currency", sel.equals("Auto")?"auto":sel).apply();
                    tvCurrency.setText(mapCurrencyLabel(sel.equals("Auto")?"auto":sel));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    private void showWeekStartDialog(TextView tvWeekStart) {
        final String[] items = new String[]{"Monday", "Sunday"};
        int checked = 0;
        String cur = sp.getString("pref_week_start", "Monday");
        for (int i = 0; i < items.length; i++) if (items[i].equalsIgnoreCase(cur)) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(R.string.change)
                .setSingleChoiceItems(new String[]{getString(R.string.chip_mon), getString(R.string.chip_sun)}, checked, (dialog, which) -> {
                    String sel = items[which];
                    sp.edit().putString("pref_week_start", sel).apply();
                    tvWeekStart.setText(mapWeekLabel(sel));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String mapCurrencyLabel(String cur) {
        if (cur == null) return getString(R.string.currency);
        switch (cur.toUpperCase(Locale.ROOT)) {
            case "VND": return "Việt Nam Đồng (₫)";
            case "USD": return "USD ($)";
            case "EUR": return "EUR (€)";
            case "CNY": return "CNY (¥)";
            case "AUTO": return "Auto";
            default: return cur;
        }
    }

    private String mapWeekLabel(String code) {
        if (code == null) return getString(R.string.chip_mon);
        // use if instead of switch to satisfy lint
        if ("Sunday".equalsIgnoreCase(code)) return getString(R.string.chip_sun);
        return getString(R.string.chip_mon);
    }

    /**
     * Update auto detection summary based on saved phone numbers
     */
    private void updateAutoDetectSummary(TextView tvAutoDetect) {
        java.util.Set<String> phones = sp.getStringSet("pref_auto_detect_phones", new java.util.HashSet<>());
        if (phones.isEmpty()) {
            tvAutoDetect.setText(getString(R.string.auto_detect_no_phones));
        } else {
            tvAutoDetect.setText(String.format(java.util.Locale.getDefault(), "%d số điện thoại", phones.size()));
        }
    }

    /**
     * Show dialog to manage auto detection feature
     */
    private void showAutoDetectDialog(TextView tvAutoDetect) {
        java.util.Set<String> phones = new java.util.HashSet<>(sp.getStringSet("pref_auto_detect_phones", new java.util.HashSet<>()));

        // Create list of phone numbers for display
        final java.util.List<String> phoneList = new java.util.ArrayList<>(phones);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.auto_transaction_detection);

        if (phoneList.isEmpty()) {
            builder.setMessage(R.string.auto_detect_no_phones);
        } else {
            // Show list of phone numbers
            String[] phoneArray = phoneList.toArray(new String[0]);
            builder.setItems(phoneArray, (dialog, which) -> {
                // Remove phone number
                showRemovePhoneDialog(phoneList.get(which), tvAutoDetect);
            });
        }

        builder.setPositiveButton(R.string.add, (dialog, which) -> showAddPhoneDialog(tvAutoDetect));

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    /**
     * Show dialog to add phone number
     */
    private void showAddPhoneDialog(TextView tvAutoDetect) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.auto_detect_add_phone);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.auto_detect_phone_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        builder.setView(input);

        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String phone = input.getText().toString().trim();
            if (phone.isEmpty()) {
                Toast.makeText(this, R.string.phone_number_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            // Add phone to saved list
            java.util.Set<String> phones = new java.util.HashSet<>(sp.getStringSet("pref_auto_detect_phones", new java.util.HashSet<>()));
            phones.add(phone);
            sp.edit().putStringSet("pref_auto_detect_phones", phones).apply();

            // Enable auto detection
            sp.edit().putBoolean("pref_auto_detect_enabled", true).apply();

            updateAutoDetectSummary(tvAutoDetect);
            Toast.makeText(this, R.string.phone_number_added, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    /**
     * Show dialog to confirm phone removal
     */
    private void showRemovePhoneDialog(String phone, TextView tvAutoDetect) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(phone)
            .setPositiveButton(R.string.delete, (dialog, which) -> {
                java.util.Set<String> phones = new java.util.HashSet<>(sp.getStringSet("pref_auto_detect_phones", new java.util.HashSet<>()));
                phones.remove(phone);
                sp.edit().putStringSet("pref_auto_detect_phones", phones).apply();

                // Disable auto detection if no phones left
                if (phones.isEmpty()) {
                    sp.edit().putBoolean("pref_auto_detect_enabled", false).apply();
                }

                updateAutoDetectSummary(tvAutoDetect);
                Toast.makeText(this, R.string.phone_number_removed, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void exportAndSendEmail() {
        String userId = "local";
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null)
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        } catch (Exception ignored) {}

        // Get user email
        String userEmail = "";
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null &&
                FirebaseAuth.getInstance().getCurrentUser().getEmail() != null) {
                userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            }
        } catch (Exception ignored) {}

        if (userEmail.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy email người dùng", Toast.LENGTH_SHORT).show();
            return;
        }

        final String email = userEmail;

        // Get current month range
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long fromTime = cal.getTimeInMillis();

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long toTime = cal.getTimeInMillis();

        // Get current month/year for email subject
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        String monthYear = monthFormat.format(new Date());

        TransactionRepository repo = TransactionRepository.getInstance(getApplicationContext());
        repo.getByUser(userId, entities -> {
            if (entities == null || entities.isEmpty()) {
                runOnUiThread(() ->
                    Toast.makeText(this, getString(R.string.no_data), Toast.LENGTH_SHORT).show());
                return;
            }

            // Filter transactions for current month
            List<TransactionEntity> monthTransactions = new ArrayList<>();
            double totalIncome = 0;
            double totalExpense = 0;

            for (TransactionEntity te : entities) {
                if (te.timestamp >= fromTime && te.timestamp <= toTime) {
                    monthTransactions.add(te);
                    if ("income".equalsIgnoreCase(te.type)) {
                        totalIncome += te.amount;
                    } else if ("expense".equalsIgnoreCase(te.type)) {
                        totalExpense += Math.abs(te.amount);
                    }
                }
            }

            if (monthTransactions.isEmpty()) {
                runOnUiThread(() ->
                    Toast.makeText(this, "Không có dữ liệu tháng này", Toast.LENGTH_SHORT).show());
                return;
            }

            // Build email content
            StringBuilder emailBody = new StringBuilder();
            emailBody.append("Báo cáo chi tiêu tháng ").append(monthYear).append("\n\n");
            emailBody.append("═══════════════════════════════════\n");
            emailBody.append("TỔNG QUAN\n");
            emailBody.append("═══════════════════════════════════\n\n");
            emailBody.append("📈 Tổng thu nhập: ").append(CurrencyUtils.formatCurrency(this, totalIncome)).append("\n");
            emailBody.append("📉 Tổng chi tiêu: ").append(CurrencyUtils.formatCurrency(this, totalExpense)).append("\n");
            emailBody.append("💰 Số dư: ").append(CurrencyUtils.formatCurrency(this, totalIncome - totalExpense)).append("\n\n");

            emailBody.append("═══════════════════════════════════\n");
            emailBody.append("CHI TIẾT GIAO DỊCH (").append(monthTransactions.size()).append(" giao dịch)\n");
            emailBody.append("═══════════════════════════════════\n\n");

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

            for (TransactionEntity te : monthTransactions) {
                String type = "income".equalsIgnoreCase(te.type) ? "📈 Thu" : "📉 Chi";
                String date = dateFormat.format(new Date(te.timestamp));
                String amount = CurrencyUtils.formatCurrency(this, Math.abs(te.amount));
                String title = te.title != null && !te.title.isEmpty() ? te.title : "Không có tiêu đề";
                String category = te.category != null ? CategoryHelper.getLocalizedCategory(this, te.category) : "";

                emailBody.append(type).append(" | ").append(date).append("\n");
                emailBody.append("   ").append(title);
                if (!category.isEmpty()) {
                    emailBody.append(" (").append(category).append(")");
                }
                emailBody.append("\n");
                emailBody.append("   Số tiền: ").append(amount).append("\n");
                if (te.note != null && !te.note.isEmpty()) {
                    emailBody.append("   Ghi chú: ").append(te.note).append("\n");
                }
                emailBody.append("\n");
            }

            emailBody.append("═══════════════════════════════════\n");
            emailBody.append("Xuất từ Ez Money\n");
            emailBody.append("Ngày xuất: ").append(dateFormat.format(new Date())).append("\n");

            // Send email
            runOnUiThread(() -> {
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("message/rfc822");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Ez Money - Báo cáo chi tiêu " + monthYear);
                emailIntent.putExtra(Intent.EXTRA_TEXT, emailBody.toString());

                try {
                    startActivity(Intent.createChooser(emailIntent, "Gửi báo cáo qua email"));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(this, "Không tìm thấy ứng dụng email", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void exportCsv() {
        String userId = "local";
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
        TransactionRepository repo = TransactionRepository.getInstance(getApplicationContext());
        repo.getByUser(userId, entities -> {
            if (entities == null) {
                runOnUiThread(() -> Toast.makeText(this, getString(R.string.no_data), Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
                File f = new File(getFilesDir(), "transactions_" + stamp + ".csv");
                FileWriter fw = new FileWriter(f);
                PrintWriter pw = new PrintWriter(fw);
                pw.println("id,userId,type,amount,category,title,note,timestamp");
                for (TransactionEntity te : entities) {
                    pw.printf(Locale.ROOT, "%d,%s,%s,%.2f,%s,%s,%s,%d\n",
                            te.id, te.userId, te.type, te.amount,
                            te.category == null ? "" : te.category.replace(",", " "),
                            te.title == null ? "" : te.title.replace(",", " "),
                            te.note == null ? "" : te.note.replace(",", " "), te.timestamp);
                }
                pw.flush(); pw.close(); fw.close();
                runOnUiThread(() -> Toast.makeText(this, "Xuất CSV thành công: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Xuất CSV thất bại: " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }
}
