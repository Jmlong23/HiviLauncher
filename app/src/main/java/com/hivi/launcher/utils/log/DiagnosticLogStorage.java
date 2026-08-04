package com.hivi.launcher.utils.log;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Applies the retention policy for persisted Launcher diagnostic logs.
 */
public final class DiagnosticLogStorage {
    public static final long MAX_SINGLE_LOG_FILE_BYTES = 4L * 1024L * 1024L;
    public static final long MAX_TOTAL_LOG_BYTES = 64L * 1024L * 1024L;
    public static final long MAX_LOG_AGE_MILLIS = TimeUnit.DAYS.toMillis(7L);

    private static final String TAG = "DiagnosticLogStorage";
    private static final Object LOCK = new Object();
    private static final Set<String> sUploadReservedPaths = new HashSet<>();
    private static final Comparator<File> OLDEST_FIRST = new Comparator<File>() {
        @Override
        public int compare(File first, File second) {
            int result = Long.compare(first.lastModified(), second.lastModified());
            return result != 0 ? result : first.getName().compareTo(second.getName());
        }
    };

    private DiagnosticLogStorage() {
    }

    /**
     * Prevents selected logs from being pruned while they are being packaged or uploaded.
     */
    public static void reserveForUpload(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            for (File file : files) {
                if (file != null) {
                    sUploadReservedPaths.add(file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Releases logs previously protected by {@link #reserveForUpload(List)}.
     */
    public static void releaseFromUpload(List<File> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            for (File file : files) {
                if (file != null) {
                    sUploadReservedPaths.remove(file.getAbsolutePath());
                }
            }
        }
    }

    public static void prune(Context context) {
        if (context == null) {
            return;
        }
        prune(PersistentLogcatMirror.getLogDirectory(context));
    }

    static void prune(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        synchronized (LOCK) {
            long oldestAllowedTimestamp = System.currentTimeMillis() - MAX_LOG_AGE_MILLIS;
            List<File> files = getManagedLogFiles(directory);
            for (File file : files) {
                if (file.lastModified() < oldestAllowedTimestamp && !isProtected(file)) {
                    deleteQuietly(file);
                }
            }

            files = getManagedLogFiles(directory);
            long totalBytes = getTotalBytes(files);
            for (File file : files) {
                if (totalBytes <= MAX_TOTAL_LOG_BYTES) {
                    break;
                }
                if (isProtected(file)) {
                    continue;
                }
                long fileSize = file.length();
                if (deleteQuietly(file)) {
                    totalBytes -= fileSize;
                }
            }
        }
    }

    private static List<File> getManagedLogFiles(File directory) {
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<File> managedFiles = new ArrayList<>();
        for (File file : files) {
            if (file != null && file.isFile() && isManagedLogFile(file)) {
                managedFiles.add(file);
            }
        }
        Collections.sort(managedFiles, OLDEST_FIRST);
        return managedFiles;
    }

    private static boolean isManagedLogFile(File file) {
        String name = file.getName();
        return name.endsWith(".log") || name.endsWith(".txt");
    }

    private static long getTotalBytes(List<File> files) {
        long totalBytes = 0L;
        for (File file : files) {
            totalBytes += file.length();
        }
        return totalBytes;
    }

    private static boolean isProtected(File file) {
        if (PersistentLogcatMirror.ACTIVE_STREAM_FILE_NAME.equals(file.getName())
                || DailyAppLogWriter.isActiveLogFile(file)) {
            return true;
        }
        return sUploadReservedPaths.contains(file.getAbsolutePath());
    }

    private static boolean deleteQuietly(File file) {
        if (!file.delete()) {
            Log.w(TAG, "Unable to delete retained diagnostic log: " + file);
            return false;
        }
        return true;
    }
}
