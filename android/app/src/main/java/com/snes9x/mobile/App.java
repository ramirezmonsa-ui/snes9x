package com.snes9x.mobile;

import android.app.Application;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Saves the details of a crash so the start screen can show them next time, since there is no
 * other way to see them without a computer.
 */
public class App extends Application {
    static final String CRASH_FILE = "last_crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            saveCrash(error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    private void saveCrash(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        String report = "Versión " + versionName()
                + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + " · " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n" + trace;
        try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), CRASH_FILE))) {
            out.write(report.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Nothing else we can do while crashing.
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
