package com.hivi.launcher.settings.presenter;

import android.content.Context;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;

import com.hivi.launcher.BuildConfig;
import com.hivi.launcher.R;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.settings.model.LogUploadManager;
import com.hivi.launcher.settings.model.SettingsModel;
import com.hivi.launcher.settings.ui.SettingsView;
import com.hivi.launcher.update.SystemUpdateInfo;
import com.hivi.launcher.update.SystemUpdateManager;
import com.hivi.launcher.utils.Constants;
import com.hivi.launcher.utils.network.ApiService;
import com.hivi.launcher.utils.network.NetworkCallback;
import com.hivi.launcher.utils.network.NetworkManager;

import org.json.JSONObject;

import io.reactivex.disposables.Disposable;

public final class SettingsPresenter extends BasePresenter<SettingsView> {
    private static final String TAG = "SettingsPresenter";
    private final SettingsModel mModel = new SettingsModel();
    public static final int SECTION_NETWORK = 0;
    public static final int SECTION_DISPLAY = 1;
    public static final int SECTION_SYSTEM = 2;
    public static final int SECTION_ABOUT = 3;
    public static final int SECTION_MAINTENANCE = 4;
    private final Context mApplicationContext;
    private ApiService mApiService;
    private SystemUpdateManager mSystemUpdateManager;
    private SystemUpdateInfo mSystemUpdateInfo;
    private Disposable mSystemUpdateCheckRequest;
    private boolean mSystemUpdateInProgress;
    private LogUploadManager mLogUploadManager;
    private boolean mLogUploadInProgress;

    public SettingsPresenter(SettingsView view) {
        this(null, view, SettingsModel.LANGUAGE_CHINESE);
    }

    public SettingsPresenter(SettingsView view, int initialLanguage) {
        this(null, view, initialLanguage);
    }

    public SettingsPresenter(Context context, SettingsView view, int initialLanguage) {
        super(view);
        mApplicationContext = context == null ? null : context.getApplicationContext();
        mSystemUpdateInfo = SystemUpdateInfo.currentVersion(getCurrentVersionName(),
                BuildConfig.VERSION_CODE);
        if (isValidLanguage(initialLanguage)) {
            mModel.setLanguage(initialLanguage);
        }
    }

    public void init() {
        renderSelectedSection();
        renderDisplaySettings();
        renderSystemUpdate();
        if (mModel.getSelectedSection() == SECTION_SYSTEM) {
            requestSystemUpdateDetails();
        }
    }

    public void onSectionSelected(int section) {
        if (!isValidSection(section)) {
            return;
        }
        boolean sectionChanged = mModel.getSelectedSection() != section;
        dismissDisplayDropdowns();
        if (!sectionChanged) {
            renderDisplaySettings();
            if (section == SECTION_SYSTEM) {
                requestSystemUpdateDetails();
            }
            return;
        }
        mModel.setSelectedSection(section);
        renderSelectedSection();
        renderDisplaySettings();
        if (section == SECTION_SYSTEM) {
            requestSystemUpdateDetails();
        }
    }

    public void onLanguageSelected() {
        boolean expanded = !mModel.isLanguageOptionsExpanded();
        mModel.setLanguageOptionsExpanded(expanded);
        if (expanded) {
            mModel.setScreenSaverTimeoutOptionsExpanded(false);
        }
        renderDisplaySettings();
    }

    public void onLanguageOptionSelected(int language) {
        if (!isValidLanguage(language)) {
            return;
        }
        mModel.setLanguage(language);
        dismissDisplayDropdowns();
        renderDisplaySettings();
    }

    public void onScreenSaverToggled() {
        boolean enabled = !mModel.isScreenSaverEnabled();
        mModel.setScreenSaverEnabled(enabled);
        dismissDisplayDropdowns();
        renderDisplaySettings();
    }

    public void onScreenSaverTimeoutSelected() {
        if (!mModel.isScreenSaverEnabled()) {
            return;
        }
        boolean expanded = !mModel.isScreenSaverTimeoutOptionsExpanded();
        mModel.setScreenSaverTimeoutOptionsExpanded(expanded);
        if (expanded) {
            mModel.setLanguageOptionsExpanded(false);
        }
        renderDisplaySettings();
    }

    public void onScreenSaverTimeoutOptionSelected(int timeout) {
        if (!mModel.isScreenSaverEnabled() || !isValidScreenSaverTimeout(timeout)) {
            return;
        }
        mModel.setScreenSaverTimeout(timeout);
        dismissDisplayDropdowns();
        renderDisplaySettings();
    }

    public void onDisplayDropdownDismissed() {
        if (!mModel.isLanguageOptionsExpanded()
                && !mModel.isScreenSaverTimeoutOptionsExpanded()) {
            return;
        }
        dismissDisplayDropdowns();
        renderDisplaySettings();
    }

