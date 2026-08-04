package com.hivi.launcher.account.presenter;

import android.content.Context;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;

import com.hivi.launcher.account.model.AuthorizedUserInfo;
import com.hivi.launcher.account.model.AuthorizationUiState;
import com.hivi.launcher.account.model.AuthorizationUiState.RetryReason;
import com.hivi.launcher.account.ui.AuthorizationView;
import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.utils.network.ApiService;
import com.hivi.launcher.utils.network.AuthorizationStore;
import com.hivi.launcher.utils.network.NetworkCallback;
import com.hivi.launcher.utils.network.NetworkManager;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;

import org.json.JSONObject;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Owns QR authorization state transitions, polling, persistence, and request cleanup.
 */
public final class AuthorizationPresenter extends BasePresenter<AuthorizationView> {
    private static final String TAG = "AuthorizationPresenter";
    private static final int QR_STATUS_WAITING = 1;
    private static final int QR_STATUS_SCANNED = 2;
    private static final int QR_STATUS_CONFIRMED = 3;
    private static final int QR_STATUS_CANCELLED = 4;
    private static final int QR_STATUS_EXPIRED = 5;
    private static final int MAX_POLL_COUNT = 300;
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final long RETRY_DELAY_MS = 1600L;
    private static final String[] ACCOUNT_NAME_KEYS = {
            "nickname", "nickName", "userName", "username", "name", "accountName"
    };
    private final Context mContext;
    private final ApiService mApiService;
    private final Runnable mUserInfoChangedCallback;
    private Disposable mQrRequest;
    private Disposable mLogoutRequest;
    private Disposable mUserDetailsRequest;
    private String mQrId;
    private int mPollCount;
    private boolean mLogoutInProgress;

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (TextUtils.isEmpty(mQrId)) {
                return;
            }
            if (mPollCount >= MAX_POLL_COUNT) {
                reloadQrCode(RetryReason.EXPIRED);
                return;
            }
            mPollCount++;
            requestQrState(mQrId);
        }
    };

    public AuthorizationPresenter(Context context, AuthorizationView view) {
        this(context, view, null);
    }

    public AuthorizationPresenter(Context context, AuthorizationView view,
            Runnable userInfoChangedCallback) {
        super(view);
        mContext = context.getApplicationContext();
        mApiService = NetworkManager.getApiService();
        mUserInfoChangedCallback = userInfoChangedCallback;
    }

    public void start() {
        if (AuthorizationStore.hasToken(mContext)) {
            requestUserDetails();
            render(AuthorizationUiState.accountInfo());
        } else {
            requestNewQrCode();
        }
    }

    public void refreshQrCode() {
        if (mLogoutInProgress) {
            return;
        }
        disposeQrRequest();
        disposeUserDetailsRequest();
        requestNewQrCode();
    }

    public void showAccountInfo() {
        if (AuthorizationStore.hasToken(mContext)) {
            render(AuthorizationUiState.accountInfo());
        }
    }

    public void cancelAuthorization() {
        if (mLogoutInProgress || !AuthorizationStore.hasToken(mContext)) {
            return;
        }
        mLogoutInProgress = true;
        render(AuthorizationUiState.canceling());
        disposeLogoutRequest();
        disposeUserDetailsRequest();
        mLogoutRequest = NetworkManager.execute(
                mApiService.qrLogout(SWDeviceStatus.getUUID().toString()),
                new NetworkCallback<String>() {
                    @Override
                    public void onSuccess(String response) {
                        mLogoutRequest = null;
                        if (!isViewAttached()) {
                            return;
                        }
                        try {
                            if (isLogoutSuccess(response)
                                    || shouldTreatLogoutFailureAsCancelled(response)) {
                                AuthorizationStore.clearToken(mContext);
                                mLogoutInProgress = false;
                                mQrId = null;
                                removeUiThreadRunnable(mPollRunnable);
                                render(AuthorizationUiState.unauthorized());
                            } else {
                                handleCancelAuthorizationFailure();
                            }
                        } catch (Exception e) {
                            AppLog.e(TAG, "Unable to parse cancel authorization response", e);
                            handleCancelAuthorizationFailure();
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        mLogoutRequest = null;
                        if (isViewAttached()) {
                            AppLog.e(TAG, "Unable to cancel authorization", throwable);
                            handleCancelAuthorizationFailure();
                        }
                    }
                });
    }

    @Override
    public void detach() {
        disposeQrRequest();
        disposeLogoutRequest();
        mQrId = null;
        mLogoutInProgress = false;
        // Keep the in-flight details request alive so a successful authorization can still
        // persist the user profile after the success dialog automatically closes.
        super.detach();
    }

    private void requestNewQrCode() {
        if (!isViewAttached()) {
            return;
        }
        removeUiThreadRunnable(mPollRunnable);
        mQrId = null;
        mPollCount = 0;
        render(AuthorizationUiState.loading());
        requestQrState(null);
    }

    private void requestQrState(final String qrId) {
        disposeQrRequest();
        Observable<String> request = qrId == null ? mApiService.getQr() : mApiService.getQr(qrId);
        mQrRequest = NetworkManager.execute(request, new NetworkCallback<String>() {
            @Override
            public void onSuccess(String response) {
                mQrRequest = null;
                try {
                    handleQrState(readQrState(response), qrId == null);
                } catch (Exception e) {
                    AppLog.e(TAG, "Unable to parse QR response", e);
                    handleRequestFailure();
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                mQrRequest = null;
                handleRequestFailure();
            }
        });
    }

    private void handleQrState(QrState state, boolean isInitialRequest) {
        if (!isViewAttached()) {
            return;
        }
        if (state.status == QR_STATUS_WAITING && !TextUtils.isEmpty(state.id)) {
            mQrId = state.id;
            render(AuthorizationUiState.waitingForScan(state.id + "&MG100"
                    + SWDeviceStatus.getUUID().toString()));
            schedulePoll();
            return;
        }
        if (state.status == QR_STATUS_SCANNED) {
            render(AuthorizationUiState.scanned());
            schedulePoll();
            return;
        }
        if (state.status == QR_STATUS_CONFIRMED && !TextUtils.isEmpty(state.token)) {
            AuthorizationStore.saveAuthorization(mContext, state.token, state.accountName);
            removeUiThreadRunnable(mPollRunnable);
            requestUserDetails();
            render(AuthorizationUiState.authorized());
            return;
        }
        if (state.status == QR_STATUS_CANCELLED) {
            reloadQrCode(RetryReason.CANCELED);
            return;
        }
        if (state.status == QR_STATUS_EXPIRED) {
            reloadQrCode(RetryReason.EXPIRED);
            return;
        }
        if (isInitialRequest) {
            handleRequestFailure();
        }
    }

    private void schedulePoll() {
        removeUiThreadRunnable(mPollRunnable);
        runOnUiThreadDelayed(mPollRunnable, POLL_INTERVAL_MS);
    }

    private void reloadQrCode(RetryReason reason) {
        if (!isViewAttached()) {
            return;
        }
        render(AuthorizationUiState.retrying(reason));
        removeUiThreadRunnable(mPollRunnable);
        runOnUiThreadDelayed(new Runnable() {
            @Override
            public void run() {
                requestNewQrCode();
            }
        }, RETRY_DELAY_MS);
    }

    private void handleRequestFailure() {
        reloadQrCode(RetryReason.NETWORK);
    }

    private void handleCancelAuthorizationFailure() {
        mLogoutInProgress = false;
        render(AuthorizationUiState.cancelFailed());
    }

    private void requestUserDetails() {
        final String requestToken = AuthorizationStore.getToken(mContext);
        if (TextUtils.isEmpty(requestToken)) {
            return;
        }
        disposeUserDetailsRequest();
        mUserDetailsRequest = NetworkManager.execute(mApiService.getUserDetails(),
                new NetworkCallback<String>() {
                    @Override
                    public void onSuccess(String response) {
                        mUserDetailsRequest = null;
                        if (!TextUtils.equals(requestToken, AuthorizationStore.getToken(mContext))) {
                            return;
                        }
                        try {
                            AuthorizedUserInfo userInfo = readUserDetails(response);
                            AuthorizationStore.saveUserInfo(mContext, userInfo);
                            AppLog.d(TAG, "Authorization succeeded. userId=" + userInfo.getId());
                            notifyUserInfoUpdated();
                        } catch (Exception e) {
                            AppLog.e(TAG, "Unable to parse user details response", e);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        mUserDetailsRequest = null;
                        AppLog.e(TAG, "Unable to load authorized user details", throwable);
                    }
                });
    }

    private void render(AuthorizationUiState state) {
        AuthorizationView view = getView();
        if (view != null) {
            view.renderAuthorization(state);
        }
    }

    private QrState readQrState(String responseText) throws Exception {
        JSONObject response = new JSONObject(responseText);
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("Invalid authorization response");
        }
        int status = data.optInt("status");
        return new QrState(data.optString("id"), status, data.optString("token"),
                readAccountName(data));
    }

    private String readAccountName(JSONObject data) {
        String accountName = readAccountName(data, ACCOUNT_NAME_KEYS);
        if (!TextUtils.isEmpty(accountName)) {
            return accountName;
        }
        return readAccountName(data.optJSONObject("user"), ACCOUNT_NAME_KEYS);
    }

    private String readAccountName(JSONObject source, String[] keys) {
        if (source == null) {
            return "";
        }
        for (String key : keys) {
            String value = source.optString(key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private AuthorizedUserInfo readUserDetails(String responseText) throws Exception {
        JSONObject response = new JSONObject(responseText);
        if (!response.optBoolean("success") && response.optInt("code") != 200) {
            throw new IllegalStateException("User details request was not successful");
        }
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("Invalid user details response");
        }
        return new AuthorizedUserInfo(data.optString("id"), data.optString("name"),
                data.optString("avatarUrl"), data.optString("area"), data.optString("phone"),
                data.optString("preferred"), data.optString("createTime"), data.optInt("isDel"));
    }

    private boolean isLogoutSuccess(String responseText) throws Exception {
        JSONObject response = new JSONObject(responseText);
        return response.optBoolean("success") || response.optInt("code") == 200;
    }

    private boolean shouldTreatLogoutFailureAsCancelled(String responseText) throws Exception {
        String message = new JSONObject(responseText).optString("message");
        return message.contains("登录状态过期")
                || message.contains("已解绑")
                || message.contains("未绑定")
                || message.contains("无效token")
                || message.contains("token失效");
    }

    private void disposeQrRequest() {
        if (mQrRequest != null && !mQrRequest.isDisposed()) {
            mQrRequest.dispose();
        }
        mQrRequest = null;
    }

    private void disposeLogoutRequest() {
        if (mLogoutRequest != null && !mLogoutRequest.isDisposed()) {
            mLogoutRequest.dispose();
        }
        mLogoutRequest = null;
    }

    private void disposeUserDetailsRequest() {
        if (mUserDetailsRequest != null && !mUserDetailsRequest.isDisposed()) {
            mUserDetailsRequest.dispose();
        }
        mUserDetailsRequest = null;
    }

    private void notifyUserInfoUpdated() {
        AuthorizationView view = getView();
        if (view != null) {
            view.onUserInfoUpdated();
        }
        if (mUserInfoChangedCallback != null) {
            mUserInfoChangedCallback.run();
        }
    }

    private static final class QrState {
        private final String id;
        private final int status;
        private final String token;
        private final String accountName;

        private QrState(String id, int status, String token, String accountName) {
            this.id = id;
            this.status = status;
            this.token = token;
            this.accountName = accountName;
        }
    }
}
