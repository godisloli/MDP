package net.tiramisu.mdp;

import android.content.Context;

/**
 * Helper class for managing category names and translations
 */
public class CategoryHelper {

    // Standard category keys (stored in database)
    public static final String KEY_FOOD = "Ăn uống";
    public static final String KEY_TRANSPORT = "Đi lại";
    public static final String KEY_SHOPPING = "Mua sắm";
    public static final String KEY_EDUCATION = "Học tập";
    public static final String KEY_ENTERTAINMENT = "Giải trí";
    public static final String KEY_OTHER = "Khác";
    public static final String KEY_INCOME = "Thu nhập";

    /**
     * Get localized category name
     */
    public static String getLocalizedCategory(Context context, String categoryKey) {
        if (context == null || categoryKey == null) return categoryKey;

        switch (categoryKey) {
            case KEY_FOOD:
                return context.getString(R.string.category_food);
            case KEY_TRANSPORT:
                return context.getString(R.string.category_transport);
            case KEY_SHOPPING:
                return context.getString(R.string.category_shopping);
            case KEY_EDUCATION:
                return context.getString(R.string.category_education);
            case KEY_ENTERTAINMENT:
                return context.getString(R.string.category_entertainment);
            case KEY_OTHER:
                return context.getString(R.string.category_other);
            case KEY_INCOME:
                return context.getString(R.string.category_income);
            default:
                return categoryKey;
        }
    }

    /**
     * Get all category keys
     */
    public static String[] getCategoryKeys() {
        return new String[]{
            KEY_FOOD,
            KEY_TRANSPORT,
            KEY_SHOPPING,
            KEY_EDUCATION,
            KEY_ENTERTAINMENT,
            KEY_OTHER
        };
    }

    /**
     * Get all localized category names
     */
    public static String[] getLocalizedCategories(Context context) {
        return new String[]{
            context.getString(R.string.category_food),
            context.getString(R.string.category_transport),
            context.getString(R.string.category_shopping),
            context.getString(R.string.category_education),
            context.getString(R.string.category_entertainment),
            context.getString(R.string.category_other)
        };
    }
}

