package net.tiramisu.mdp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.repo.TransactionRepository;

import java.util.Calendar;

/**
 * BroadcastReceiver to handle daily reminder alarms
 */
public class DailyReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "DailyReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Daily reminder alarm received");

        // Check if reminder is enabled
        SharedPreferences sp = context.getSharedPreferences("mdp_prefs", Context.MODE_PRIVATE);
        boolean isEnabled = sp.getBoolean("pref_reminder_daily", true);

        if (!isEnabled) {
            Log.d(TAG, "Reminder is disabled, skipping notification");
            return;
        }

        // Get user ID
        String userId = "local";
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting user ID: " + e.getMessage());
        }

        // Calculate today's start and end timestamps
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();

        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        long endOfDay = cal.getTimeInMillis();

        // Get transaction repository
        TransactionRepository repository = TransactionRepository.getInstance(context);

        // Fetch today's expense and income totals
        final String finalUserId = userId;
        repository.getSumExpenseInRange(userId, startOfDay, endOfDay, expense -> {
            repository.getSumIncomeInRange(finalUserId, startOfDay, endOfDay, income -> {
                double totalExpense = expense != null ? expense : 0.0;
                double totalIncome = income != null ? income : 0.0;

                Log.d(TAG, "Today's expense: " + totalExpense + ", income: " + totalIncome);

                // Show notification
                NotificationHelper.showDailyReminder(context, totalExpense, totalIncome);
            });
        });
    }
}

