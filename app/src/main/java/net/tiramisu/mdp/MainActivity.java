package net.tiramisu.mdp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private FloatingActionButton fabAdd;

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

            // Register the activity result launcher
            addTransactionLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result != null && result.getResultCode() == RESULT_OK && result.getData() != null) {
                            // TODO: handle the newly added transaction, e.g. notify TransactionsFragment or refresh data
                            // For now we'll simply switch to the Transactions page so the user can see the new item
                            if (viewPager != null) viewPager.setCurrentItem(2, true);
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
                }
            });

            if (fabAdd != null) {
                fabAdd.setOnClickListener(v -> {
                    Intent i = new Intent(MainActivity.this, AddTransactionActivity.class);
                    addTransactionLauncher.launch(i);
                });
            }

        } catch (Throwable t) {
            // Catch any unexpected startup errors, show a Toast and redirect to LoginActivity as a safe fallback
            t.printStackTrace();
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