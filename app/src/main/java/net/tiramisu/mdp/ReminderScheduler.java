package net.tiramisu.mdp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;

/**
 * Helper class to schedule and cancel daily reminder alarms
 */
public class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";
    private static final int ALARM_REQUEST_CODE = 1000;

    /**
     * Schedule daily reminder alarm at specified time
     */
    public static void scheduleReminder(Context context, int hourOfDay, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null");
            return;
        }

        Intent intent = new Intent(context, DailyReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set alarm time for today
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Schedule repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.getTimeInMillis(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        );

        Log.d(TAG, "Reminder scheduled at " + hourOfDay + ":" + minute);
    }

    /**
     * Schedule reminder based on saved preferences
     */
    public static void scheduleReminderFromPreferences(Context context) {
        SharedPreferences sp = context.getSharedPreferences("mdp_prefs", Context.MODE_PRIVATE);
        boolean isEnabled = sp.getBoolean("pref_reminder_daily", true);

        if (!isEnabled) {
            cancelReminder(context);
            return;
        }

        String timeStr = sp.getString("pref_reminder_time", "19:00");
        int hour = 19, minute = 0;

        try {
            String[] parts = timeStr.split(":");
            if (parts.length >= 2) {
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing time: " + e.getMessage());
        }

        scheduleReminder(context, hour, minute);
    }

    /**
     * Cancel daily reminder alarm
     */
    public static void cancelReminder(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager is null");
            return;
        }

        Intent intent = new Intent(context, DailyReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Reminder cancelled");
    }
}

