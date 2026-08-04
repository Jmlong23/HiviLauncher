package com.hivi.launcher.utils.log;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Persists the device main/system/crash logcat streams between manual diagnostic uploads.
 *
 * <p>The currently written stream is sealed immediately before an upload and a fresh stream is
 * started, so the complete pre-upload interval can be included in the archive.</p>
 */
public final class PersistentLogcatMirror {
    public static final String ACTIVE_STREAM_FILE_NAME = "logcat_active.log";
    private static final String TAG = "PersistentLogcatMirror";
    private static final String LOG_DIRECTORY_NAME = "diagnostic_logs";
    private static final long MAX_SEGMENT_BYTES = 4L * 1024L * 1024L;
    private static final Object LOCK = new Object();

    private static volatile Thread sWorker;
    private static volatile Process sLogcatProcess;
    private static volatile boolean sStopRequested;
    private static File sLogDirectory;
    private static File sActiveStreamFile;
    private static FileOutputStream sActiveStream;
    private static long sSegmentBytes;

    private PersistentLogcatMirror() {
    }

    public static void start(Context context) {
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            if (sWorker != null && !sWorker.isAlive()) {
                sWorker = null;
            }
            if (sWorker != null) {
                return;
            }
            sStopRequested = false;
            sLogDirectory = getLogDirectory(appContext);
            if (!sLogDirectory.isDirectory() && !sLogDirectory.mkdirs()) {
                Log.e(TAG, "Cannot create diagnostic log directory: " + sLogDirectory);
                return;
            }
            sActiveStreamFile = new File(sLogDirectory, ACTIVE_STREAM_FILE_NAME);
            DiagnosticLogStorage.prune(sLogDirectory);
            startWorkerLocked();
        }
    }

    /**
     * Stops and seals the active stream, then immediately starts a replacement stream.
     */
    public static void sealForUpload(Context context) throws InterruptedException {
        Context appContext = context.getApplicationContext();
        Thread worker;
        Process process;
        synchronized (LOCK) {
            sStopRequested = true;
            worker = sWorker;
            process = sLogcatProcess;
        }
        if (process != null) {
            process.destroy();
        }
        if (worker != null) {
            worker.join(5000L);
        }
        synchronized (LOCK) {
            sWorker = null;
            sStopRequested = false;
            sLogcatProcess = null;
            closeActiveStreamLocked();
            File directory = sLogDirectory == null ? getLogDirectory(appContext) : sLogDirectory;
            if (!directory.isDirectory()) {
                directory.mkdirs();
            }
            sLogDirectory = directory;
            File active = new File(directory, ACTIVE_STREAM_FILE_NAME);
            if (active.isFile() && active.length() > 0L) {
                File sealed = new File(directory, "logcat_" + System.currentTimeMillis() + ".log");
                if (!active.renameTo(sealed)) {
                    Log.w(TAG, "Unable to seal diagnostic log: " + active);
                }
            }
            sActiveStreamFile = new File(directory, ACTIVE_STREAM_FILE_NAME);
            sSegmentBytes = 0L;
            startWorkerLocked();
        }
        DailyAppLogWriter writer = DailyAppLogWriter.getInstance();
        if (writer != null) {
            writer.sealForUpload();
        }
    }

    public static File getLogDirectory(Context context) {
        Context deviceContext = context.getApplicationContext()
                .createDeviceProtectedStorageContext();
        return deviceContext.getDir(LOG_DIRECTORY_NAME, Context.MODE_PRIVATE);
    }

    private static void startWorkerLocked() {
        sWorker = new Thread(PersistentLogcatMirror::runMirror, "persistent-logcat");
        sWorker.setDaemon(true);
        sWorker.start();
    }

    private static void runMirror() {
        try {
            while (!sStopRequested) {
                Process process = null;
                BufferedReader reader = null;
                try {
                    process = new ProcessBuilder("logcat", "-v", "threadtime", "-b", "main",
                            "-b", "system", "-b", "crash").redirectErrorStream(true).start();
                    sLogcatProcess = process;
                    reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                            StandardCharsets.UTF_8));
                    openActiveStreamIfNeeded();
                    String line;
                    while (!sStopRequested && (line = reader.readLine()) != null) {
                        byte[] bytes = (line + '\n').getBytes(StandardCharsets.UTF_8);
                        synchronized (LOCK) {
                            rotateIfNeededLocked(bytes.length);
                            if (sActiveStream != null) {
                                sActiveStream.write(bytes);
                                sSegmentBytes += bytes.length;
                                if (sSegmentBytes % (64L * 1024L) < bytes.length) {
                                    sActiveStream.flush();
                                }
                            }
                        }
                    }
                } catch (IOException exception) {
                    Log.w(TAG, "Diagnostic logcat mirror error.", exception);
                    sleepQuietly(2000L);
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException ignored) {
                            // Nothing else can be done while restarting the collector.
                        }
                    }
                    if (process != null) {
                        process.destroy();
                    }
                    sLogcatProcess = null;
                    synchronized (LOCK) {
                        closeActiveStreamLocked();
                    }
                }
            }
        } finally {
            synchronized (LOCK) {
                if (Thread.currentThread() == sWorker) {
                    sWorker = null;
                }
            }
        }
    }

    private static void openActiveStreamIfNeeded() throws IOException {
        synchronized (LOCK) {
            if (sActiveStream != null) {
                return;
            }
            if (sActiveStreamFile == null) {
                return;
            }
            File parent = sActiveStreamFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            sActiveStream = new FileOutputStream(sActiveStreamFile, true);
            sSegmentBytes = sActiveStreamFile.length();
        }
    }

    private static void rotateIfNeededLocked(int nextWriteBytes) throws IOException {
        if (sActiveStreamFile == null || sLogDirectory == null
                || sSegmentBytes + nextWriteBytes <= MAX_SEGMENT_BYTES) {
            return;
        }
        closeActiveStreamLocked();
        File sealed = new File(sLogDirectory, "logcat_part_" + System.currentTimeMillis()
                + ".log");
        if (sActiveStreamFile.isFile() && !sActiveStreamFile.renameTo(sealed)) {
            Log.w(TAG, "Unable to rotate diagnostic log: " + sActiveStreamFile);
        }
        sActiveStreamFile = new File(sLogDirectory, ACTIVE_STREAM_FILE_NAME);
        sActiveStream = new FileOutputStream(sActiveStreamFile, true);
        sSegmentBytes = 0L;
        DiagnosticLogStorage.prune(sLogDirectory);
    }

    private static void closeActiveStreamLocked() {
        if (sActiveStream == null) {
            return;
        }
        try {
            sActiveStream.flush();
            sActiveStream.close();
        } catch (IOException ignored) {
            // Closing an individual diagnostic segment must not stop the mirror.
        }
        sActiveStream = null;
    }

    private static void sleepQuietly(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
