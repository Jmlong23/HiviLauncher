package com.hivi.launcher.systemapps.model;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.hivi.launcher.utils.log.AppLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.List;

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
public final class PresetAppInstaller {
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
    }

    private final Context mApplicationContext;
    private final OkHttpClient mHttpClient = new OkHttpClient.Builder().build();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public PresetAppInstaller(Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    public void downloadAndInstall(final String downloadUrl, final List<String> candidatePackages,
            final Listener listener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File apkFile = downloadApk(downloadUrl, listener);
                    String packageName = verifyApk(apkFile, candidatePackages);
                    AppLog.i(TAG, "Preset app package is ready; installing: "
                            + apkFile.getAbsolutePath());
                    notifyInstallStarted(listener);
                    installApk(apkFile);
                    notifyInstalled(packageName, listener);
                } catch (Throwable throwable) {
                    AppLog.w(TAG, "Preset app download/install failed: " + throwable);
                    if (throwable instanceof InstallException) {
                        notifyFailure(true, listener);
                    } else {
                        notifyFailure(false, listener);
                    }
                }
            }
        }, "PresetAppInstall").start();
    }

    private File downloadApk(String downloadUrl, Listener listener) throws IOException {
        if (TextUtils.isEmpty(downloadUrl)) {
            throw new IOException("Preset app download URL is empty.");
        }

        Request request = new Request.Builder().url(downloadUrl).get().build();
        try (Response response = mHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unable to download preset app. HTTP " + response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Preset app response is empty.");
            }
            long contentLength = responseBody.contentLength();
            if (contentLength > MAX_APK_BYTES) {
                throw new IOException("Preset app package is too large.");
            }

            File downloadDirectory = getDownloadDirectory();
            String fileName = safeFileName(new File(downloadUrl).getName());
            File temporaryFile = new File(downloadDirectory, fileName + ".download");
            File apkFile = new File(downloadDirectory, fileName);
            deleteQuietly(temporaryFile);
            deleteQuietly(apkFile);

            long totalBytes = 0L;
            int lastProgress = -1;
            try (InputStream input = responseBody.byteStream();
                    OutputStream output = new FileOutputStream(temporaryFile)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
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
            } catch (IOException exception) {
                deleteQuietly(temporaryFile);
                throw exception;
            }

            if (totalBytes <= 0L) {
                deleteQuietly(temporaryFile);
                throw new IOException("Preset app package is empty.");
            }
            if (!temporaryFile.renameTo(apkFile)) {
                deleteQuietly(temporaryFile);
                throw new IOException("Unable to prepare preset app package.");
            }
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
}
