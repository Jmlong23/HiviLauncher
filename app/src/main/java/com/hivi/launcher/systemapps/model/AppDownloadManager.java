package com.hivi.launcher.systemapps.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;

import com.hivi.launcher.utils.log.AppLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class AppDownloadManager {
    private static final String TAG = "AppDownloadManager";
    private static final long MAX_PACKAGE_BYTES = 1024L * 1024L * 1024L;
    private static AppDownloadManager sInstance;

    public interface Listener {
        void onAppDownloadChanged(SupportedApp app);
    }

    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final File mDirectory;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient mClient = new OkHttpClient.Builder().build();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Map<SupportedApp, AppDownloadTask> mTasks = new EnumMap<>(SupportedApp.class);
    private final Map<SupportedApp, Call> mCalls = new EnumMap<>(SupportedApp.class);
    private final CopyOnWriteArraySet<Listener> mListeners = new CopyOnWriteArraySet<>();

    private AppDownloadManager(Context context) {
        mContext = context.getApplicationContext();
        mPreferences = mContext.getSharedPreferences("system_app_downloads", Context.MODE_PRIVATE);
        mDirectory = new File(mContext.getFilesDir(), "system-app-downloads");
        for (SupportedApp app : SupportedApp.values()) {
            AppDownloadTask task = new AppDownloadTask();
            String key = app.name();
            if (getPartialFile(app).length() > 0L && getDownloadUrl(app).equals(
                    mPreferences.getString(key + ".url", ""))) {
                task.restorePaused(getPartialFile(app).length(),
                        mPreferences.getLong(key + ".total", -1L));
            }
            mTasks.put(app, task);
        }
    }

    public static synchronized AppDownloadManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AppDownloadManager(context);
        }
        return sInstance;
    }

    public AppDownloadTask getTask(SupportedApp app) {
        return mTasks.get(app);
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    public boolean enqueue(SupportedApp app) {
        String url = getDownloadUrl(app);
        if (HttpUrl.parse(url) == null) {
            return false;
        }
        AppDownloadTask task = getTask(app);
        int generation = task.enqueue();
        if (generation >= 0) {
            publish(app);
            mExecutor.execute(() -> runDownload(app, task, generation, url));
        }
        return true;
    }

    public void pause(SupportedApp app) {
        if (getTask(app).pause()) {
            synchronized (mCalls) {
                Call call = mCalls.get(app);
                if (call != null) {
                    call.cancel();
                }
            }
            publish(app);
        }
    }

    private void runDownload(SupportedApp app, AppDownloadTask task, int generation, String url) {
        if (!task.start(generation)) {
            return;
        }
        publish(app);
        try {
            if (!mDirectory.isDirectory() && !mDirectory.mkdirs()) {
                throw new IOException("Unable to create app download directory.");
            }
            File packageFile = downloadPackage(app, task, generation, url);
            ensureCurrent(task, generation);
            PackageInfo info = mContext.getPackageManager().getPackageArchiveInfo(
                    packageFile.getAbsolutePath(), 0);
            if (info == null || !app.matchesPackage(info.packageName)) {
                deleteFile(packageFile);
                throw new IOException("APK does not match the selected application.");
            }
            if (!task.beginInstalling(generation)) {
                return;
            }
            publish(app);
            installPackage(packageFile);
            if (mContext.getPackageManager().getLaunchIntentForPackage(info.packageName) == null) {
                throw new IOException("Installed app has no launcher activity.");
            }
            task.complete(generation);
            deleteFile(packageFile);
        } catch (Exception exception) {
            if (task.isCurrent(generation)) {
                task.fail(generation);
                AppLog.w(TAG, "App download or installation failed: " + app.name(), exception);
            }
        } finally {
            synchronized (mCalls) {
                mCalls.remove(app);
            }
            publish(app);
        }
    }

    private File downloadPackage(SupportedApp app, AppDownloadTask task, int generation,
            String url) throws IOException {
        File partialFile = getPartialFile(app);
        String key = app.name();
        String validator = mPreferences.getString(key + ".validator", "");
        long offset = partialFile.length();
        if (offset > 0L && offset == mPreferences.getLong(key + ".total", -1L)
                && url.equals(mPreferences.getString(key + ".url", ""))) {
            ensureCurrent(task, generation);
            task.updateProgress(generation, offset, offset);
            return partialFile;
        }
        if (!url.equals(mPreferences.getString(key + ".url", "")) || validator.isEmpty()) {
            offset = 0L;
        }
        Request.Builder request = new Request.Builder().url(url).header("Accept-Encoding", "identity");
        if (offset > 0L) {
            request.header("Range", "bytes=" + offset + "-").header("If-Range", validator);
        }
        Call call = mClient.newCall(request.build());
        synchronized (mCalls) {
            ensureCurrent(task, generation);
            mCalls.put(app, call);
        }
        try (Response response = call.execute()) {
            ResponseBody body = response.body();
            if ((response.code() != 200 && response.code() != 206) || body == null) {
                if (response.code() == 416) {
                    deleteFile(partialFile);
                }
                throw new IOException("APK request failed. HTTP " + response.code());
            }
            long total;
            if (response.code() == 206) {
                try {
                    total = DownloadRange.validate(response.header("Content-Range"),
                            offset, body.contentLength());
                } catch (IOException exception) {
                    deleteFile(partialFile);
                    throw exception;
                }
            } else {
                offset = 0L;
                total = body.contentLength();
            }
            if (total > MAX_PACKAGE_BYTES) {
                throw new IOException("APK exceeds the download size limit.");
            }
            String nextValidator = response.header("ETag", "");
            if (nextValidator.isEmpty() || nextValidator.startsWith("W/")) {
                nextValidator = response.header("Last-Modified", "");
            }
            mPreferences.edit().putString(key + ".url", url)
                    .putString(key + ".validator", nextValidator)
                    .putLong(key + ".total", total).apply();
            long downloaded = offset;
            int lastProgress = -2;
            long lastPublish = 0L;
            try (InputStream input = body.byteStream();
                    FileOutputStream output = new FileOutputStream(partialFile, offset > 0L)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    ensureCurrent(task, generation);
                    downloaded += count;
                    if (downloaded > MAX_PACKAGE_BYTES || (total > 0L && downloaded > total)) {
                        deleteFile(partialFile);
                        throw new IOException("APK length exceeds the expected size.");
                    }
                    output.write(buffer, 0, count);
                    task.updateProgress(generation, downloaded, total);
                    int progress = task.getProgress();
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (progress != lastProgress && now - lastPublish >= 100L) {
                        lastProgress = progress;
                        lastPublish = now;
                        publish(app);
                    }
                }
            }
            ensureCurrent(task, generation);
            if (downloaded <= 0L || (total >= 0L && downloaded != total)) {
                throw new IOException("APK download is incomplete.");
            }
            task.updateProgress(generation, downloaded, downloaded);
            return partialFile;
        }
    }

    private void installPackage(File packageFile) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("pm", "install", "-r", packageFile.getAbsolutePath())
                .redirectErrorStream(true).start();
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (!process.waitFor(120L, TimeUnit.SECONDS)) {
                    throw new IOException("App installation timed out.");
                }
            } else {
                process.waitFor();
            }
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            }
            if (process.exitValue() != 0 || !result.toString().contains("Success")) {
                throw new IOException("App installation failed: " + result);
            }
        } finally {
            process.destroy();
        }
    }

    private String getDownloadUrl(SupportedApp app) {
        return mContext.getString(app.getDownloadUrlResId()).trim();
    }

    private File getPartialFile(SupportedApp app) {
        return new File(mDirectory, app.name() + ".apk.part");
    }

    private void publish(SupportedApp app) {
        mMainHandler.post(() -> {
            for (Listener listener : mListeners) {
                listener.onAppDownloadChanged(app);
            }
        });
    }

    private static void ensureCurrent(AppDownloadTask task, int generation) throws IOException {
        if (!task.isCurrent(generation)) {
            throw new IOException("Download was paused or replaced.");
        }
    }

    private static void deleteFile(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Unable to remove the cached APK.");
        }
    }
}
