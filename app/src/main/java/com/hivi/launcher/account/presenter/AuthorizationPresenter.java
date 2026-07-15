package com.hivi.launcher.account.presenter;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

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

    private final Context mContext;
    private final ApiService mApiService;
    private Disposable mQrRequest;
    private Disposable mLogoutRequest;
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
        super(view);
        mContext = context.getApplicationContext();
        mApiService = NetworkManager.getApiService();
    }

    public void start() {
        if (AuthorizationStore.hasToken(mContext)) {
            render(AuthorizationUiState.authorized());
        } else {
            requestNewQrCode();
        }
    }

    public void cancelAuthorization() {
        if (mLogoutInProgress || !AuthorizationStore.hasToken(mContext)) {
            return;
        }
        mLogoutInProgress = true;
        render(AuthorizationUiState.canceling());
        disposeLogoutRequest();
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
                                requestNewQrCode();
                            } else {
                                handleCancelAuthorizationFailure();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Unable to parse cancel authorization response", e);
                            handleCancelAuthorizationFailure();
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        mLogoutRequest = null;
                        if (isViewAttached()) {
                            Log.e(TAG, "Unable to cancel authorization", throwable);
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
                    Log.e(TAG, "Unable to parse QR response", e);
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
            AuthorizationStore.saveToken(mContext, state.token);
            removeUiThreadRunnable(mPollRunnable);
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
        return new QrState(data.optString("id"), data.optInt("status"), data.optString("token"));
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

    private static final class QrState {
        private final String id;
        private final int status;
        private final String token;

        private QrState(String id, int status, String token) {
            this.id = id;
            this.status = status;
            this.token = token;
        }
    }
}
