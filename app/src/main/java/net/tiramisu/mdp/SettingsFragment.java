package net.tiramisu.mdp;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Locale;

/**
 * SettingsFragment provides the settings UI inside the ViewPager.
 * It reuses the existing layout `activity_settings.xml` and implements
 * the same behaviour as SettingsActivity (preferences, dialogs, export).
 */
public class SettingsFragment extends Fragment {
    private SharedPreferences sp;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the same layout used by the activity
        return inflater.inflate(R.layout.activity_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Register permission launcher
        requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    Toast.makeText(requireContext(), getString(R.string.ok), Toast.LENGTH_SHORT).show();
                    // Schedule alarm if reminder is enabled
                    boolean isEnabled = sp.getBoolean("pref_reminder_daily", true);
                    if (isEnabled) {
                        ReminderScheduler.scheduleReminderFromPreferences(requireContext());
                    }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show();
                    // Turn off switch if permission denied
                    View v = getView();
                    if (v != null) {
                        SwitchMaterial switchDaily = v.findViewById(R.id.switchDaily);
                        if (switchDaily != null && switchDaily.isChecked()) {
                            switchDaily.setChecked(false);
                        }
                    }
                }
            }
        );

        sp = requireActivity().getSharedPreferences("mdp_prefs", Context.MODE_PRIVATE);

        TextView tvCurrency = view.findViewById(R.id.tvCurrency);
        TextView tvAutoDetect = view.findViewById(R.id.tvAutoDetect);
        TextView tvLanguage = view.findViewById(R.id.tvLanguage);
        TextView tvReminderSummary = view.findViewById(R.id.tvReminderSummary);
        SwitchMaterial switchDaily = view.findViewById(R.id.switchDaily);
        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        TextView tvVersion = view.findViewById(R.id.tvVersion);

        View cardCurrency = view.findViewById(R.id.card_currency);
        View cardAutoDetect = view.findViewById(R.id.card_auto_detect);
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
        String language = sp.getString("pref_language", "vi");
        String reminder = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
        boolean dailyOn = sp.getBoolean("pref_reminder_daily", true);

        tvCurrency.setText(mapCurrencyLabel(cur));
        tvLanguage.setText(LocaleHelper.getLanguageDisplayName(requireContext(), language));
        String extra = dailyOn ? (" " + getString(R.string.reminder_daily)) : "";
        tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), reminder, extra));
        switchDaily.setChecked(dailyOn);

        // Auto detection summary
        updateAutoDetectSummary(tvAutoDetect);

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
        cardAutoDetect.setOnClickListener(v -> showAutoDetectDialog(tvAutoDetect));
        cardLanguage.setOnClickListener(v -> showLanguageDialog(tvLanguage));
        cardReminder.setOnClickListener(v -> showTimePicker(tvReminderSummary));
        cardAccount.setOnClickListener(v -> showAccountDialog(emailFinal));
        cardData.setOnClickListener(v -> showDataOptions());
        cardAbout.setOnClickListener(v -> showAboutDialog(tvVersion.getText().toString()));

        // keep individual controls working too
        tvCurrency.setOnClickListener(v -> showCurrencyDialog(tvCurrency));
        tvAutoDetect.setOnClickListener(v -> showAutoDetectDialog(tvAutoDetect));
        tvLanguage.setOnClickListener(v -> showLanguageDialog(tvLanguage));
        tvReminderSummary.setOnClickListener(v -> showTimePicker(tvReminderSummary));

        switchDaily.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Check and request notification permission first
                if (!hasNotificationPermission()) {
                    requestNotificationPermission();
                    // Schedule will happen after permission is granted
                    sp.edit().putBoolean("pref_reminder_daily", isChecked).apply();
                    String time = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
                    String ex = " " + getString(R.string.reminder_daily);
                    tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), time, ex));
                } else {
                    // Permission already granted, schedule immediately
                    sp.edit().putBoolean("pref_reminder_daily", isChecked).apply();
                    String time = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
                    String ex = " " + getString(R.string.reminder_daily);
                    tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), time, ex));
                    ReminderScheduler.scheduleReminderFromPreferences(requireContext());
                    Toast.makeText(requireContext(), getString(R.string.reminder_daily) + " " + getString(R.string.ok), Toast.LENGTH_SHORT).show();
                }
            } else {
                sp.edit().putBoolean("pref_reminder_daily", isChecked).apply();
                String time = sp.getString("pref_reminder_time", getString(R.string.reminder_time_default));
                tvReminderSummary.setText(getString(R.string.reminder_summary_format, getString(R.string.reminder_time_label), time, ""));
                ReminderScheduler.cancelReminder(requireContext());
                Toast.makeText(requireContext(), getString(R.string.reminder_off), Toast.LENGTH_SHORT).show();
            }
        });

        // Test notification button
        view.findViewById(R.id.btnTestNotification).setOnClickListener(v -> {
            if (!hasNotificationPermission()) {
                requestNotificationPermission();
                // Show toast to inform user
                Toast.makeText(requireContext(), getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show();
            } else {
                NotificationHelper.showTestNotification(requireContext());
                Toast.makeText(requireContext(), getString(R.string.test_notification), Toast.LENGTH_SHORT).show();
            }
        });

        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignored) {}
            Intent i = new Intent(requireActivity(), LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            requireActivity().finish();
        });

        view.findViewById(R.id.btnExportCsv).setOnClickListener(v -> exportAndSendEmail());
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

            // Reschedule alarm with new time if reminder is enabled
            boolean isEnabled = sp.getBoolean("pref_reminder_daily", true);
            if (isEnabled) {
                ReminderScheduler.scheduleReminder(requireContext(), h, m);
            }

            Toast.makeText(requireContext(), getString(R.string.reminder_time_label) + ": " + formatted, Toast.LENGTH_SHORT).show();
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

        // If Auto, show "Auto (detected currency)"
        if ("AUTO".equalsIgnoreCase(cur)) {
            String detectedCurrency = getAutoCurrencyCode();
            String currencyName = getCurrencyName(detectedCurrency);
            return "Auto (" + currencyName + ")";
        }

        switch (cur.toUpperCase(Locale.ROOT)) {
            case "VND": return "Việt Nam Đồng (₫)";
            case "USD": return "USD ($)";
            case "EUR": return "EUR (€)";
            case "CNY": return "CNY (¥)";
            default: return cur;
        }
    }

    /**
     * Get the currency code that would be used in Auto mode
     */
    private String getAutoCurrencyCode() {
        android.content.res.Configuration config = requireContext().getResources().getConfiguration();
        Locale currentLocale = config.getLocales().get(0);
        String language = currentLocale.getLanguage();

        switch (language) {
            case "en": return "USD";
            case "vi": return "VND";
            case "zh": return "CNY";
            case "es": return "EUR";
            default:
                try {
                    java.util.Currency currency = java.util.Currency.getInstance(currentLocale);
                    return currency.getCurrencyCode();
                } catch (Exception e) {
                    return "VND";
                }
        }
    }

    /**
     * Get display name for currency code
     */
    private String getCurrencyName(String code) {
        switch (code) {
            case "VND": return "₫";
            case "USD": return "$";
            case "EUR": return "€";
            case "CNY": return "¥";
            default: return code;
        }
    }


    /**
     * Check if notification permission is granted (Android 13+)
     */
    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // No permission needed before Android 13
    }

    /**
     * Request notification permission (Android 13+)
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                // Show explanation dialog before requesting permission
                if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showPermissionExplanationDialog();
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        }
    }

    /**
     * Show dialog explaining why notification permission is needed
     */
    private void showPermissionExplanationDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.notification_permission_required)
            .setMessage(R.string.notification_permission_explanation)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
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
            Toast.makeText(requireContext(), "Không tìm thấy email người dùng", Toast.LENGTH_SHORT).show();
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

        TransactionRepository repo = TransactionRepository.getInstance(requireContext());
        repo.getByUser(userId, entities -> {
            if (entities == null || entities.isEmpty()) {
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), getString(R.string.no_data), Toast.LENGTH_SHORT).show());
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
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "Không có dữ liệu tháng này", Toast.LENGTH_SHORT).show());
                return;
            }

            // Build email content
            StringBuilder emailBody = new StringBuilder();
            emailBody.append("Báo cáo chi tiêu tháng ").append(monthYear).append("\n\n");
            emailBody.append("═══════════════════════════════════\n");
            emailBody.append("TỔNG QUAN\n");
            emailBody.append("═══════════════════════════════════\n\n");
            emailBody.append("📈 Tổng thu nhập: ").append(CurrencyUtils.formatCurrency(requireContext(), totalIncome)).append("\n");
            emailBody.append("📉 Tổng chi tiêu: ").append(CurrencyUtils.formatCurrency(requireContext(), totalExpense)).append("\n");
            emailBody.append("💰 Số dư: ").append(CurrencyUtils.formatCurrency(requireContext(), totalIncome - totalExpense)).append("\n\n");

            emailBody.append("═══════════════════════════════════\n");
            emailBody.append("CHI TIẾT GIAO DỊCH (").append(monthTransactions.size()).append(" giao dịch)\n");
            emailBody.append("═══════════════════════════════════\n\n");

            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

            for (TransactionEntity te : monthTransactions) {
                String type = "income".equalsIgnoreCase(te.type) ? "📈 Thu" : "📉 Chi";
                String date = dateFormat.format(new Date(te.timestamp));
                String amount = CurrencyUtils.formatCurrency(requireContext(), Math.abs(te.amount));
                String title = te.title != null && !te.title.isEmpty() ? te.title : "Không có tiêu đề";
                String category = te.category != null ? CategoryHelper.getLocalizedCategory(requireContext(), te.category) : "";

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
            requireActivity().runOnUiThread(() -> {
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("message/rfc822");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Ez Money - Báo cáo chi tiêu " + monthYear);
                emailIntent.putExtra(Intent.EXTRA_TEXT, emailBody.toString());

                try {
                    startActivity(Intent.createChooser(emailIntent, "Gửi báo cáo qua email"));
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(requireContext(), "Không tìm thấy ứng dụng email", Toast.LENGTH_SHORT).show();
                }
            });
        });
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
        pw.println("id,userId,type,amount,category,title,note,timestamp");
        for (TransactionEntity te : entities) {
            pw.printf(Locale.ROOT, "%d,%s,%s,%.2f,%s,%s,%s,%d\n",
                    te.id, te.userId, te.type, te.amount,
                    te.category == null ? "" : te.category.replace(",", " "),
                    te.title == null ? "" : te.title.replace(",", " "),
                    te.note == null ? "" : te.note.replace(",", " "), te.timestamp);
        }
        pw.flush();
        return pw;
    }

    /**
     * Update auto detection summary based on saved phone numbers
     */
    private void updateAutoDetectSummary(TextView tvAutoDetect) {
        java.util.Set<String> phones = sp.getStringSet("pref_auto_detect_phones", new java.util.HashSet<>());
        if (phones.isEmpty()) {
            tvAutoDetect.setText(getString(R.string.auto_detect_no_phones));
        } else {
            tvAutoDetect.setText(phones.size() + " số điện thoại");
        }
    }

    /**
     * Show dialog to manage auto detection feature
     */
    private void showAutoDetectDialog(TextView tvAutoDetect) {
        java.util.Set<String> phones = new java.util.HashSet<>(sp.getStringSet("pref_auto_detect_phones", new java.util.HashSet<>()));

        // Create list of phone numbers for display
        final java.util.List<String> phoneList = new java.util.ArrayList<>(phones);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
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

        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            showAddPhoneDialog(tvAutoDetect);
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    /**
     * Show dialog to add phone number
     */
    private void showAddPhoneDialog(TextView tvAutoDetect) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.auto_detect_add_phone);

        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint(R.string.auto_detect_phone_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        builder.setView(input);

        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String phone = input.getText().toString().trim();
            if (phone.isEmpty()) {
                Toast.makeText(requireContext(), R.string.phone_number_invalid, Toast.LENGTH_SHORT).show();
                return;
            }

            // Check SMS permission
            if (!hasSmsPermission()) {
                requestSmsPermission();
                return;
            }

            // Add phone to saved list
            java.util.Set<String> phones = new java.util.HashSet<>(sp.getStringSet("pref_auto_detect_phones", new java.util.HashSet<>()));
            phones.add(phone);
            sp.edit().putStringSet("pref_auto_detect_phones", phones).apply();

            // Enable auto detection
            sp.edit().putBoolean("pref_auto_detect_enabled", true).apply();

            updateAutoDetectSummary(tvAutoDetect);
            Toast.makeText(requireContext(), R.string.phone_number_added, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    /**
     * Show dialog to confirm phone removal
     */
    private void showRemovePhoneDialog(String phone, TextView tvAutoDetect) {
        new AlertDialog.Builder(requireContext())
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
                Toast.makeText(requireContext(), R.string.phone_number_removed, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Check if SMS permission is granted
     */
    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECEIVE_SMS)
            == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request SMS permission
     */
    private void requestSmsPermission() {
        if (!hasSmsPermission()) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)) {
                showSmsPermissionExplanationDialog();
            } else {
                requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, 1001);
            }
        }
    }

    /**
     * Show dialog explaining why SMS permission is needed
     */
    private void showSmsPermissionExplanationDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.auto_detect_permission_required)
            .setMessage(R.string.auto_detect_permission_explanation)
            .setPositiveButton(R.string.ok, (dialog, which) -> {
                requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, 1001);
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), getString(R.string.ok), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), getString(R.string.sms_permission_denied), Toast.LENGTH_LONG).show();
            }
        }
    }
}
