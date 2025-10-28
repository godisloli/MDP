package net.tiramisu.mdp.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import net.tiramisu.mdp.model.CategorySum;
import net.tiramisu.mdp.model.TransactionEntity;

import java.util.List;

@Dao
public interface TransactionDao {
    @Insert
    long insert(TransactionEntity t);

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY timestamp DESC")
    List<TransactionEntity> getByUser(String userId);

    // Sum of positive amounts (income) in a time range
    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND timestamp >= :from AND timestamp <= :to AND amount > 0")
    Double getSumIncomeInRange(String userId, long from, long to);

    // Sum of negative amounts (expense) in a time range
    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND timestamp >= :from AND timestamp <= :to AND amount < 0")
    Double getSumExpenseInRange(String userId, long from, long to);

    // Sum of all amounts in a time range
    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND timestamp >= :from AND timestamp <= :to")
    Double getSumAllInRange(String userId, long from, long to);

    // migration helper: move transactions from one userId to another
    @Query("UPDATE transactions SET userId = :newUserId WHERE userId = :oldUserId")
    void migrateUserId(String oldUserId, String newUserId);

    // Sum grouped by category for a user in time range
    @Query("SELECT category as category, SUM(amount) as total FROM transactions WHERE userId = :userId AND timestamp >= :from AND timestamp <= :to GROUP BY category ORDER BY total DESC")
    List<CategorySum> getCategorySumsInRange(String userId, long from, long to);

    // Get earliest transaction timestamp for a user (or NULL if none)
    @Query("SELECT MIN(timestamp) FROM transactions WHERE userId = :userId")
    Long getMinTimestampForUser(String userId);
}
