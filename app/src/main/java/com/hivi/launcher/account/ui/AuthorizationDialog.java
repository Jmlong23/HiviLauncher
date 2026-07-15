package com.hivi.launcher.account.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.hivi.launcher.R;
import com.hivi.launcher.utils.UiUtils;
import com.hivi.launcher.utils.network.ApiService;
import com.hivi.launcher.utils.network.AuthorizationStore;
import com.hivi.launcher.utils.network.NetworkCallback;
import com.hivi.launcher.utils.network.NetworkManager;
import com.ljm.audiotoollib.upnpserver.entity.SWDeviceStatus;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

public class AuthorizationDialog {
    private static final String TAG = "AuthorizationDialog";
    private static final int QR_STATUS_WAITING = 1;
    private static final int QR_STATUS_SCANNED = 2;
    private static final int QR_STATUS_CONFIRMED = 3;
    private static final int QR_STATUS_CANCELLED = 4;
    private static final int QR_STATUS_EXPIRED = 5;
    private static final int MAX_POLL_COUNT = 300;
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final long RETRY_DELAY_MS = 1600L;

    private final Activity mActivity;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ApiService mApiService = NetworkManager.getApiService();

    private Disposable mQrRequest;
    private Disposable mLogoutRequest;
    private Dialog mDialog;
    private ImageView mQrCodeView;
    private TextView mHintView;
    private TextView mCancelAuthorizationButton;
    private String mQrId;
    private int mPollCount;
    private boolean mStopped;
    private boolean mLogoutInProgress;

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mStopped || mQrId == null) {
                return;
            }
            if (mPollCount >= MAX_POLL_COUNT) {
                reloadQrCode(mActivity.getString(R.string.auth_hint5));
                return;
            }
            mPollCount++;
            requestQrState(mQrId);
        }
    };

    public AuthorizationDialog(Activity activity) {
        mActivity = activity;
    }

    public void show() {
        if (isShowing()) {
            return;
        }
        mStopped = false;
        mDialog = new Dialog(mActivity);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(createContentView());
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnDismissListener(dialog -> stopRequests());
        mDialog.show();
        configureWindow();

        if (hasAuthorization()) {
            showAuthorizedState();
        } else {
            requestNewQrCode();
        }
    }

    public boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    public void dismiss() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    private View createContentView() {
        View root = LayoutInflater.from(mActivity).inflate(R.layout.dialog_authorization, null);
        mQrCodeView = root.findViewById(R.id.authorization_qr_code);
        mHintView = root.findViewById(R.id.authorization_hint);
        mCancelAuthorizationButton = root.findViewById(R.id.authorization_cancel_button);
        mCancelAuthorizationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelAuthorization();
            }
        });
        return root;
    }

    private void configureWindow() {
        Window window = mDialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        int maxWidth = mActivity.getResources().getDisplayMetrics().widthPixels - ui(48);
        int maxHeight = mActivity.getResources().getDisplayMetrics().heightPixels - ui(48);
        window.setLayout(Math.min(ui(1004), maxWidth), Math.min(ui(618), maxHeight));
        window.setGravity(Gravity.CENTER);
    }

    private void requestNewQrCode() {
        if (mStopped) {
            return;
        }
        mMainHandler.removeCallbacks(mPollRunnable);
        mQrId = null;
        mPollCount = 0;
        mCancelAuthorizationButton.setVisibility(View.GONE);
        mQrCodeView.setImageBitmap(createLoadingQrCode());
        mQrCodeView.setVisibility(View.VISIBLE);
        updateHint(mActivity.getString(R.string.auth_hint2));
        requestQrState(null);
    }

    private void requestQrState(final String qrId) {
        disposeQrRequest();
        Observable<String> request = qrId == null ? mApiService.getQr() : mApiService.getQr(qrId);
        mQrRequest = NetworkManager.execute(request, new NetworkCallback<String>() {
            @Override
            public void onSuccess(String response) {
                try {
                    handleQrState(readQrState(response), qrId == null);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to parse QR response", e);
                    handleRequestFailure();
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                handleRequestFailure();
            }
        });
    }

    private QrState readQrState(String responseText) throws Exception {
        JSONObject response = new JSONObject(responseText);
        JSONObject data = response.optJSONObject("data");
        if (data == null) {
            throw new IllegalStateException("Invalid authorization response");
        }
        return new QrState(data.optString("id"), data.optInt("status"), data.optString("token"));
    }

    private void handleQrState(QrState state, boolean isInitialRequest) {
        if (mStopped || !isShowing()) {
            return;
        }
        if (state.status == QR_STATUS_WAITING && !isEmpty(state.id)) {
            mQrId = state.id;
            mQrCodeView.setImageBitmap(createQrCode(state.id + "&MG100"
                    + SWDeviceStatus.getUUID().toString(), ui(170)));
            mQrCodeView.setVisibility(View.VISIBLE);
            updateHint(mActivity.getString(R.string.auth_hint1));
            mMainHandler.removeCallbacks(mPollRunnable);
            mMainHandler.postDelayed(mPollRunnable, POLL_INTERVAL_MS);
            return;
        }
        if (state.status == QR_STATUS_SCANNED) {
            mQrCodeView.setVisibility(View.INVISIBLE);
            updateHint(mActivity.getString(R.string.auth_hint3));
            mMainHandler.postDelayed(mPollRunnable, POLL_INTERVAL_MS);
            return;
        }
        if (state.status == QR_STATUS_CONFIRMED && !isEmpty(state.token)) {
            saveAuthorization(state.token);
            mMainHandler.removeCallbacks(mPollRunnable);
            showAuthorizedState(mActivity.getString(R.string.auth_hint4));
            return;
        }
        if (state.status == QR_STATUS_CANCELLED) {
            reloadQrCode(mActivity.getString(R.string.auth_hint6));
            return;
        }
        if (state.status == QR_STATUS_EXPIRED) {
            reloadQrCode(mActivity.getString(R.string.auth_hint5));
            return;
        }
        if (isInitialRequest) {
            handleRequestFailure();
        }
    }

    private void handleRequestFailure() {
        if (mStopped || !isShowing()) {
            return;
        }
        mQrCodeView.setVisibility(View.INVISIBLE);
        updateHint(mActivity.getString(R.string.auth_hint7));
        mMainHandler.removeCallbacks(mPollRunnable);
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                requestNewQrCode();
            }
        }, RETRY_DELAY_MS);
    }

    private void reloadQrCode(String hint) {
        if (mStopped || !isShowing()) {
            return;
        }
        mQrCodeView.setVisibility(View.INVISIBLE);
        updateHint(hint);
        mMainHandler.removeCallbacks(mPollRunnable);
        mMainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                requestNewQrCode();
            }
        }, RETRY_DELAY_MS);
    }

    private void showAuthorizedState() {
        showAuthorizedState(mActivity.getString(R.string.auth_hint8));
    }

    private void showAuthorizedState(String hint) {
        mQrCodeView.setVisibility(View.INVISIBLE);
        updateHint(hint);
        mCancelAuthorizationButton.setEnabled(true);
        mCancelAuthorizationButton.setVisibility(View.VISIBLE);
    }

    private void cancelAuthorization() {
        if (mStopped || !isShowing() || mLogoutInProgress || !hasAuthorization()) {
            return;
        }
        mLogoutInProgress = true;
        mCancelAuthorizationButton.setEnabled(false);
        updateHint(mActivity.getString(R.string.auth_hint_canceling));
        mLogoutRequest = NetworkManager.execute(
                mApiService.qrLogout(SWDeviceStatus.getUUID().toString()),
                new NetworkCallback<String>() {
                    @Override
                    public void onSuccess(String response) {
                        mLogoutRequest = null;
                        if (mStopped || !isShowing()) {
                            return;
                        }
                        try {
                            if (isLogoutSuccess(response)
                                    || shouldTreatLogoutFailureAsCancelled(response)) {
                                finishCancelAuthorization();
                            } else {
                                Log.e(TAG, "Cancel authorization was rejected: " + response);
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
                        if (mStopped || !isShowing()) {
                            return;
                        }
                        Log.e(TAG, "Unable to cancel authorization", throwable);
                        handleCancelAuthorizationFailure();
                    }
                });
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

    private void finishCancelAuthorization() {
        AuthorizationStore.clearToken(mActivity);
        mLogoutInProgress = false;
        requestNewQrCode();
    }

    private void handleCancelAuthorizationFailure() {
        mLogoutInProgress = false;
        mCancelAuthorizationButton.setEnabled(true);
        updateHint(mActivity.getString(R.string.auth_hint_cancel_failed));
    }

    private boolean hasAuthorization() {
        return AuthorizationStore.hasToken(mActivity);
    }

    private void saveAuthorization(String token) {
        AuthorizationStore.saveToken(mActivity, token);
    }

    private void stopRequests() {
        mStopped = true;
        mQrId = null;
        mLogoutInProgress = false;
        mMainHandler.removeCallbacksAndMessages(null);
        disposeQrRequest();
        disposeLogoutRequest();
        mDialog = null;
    }

    private void updateHint(String hint) {
        if (mHintView != null) {
            mHintView.setText(hint);
        }
    }

    private Bitmap createLoadingQrCode() {
        return createQrCode("HiviLauncher", ui(170));
    }

    private Bitmap createQrCode(String value, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE,
                    size, size, hints);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int offset = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? 0xff303030 : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
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

    private int ui(int value) {
        return UiUtils.ui(mActivity, value);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
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
