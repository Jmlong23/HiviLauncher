package com.hivi.launcher.settings.model;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;

import com.hivi.launcher.BuildConfig;
import com.hivi.launcher.account.model.AuthorizedUserInfo;
import com.hivi.launcher.utils.Constants;
import com.hivi.launcher.utils.log.DailyAppLogWriter;
import com.hivi.launcher.utils.log.DiagnosticLogStorage;
import com.hivi.launcher.utils.log.PackageLogcatCollector;
import com.hivi.launcher.utils.log.PersistentLogcatMirror;
import com.hivi.launcher.utils.network.ApiService;
import com.hivi.launcher.utils.network.AuthorizationStore;
import com.hivi.launcher.utils.network.NetworkCallback;
import com.hivi.launcher.utils.network.NetworkManager;
import com.hivi.launcher.utils.network.ProgressRequestBody;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.reactivex.disposables.Disposable;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Collects the launcher diagnostic logs, packages them, and sends them to the existing logger
 * upload endpoint used by HiviAudio.
 */
public final class LogUploadManager {
    private static final String TAG = "LogUploadManager";
    private static final String EXPORT_DIRECTORY_NAME = "logger_export";
    private static final MediaType TEXT_MEDIA_TYPE =
            MediaType.parse("text/plain; charset=utf-8");
    private static final MediaType FILE_MEDIA_TYPE =
            MediaType.parse("application/octet-stream");

    public interface Callback {
        void onPreparing();

        void onPackaging();

        void onUploading();

        void onUploadProgress(int percent);

        void onSuccess();

        void onFailure(Throwable throwable);
    }

    private final Context mApplicationContext;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mUploadInProgress = new AtomicBoolean();
    private final AtomicBoolean mDestroyed = new AtomicBoolean();
    private volatile Disposable mUploadRequest;

    public LogUploadManager(Context context) {
        mApplicationContext = context.getApplicationContext();
    }

    public void upload(Callback callback) {
        if (callback == null || mDestroyed.get()
                || !mUploadInProgress.compareAndSet(false, true)) {
            return;
        }
        mExecutor.execute(() -> prepareAndUpload(callback));
    }

    public void destroy() {
        mDestroyed.set(true);
        Disposable request = mUploadRequest;
        if (request != null && !request.isDisposed()) {
            request.dispose();
        }
        mUploadRequest = null;
        mUploadInProgress.set(false);
        mExecutor.shutdownNow();
    }

