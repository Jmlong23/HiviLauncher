package com.hivi.audionativelib;

import android.util.Log;

/**
 * AudioNativeLib 内部日志门面。
 *
 * <p>默认直接调用 {@link android.util.Log}。
 * 在 Application 启动后，通过 {@link #setDelegate(LogDelegate)} 注入一个委托，
 * 就可以把库日志同步写入 app 侧的持久化文件（如 DailyAppLogWriter），
 * 而不需要库依赖 app 模块。
 */
public final class NativeLog {

    private NativeLog() {
    }

    /** 外部可注入的委托接口 */
    public interface LogDelegate {
        void log(String level, String tag, String msg);
    }

    private static volatile LogDelegate sDelegate;

    /** 在 Application.onCreate() 中调用，注入写文件委托 */
    public static void setDelegate(LogDelegate delegate) {
        sDelegate = delegate;
    }

    public static void v(String tag, String msg) {
        dispatch("V", tag, msg);
        Log.v(tag, msg);
    }

    public static void d(String tag, String msg) {
        dispatch("D", tag, msg);
        Log.d(tag, msg);
    }

    public static void i(String tag, String msg) {
        dispatch("I", tag, msg);
        Log.i(tag, msg);
    }

    public static void w(String tag, String msg) {
        dispatch("W", tag, msg);
        Log.w(tag, msg);
    }

    public static void e(String tag, String msg) {
        dispatch("E", tag, msg);
        Log.e(tag, msg);
    }

    private static void dispatch(String level, String tag, String msg) {
        LogDelegate d = sDelegate;
        if (d != null) {
            d.log(level, tag, msg);
        }
    }
}
