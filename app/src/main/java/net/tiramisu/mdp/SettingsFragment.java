package net.tiramisu.mdp;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * SettingsFragment provides the settings UI inside the ViewPager.
 * It reuses the existing layout `activity_settings.xml` and implements
 * the same behaviour as SettingsActivity (preferences, dialogs, export).
 */
public class SettingsFragment extends Fragment {
    private SharedPreferences sp;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the same layout used by the activity
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sp = requireActivity().getSharedPreferences("mdp_prefs", Context.MODE_PRIVATE);

        TextView tvCurrency = view.findViewById(R.id.tvCurrency);
        TextView tvWeekStart = view.findViewById(R.id.tvWeekStart);
        TextView tvLanguage = view.findViewById(R.id.tvLanguage);
        TextView tvReminderSummary = view.findViewById(R.id.tvReminderSummary);
        SwitchMaterial switchDaily = view.findViewById(R.id.switchDaily);
        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        TextView tvVersion = view.findViewById(R.id.tvVersion);

        View cardCurrency = view.findViewById(R.id.card_currency);
        View cardWeekStart = view.findViewById(R.id.card_week_start);
        View cardLanguage = view.findViewById(R.id.card_language);
        View cardReminder = view.findViewById(R.id.card_reminder);
        View cardAccount = view.findViewById(R.id.card_account);
        View cardData = view.findViewById(R.id.card_data);
        View cardAbout = view.findViewById(R.id.card_about);
        // Ensure the settings scroll view has bottom padding so content isn't covered by bottom nav
        View scroll = view.findViewById(R.id.scrollSettings);
        if (scroll != null) {
            // Try to read bottomNav height from the activity
            try {
                View bottom = requireActivity().findViewById(R.id.bottomNav);
                if (bottom != null) {
                    int h = bottom.getHeight();
                    if (h <= 0) {
                        // not measured yet: post a runnable
                        bottom.post(() -> {
                            int hh = bottom.getHeight();
                            scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(), scroll.getPaddingRight(), Math.max(scroll.getPaddingBottom(), hh + 8));
                        });
                    } else {
                        scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(), scroll.getPaddingRight(), Math.max(scroll.getPaddingBottom(), h + 8));
                    }
                } else {
                    // Fallback: use WindowInsets to account for system bars
                    ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
                        int bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                        v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), Math.max(v.getPaddingBottom(), bottomInset + 8));
                        return insets;
                    });
                }
            } catch (Exception ignored) {
                // ignore any failures
            }
        }

        // load prefs
        String cur = sp.getString("pref_currency", "VND");
        String weekStart = sp.getString("pref_week_start", "Monday");
        String language = sp.getString("pref_language", "vi");
        String reminder = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
        boolean dailyOn = sp.getBoolean("pref_reminder_daily", true);

        tvCurrency.setText(mapCurrencyLabel(cur));
        tvWeekStart.setText(mapWeekLabel(weekStart));
        tvLanguage.setText(LocaleHelper.getLanguageDisplayName(requireContext(), language));
        String extra = dailyOn ? (" " + getString(R.string.reminder_daily)) : "";
        tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), reminder, extra));
        switchDaily.setChecked(dailyOn);

        // user email
        String email = "";
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) email = FirebaseAuth.getInstance().getCurrentUser().getEmail(); } catch (Exception ignored) {}
        if (email == null || email.isEmpty()) email = getString(R.string.email_hint);
        tvUserEmail.setText(email);
        final String emailFinal = email; // make effectively final for lambdas

        // version
        try {
            PackageManager pm = requireActivity().getPackageManager();
            PackageInfo pi = pm.getPackageInfo(requireActivity().getPackageName(), 0);
            tvVersion.setText(pi.versionName == null ? "1.0" : pi.versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
            tvVersion.setText("1.0");
        }

        // clicks — wire the whole card areas so tapping anywhere opens the dialog
        cardCurrency.setOnClickListener(v -> showCurrencyDialog(tvCurrency));
        cardLanguage.setOnClickListener(v -> showLanguageDialog(tvLanguage));
        cardWeekStart.setOnClickListener(v -> showWeekStartDialog(tvWeekStart));
        cardReminder.setOnClickListener(v -> showTimePicker(tvReminderSummary));
        cardAccount.setOnClickListener(v -> showAccountDialog(emailFinal));
        cardData.setOnClickListener(v -> showDataOptions());
        cardAbout.setOnClickListener(v -> showAboutDialog(tvVersion.getText().toString()));

        // keep individual controls working too
        tvCurrency.setOnClickListener(v -> showCurrencyDialog(tvCurrency));
        tvLanguage.setOnClickListener(v -> showLanguageDialog(tvLanguage));
        tvWeekStart.setOnClickListener(v -> showWeekStartDialog(tvWeekStart));

        switchDaily.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("pref_reminder_daily", isChecked).apply();
            String time = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
            String ex = isChecked ? (" " + getString(R.string.reminder_daily)) : "";
            tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), time, ex));
            Toast.makeText(requireContext(), isChecked ? "Bật nhắc hàng ngày" : "Tắt nhắc hàng ngày", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignored) {}
            Intent i = new Intent(requireActivity(), LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            requireActivity().finish();
        });

        view.findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportCsv());
        view.findViewById(R.id.btnImportCsv).setOnClickListener(v -> Toast.makeText(requireContext(), "Import not implemented", Toast.LENGTH_SHORT).show());
    }

    private void showCurrencyDialog(TextView tvCurrency) {
        final String[] items = new String[]{"Auto", "VND", "USD", "EUR", "CNY"};
        int checked = 0;
        String cur = sp.getString("pref_currency", "auto");
        for (int i = 0; i < items.length; i++) if (items[i].equalsIgnoreCase(cur)) checked = i;
        new AlertDialog.Builder(requireContext())
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

    private void showLanguageDialog(TextView tvLanguage) {
        final String[] languageCodes = new String[]{"en", "vi", "zh", "es"};
        final String[] languageNames = new String[]{"English", "Tiếng Việt", "中文", "Español"};

        int checked = 0;
        String currentLang = sp.getString("pref_language", "vi");
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equals(currentLang)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Ngôn ngữ / Language")
                .setSingleChoiceItems(languageNames, checked, (dialog, which) -> {
                    String selectedCode = languageCodes[which];
                    sp.edit().putString("pref_language", selectedCode).apply();
                    tvLanguage.setText(languageNames[which]);

                    // Apply locale change
                    LocaleHelper.setLocale(requireContext(), selectedCode);

                    // Restart activity to apply changes
                    requireActivity().recreate();

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
        new AlertDialog.Builder(requireContext())
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

    private void showTimePicker(TextView tvReminderSummary) {
        // read current time from prefs (format HH:mm)
        String cur = sp.getString("pref_reminder_time", "19:00");
        int hour = 19, minute = 0;
        try {
            String[] parts = cur.split(":");
            if (parts.length >= 2) {
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            }
        } catch (Exception ignored) {}

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);

        TimePickerDialog tpd = new TimePickerDialog(requireContext(), (view, h, m) -> {
            String formatted = String.format(Locale.ROOT, "%02d:%02d", h, m);
            sp.edit().putString("pref_reminder_time", formatted).apply();
            String extra = sp.getBoolean("pref_reminder_daily", true) ? (" " + getString(R.string.reminder_daily)) : "";
            tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), formatted, extra));
            Toast.makeText(requireContext(), "Thời gian nhắc đã được lưu: " + formatted, Toast.LENGTH_SHORT).show();
        }, hour, minute, true);
        tpd.show();
    }

    private void showAccountDialog(String email) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.account)
                .setMessage(email)
                .setPositiveButton(R.string.logout, (d, i) -> {
                    try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignored) {}
                    Intent it = new Intent(requireActivity(), LoginActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                    requireActivity().finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDataOptions() {
        final String[] items = new String[]{"Xuất dữ liệu (.csv)", "Nhập dữ liệu (.csv)"};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.data)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) exportCsv();
                    else Toast.makeText(requireContext(), "Import not implemented", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showAboutDialog(String version) {
        String msg = getString(R.string.app_name) + "\n" + getString(R.string.version) + ": " + version;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.about)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, null)
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
        if ("Sunday".equalsIgnoreCase(code)) return getString(R.string.chip_sun);
        return getString(R.string.chip_mon);
    }


    private void exportCsv() {
        String userId = "local";
        try { if (FirebaseAuth.getInstance().getCurrentUser() != null) userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); } catch (Exception ignored) {}
        TransactionRepository repo = TransactionRepository.getInstance(requireContext());
        repo.getByUser(userId, entities -> {
            if (entities == null) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), getString(R.string.no_data), Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date());
                File f = new File(requireContext().getFilesDir(), "transactions_" + stamp + ".csv");
                FileWriter fw = new FileWriter(f);
                PrintWriter pw = getPrintWriter(entities, fw);
                pw.close(); fw.close();
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Xuất CSV thành công: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show());
            } catch (Exception ex) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Xuất CSV thất bại: " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @NonNull
    private static PrintWriter getPrintWriter(List<TransactionEntity> entities, FileWriter fw) {
        PrintWriter pw = new PrintWriter(fw);
        pw.println("id,userId,type,amount,category,note,timestamp");
        for (TransactionEntity te : entities) {
            pw.printf(Locale.ROOT, "%d,%s,%s,%.2f,%s,%s,%d\n",
                    te.id, te.userId, te.type, te.amount,
                    te.category == null ? "" : te.category.replace(",", " "),
                    te.note == null ? "" : te.note.replace(",", " "), te.timestamp);
        }
        pw.flush();
        return pw;
    }
}
