package net.tiramisu.mdp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import android.widget.TextView;
import android.view.View;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabAdd;
    // private View topSummariesContainer;

    // top summary text views (fragments update the activity-level views directly)
    // private TextView tvMonthTitleShared;

    // Activity Result launcher for AddTransactionActivity
    private ActivityResultLauncher<Intent> addTransactionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Ensure Firebase is initialized before using FirebaseAuth
            try {
                FirebaseApp.initializeApp(this);
            } catch (Exception ex) {
                // If Firebase fails to initialize, continue but avoid calling FirebaseAuth
            }

            // If there's no authenticated user, redirect to LoginActivity immediately
            try {
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    Intent i = new Intent(MainActivity.this, LoginActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                    return;
                }
            } catch (Exception ex) {
                // If FirebaseAuth is not available or throws, redirect to LoginActivity as a safe fallback
                Intent i = new Intent(MainActivity.this, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
                return;
            }

            EdgeToEdge.enable(this);
            setContentView(R.layout.activity_main);

            viewPager = findViewById(R.id.viewPager);
            bottomNav = findViewById(R.id.bottomNav);
            fabAdd = findViewById(R.id.fabAdd);
            // topSummariesContainer = findViewById(R.id.topSummariesContainer);
            // tvMonthTitleShared = findViewById(R.id.tvMonthTitle);

            // Register the activity result launcher
            addTransactionLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result != null && result.getResultCode() == RESULT_OK && result.getData() != null) {
                            // Extract transaction data
                            Intent data = result.getData();
                            String category = data.getStringExtra(AddTransactionActivity.EXTRA_CATEGORY);
                            double amount = data.getDoubleExtra(AddTransactionActivity.EXTRA_AMOUNT, 0);
                            String title = data.getStringExtra(AddTransactionActivity.EXTRA_TITLE);
                            boolean isIncome = data.getBooleanExtra(AddTransactionActivity.EXTRA_IS_INCOME, false);

                            // Try to update TransactionsFragment UI directly
                            if (viewPager != null) viewPager.setCurrentItem(2, true);
                            ViewPagerAdapter vpAdapter = null;
                            if (viewPager != null && viewPager.getAdapter() != null) {
                                vpAdapter = (ViewPagerAdapter) viewPager.getAdapter();
                            }
                            if (vpAdapter != null) {
                                Fragment f = vpAdapter.getFragment(2);
                                if (f instanceof TransactionsFragment) {
                                    TransactionsFragment tf = (TransactionsFragment) f;
                                    // choose icon/title based on type
                                    int icon = R.drawable.ic_transaction;
                                    String displayTitle = title != null ? title : category;
                                    if (isIncome) {
                                        icon = R.drawable.ic_wallet;
                                        if (displayTitle == null || displayTitle.isEmpty()) displayTitle = "Thu nhập";
                                    } else {
                                        if ("Ăn uống".equals(category)) icon = R.drawable.ic_food;
                                        if ("Đi lại".equals(category)) icon = R.drawable.ic_transport;
                                        if ("Mua sắm".equals(category)) icon = R.drawable.ic_shopping;
                                        if ("Giải trí".equals(category)) icon = R.drawable.ic_entertainment;
                                    }

                                    Transaction tx = new Transaction(displayTitle, "now", amount, icon);
                                    tf.addTransaction(tx);
                                }
                                // also refresh HomeFragment sums
                                Fragment hf = vpAdapter.getFragment(0);
                                if (hf instanceof HomeFragment) {
                                    ((HomeFragment) hf).refreshData();
                                }
                            }
                        }
                    }
            );

            // Setup pager with adapter
            ViewPagerAdapter adapter = new ViewPagerAdapter(this);
            viewPager.setAdapter(adapter);
            viewPager.setUserInputEnabled(true); // allow swipe
            // Keep all 4 pages in memory to avoid re-creation during quick swipes
            viewPager.setOffscreenPageLimit(4);
            // Start on Home
            viewPager.setCurrentItem(0, false);
            if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_home);

            // Wire BottomNavigationView -> ViewPager (guarded against null)
            if (bottomNav != null) {
                bottomNav.setOnItemSelectedListener(item -> {
                    int idx = menuItemToPosition(item.getItemId());
                    if (idx >= 0) viewPager.setCurrentItem(idx, true);
                    return true;
                });
            }

            // Wire ViewPager -> BottomNavigationView
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    int menuId = positionToMenuItem(position);
                    if (menuId != -1 && bottomNav != null) bottomNav.setSelectedItemId(menuId);

                    // Show/hide FAB based on page (show on Home/Transactions)
                    if (fabAdd != null) {
                        fabAdd.setVisibility((position == 0 || position == 2) ? FloatingActionButton.VISIBLE : FloatingActionButton.GONE);
                    }

                    // keep BottomNav and FAB in sync with pager
                    // (no shared summary behavior in the activity)
                }
            });

            if (fabAdd != null) {
                fabAdd.setOnClickListener(v -> {
                    Intent i = new Intent(MainActivity.this, AddTransactionActivity.class);
                    addTransactionLauncher.launch(i);
                });
            }

            // No activity-level shared summaries to initialize

        } catch (Throwable t) {
            // Catch any unexpected startup errors, show a Toast and redirect to LoginActivity as a safe fallback
            Log.e(TAG, "App initialization error", t);
            try {
                File f = new File(getFilesDir(), "crash_main_oncreate.txt");
                FileWriter fw = new FileWriter(f, true);
                fw.write("\n---- MainActivity onCreate crash on " + System.currentTimeMillis() + " ----\n");
                PrintWriter pw = new PrintWriter(fw);
                t.printStackTrace(pw);
                pw.flush();
                pw.close();
                fw.close();
            } catch (Exception e) {
                Log.w(TAG, "Failed to write crash file", e);
            }
            Toast.makeText(this, "App initialization error — redirecting to login.", Toast.LENGTH_LONG).show();
            try {
                Intent i = new Intent(MainActivity.this, LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception ignored) {}
            finish();
        }
    }

    private int menuItemToPosition(int menuId) {
        // Use if/else because resource IDs are non-final under newer AGP and can't be used in switch-case
        if (menuId == R.id.nav_home) return 0;
        if (menuId == R.id.nav_chart) return 1;
        if (menuId == R.id.nav_transactions) return 2;
        if (menuId == R.id.nav_settings) return 3;
        return -1;
    }

    private int positionToMenuItem(int pos) {
        if (pos == 0) return R.id.nav_home;
        if (pos == 1) return R.id.nav_chart;
        if (pos == 2) return R.id.nav_transactions;
        if (pos == 3) return R.id.nav_settings;
        return -1;
    }
}