    public void onSystemUpdateSelected() {
        if (mSystemUpdateInProgress || mSystemUpdateInfo == null
                || !mSystemUpdateInfo.isUpdateAvailable()) {
            return;
        }
        SettingsView view = getView();
        if (view != null) {
            view.showSystemUpdateConfirmation(mSystemUpdateInfo);
        }
    }

    public void onSystemUpdateConfirmed() {
        if (mSystemUpdateInProgress || mSystemUpdateInfo == null
                || !mSystemUpdateInfo.isUpdateAvailable()) {
            return;
        }
        if (mApplicationContext == null) {
            handleSystemUpdateFailure(new IllegalStateException("Update manager is unavailable."));
            return;
        }
        if (mSystemUpdateManager == null) {
            mSystemUpdateManager = new SystemUpdateManager(mApplicationContext);
        }
        mSystemUpdateInProgress = true;
        renderSystemUpdate();
        SettingsView view = getView();
        if (view != null) {
            view.showSystemUpdateProgress(0,
                    mApplicationContext.getString(R.string.system_update_downloading));
        }
        mSystemUpdateManager.downloadAndInstall(mSystemUpdateInfo, new SystemUpdateManager.Callback() {
            @Override
            public void onDownloadStarted() {
                renderSystemUpdateProgress(0, R.string.system_update_downloading);
            }

            @Override
            public void onDownloadProgress(int progress) {
                renderSystemUpdateProgress(progress, R.string.system_update_downloading);
            }

            @Override
            public void onInstalling() {
                renderSystemUpdateProgress(100, R.string.system_update_installing);
            }

            @Override
            public void onInstallSubmitted() {
                // The package installer reports the final success/failure through the manifest
                // receiver, allowing the result to survive replacement of this package.
            }

            @Override
            public void onFailure(Throwable throwable) {
                handleSystemUpdateFailure(throwable);
            }
        });
    }

    public void onLogUploadSelected() {
        if (mLogUploadInProgress) {
            return;
        }
        SettingsView view = getView();
        if (view != null) {
            view.showLogUploadConfirmation();
        }
    }

    public void onLogUploadConfirmed() {
        if (mLogUploadInProgress) {
            return;
        }
        if (mApplicationContext == null) {
            handleLogUploadFailure(new IllegalStateException("Log upload manager is unavailable."));
            return;
        }
        if (mLogUploadManager == null) {
            mLogUploadManager = new LogUploadManager(mApplicationContext);
        }
        mLogUploadInProgress = true;
        mLogUploadManager.upload(new LogUploadManager.Callback() {
            @Override
            public void onPreparing() {
                renderLogUploadProgress(0, R.string.log_upload_preparing);
            }

            @Override
            public void onPackaging() {
                renderLogUploadProgress(0, R.string.log_upload_packaging);
            }

            @Override
            public void onUploading() {
                renderLogUploadProgress(0, R.string.log_upload_uploading);
            }

            @Override
            public void onUploadProgress(int percent) {
                renderLogUploadProgress(percent, R.string.log_upload_uploading);
            }

            @Override
            public void onSuccess() {
                handleLogUploadSuccess();
            }

            @Override
            public void onFailure(Throwable throwable) {
                handleLogUploadFailure(throwable);
            }
        });
    }

    @Override
    public void detach() {
        disposeSystemUpdateCheckRequest();
        if (mLogUploadManager != null) {
            mLogUploadManager.destroy();
            mLogUploadManager = null;
        }
        super.detach();
    }

    private void renderSelectedSection() {
        SettingsView view = getView();
        if (view != null) {
            view.renderSettingsSection(mModel.getSelectedSection());
        }
    }

    private void renderDisplaySettings() {
        SettingsView view = getView();
        if (view != null) {
            view.renderDisplaySettings(mModel.getLanguage(), mModel.isLanguageOptionsExpanded(),
                    mModel.isScreenSaverEnabled(), mModel.getScreenSaverTimeout(),
                    mModel.isScreenSaverTimeoutOptionsExpanded());
        }
    }

    private void requestSystemUpdateDetails() {
        if (mApplicationContext == null || mSystemUpdateCheckRequest != null) {
            return;
        }
        if (mApiService == null) {
            mApiService = NetworkManager.getApiService();
        }
        AppLog.i(TAG, "Checking system update: productType="
                + Constants.APP_UPDATE_PRODUCT_TYPE
                + ", currentVersionName=" + getCurrentVersionName()
                + ", currentVersionCode=" + BuildConfig.VERSION_CODE);
        mSystemUpdateCheckRequest = NetworkManager.execute(
                mApiService.getAppVersionDetails(Constants.APP_UPDATE_PRODUCT_TYPE),
                new NetworkCallback<String>() {
                    @Override
                    public void onSuccess(String response) {
                        mSystemUpdateCheckRequest = null;
                        try {
                            mSystemUpdateInfo = parseSystemUpdateInfo(response);
                            AppLog.i(TAG, "System update check result: currentVersionName="
                                    + mSystemUpdateInfo.getCurrentVersionName()
                                    + ", currentVersionCode="
                                    + mSystemUpdateInfo.getCurrentVersionCode()
                                    + ", latestVersionName="
                                    + mSystemUpdateInfo.getLatestVersionName()
                                    + ", latestVersionCode="
                                    + mSystemUpdateInfo.getLatestVersionCode()
                                    + ", hasDownloadUrl="
                                    + !TextUtils.isEmpty(mSystemUpdateInfo.getDownloadUrl())
                                    + ", updateAvailable="
                                    + mSystemUpdateInfo.isUpdateAvailable());
                        } catch (Exception exception) {
                            AppLog.w(TAG, "Unable to parse system update response.", exception);
                        }
                        renderSystemUpdate();
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        mSystemUpdateCheckRequest = null;
                        AppLog.w(TAG, "Unable to check system update: productType="
                                + Constants.APP_UPDATE_PRODUCT_TYPE, throwable);
                        renderSystemUpdate();
                    }
                });
    }

