package com.hivi.launcher;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;

import com.hivi.audionativelib.NativeLog;
import com.hivi.launcher.utils.LocaleHelper;
import com.hivi.launcher.utils.log.DailyAppLogWriter;
import com.hivi.launcher.utils.log.PersistentLogcatMirror;
import com.hivi.launcher.utils.network.NetworkManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HiviLauncherApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.applyLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LocaleHelper.applyLocale(this);
        Context deviceProtectedContext = createDeviceProtectedStorageContext();
        DailyAppLogWriter.init(deviceProtectedContext);
        NativeLog.setDelegate((level, tag, message) -> {
            DailyAppLogWriter writer = DailyAppLogWriter.getInstance();
            if (writer != null) {
                writer.append(level, tag, message);
            }
        });
        installCrashLogHandler();
        NetworkManager.initialize(deviceProtectedContext);
        PersistentLogcatMirror.start(deviceProtectedContext);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleHelper.applyLocale(this);
    }

    private void installCrashLogHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            writeCrashLog(thread, throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private void writeCrashLog(Thread thread, Throwable throwable) {
        try {
            File logDirectory = PersistentLogcatMirror.getLogDirectory(this);
            if (!logDirectory.isDirectory() && !logDirectory.mkdirs()) {
                return;
            }
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US)
                    .format(new Date());
            File crashFile = new File(logDirectory, timestamp + "_crash.log");
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            writer.println("========== Launcher Crash Report ==========");
            writer.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(new Date()));
            writer.println("Thread: " + (thread == null ? "(unknown)" : thread.getName())
                    + " (id=" + (thread == null ? -1L : thread.getId()) + ")");
            writer.println("-------------------------------------------");
            if (throwable != null) {
                throwable.printStackTrace(writer);
            } else {
                writer.println("No Throwable was supplied to the uncaught exception handler.");
            }
            writer.println("===========================================");
            writer.flush();
            try (FileOutputStream output = new FileOutputStream(crashFile)) {
                output.write(stringWriter.toString().getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        } catch (Throwable ignored) {
            // The crash reporter must never prevent Android's original exception handler.
        }
    }
}
