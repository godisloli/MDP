package net.tiramisu.mdp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/**
 * LocaleHelper manages language/locale changes for the application.
 * It persists the user's language preference and applies it to the app context.
 */
public class LocaleHelper {
    private static final String PREF_LANGUAGE = "pref_language";
    private static final String PREFS_NAME = "mdp_prefs";

    /**
     * Sets the app locale to the saved language preference.
     * Call this in attachBaseContext() of activities.
     */
    public static Context setLocale(Context context) {
        return setLocale(context, getPersistedLanguage(context));
    }

    /**
     * Sets and persists the app locale.
     */
    public static Context setLocale(Context context, String languageCode) {
        persist(context, languageCode);
        return updateResources(context, languageCode);
    }

    /**
     * Gets the currently persisted language code.
     */
    public static String getPersistedLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_LANGUAGE, "vi"); // default to Vietnamese
    }

    /**
     * Persists the language preference.
     */
    private static void persist(Context context, String languageCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_LANGUAGE, languageCode).apply();
    }

    /**
     * Updates the context resources with the new locale.
     */
    private static Context updateResources(Context context, String languageCode) {
        Locale locale = getLocaleFromCode(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale);
            context = context.createConfigurationContext(configuration);
        } else {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        }

        return context;
    }

    /**
     * Maps language code to Locale object.
     */
    private static Locale getLocaleFromCode(String languageCode) {
        switch (languageCode) {
            case "en":
                return new Locale("en");
            case "vi":
                return new Locale("vi");
            case "zh":
                return Locale.SIMPLIFIED_CHINESE;
            case "es":
                return new Locale("es");
            default:
                return new Locale("vi");
        }
    }

    /**
     * Gets the display name for a language code.
     */
    public static String getLanguageDisplayName(Context context, String languageCode) {
        switch (languageCode) {
            case "en":
                return "English";
            case "vi":
                return "Tiếng Việt";
            case "zh":
                return "中文";
            case "es":
                return "Español";
            default:
                return "Unknown";
        }
    }
}

