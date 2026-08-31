package com.hivi.launcher.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.text.TextUtils;

import com.hivi.launcher.utils.log.AppLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads, validates, and installs a launcher APK without stopping the running launcher.
 */
public final class SystemUpdateManager {
    private static final String TAG = "SystemUpdateManager";
    private static final long MAX_UPDATE_PACKAGE_BYTES = 1024L * 1024L * 1024L;

    public interface Callback {
        void onDownloadStarted();

        void onDownloadProgress(int progress);

        void onInstallStarted();

        void onFailure(Throwable throwable);
    }

    private final Context mApplicationContext;
    private final OkHttpClient mHttpClient = new OkHttpClient.Builder().build();

    public SystemUpdateManager(Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    public void downloadAndInstall(final SystemUpdateInfo updateInfo, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    callback.onDownloadStarted();
                    File packageFile = downloadPackage(updateInfo, callback);
                    verifyPackage(packageFile);
                    AppLog.i(TAG, "Update package is ready; installing without stopping launcher: "
                            + packageFile.getAbsolutePath());
                    callback.onInstallStarted();
                    installPackage(packageFile);
                } catch (Throwable throwable) {
                    callback.onFailure(throwable);
                }
            }
        }, "SystemUpdate").start();
    }

    private File downloadPackage(SystemUpdateInfo updateInfo, Callback callback) throws IOException {
        if (updateInfo == null || TextUtils.isEmpty(updateInfo.getDownloadUrl())) {
            throw new IOException("Update package URL is empty.");
        }

        Request request = new Request.Builder()
                .url(updateInfo.getDownloadUrl())
                .get()
                .build();
        try (Response response = mHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unable to download update package. HTTP "
                        + response.code());
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("Update package response is empty.");
            }
            long contentLength = responseBody.contentLength();
            if (contentLength > MAX_UPDATE_PACKAGE_BYTES) {
                throw new IOException("Update package is too large.");
            }

            File updateDirectory = getUpdateDirectory();
            File temporaryFile = new File(updateDirectory, "hivi-launcher-update.apk.download");
            File packageFile = new File(updateDirectory, "hivi-launcher-update.apk");
            deleteQuietly(temporaryFile);
            deleteQuietly(packageFile);

            long totalBytes = 0L;
            int lastProgress = -1;
            try (InputStream input = responseBody.byteStream();
                    OutputStream output = new FileOutputStream(temporaryFile)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > MAX_UPDATE_PACKAGE_BYTES) {
                        throw new IOException("Update package is too large.");
                    }
                    output.write(buffer, 0, read);
                    if (contentLength > 0L) {
                        int progress = (int) Math.min(90L, totalBytes * 90L / contentLength);
                        if (progress != lastProgress) {
                            lastProgress = progress;
                            callback.onDownloadProgress(progress);
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
                throw new IOException("Update package is empty.");
            }
            if (!temporaryFile.renameTo(packageFile)) {
                deleteQuietly(temporaryFile);
                throw new IOException("Unable to prepare update package.");
            }
            callback.onDownloadProgress(90);
            return packageFile;
        }
    }

    private File getUpdateDirectory() throws IOException {
        File baseDirectory = mApplicationContext.getExternalFilesDir(null);
        if (baseDirectory == null) {
            baseDirectory = mApplicationContext.getCacheDir();
        }
        File updateDirectory = new File(baseDirectory, "updates");
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw new IOException("Unable to create update directory.");
        }
        return updateDirectory;
    }

    private void verifyPackage(File packageFile) throws IOException {
        PackageInfo packageInfo = mApplicationContext.getPackageManager().getPackageArchiveInfo(
                packageFile.getAbsolutePath(), 0);
        if (packageInfo == null
                || !TextUtils.equals(mApplicationContext.getPackageName(), packageInfo.packageName)) {
            throw new IOException("Downloaded package does not belong to this launcher.");
        }
    }

    private void installPackage(File packageFile) throws IOException, InterruptedException {
        StringBuilder installerOutput = new StringBuilder();
        Process process = new ProcessBuilder("pm", "install", "-i",
                mApplicationContext.getPackageName(), "-r", "--dont-kill",
                packageFile.getAbsolutePath())
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
            AppLog.i(TAG, "Update installation command completed: " + installerOutput);
            return;
        }
        throw new IOException("Update installation command failed, exitCode=" + exitCode
                + ", output=" + installerOutput);
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
