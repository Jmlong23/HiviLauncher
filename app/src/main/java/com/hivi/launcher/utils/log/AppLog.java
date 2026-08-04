package com.hivi.launcher.utils.log;

import android.util.Log;

/**
 * Application logging facade that writes to both logcat and the persistent diagnostic log.
 */
public final class AppLog {
    private AppLog() {
    }

    public static int v(String tag, String message) {
        write("V", tag, message);
        return Log.v(tag, message);
    }

    public static int v(String tag, String message, Throwable throwable) {
        write("V", tag, formatThrowableMessage(message, throwable));
        return Log.v(tag, message, throwable);
    }

    public static int d(String tag, String message) {
        write("D", tag, message);
        return Log.d(tag, message);
    }

    public static int d(String tag, String message, Throwable throwable) {
        write("D", tag, formatThrowableMessage(message, throwable));
        return Log.d(tag, message, throwable);
    }

    public static int i(String tag, String message) {
        write("I", tag, message);
        return Log.i(tag, message);
    }

    public static int i(String tag, String message, Throwable throwable) {
        write("I", tag, formatThrowableMessage(message, throwable));
        return Log.i(tag, message, throwable);
    }

    public static int w(String tag, String message) {
        write("W", tag, message);
        return Log.w(tag, message);
    }

    public static int w(String tag, Throwable throwable) {
        String message = Log.getStackTraceString(throwable);
        write("W", tag, message);
        return Log.w(tag, throwable);
    }

    public static int w(String tag, String message, Throwable throwable) {
        write("W", tag, formatThrowableMessage(message, throwable));
        return Log.w(tag, message, throwable);
    }

    public static int e(String tag, String message) {
        write("E", tag, message);
        return Log.e(tag, message);
    }

    public static int e(String tag, String message, Throwable throwable) {
        write("E", tag, formatThrowableMessage(message, throwable));
        return Log.e(tag, message, throwable);
    }

    public static int wtf(String tag, String message) {
        write("A", tag, message);
        return Log.wtf(tag, message);
    }

    public static int wtf(String tag, Throwable throwable) {
        String message = Log.getStackTraceString(throwable);
        write("A", tag, message);
        return Log.wtf(tag, throwable);
    }

    public static int wtf(String tag, String message, Throwable throwable) {
        write("A", tag, formatThrowableMessage(message, throwable));
        return Log.wtf(tag, message, throwable);
    }

    private static String formatThrowableMessage(String message, Throwable throwable) {
        String stackTrace = Log.getStackTraceString(throwable);
        return message == null || message.length() == 0 ? stackTrace : message + '\n' + stackTrace;
    }

    private static void write(String level, String tag, String message) {
        DailyAppLogWriter writer = DailyAppLogWriter.getInstance();
        if (writer != null) {
            writer.append(level, tag, message);
        }
    }
}
