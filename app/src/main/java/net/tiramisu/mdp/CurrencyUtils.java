package net.tiramisu.mdp;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CurrencyUtils {
    private static final String PREFS = "mdp_prefs";

    // Basic, static exchange rates: target currency per 1 VND
    // These are approximate and should be updated from a remote source in a real app.
    private static Map<String, Double> ratesPerVnd() {
        Map<String, Double> m = new HashMap<>();
        m.put("VND", 1.0);
        m.put("USD", 1.0 / 23000.0); // ~0.00004348
        m.put("EUR", 1.0 / 25000.0); // ~0.00004
        m.put("CNY", 1.0 / 3300.0);  // ~0.000303
        // add more if needed
        return m;
    }

    // Format an amount that is stored in the app's base currency (VND) into the user's selected currency.
    public static String formatCurrency(Context ctx, double amountInVnd) {
        String pref = getPrefCurrency(ctx);
        // if auto or same as VND, just format as VND (or locale default for auto)
        if ("auto".equalsIgnoreCase(pref)) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.getDefault());
            return nf.format(amountInVnd);
        }

        String code = pref.toUpperCase(Locale.ROOT);
        if ("AUTO".equals(code)) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.getDefault());
            return nf.format(amountInVnd);
        }

        // Convert from VND into target currency using simple table
        double converted = amountInVnd;
        Map<String, Double> rates = ratesPerVnd();
        if (!"VND".equals(code)) {
            Double r = rates.get(code);
            if (r != null) converted = amountInVnd * r;
            else {
                // unknown currency code: attempt no conversion
                converted = amountInVnd;
            }
        }

        // Get formatter for target currency and format
        NumberFormat nf = getFormatterForCode(code);
        try {
            Currency c = Currency.getInstance(code);
            nf.setCurrency(c);
        } catch (Exception ignored) {}
        return nf.format(converted);
    }

    private static String getPrefCurrency(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return sp.getString("pref_currency", "VND");
        } catch (Exception ex) {
            return "VND";
        }
    }

    private static NumberFormat getFormatterForCode(String code) {
        Locale locale;
        switch (code) {
            case "USD": locale = Locale.US; break;
            case "EUR": locale = Locale.GERMANY; break;
            case "CNY": locale = Locale.CHINA; break;
            case "VND": locale = new Locale.Builder().setLanguage("vi").setRegion("VN").build(); break;
            default: locale = Locale.getDefault(); break;
        }
        return NumberFormat.getCurrencyInstance(locale);
    }
}
