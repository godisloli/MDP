package net.tiramisu.mdp.repo;

import android.content.Context;

import net.tiramisu.mdp.db.AppDatabase;
import net.tiramisu.mdp.db.TransactionDao;
import net.tiramisu.mdp.model.TransactionEntity;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class TransactionRepository {
    private final TransactionDao dao;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public TransactionRepository(Context context) {
        dao = AppDatabase.getInstance(context).transactionDao();
    }

    public void insert(final TransactionEntity t, Runnable callback) {
        executor.execute(() -> {
            try {
                dao.insert(t);
            } catch (Exception ignored) {}
            if (callback != null) callback.run();
        });
    }

    public void getByUser(final String userId, final Consumer<List<TransactionEntity>> callback) {
        executor.execute(() -> {
            List<TransactionEntity> res = null;
            try {
                res = dao.getByUser(userId);
            } catch (Exception ignored) {}
            if (callback != null) callback.accept(res);
        });
    }

    public void getSumIncomeInRange(final String userId, final long from, final long to, final Consumer<Double> callback) {
        executor.execute(() -> {
            Double v = null;
            try { v = dao.getSumIncomeInRange(userId, from, to); } catch (Exception ignored) {}
            if (callback != null) callback.accept(v == null ? 0.0 : v);
        });
    }

    public void getSumExpenseInRange(final String userId, final long from, final long to, final Consumer<Double> callback) {
        executor.execute(() -> {
            Double v = null;
            try { v = dao.getSumExpenseInRange(userId, from, to); } catch (Exception ignored) {}
            if (callback != null) callback.accept(v == null ? 0.0 : v);
        });
    }

    public void getSumAllInRange(final String userId, final long from, final long to, final Consumer<Double> callback) {
        executor.execute(() -> {
            Double v = null;
            try { v = dao.getSumAllInRange(userId, from, to); } catch (Exception ignored) {}
            if (callback != null) callback.accept(v == null ? 0.0 : v);
        });
    }

    public void migrateUserId(final String oldUserId, final String newUserId, final Runnable callback) {
        executor.execute(() -> {
            try { dao.migrateUserId(oldUserId, newUserId); } catch (Exception ignored) {}
            if (callback != null) callback.run();
        });
    }

    public void getNewestTransactions(final String userId, final int limit, final Consumer<List<TransactionEntity>> callback) {
        executor.execute(() -> {
            List<TransactionEntity> res = null;
            try {
                // getByUser returns all ordered desc; we can slice to limit
                res = dao.getByUser(userId);
                if (res != null && res.size() > limit) res = res.subList(0, limit);
            } catch (Exception ignored) {}
            if (callback != null) callback.accept(res);
        });
    }
}
