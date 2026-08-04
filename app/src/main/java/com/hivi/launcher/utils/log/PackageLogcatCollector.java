package com.hivi.launcher.utils.log;

import android.app.ActivityManager;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exports a point-in-time logcat snapshot for all running processes belonging to this package.
 */
public final class PackageLogcatCollector {
    private static final int SYSTEM_SNAPSHOT_LINE_COUNT = 12000;

    private PackageLogcatCollector() {
    }

    public static File dumpToTextFile(Context context, File outputDirectory) throws IOException {
        Context appContext = context.getApplicationContext();
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new IOException("Cannot create log export directory: " + outputDirectory);
        }
        String packageName = appContext.getPackageName();
        Set<Integer> processIds = collectProcessIds(appContext, packageName);
        File output = new File(outputDirectory, "logcat_" + packageName.replace('.', '_')
                + "_" + System.currentTimeMillis() + ".txt");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            String header = "package=" + packageName + "\nprocessIds=" + processIds
                    + "\ncapturedAt=" + System.currentTimeMillis() + "\n\n";
            stream.write(header.getBytes(StandardCharsets.UTF_8));
            for (int processId : processIds) {
                stream.write(("======== logcat -d --pid " + processId + " ========\n")
                        .getBytes(StandardCharsets.UTF_8));
                dumpProcessLogcat(processId, stream);
                stream.write('\n');
            }
        }
        return output;
    }

    /**
     * Captures the recent framework and system-service logs already present in logcat when the
     * user starts an upload. This complements the continuously persisted system buffer and also
     * covers entries written before the Launcher process was started.
     */
    public static File dumpSystemSnapshotToTextFile(Context context, File outputDirectory)
            throws IOException {
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new IOException("Cannot create log export directory: " + outputDirectory);
        }
        File output = new File(outputDirectory, "logcat_system_tail_"
                + System.currentTimeMillis() + ".txt");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            String header = "buffers=system,crash\ncapturedAt=" + System.currentTimeMillis()
                    + "\nmaxLines=" + SYSTEM_SNAPSHOT_LINE_COUNT + "\n\n";
            stream.write(header.getBytes(StandardCharsets.UTF_8));
            dumpSystemLogcat(stream);
        }
        return output;
    }

    private static Set<Integer> collectProcessIds(Context context, String packageName) {
        Set<Integer> processIds = new LinkedHashSet<>();
        processIds.add(android.os.Process.myPid());
        ActivityManager activityManager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return processIds;
        }
        List<ActivityManager.RunningAppProcessInfo> processes =
                activityManager.getRunningAppProcesses();
        if (processes == null) {
            return processIds;
        }
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process == null || process.processName == null) {
                continue;
            }
            if (packageName.equals(process.processName)
                    || process.processName.startsWith(packageName + ":")) {
                processIds.add(process.pid);
            }
        }
        return processIds;
    }

    private static void dumpProcessLogcat(int processId, FileOutputStream output)
            throws IOException {
        Process process = null;
        try {
            process = new ProcessBuilder("logcat", "-d", "-v", "threadtime", "-b", "main",
                    "-b", "system", "-b", "crash", "--pid", String.valueOf(processId))
                    .redirectErrorStream(true).start();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while collecting logcat.", exception);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static void dumpSystemLogcat(FileOutputStream output) throws IOException {
        Process process = null;
        try {
            process = new ProcessBuilder("logcat", "-d", "-v", "threadtime", "-b", "system",
                    "-b", "crash", "-t", String.valueOf(SYSTEM_SNAPSHOT_LINE_COUNT))
                    .redirectErrorStream(true).start();
            try (InputStream input = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            }
            process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while collecting system logcat.", exception);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