    private SystemUpdateInfo parseSystemUpdateInfo(String responseText) throws Exception {
        JSONObject response = new JSONObject(responseText);
        if (response.has("code") && response.optInt("code", 200) != 200) {
            throw new IllegalStateException("Update service returned code "
                    + response.optInt("code"));
        }
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("Update response data is missing.");
        }
        String latestVersionName = data.optString("versionName");
        if (TextUtils.isEmpty(latestVersionName)) {
            throw new IllegalStateException("Update response version name is missing.");
        }
        AppLog.i(TAG, "System update service response: code="
                + response.optInt("code", 200)
                + ", message=" + response.optString("message")
                + ", versionName=" + latestVersionName
                + ", versionCode=" + data.optLong("versionCode", 0L)
                + ", hasUpdateUrl=" + !TextUtils.isEmpty(data.optString("updateUrl"))
                + ", updateExplain=" + data.optString("updateExplain"));
        return new SystemUpdateInfo(getCurrentVersionName(), BuildConfig.VERSION_CODE,
                latestVersionName, data.optLong("versionCode", 0L),
                data.optString("updateUrl"));
    }

    private String getCurrentVersionName() {
        final String debugSuffix = "-debug";
        String versionName = BuildConfig.VERSION_NAME;
        return versionName.endsWith(debugSuffix)
                ? versionName.substring(0, versionName.length() - debugSuffix.length())
                : versionName;
    }

    private void renderSystemUpdate() {
        SettingsView view = getView();
        if (view != null) {
            view.renderSystemUpdate(mSystemUpdateInfo, mSystemUpdateInProgress);
        }
    }

    private void renderSystemUpdateProgress(final int progress, final int statusResId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SettingsView view = getView();
                if (view != null && mApplicationContext != null) {
                    view.showSystemUpdateProgress(progress,
                            mApplicationContext.getString(statusResId));
                }
            }
        });
    }

    private void handleSystemUpdateFailure(final Throwable throwable) {
        AppLog.e(TAG, "System update failed.", throwable);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSystemUpdateInProgress = false;
                renderSystemUpdate();
                SettingsView view = getView();
                if (view != null) {
                    view.dismissSystemUpdateProgress();
                    if (mApplicationContext != null) {
                        view.showToast(mApplicationContext.getString(R.string.system_update_failed));
                    }
                }
            }
        });
    }

    private void renderLogUploadProgress(final int progress, final int statusResId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SettingsView view = getView();
                if (view != null && mApplicationContext != null) {
                    view.showLogUploadProgress(progress,
                            mApplicationContext.getString(statusResId));
                }
            }
        });
    }

    private void handleLogUploadSuccess() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogUploadInProgress = false;
                SettingsView view = getView();
                if (view != null) {
                    view.showLogUploadSuccess();
                }
            }
        });
    }

    private void handleLogUploadFailure(final Throwable throwable) {
        AppLog.e(TAG, "Log upload failed.", throwable);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogUploadInProgress = false;
                SettingsView view = getView();
                if (view != null) {
                    view.showLogUploadFailure();
                }
            }
        });
    }

    private void disposeSystemUpdateCheckRequest() {
        if (mSystemUpdateCheckRequest != null && !mSystemUpdateCheckRequest.isDisposed()) {
            mSystemUpdateCheckRequest.dispose();
        }
        mSystemUpdateCheckRequest = null;
    }

    private void dismissDisplayDropdowns() {
        mModel.setLanguageOptionsExpanded(false);
        mModel.setScreenSaverTimeoutOptionsExpanded(false);
    }

    private boolean isValidSection(int section) {
        return section >= SECTION_NETWORK && section <= SECTION_MAINTENANCE;
    }

    private boolean isValidLanguage(int language) {
        return language == SettingsModel.LANGUAGE_CHINESE
                || language == SettingsModel.LANGUAGE_ENGLISH;
    }

    private boolean isValidScreenSaverTimeout(int timeout) {
        return timeout >= SettingsModel.SCREEN_SAVER_TIMEOUT_ONE_MINUTE
                && timeout <= SettingsModel.SCREEN_SAVER_TIMEOUT_NEVER;
    }
}
