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

    private static final String AUTO_CHANNEL_ID = "auto_transaction_channel";
    private static final String AUTO_CHANNEL_NAME = "Auto Transaction";
    private static final int AUTO_NOTIFICATION_ID = 1002;

    /**
     * Create notification channel (required for Android O+)
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Daily reminder channel
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Daily expense reminder notifications");

            // Auto transaction channel
            NotificationChannel autoChannel = new NotificationChannel(
                AUTO_CHANNEL_ID,
                AUTO_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            );
            autoChannel.setDescription("Automatic transaction detection notifications");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                manager.createNotificationChannel(autoChannel);
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

    /**
     * Show notification when auto transaction is detected
     */
    public static void showAutoTransactionNotification(Context context, String amount) {
        createNotificationChannel(context);

        String title = context.getString(R.string.auto_transaction_detected);
        String content = context.getString(R.string.auto_transaction_added, amount);

        // Create intent to open app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("open_tab", "transactions");
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, AUTO_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);

        // Show notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Check permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        notificationManager.notify(AUTO_NOTIFICATION_ID, builder.build());
    }
}

