package net.tiramisu.mdp;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SMS Receiver for automatic transaction detection
 * Listens for SMS from registered phone numbers and extracts transaction amounts
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String PREFS_NAME = "mdp_prefs";
    private static final String PREF_AUTO_DETECT_ENABLED = "pref_auto_detect_enabled";
    private static final String PREF_AUTO_DETECT_PHONES = "pref_auto_detect_phones";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null ||
            !intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            return;
        }

        // Check if feature is enabled
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean enabled = sp.getBoolean(PREF_AUTO_DETECT_ENABLED, false);
        if (!enabled) {
            return;
        }

        // Check SMS permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            Bundle bundle = intent.getExtras();
            if (bundle == null) {
                return;
            }

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null || pdus.length == 0) {
                return;
            }

            // Get registered phone numbers
            Set<String> registeredPhones = sp.getStringSet(PREF_AUTO_DETECT_PHONES, new HashSet<>());
            if (registeredPhones.isEmpty()) {
                return;
            }

            // Get format for SMS parsing
            String format = bundle.getString("format");

            // Parse all SMS messages
            for (Object pdu : pdus) {
                SmsMessage smsMessage;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                } else {
                    smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                }

                if (smsMessage == null) {
                    continue;
                }

                String sender = smsMessage.getOriginatingAddress();
                String messageBody = smsMessage.getMessageBody();

                // Normalize sender phone number (remove country code, spaces, etc.)
                String normalizedSender = normalizePhoneNumber(sender);

                // Check if sender is in registered list
                boolean isRegistered = false;
                for (String phone : registeredPhones) {
                    if (normalizedSender.contains(normalizePhoneNumber(phone)) ||
                        normalizePhoneNumber(phone).contains(normalizedSender)) {
                        isRegistered = true;
                        break;
                    }
                }

                if (!isRegistered) {
                    continue;
                }

                Log.d(TAG, "SMS from registered number: " + sender);
                Log.d(TAG, "Message: " + messageBody);

                // Extract amount from message
                Double amount = extractAmount(messageBody);
                if (amount != null && amount > 0) {
                    // Create transaction
                    createTransaction(context, amount, sender, messageBody);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS", e);
        }
    }

    /**
     * Normalize phone number by removing country code, spaces, dashes, etc.
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) {
            return "";
        }
        // Remove all non-digit characters
        String normalized = phone.replaceAll("[^0-9]", "");
        // Remove country code if present
        if (normalized.startsWith("84")) {
            normalized = "0" + normalized.substring(2);
        } else if (normalized.startsWith("+84")) {
            normalized = "0" + normalized.substring(3);
        }
        return normalized;
    }

    /**
     * Extract amount from SMS message
     * Format: X * 1000 (e.g., 100,000 VND or 100.000 VND or 100000)
     * If there are 2 amounts in the message, ignore it
     */
    private Double extractAmount(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }

        // Pattern to match amounts in various formats
        // Matches: 100,000 or 100.000 or 100000 or 100,000.00
        Pattern pattern = Pattern.compile("([0-9]{1,3}([,.]?[0-9]{3})*([.,][0-9]{1,2})?)");
        Matcher matcher = pattern.matcher(message);

        Double firstAmount = null;
        boolean hasSecondAmount = false;

        while (matcher.find()) {
            String amountStr = matcher.group(1);
            if (amountStr == null) {
                continue;
            }

            // Remove thousand separators and parse
            String cleanAmount = amountStr.replace(",", "").replace(".", "");

            try {
                double amount = Double.parseDouble(cleanAmount);

                // Check if amount is in format X * 1000 (must be at least 1000)
                if (amount >= 1000 && amount % 1000 == 0) {
                    if (firstAmount == null) {
                        firstAmount = amount;
                    } else {
                        // Found second amount, mark to ignore
                        hasSecondAmount = true;
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                Log.d(TAG, "Failed to parse amount: " + amountStr);
            }
        }

        // If there are 2 amounts, ignore the message
        if (hasSecondAmount) {
            Log.d(TAG, "Message contains 2 amounts, ignoring");
            return null;
        }

        return firstAmount;
    }

    /**
     * Create transaction in database
     */
    private void createTransaction(Context context, double amount, String sender, String message) {
        try {
            String userId = "local";
            try {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                }
            } catch (Exception ignored) {
            }

            // Create transaction with proper constructor
            String noteText = "Từ SMS: " + sender + " - " + message.substring(0, Math.min(50, message.length()));
            TransactionEntity transaction = new TransactionEntity(
                userId,
                "expense", // Default to expense
                amount,
                noteText,
                "other",
                "Giao dịch tự động", // Auto transaction
                System.currentTimeMillis()
            );

            // Save to database
            TransactionRepository repo = TransactionRepository.getInstance(context);
            repo.insert(transaction, () -> {
                Log.d(TAG, "Auto transaction created: " + amount);

                // Show notification
                String amountStr = CurrencyUtils.formatCurrency(context, amount);
                NotificationHelper.showAutoTransactionNotification(context, amountStr);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error creating transaction", e);
        }
    }
}

