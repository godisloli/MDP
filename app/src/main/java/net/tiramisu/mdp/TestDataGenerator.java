package net.tiramisu.mdp;

import android.content.Context;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;

import net.tiramisu.mdp.model.TransactionEntity;
import net.tiramisu.mdp.repo.TransactionRepository;

import java.util.Calendar;
import java.util.Random;

/**
 * Utility class to generate test transaction data for testing month-over-month comparison
 */
public class TestDataGenerator {
    private static final String TAG = "TestDataGenerator";

    private final Context context;
    private final TransactionRepository repository;
    private final Random random = new Random();

    public TestDataGenerator(Context context) {
        this.context = context;
        this.repository = TransactionRepository.getInstance(context);
    }

    /**
     * Generate test transactions for current month and previous month
     * @param callback Called when generation is complete
     */
    public void generateTestData(Runnable callback) {
        String userId = getUserId();

        // Generate transactions for current month (October 2025)
        generateMonthTransactions(userId, 2025, Calendar.OCTOBER, 10, callback);

        // Generate transactions for previous month (September 2025)
        generateMonthTransactions(userId, 2025, Calendar.SEPTEMBER, 10, null);
    }

    /**
     * Generate transactions for a specific month
     */
    private void generateMonthTransactions(String userId, int year, int month, int count, Runnable callback) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Generate income transactions (20-30% of total)
        int incomeCount = Math.max(1, count / 4);
        for (int i = 0; i < incomeCount; i++) {
            int day = random.nextInt(maxDay) + 1;
            cal.set(Calendar.DAY_OF_MONTH, day);
            cal.set(Calendar.HOUR_OF_DAY, random.nextInt(24));
            cal.set(Calendar.MINUTE, random.nextInt(60));

            double amount = generateIncomeAmount();
            String title = getRandomIncomeTitle();

            TransactionEntity transaction = new TransactionEntity(
                userId,
                "income",
                amount,
                "Test data - Thu nhập",
                CategoryHelper.KEY_INCOME,
                title,
                cal.getTimeInMillis()
            );

            final boolean isLast = (i == incomeCount - 1) && (callback != null);
            repository.insert(transaction, isLast ? callback : null);
        }

        // Generate expense transactions
        int expenseCount = count - incomeCount;
        String[] categories = CategoryHelper.getCategoryKeys();

        for (int i = 0; i < expenseCount; i++) {
            int day = random.nextInt(maxDay) + 1;
            cal.set(Calendar.DAY_OF_MONTH, day);
            cal.set(Calendar.HOUR_OF_DAY, random.nextInt(24));
            cal.set(Calendar.MINUTE, random.nextInt(60));

            // Pick random category (exclude income category)
            String category = categories[random.nextInt(categories.length - 1)];
            double amount = -Math.abs(generateExpenseAmount(category));
            String title = getRandomExpenseTitle(category);

            TransactionEntity transaction = new TransactionEntity(
                userId,
                "expense",
                amount,
                "Test data - Chi tiêu",
                category,
                title,
                cal.getTimeInMillis()
            );

            repository.insert(transaction, null);
        }

        Log.d(TAG, "Generated " + count + " transactions for " + year + "/" + (month + 1));
    }

    private String getUserId() {
        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                return FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
        } catch (Exception ignored) {}
        return "local";
    }

    private double generateIncomeAmount() {
        // Generate income between 5,000,000 - 20,000,000 VND
        return 5000000 + random.nextInt(15000000);
    }

    private double generateExpenseAmount(String category) {
        // Generate different expense ranges based on category
        switch (category) {
            case CategoryHelper.KEY_FOOD:
                return 50000 + random.nextInt(200000); // 50k - 250k
            case CategoryHelper.KEY_TRANSPORT:
                return 20000 + random.nextInt(150000); // 20k - 170k
            case CategoryHelper.KEY_SHOPPING:
                return 100000 + random.nextInt(900000); // 100k - 1M
            case CategoryHelper.KEY_ENTERTAINMENT:
                return 50000 + random.nextInt(300000); // 50k - 350k
            case CategoryHelper.KEY_EDUCATION:
                return 200000 + random.nextInt(1800000); // 200k - 2M
            default:
                return 50000 + random.nextInt(450000); // 50k - 500k
        }
    }

    private String getRandomIncomeTitle() {
        String[] titles = {
            "Lương tháng",
            "Thưởng",
            "Làm thêm",
            "Dự án",
            "Freelance",
            "Đầu tư"
        };
        return titles[random.nextInt(titles.length)];
    }

    private String getRandomExpenseTitle(String category) {
        switch (category) {
            case CategoryHelper.KEY_FOOD:
                String[] food = {"Ăn sáng", "Ăn trưa", "Ăn tối", "Cà phê", "Ăn vặt", "Siêu thị"};
                return food[random.nextInt(food.length)];
            case CategoryHelper.KEY_TRANSPORT:
                String[] transport = {"Xe bus", "Grab", "Xăng xe", "Gửi xe", "Taxi"};
                return transport[random.nextInt(transport.length)];
            case CategoryHelper.KEY_SHOPPING:
                String[] shopping = {"Quần áo", "Giày dép", "Phụ kiện", "Mỹ phẩm", "Đồ dùng"};
                return shopping[random.nextInt(shopping.length)];
            case CategoryHelper.KEY_ENTERTAINMENT:
                String[] entertainment = {"Xem phim", "Game", "Karaoke", "Du lịch", "Sách"};
                return entertainment[random.nextInt(entertainment.length)];
            case CategoryHelper.KEY_EDUCATION:
                String[] education = {"Học phí", "Sách vở", "Khóa học", "Đào tạo"};
                return education[random.nextInt(education.length)];
            default:
                String[] other = {"Chi phí khác", "Tiện ích", "Y tế", "Quà tặng"};
                return other[random.nextInt(other.length)];
        }
    }

    /**
     * Clear all test data (for cleanup)
     */
    public void clearTestData() {
        // Note: This would require adding a delete all method to the repository
        Log.d(TAG, "Clear test data - implement if needed");
    }
}