    private void prepareAndUpload(Callback callback) {
        UploadBundle bundle = null;
        try {
            notifyPreparing(callback);
            DailyAppLogWriter writer = DailyAppLogWriter.getInstance();
            if (writer != null) {
                writer.sealForUpload();
            }
            bundle = createUploadBundle(callback);
            if (mDestroyed.get()) {
                discardUploadBundle(bundle);
                return;
            }
            notifyUploading(callback);
            final UploadBundle uploadBundle = bundle;
            RequestBody rawFileBody = RequestBody.create(FILE_MEDIA_TYPE, uploadBundle.archive);
            RequestBody progressFileBody = new ProgressRequestBody(rawFileBody,
                    percent -> {
                        if (!mDestroyed.get()) {
                            callback.onUploadProgress(percent);
                        }
                    });
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file",
                    uploadBundle.archive.getName(), progressFileBody);
            ApiService apiService = NetworkManager.getApiService();
            mUploadRequest = NetworkManager.execute(apiService.uploadLogger(filePart,
                            createTextBody(getDeviceUuid()),
                            createTextBody(BuildConfig.VERSION_NAME),
                            createTextBody(getDeviceInfo()),
                            createTextBody(getUserId()),
                            createTextBody(getUserPhone())),
                    new NetworkCallback<String>() {
                        @Override
                        public void onSuccess(String response) {
                            handleUploadSuccess(callback, uploadBundle, response);
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            handleUploadFailure(callback, uploadBundle, throwable);
                        }
                    });
            if (mDestroyed.get()) {
                Disposable uploadRequest = mUploadRequest;
                if (uploadRequest != null && !uploadRequest.isDisposed()) {
                    uploadRequest.dispose();
                }
                mUploadRequest = null;
                mUploadInProgress.set(false);
                discardUploadBundle(uploadBundle);
            }
        } catch (Throwable throwable) {
            if (bundle != null) {
                discardUploadBundle(bundle);
            } else {
                DiagnosticLogStorage.prune(mApplicationContext);
            }
            finishWithFailure(callback, throwable);
        }
    }

    private UploadBundle createUploadBundle(Callback callback) throws Exception {
        File exportDirectory = new File(mApplicationContext.getFilesDir(), EXPORT_DIRECTORY_NAME);
        if (!exportDirectory.isDirectory() && !exportDirectory.mkdirs()) {
            throw new IOException("Cannot create diagnostic export directory.");
        }
        deleteStaleExportFiles(exportDirectory);

        List<File> temporaryFiles = new ArrayList<>();
        List<File> persistentLogs = collectApplicationLogs();
        DiagnosticLogStorage.reserveForUpload(persistentLogs);
        File archive = null;
        try {
            File logcatSnapshot = PackageLogcatCollector.dumpToTextFile(mApplicationContext,
                    exportDirectory);
            temporaryFiles.add(logcatSnapshot);
            File deviceInfo = writeDeviceInfoFile(exportDirectory);
            temporaryFiles.add(deviceInfo);

            List<File> filesToArchive = new ArrayList<>(persistentLogs);
            filesToArchive.addAll(temporaryFiles);
            if (filesToArchive.isEmpty()) {
                throw new IOException("No diagnostic logs are available.");
            }

            notifyPackaging(callback);
            File mergedLog = mergeLogsForDownload(exportDirectory, filesToArchive);
            temporaryFiles.add(mergedLog);
            archive = new File(exportDirectory, "logs_bundle_" + System.currentTimeMillis()
                    + ".zip");
            List<File> archiveContents = new ArrayList<>(1);
            archiveContents.add(mergedLog);
            zipFiles(archiveContents, archive);
            if (!archive.isFile() || archive.length() == 0L) {
                deleteQuietly(archive);
                throw new IOException("Diagnostic log archive is empty.");
            }
            return new UploadBundle(archive, persistentLogs, temporaryFiles);
        } catch (Exception exception) {
            deleteQuietly(archive);
            deleteFiles(temporaryFiles);
            DiagnosticLogStorage.releaseFromUpload(persistentLogs);
            DiagnosticLogStorage.prune(mApplicationContext);
            throw exception;
        }
    }

    /**
     * Collects only Launcher-owned persisted logs. The continuous full system logcat mirror is
     * intentionally excluded from uploads; app-process logcat is collected separately at upload
     * time by {@link PackageLogcatCollector}.
     */
    private List<File> collectApplicationLogs() {
        List<File> logs = new ArrayList<>();
        File[] files = PersistentLogcatMirror.getLogDirectory(mApplicationContext).listFiles();
        if (files == null) {
            return logs;
        }
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.startsWith("app_log_") || name.endsWith("_crash.log")) {
                logs.add(file);
            }
        }
        return logs;
    }

    private File writeDeviceInfoFile(File outputDirectory) throws IOException {
        File file = new File(outputDirectory, "device_info_" + System.currentTimeMillis() + ".txt");
        String contents = "capturedAt=" + System.currentTimeMillis() + '\n'
                + "packageName=" + mApplicationContext.getPackageName() + '\n'
                + "appVersion=" + BuildConfig.VERSION_NAME + '\n'
                + "manufacturer=" + Build.MANUFACTURER + '\n'
                + "model=" + Build.MODEL + '\n'
                + "product=" + Build.PRODUCT + '\n'
                + "androidVersion=" + Build.VERSION.RELEASE + '\n'
                + "sdkInt=" + Build.VERSION.SDK_INT + '\n';
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /**
     * Keeps collection files independent while the application is running, but exposes one
     * readable diagnostic log in the downloaded archive. The section headers retain each
     * source file's identity, which is useful when inspecting logcat, app logs, and snapshots.
     */
    private File mergeLogsForDownload(File outputDirectory, List<File> sourceFiles)
            throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss_SSS", Locale.US)
                .format(new Date());
        File output = new File(outputDirectory, "launcher_diagnostics_" + timestamp + ".log");
        byte[] buffer = new byte[8192];
        try (FileOutputStream stream = new FileOutputStream(output)) {
            writeUtf8(stream, "========== Launcher Diagnostic Log ==========\n"
                    + "generatedAt=" + System.currentTimeMillis() + "\n"
                    + "sourceFileCount=" + sourceFiles.size() + "\n"
                    + "=============================================\n\n");
            for (File sourceFile : sourceFiles) {
                if (sourceFile == null || !sourceFile.isFile()) {
                    continue;
                }
                writeUtf8(stream, "========== BEGIN " + sourceFile.getName() + " ==========\n"
                        + "sourceFile=" + sourceFile.getName() + "\n"
                        + "lastModified=" + sourceFile.lastModified() + "\n"
                        + "sizeBytes=" + sourceFile.length() + "\n"
                        + "================================================\n\n");
                try (FileInputStream input = new FileInputStream(sourceFile)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        stream.write(buffer, 0, read);
                    }
                }
                writeUtf8(stream, "\n========== END " + sourceFile.getName()
                        + " ==========\n\n");
            }
        }
        return output;
    }

    private void handleUploadSuccess(Callback callback, UploadBundle bundle, String response) {
        try {
            validateUploadResponse(response);
            deleteUploadedPersistentLogs(bundle.persistentLogs);
            finishWithSuccess(callback);
        } catch (Throwable throwable) {
            finishWithFailure(callback, throwable);
        } finally {
            discardUploadBundle(bundle);
        }
    }

    private void handleUploadFailure(Callback callback, UploadBundle bundle, Throwable throwable) {
        try {
            finishWithFailure(callback, throwable);
        } finally {
            discardUploadBundle(bundle);
        }
    }

    private void validateUploadResponse(String response) throws Exception {
        if (TextUtils.isEmpty(response)) {
            return;
        }
        try {
            JSONObject object = new JSONObject(response);
            if (object.has("success") && !object.optBoolean("success")) {
                throw new IOException(object.optString("message", "Log upload was rejected."));
            }
            if (object.has("code") && object.optInt("code", 200) != 200) {
                throw new IOException(object.optString("message", "Log upload was rejected."));
            }
        } catch (org.json.JSONException ignored) {
            // The logger endpoint has historically returned plain text on success.
        }
    }

    private RequestBody createTextBody(String value) {
        return RequestBody.create(TEXT_MEDIA_TYPE, value == null ? "" : value);
    }

    private String getDeviceUuid() {
        try {
            Object uuid = SWDeviceStatus.getUUID();
            return uuid == null ? "" : uuid.toString();
        } catch (Exception exception) {
            AppLog.w(TAG, "Unable to read device UUID for log upload.", exception);
            return "";
        }
    }

    private String getDeviceInfo() {
        return TextUtils.isEmpty(Build.MODEL) ? Constants.APP_UPDATE_PRODUCT_TYPE : Build.MODEL;
    }

    private String getUserId() {
        AuthorizedUserInfo userInfo = AuthorizationStore.getUserInfo(mApplicationContext);
        return userInfo == null ? "" : userInfo.getId();
    }

    private String getUserPhone() {
        AuthorizedUserInfo userInfo = AuthorizationStore.getUserInfo(mApplicationContext);
        return userInfo == null ? "" : userInfo.getPhone();
    }

    private void deleteUploadedPersistentLogs(List<File> files) {
        for (File file : files) {
            deleteQuietly(file);
        }
    }

    private void deleteFiles(List<File> files) {
        for (File file : files) {
            deleteQuietly(file);
        }
    }

    private void deleteStaleExportFiles(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file != null && file.isFile()) {
                deleteQuietly(file);
            }
        }
    }

    private void discardUploadBundle(UploadBundle bundle) {
        if (bundle == null) {
            return;
        }
        deleteQuietly(bundle.archive);
        deleteFiles(bundle.temporaryFiles);
        DiagnosticLogStorage.releaseFromUpload(bundle.persistentLogs);
        DiagnosticLogStorage.prune(mApplicationContext);
    }

    private void finishWithSuccess(Callback callback) {
        mUploadRequest = null;
        mUploadInProgress.set(false);
        if (!mDestroyed.get()) {
            callback.onSuccess();
        }
    }

    private void finishWithFailure(Callback callback, Throwable throwable) {
        mUploadRequest = null;
        mUploadInProgress.set(false);
        AppLog.e(TAG, "Diagnostic log upload failed.", throwable);
        if (!mDestroyed.get()) {
            callback.onFailure(throwable);
        }
    }

    private void notifyPreparing(Callback callback) {
        if (!mDestroyed.get()) {
            callback.onPreparing();
        }
    }

    private void notifyPackaging(Callback callback) {
        if (!mDestroyed.get()) {
            callback.onPackaging();
        }
    }

    private void notifyUploading(Callback callback) {
        if (!mDestroyed.get()) {
            callback.onUploading();
        }
    }

    private static void zipFiles(List<File> files, File archive) throws IOException {
        byte[] buffer = new byte[8192];
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive))) {
            for (File file : files) {
                if (file == null || !file.isFile()) {
                    continue;
                }
                output.putNextEntry(new ZipEntry(file.getName()));
                try (FileInputStream input = new FileInputStream(file)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
                output.closeEntry();
            }
        }
    }

    private static void writeUtf8(FileOutputStream stream, String contents) throws IOException {
        stream.write(contents.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            AppLog.w(TAG, "Unable to delete temporary diagnostic file: " + file);
        }
    }

    private static final class UploadBundle {
        private final File archive;
        private final List<File> persistentLogs;
        private final List<File> temporaryFiles;

        private UploadBundle(File archive, List<File> persistentLogs, List<File> temporaryFiles) {
            this.archive = archive;
            this.persistentLogs = persistentLogs;
            this.temporaryFiles = temporaryFiles;
        }
    }
}
