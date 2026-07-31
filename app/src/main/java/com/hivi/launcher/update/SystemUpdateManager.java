package com.hivi.launcher.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.text.TextUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Downloads a verified launcher APK and submits it to Android's package installer.
 *
 * <p>The terminal install result is delivered by {@link SystemUpdateInstallReceiver}, which
 * survives replacement of the current launcher package.</p>
 */
public final class SystemUpdateManager {
    private static final long MAX_UPDATE_PACKAGE_BYTES = 1024L * 1024L * 1024L;
    private static final int INSTALL_STATUS_PENDING_INTENT_MUTABLE = 0x02000000;

    public interface Callback {
        void onDownloadStarted();

        void onDownloadProgress(int progress);

        void onInstalling();

        void onInstallSubmitted();

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
                    verifyPackage(packageFile, updateInfo);
                    callback.onInstalling();
                    submitPackageInstall(packageFile);
                    callback.onInstallSubmitted();
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

    private void verifyPackage(File packageFile, SystemUpdateInfo updateInfo) throws IOException {
        PackageInfo packageInfo = mApplicationContext.getPackageManager().getPackageArchiveInfo(
                packageFile.getAbsolutePath(), 0);
        if (packageInfo == null
                || !TextUtils.equals(mApplicationContext.getPackageName(), packageInfo.packageName)) {
            throw new IOException("Downloaded package does not belong to this launcher.");
        }
        long packageVersionCode = getVersionCode(packageInfo);
        if (packageVersionCode <= updateInfo.getCurrentVersionCode()) {
            throw new IOException("Downloaded package is not newer than the installed version.");
        }
        if (updateInfo.getLatestVersionCode() > 0L
                && packageVersionCode < updateInfo.getLatestVersionCode()) {
            throw new IOException("Downloaded package version does not match the update service.");
        }
    }

    private long getVersionCode(PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }

    private void submitPackageInstall(File packageFile) throws Exception {
        PackageInstaller packageInstaller = mApplicationContext.getPackageManager()
                .getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(mApplicationContext.getPackageName());
        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = null;
        try {
            session = packageInstaller.openSession(sessionId);
            try (InputStream input = new FileInputStream(packageFile);
                    OutputStream output = session.openWrite("base.apk", 0L, packageFile.length())) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                session.fsync(output);
            }

            Intent statusIntent = new Intent(mApplicationContext,
                    SystemUpdateInstallReceiver.class)
                    .setAction(SystemUpdateInstallReceiver.ACTION_INSTALL_STATUS);
            int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) {
                // PackageInstaller supplies its result extras after Android 12, so this
                // PendingIntent must be mutable. compileSdk 30 does not expose the constant.
                pendingIntentFlags |= INSTALL_STATUS_PENDING_INTENT_MUTABLE;
            }
            PendingIntent statusPendingIntent = PendingIntent.getBroadcast(mApplicationContext,
                    sessionId, statusIntent, pendingIntentFlags);
            session.commit(statusPendingIntent.getIntentSender());
        } catch (Exception exception) {
            packageInstaller.abandonSession(sessionId);
            throw exception;
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }
}
