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
import java.util.Date;
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
        TextView tvTheme = findViewById(R.id.tvTheme);
        TextView tvReminderSummary = findViewById(R.id.tvReminderSummary);
        SwitchCompat switchDaily = findViewById(R.id.switchDaily);
        TextView tvUserEmail = findViewById(R.id.tvUserEmail);
        TextView tvVersion = findViewById(R.id.tvVersion);

        // load prefs
        String cur = sp.getString("pref_currency", "VND");
        String weekStart = sp.getString("pref_week_start", "Monday");
        String theme = sp.getString("pref_theme", "system");
        String reminder = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
        boolean dailyOn = sp.getBoolean("pref_reminder_daily", true);

        tvCurrency.setText(mapCurrencyLabel(cur));
        tvWeekStart.setText(mapWeekLabel(weekStart));
        tvTheme.setText(mapThemeLabel(theme));
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
        findViewById(R.id.card_theme).setOnClickListener(v -> showThemeDialog(tvTheme));
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

        findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportCsv());
        findViewById(R.id.btnImportCsv).setOnClickListener(v -> Toast.makeText(this, "Import not implemented", Toast.LENGTH_SHORT).show());
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

    private void showThemeDialog(TextView tvTheme) {
        final String[] items = new String[]{"System", "Light", "Dark", "Red"};
        int checked = 0;
        String cur = sp.getString("pref_theme", "system");
        for (int i = 0; i < items.length; i++) if (mapThemeValue(items[i]).equals(cur)) checked = i;
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    String sel = mapThemeValue(items[which]);
                    sp.edit().putString("pref_theme", sel).apply();
                    tvTheme.setText(mapThemeLabel(sel));
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

    private String mapThemeLabel(String code) {
        if (code == null) return getString(R.string.theme);
        switch (code) {
            case "light": return getString(R.string.light_mode);
            case "dark": return getString(R.string.dark_mode);
            case "red": return "Đỏ";
            default: return getString(R.string.theme);
        }
    }

    private String mapThemeValue(String label) {
        if (label == null) return "system";
        switch (label) {
            case "Light": return "light";
            case "Dark": return "dark";
            case "Red": return "red";
            default: return "system";
        }
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
                pw.println("id,userId,type,amount,category,note,timestamp");
                for (TransactionEntity te : entities) {
                    pw.printf(Locale.ROOT, "%d,%s,%s,%.2f,%s,%s,%d\n",
                            te.id, te.userId, te.type, te.amount,
                            te.category == null ? "" : te.category.replace(",", " "),
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
