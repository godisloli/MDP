package net.tiramisu.mdp;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class MyApp extends Application {
    private static final String TAG = "MyApp";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.setLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                File f = new File(getFilesDir(), "crash_log.txt");
                FileWriter fw = new FileWriter(f, true);
                fw.write("\n---- Crash on " + System.currentTimeMillis() + " ----\n");
                PrintWriter pw = new PrintWriter(fw);
                throwable.printStackTrace(pw);
                pw.flush();
                pw.close();
                fw.close();
                Log.e(TAG, "Saved crash log to " + f.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Failed to write crash log", e);
            }
            // Let the system handle the exception (kill the process)
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(2);
        });
    }
}
