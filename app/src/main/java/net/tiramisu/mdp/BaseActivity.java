package net.tiramisu.mdp;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

/**
 * BaseActivity that applies locale settings to all activities that extend it.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }
}

