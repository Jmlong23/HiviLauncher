package com.hivi.launcher.systemapps.model;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.hivi.launcher.utils.log.AppLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 预置应用的下载与静默安装，实现“点击未下载应用 → 正在下载(%) → 正在安装 → 可启动”的
 * 内置应用分发流程。下载使用 OkHttp；安装复用 {@link com.hivi.launcher.update.SystemUpdateManager}
 * 的 {@code pm install} 方式 —— 本应用为 platform 签名 + system UID（sharedUserId
 * android.uid.system），无需用户确认，也不需要修改系统源码。
 */
public final class PresetAppInstaller implements AppDownloadQueue.Backend {
    private static final String TAG = "PresetAppInstaller";
    private static final long MAX_APK_BYTES = 512L * 1024L * 1024L;

    public interface Listener {
        /** 下载进度回调，percent 为 0~100。 */
        void onDownloadProgress(int percent);

        void onInstallStarted();

        /** 安装成功，packageName 为下载包实际解析出的包名。 */
        void onInstalled(String packageName);

        void onDownloadFailed();

        void onInstallFailed();

        default void onPaused() {
        }
    }

    private final Context mApplicationContext;
    private final OkHttpClient mHttpClient = new OkHttpClient.Builder().build();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private volatile DownloadTask mActiveTask;

