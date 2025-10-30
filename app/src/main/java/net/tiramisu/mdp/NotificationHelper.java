package net.tiramisu.mdp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Helper class for creating and managing notifications
 */
public class NotificationHelper {

    private static final String CHANNEL_ID = "daily_reminder_channel";
    private static final String CHANNEL_NAME = "Daily Reminder";
    private static final int NOTIFICATION_ID = 1001;

    /**
     * Create notification channel (required for Android O+)
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Daily expense reminder notifications");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Show daily reminder notification with expense and income summary
     */
    public static void showDailyReminder(Context context, double totalExpense, double totalIncome) {
        createNotificationChannel(context);

        // Get localized strings
        String title = context.getString(R.string.notification_reminder_title);

        // Format the content with currency
        String expenseText = context.getString(R.string.notification_expense_label) +
                           ": " + CurrencyUtils.formatCurrency(context, Math.abs(totalExpense));
        String incomeText = context.getString(R.string.notification_income_label) +
                          ": " + CurrencyUtils.formatCurrency(context, totalIncome);
        String content = expenseText + "\n" + incomeText;

        // Create intent to open app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create intent for Add Transaction action
        Intent addIntent = new Intent(context, AddTransactionActivity.class);
        addIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent addPendingIntent = PendingIntent.getActivity(
            context,
            1,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Create intent for View Details action (open Statistics tab)
        Intent viewIntent = new Intent(context, MainActivity.class);
        viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        viewIntent.putExtra("open_tab", "statistics"); // Extra to tell MainActivity to open statistics tab
        PendingIntent viewPendingIntent = PendingIntent.getActivity(
            context,
            2,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(expenseText + " | " + incomeText)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_add, context.getString(R.string.notification_action_add), addPendingIntent)
            .addAction(R.drawable.ic_chart, context.getString(R.string.notification_action_view), viewPendingIntent);

        // Show notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Check permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                // Permission not granted, can't show notification
                return;
            }
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * Show test notification
     */
    public static void showTestNotification(Context context) {
        showDailyReminder(context, 150000, 50000);
    }
}

