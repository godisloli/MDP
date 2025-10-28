package net.tiramisu.mdp.repo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FirebaseFirestore;

import net.tiramisu.mdp.db.AppDatabase;
import net.tiramisu.mdp.db.TransactionDao;
import net.tiramisu.mdp.model.TransactionEntity;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TransactionRepository {
    private final TransactionDao dao;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final Context appContext;

    // simple listeners - thread-safe
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    // LiveData cache for user base balances
    private final ConcurrentHashMap<String, MutableLiveData<Double>> baseLiveMap = new ConcurrentHashMap<>();
    // track manual updates to cached base to avoid race with remote fetch
    private final ConcurrentHashMap<String, Long> manualUpdateTs = new ConcurrentHashMap<>();

    // singleton instance
    private static volatile TransactionRepository INSTANCE;

    // private constructor to enforce singleton usage
    private TransactionRepository(Context context) {
        // use application context to avoid leaking activities
        Context appCtx = context.getApplicationContext();
        this.appContext = appCtx;
        dao = AppDatabase.getInstance(appCtx).transactionDao();
    }

    public static TransactionRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TransactionRepository.class) {
                if (INSTANCE == null) INSTANCE = new TransactionRepository(context);
            }
        }
        return INSTANCE;
    }

    // Backwards-compatible public factory (keeps old code working if someone used constructor)
    public TransactionRepository(Context context, boolean unused) {
        this(context);
    }

    public void insert(final TransactionEntity t, Runnable callback) {
        executor.execute(() -> {
            try {
                long id = dao.insert(t);
                Log.d("TRRepo", "inserted tx id=" + id + " userId=" + t.userId + " amount=" + t.amount + " ts=" + t.timestamp);
                // notify listeners after successful insert on main thread
                Handler mainHandler = new Handler(Looper.getMainLooper());
                for (Runnable r : listeners) {
                    try {
                        Log.d("TRRepo", "posting listener to main thread");
                        mainHandler.post(r);
                    } catch (Exception ignored) {}
                }
            } catch (Exception ex) {
                Log.d("TRRepo", "insert failed: " + ex.getMessage());
            }
            if (callback != null) callback.run();
        });
    }

    // allow registering/unregistering a listener. Listener runs on calling thread, so callers should post to UI if needed.
    public void registerChangeListener(Runnable listener) {
        if (listener == null) return;
        listeners.addIfAbsent(listener);
    }

    public void unregisterChangeListener(Runnable listener) {
        if (listener == null) return;
        listeners.remove(listener);
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

    // Get sums grouped by category in a time range
    public void getCategorySumsInRange(final String userId, final long from, final long to, final Consumer<List<net.tiramisu.mdp.model.CategorySum>> callback) {
        executor.execute(() -> {
            List<net.tiramisu.mdp.model.CategorySum> res = null;
            try { res = dao.getCategorySumsInRange(userId, from, to); } catch (Exception ignored) {}
            if (callback != null) callback.accept(res);
        });
    }

    // Get earliest transaction timestamp for a user
    public void getMinTimestampForUser(final String userId, final Consumer<Long> callback) {
        executor.execute(() -> {
            Long v = null;
            try { v = dao.getMinTimestampForUser(userId); } catch (Exception ignored) {}
            if (callback != null) callback.accept(v);
        });
    }

    // Allow external callers to notify listeners (e.g., when base balance stored outside the repo changes)
    public void notifyChange() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        for (Runnable r : listeners) {
            try { mainHandler.post(r); } catch (Exception ignored) {}
        }
        Log.d("TRRepo", "notifyChange: posted " + listeners.size() + " listeners");
    }

    // Provide a lifecycle-aware LiveData for a user's base balance. Creates and populates the value on first use.
    public LiveData<Double> getBaseBalanceLive(final String userId) {
        final String key = (userId == null || userId.isEmpty()) ? "local" : userId;
        MutableLiveData<Double> existing = baseLiveMap.get(key);
        if (existing != null) return existing;
        MutableLiveData<Double> ld = new MutableLiveData<>();
        MutableLiveData<Double> prior = baseLiveMap.putIfAbsent(key, ld);
        final MutableLiveData<Double> used = prior == null ? ld : prior;
        // populate initial value asynchronously
        getUserBaseBalance(key, (Double v) -> {
            if (v == null) v = 0.0;
            Long t = manualUpdateTs.get(key);
            if (t != null) {
                long age = System.currentTimeMillis() - t.longValue();
                // if user manually updated within last 5s, don't overwrite their value
                if (age < TimeUnit.SECONDS.toMillis(5)) {
                    Log.d("TRRepo", "getBaseBalanceLive: skipping populate for " + key + " because manual update " + age + "ms ago");
                    return;
                }
            }
            Log.d("TRRepo", "getBaseBalanceLive: populate " + key + " with " + v);
            used.postValue(v);
        });
        return used;
    }

    // Immediately set the cached base balance LiveData for a user (useful after a local or Firestore save)
    public void setCachedBaseBalance(final String userId, final double value) {
        final String key = (userId == null || userId.isEmpty()) ? "local" : userId;
        Log.d("TRRepo", "setCachedBaseBalance: user=" + key + " value=" + value);
        MutableLiveData<Double> ld = baseLiveMap.get(key);
        if (ld == null) {
            MutableLiveData<Double> newLd = new MutableLiveData<>(value);
            MutableLiveData<Double> prior = baseLiveMap.putIfAbsent(key, newLd);
            if (prior != null) prior.postValue(value);
        } else {
            ld.postValue(value);
        }
        manualUpdateTs.put(key, System.currentTimeMillis());
        // Note: do not call notifyChange() here — notifying repo listeners causes fragments
        // to re-read stored base immediately (and may overwrite this cached value while
        // remote writes are still propagating). Observers will receive the updated value
        // via LiveData; callers can call notifyChange() separately if they also want
        // repo listeners to run.
    }

    // Return true if the user base was manually updated within the past thresholdMs milliseconds
    public boolean wasManuallyUpdatedRecently(final String userId, final long thresholdMs) {
        final String key = (userId == null || userId.isEmpty()) ? "local" : userId;
        Long t = manualUpdateTs.get(key);
        if (t == null) return false;
        return System.currentTimeMillis() - t.longValue() < thresholdMs;
    }

    // Get stored base balance for a user: for 'local' read SharedPreferences, otherwise read Firestore 'users' doc field 'balance'
    public void getUserBaseBalance(final String userId, final Consumer<Double> callback) {
        if (userId == null || userId.isEmpty() || "local".equals(userId)) {
            // local stored in SharedPreferences
            if (appContext == null) {
                if (callback != null) callback.accept(0.0);
                return;
            }
            SharedPreferences sp = appContext.getSharedPreferences("mdp_local", Context.MODE_PRIVATE);
            double v = Double.longBitsToDouble(sp.getLong("local_balance_bits", Double.doubleToLongBits(0.0)));
            if (callback != null) callback.accept(v);
            return;
        }

        // remote user: fetch from Firestore
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(userId).get().addOnSuccessListener(doc -> {
            if (doc == null || !doc.exists()) {
                if (callback != null) callback.accept(0.0);
                return;
            }
            Object val = doc.get("balance");
            double parsed = 0.0;
            try {
                if (val instanceof Number) parsed = ((Number) val).doubleValue();
                else {
                    String s = val == null ? "" : val.toString();
                    s = s.replaceAll("[^0-9.-]", "");
                    if (!s.isEmpty()) parsed = Double.parseDouble(s);
                }
            } catch (Exception ignored) {}
            if (callback != null) callback.accept(parsed);
        }).addOnFailureListener(e -> {
            if (callback != null) callback.accept(0.0);
        });
    }

}