    public PresetAppInstaller(Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    @Override
    public void start(AppEntry app, final AppDownloadQueue.Callback callback) {
        downloadAndInstall(app.getDownloadUrl(), java.util.Arrays.asList(app.getCandidatePackages()),
                new Listener() {
                    @Override
                    public void onDownloadProgress(int percent) {
                        callback.onDownloadProgress(percent);
                    }

                    @Override
                    public void onInstallStarted() {
                        callback.onInstallStarted();
                    }

                    @Override
                    public void onInstalled(String packageName) {
                        callback.onInstalled(packageName);
                    }

                    @Override
                    public void onDownloadFailed() {
                        callback.onFailed(false);
                    }

                    @Override
                    public void onInstallFailed() {
                        callback.onFailed(true);
                    }

                    @Override
                    public void onPaused() {
                        callback.onPaused();
                    }
                });
    }

    @Override
    public boolean pause() {
        DownloadTask task = mActiveTask;
        return task != null && task.pause();
    }

    public void downloadAndInstall(final String downloadUrl, final List<String> candidatePackages,
            final Listener listener) {
        final DownloadTask task = new DownloadTask();
        mActiveTask = task;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File apkFile = downloadApk(resolveRealApkUrl(downloadUrl, task), listener, task);
                    task.checkPaused();
                    String packageName = verifyApk(apkFile, candidatePackages);
                    task.beginInstallation();
                    AppLog.i(TAG, "Preset app package is ready; installing: "
                            + apkFile.getAbsolutePath());
                    notifyInstallStarted(listener);
                    installApk(apkFile);
                    notifyInstalled(packageName, listener);
                } catch (PauseException ignored) {
                    notifyPaused(listener);
                } catch (Throwable throwable) {
                    if (task.isPaused()) {
                        notifyPaused(listener);
                        return;
                    }
                    AppLog.w(TAG, "Preset app download/install failed", throwable);
                    notifyFailure(task.isInstalling(), listener);
                } finally {
                    task.finish();
                }
            }
        }, "PresetAppInstall").start();
    }

    /**
     * 公司 Swan 后台按 APP类型 发布的版本接口（…/version/details?type=…）返回 JSON，
     * 解析其中的 updateUrl 得到最新 APK 的真实下载地址；其余直链原样返回。
     */
    private String resolveRealApkUrl(String downloadUrl, DownloadTask task) throws IOException {
        task.checkPaused();
        if (downloadUrl == null || !downloadUrl.contains("/api/version/details")) {
            return downloadUrl;
        }
        Request request = new Request.Builder().url(downloadUrl).get().build();
        try (Response response = task.execute(mHttpClient, request)) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Unable to resolve preset app version. HTTP "
                        + response.code());
            }
            JSONObject data = new JSONObject(response.body().string()).optJSONObject("data");
            String apkUrl = data == null ? null : data.optString("updateUrl", null);
            if (TextUtils.isEmpty(apkUrl)) {
                throw new IOException("Preset app version has no updateUrl.");
            }
            return apkUrl;
        } catch (JSONException exception) {
            throw new IOException("Malformed preset app version response.", exception);
        }
    }

    private File downloadApk(String downloadUrl, Listener listener, DownloadTask task)
            throws IOException {
        task.checkPaused();
        if (TextUtils.isEmpty(downloadUrl)) {
            throw new IOException("Preset app download URL is empty.");
        }

        File downloadDirectory = getDownloadDirectory();
        String fileName = safeFileName(Integer.toHexString(downloadUrl.hashCode())
                + "-" + new File(downloadUrl).getName());
        File temporaryFile = new File(downloadDirectory, fileName + ".download");
        File apkFile = new File(downloadDirectory, fileName);
        File resumeFile = new File(downloadDirectory, fileName + ".resume");
        Properties resumeProperties = new Properties();
        if (resumeFile.exists()) {
            try (InputStream input = new FileInputStream(resumeFile)) {
                resumeProperties.load(input);
            }
        }
        String validator = resumeProperties.getProperty("validator", "");
        long existingBytes = temporaryFile.exists() ? temporaryFile.length() : 0L;
        if (existingBytes > MAX_APK_BYTES || validator.isEmpty()) {
            deleteQuietly(temporaryFile);
            existingBytes = 0L;
        }
        Request.Builder requestBuilder = new Request.Builder().url(downloadUrl).get()
                .header("Accept-Encoding", "identity");
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=" + existingBytes + "-");
            requestBuilder.header("If-Range", validator);
        }
        Request request = requestBuilder.build();
        try (Response response = task.execute(mHttpClient, request)) {
            if (response.code() == 416 && existingBytes > 0L) {
                deleteQuietly(temporaryFile);
                deleteQuietly(resumeFile);
                response.close();
                return downloadApk(downloadUrl, listener, task);
            }
            if (!response.isSuccessful()) {
                throw new IOException("Unable to download preset app. HTTP " + response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Preset app response is empty.");
            }
            long totalBytes = existingBytes;
            if (response.code() == 206) {
                String contentRange = response.header("Content-Range", "");
                String prefix = "bytes " + existingBytes + "-";
                if (!contentRange.startsWith(prefix)) {
                    deleteQuietly(temporaryFile);
                    throw new IOException("Preset app range does not match saved download.");
                }
            }
            if (existingBytes > 0L && response.code() != 206) {
                deleteQuietly(temporaryFile);
                totalBytes = 0L;
            }
            long contentLength = responseBody.contentLength();
            if (contentLength > 0L) {
                contentLength += totalBytes;
            }
            if (contentLength > MAX_APK_BYTES) {
                throw new IOException("Preset app package is too large.");
            }
            if (totalBytes == 0L) {
                deleteQuietly(apkFile);
            }
            validator = response.header("ETag", "");
            if (validator.isEmpty() || validator.startsWith("W/")) {
                validator = response.header("Last-Modified", "");
            }
            resumeProperties.setProperty("validator", validator);
            try (OutputStream output = new FileOutputStream(resumeFile)) {
                resumeProperties.store(output, null);
            }
            notifyProgress(contentLength > 0L ? (int) (totalBytes * 100L / contentLength) : -1,
                    listener);
            int lastProgress = -1;
            try (InputStream input = responseBody.byteStream();
                    OutputStream output = new FileOutputStream(temporaryFile, totalBytes > 0L)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while (true) {
                    task.checkPaused();
                    read = input.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    totalBytes += read;
                    if (totalBytes > MAX_APK_BYTES) {
                        throw new IOException("Preset app package is too large.");
                    }
                    output.write(buffer, 0, read);
                    if (contentLength > 0L) {
                        int progress = (int) (totalBytes * 100L / contentLength);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            notifyProgress(progress, listener);
                        }
                    }
                }
                output.flush();
            } catch (PauseException exception) {
                throw exception;
            } catch (IOException exception) {
                if (task.isPaused()) {
                    throw new PauseException();
                }
                throw exception;
            }

            task.checkPaused();
            if (contentLength > 0L && totalBytes != contentLength) {
                throw new IOException("Preset app package download is incomplete.");
            }
            if (totalBytes <= 0L) {
                deleteQuietly(temporaryFile);
                throw new IOException("Preset app package is empty.");
            }
            if (!temporaryFile.renameTo(apkFile)) {
                deleteQuietly(temporaryFile);
                throw new IOException("Unable to prepare preset app package.");
            }
            deleteQuietly(resumeFile);
            notifyProgress(100, listener);
            return apkFile;
        }
    }

    private File getDownloadDirectory() throws IOException {
        File baseDirectory = mApplicationContext.getExternalFilesDir(null);
        if (baseDirectory == null) {
            baseDirectory = mApplicationContext.getCacheDir();
        }
        File downloadDirectory = new File(baseDirectory, "preset_apps");
        if (!downloadDirectory.exists() && !downloadDirectory.mkdirs()) {
            throw new IOException("Unable to create preset app download directory.");
        }
        return downloadDirectory;
    }

    /** 校验下载包可解析，且包名属于该预置服务的候选包名之一，返回实际包名。 */
    private String verifyApk(File apkFile, List<String> candidatePackages) throws IOException {
        PackageInfo packageInfo = mApplicationContext.getPackageManager().getPackageArchiveInfo(
                apkFile.getAbsolutePath(), 0);
        if (packageInfo == null || TextUtils.isEmpty(packageInfo.packageName)) {
            throw new IOException("Downloaded preset app package cannot be parsed.");
        }
        if (!candidatePackages.contains(packageInfo.packageName)) {
            throw new IOException("Downloaded preset app package " + packageInfo.packageName
                    + " does not match candidates " + candidatePackages + ".");
        }
        return packageInfo.packageName;
    }

    private void installApk(File apkFile) throws IOException, InterruptedException {
        StringBuilder installerOutput = new StringBuilder();
        Process process = new ProcessBuilder("pm", "install", "-i",
                mApplicationContext.getPackageName(), "-r", apkFile.getAbsolutePath())
                .redirectErrorStream(true)
                .start();
        try (BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = output.readLine()) != null) {
                installerOutput.append(line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode == 0 && installerOutput.toString().contains("Success")) {
            AppLog.i(TAG, "Preset app installation command completed: " + installerOutput);
            return;
        }
        throw new InstallException("Preset app installation failed, exitCode=" + exitCode
                + ", output=" + installerOutput);
    }

    private static String safeFileName(String rawName) {
        String safe = rawName == null ? "preset-app.apk" : rawName;
        safe = safe.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!safe.endsWith(".apk")) {
            safe = safe + ".apk";
        }
        return safe;
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    /** 仅用于区分下载失败与安装失败的内部异常。 */
    private static class InstallException extends IOException {
        InstallException(String message) {
            super(message);
        }
    }

    private void notifyProgress(final int percent, final Listener listener) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onDownloadProgress(percent);
            }
        });
    }

    private void notifyInstallStarted(final Listener listener) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onInstallStarted();
            }
        });
    }

    private void notifyInstalled(final String packageName, final Listener listener) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onInstalled(packageName);
            }
        });
    }

    private void notifyFailure(final boolean installPhase, final Listener listener) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (installPhase) {
                    listener.onInstallFailed();
                } else {
                    listener.onDownloadFailed();
                }
            }
        });
    }

    private void notifyPaused(final Listener listener) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onPaused();
            }
        });
    }

    private static class PauseException extends IOException {
    }

    static final class DownloadTask {
        private Call mCall;
        private boolean mPaused;
        private boolean mInstalling;
        private boolean mFinished;

        Response execute(OkHttpClient client, Request request) throws IOException {
            Call call = client.newCall(request);
            synchronized (this) {
                checkPaused();
                mCall = call;
            }
            return call.execute();
        }

        synchronized boolean pause() {
            if (mInstalling || mFinished) {
                return false;
            }
            mPaused = true;
            if (mCall != null) {
                mCall.cancel();
            }
            return true;
        }

        synchronized void checkPaused() throws IOException {
            if (mPaused) {
                throw new PauseException();
            }
        }

        synchronized boolean isPaused() {
            return mPaused;
        }

        synchronized boolean isInstalling() {
            return mInstalling;
        }

        synchronized void beginInstallation() throws IOException {
            checkPaused();
            mInstalling = true;
        }

        synchronized void finish() {
            mFinished = true;
            mCall = null;
        }
    }
}
