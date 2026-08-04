package com.hivi.launcher.utils.log;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronously persists Launcher business logs in daily files.
 *
 * <p>Unlike logcat, these files are not limited by the device log buffer. The active file is
 * sealed before a diagnostic upload so all entries submitted before that point are included.</p>
 */
public final class DailyAppLogWriter {
    private static final String TAG = "DailyAppLogWriter";
    private static final String LOG_DIRECTORY_NAME = "diagnostic_logs";
    private static final String ACTIVE_FILE_PREFIX = "app_log_";
    private static final String ACTIVE_FILE_SUFFIX = ".log";
    private static final int QUEUE_CAPACITY = 8192;
    private static final long SEAL_TIMEOUT_MILLIS = 5000L;
    private static final Object INIT_LOCK = new Object();

    private static volatile DailyAppLogWriter sInstance;

    private final File mLogDirectory;
    private final LinkedBlockingQueue<Command> mQueue =
            new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong mDroppedEntryCount = new AtomicLong();
    private final Object mSealLock = new Object();
    private final Thread mWriterThread;

    private String mCurrentDateTag;
    private volatile File mCurrentFile;
    private FileOutputStream mCurrentStream;

    private DailyAppLogWriter(File logDirectory) {
        mLogDirectory = logDirectory;
        mWriterThread = new Thread(this::runWriter, "daily-app-log-writer");
        mWriterThread.setDaemon(true);
        mWriterThread.start();
    }

    public static void init(Context context) {
        if (sInstance != null) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (sInstance != null) {
                return;
            }
            Context deviceContext = context.getApplicationContext()
                    .createDeviceProtectedStorageContext();
            File logDirectory = deviceContext.getDir(LOG_DIRECTORY_NAME, Context.MODE_PRIVATE);
            if (!logDirectory.isDirectory() && !logDirectory.mkdirs()) {
                Log.e(TAG, "Cannot create application log directory: " + logDirectory);
                return;
            }
            DiagnosticLogStorage.prune(logDirectory);
            sInstance = new DailyAppLogWriter(logDirectory);
        }
    }

    public static DailyAppLogWriter getInstance() {
        return sInstance;
    }

    static boolean isActiveLogFile(File file) {
        if (file == null) {
            return false;
        }
        DailyAppLogWriter writer = sInstance;
        File currentFile = writer == null ? null : writer.mCurrentFile;
        if (file.equals(currentFile)) {
            return true;
        }
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return file.getName().equals(ACTIVE_FILE_PREFIX + currentDate + ACTIVE_FILE_SUFFIX);
    }

    public void append(String level, String tag, String message) {
        String line = formatLine(level, tag, message);
        if (!mQueue.offer(new LogCommand(line))) {
            mDroppedEntryCount.incrementAndGet();
        }
    }

    /**
     * Flushes and closes the active application log, then renames it to a sealed file.
     *
     * <p>This method is called by the background upload preparation task. Entries submitted after
     * this request are written to a new active file and belong to the next upload.</p>
     */
    public File sealForUpload() {
        synchronized (mSealLock) {
            SealCommand command = new SealCommand();
            try {
                if (!mQueue.offer(command, SEAL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Timed out queuing application log seal request.");
                    return null;
                }
                if (!command.await(SEAL_TIMEOUT_MILLIS)) {
                    Log.w(TAG, "Timed out sealing application log.");
                    return null;
                }
                return command.getSealedFile();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private void runWriter() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Command command = mQueue.take();
                if (command instanceof LogCommand) {
                    writeDroppedEntryNoticeIfNeeded();
                    writeLine(((LogCommand) command).mLine);
                } else if (command instanceof SealCommand) {
                    SealCommand sealCommand = (SealCommand) command;
                    try {
                        sealCommand.complete(sealCurrentFile());
                    } catch (Throwable throwable) {
                        Log.e(TAG, "Unable to seal application log.", throwable);
                        sealCommand.complete(null);
                    }
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                closeCurrentStream();
            } catch (Throwable throwable) {
                Log.e(TAG, "Unable to persist application log.", throwable);
            }
        }
    }

    private void writeDroppedEntryNoticeIfNeeded() throws IOException {
        long droppedEntryCount = mDroppedEntryCount.getAndSet(0L);
        if (droppedEntryCount > 0L) {
            writeLine(formatLine("W", TAG, "Dropped " + droppedEntryCount
                    + " application log entries because the write queue was full."));
        }
    }

    private void writeLine(String line) throws IOException {
        ensureCurrentFile();
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        rotateCurrentFileIfNeeded(bytes.length);
        if (mCurrentStream != null) {
            mCurrentStream.write(bytes);
        }
    }

    private void ensureCurrentFile() throws IOException {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (today.equals(mCurrentDateTag) && mCurrentStream != null) {
            return;
        }
        closeCurrentStream();
        if (!mLogDirectory.isDirectory() && !mLogDirectory.mkdirs()) {
            throw new IOException("Cannot create application log directory: " + mLogDirectory);
        }
        mCurrentDateTag = today;
        mCurrentFile = new File(mLogDirectory,
                ACTIVE_FILE_PREFIX + today + ACTIVE_FILE_SUFFIX);
        mCurrentStream = new FileOutputStream(mCurrentFile, true);
        DiagnosticLogStorage.prune(mLogDirectory);
    }

    private void rotateCurrentFileIfNeeded(int nextLineBytes) throws IOException {
        if (mCurrentFile == null || mCurrentStream == null
                || mCurrentFile.length() + nextLineBytes
                <= DiagnosticLogStorage.MAX_SINGLE_LOG_FILE_BYTES) {
            return;
        }
        sealCurrentFile();
        DiagnosticLogStorage.prune(mLogDirectory);
        ensureCurrentFile();
    }

    private File sealCurrentFile() {
        if (mCurrentFile == null || mCurrentStream == null) {
            return null;
        }
        closeCurrentStream();
        File activeFile = mCurrentFile;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US)
                .format(new Date());
        File sealedFile = new File(mLogDirectory,
                ACTIVE_FILE_PREFIX + timestamp + ACTIVE_FILE_SUFFIX);
        if (!activeFile.renameTo(sealedFile)) {
            Log.w(TAG, "Unable to rename application log for upload: " + activeFile);
            sealedFile = activeFile;
        }
        mCurrentFile = null;
        mCurrentDateTag = null;
        return sealedFile;
    }

    private void closeCurrentStream() {
        if (mCurrentStream == null) {
            return;
        }
        try {
            mCurrentStream.flush();
            mCurrentStream.close();
        } catch (IOException ignored) {
            // Closing a diagnostic file must not affect application behavior.
        } finally {
            mCurrentStream = null;
        }
    }

    private static String formatLine(String level, String tag, String message) {
        String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        String threadName = Thread.currentThread().getName();
        return timestamp + " [" + threadName + "] " + String.valueOf(level) + "/"
                + String.valueOf(tag) + ": " + String.valueOf(message) + '\n';
    }

    private interface Command {
    }

    private static final class LogCommand implements Command {
        private final String mLine;

        private LogCommand(String line) {
            mLine = line;
        }
    }

    private static final class SealCommand implements Command {
        private final CountDownLatch mCompletionLatch = new CountDownLatch(1);
        private File mSealedFile;

        private boolean await(long timeoutMillis) throws InterruptedException {
            return mCompletionLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        }

        private void complete(File sealedFile) {
            mSealedFile = sealedFile;
            mCompletionLatch.countDown();
        }

        private File getSealedFile() {
            return mSealedFile;
        }
    }
}
