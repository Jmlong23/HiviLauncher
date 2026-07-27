package com.hivi.launcher.account.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.hivi.launcher.BuildConfig;
import com.hivi.launcher.R;
import com.hivi.launcher.account.model.AuthorizedUserInfo;
import com.hivi.launcher.account.model.AuthorizationUiState;
import com.hivi.launcher.account.presenter.AuthorizationPresenter;
import com.hivi.launcher.utils.UiUtils;
import com.hivi.launcher.utils.network.AuthorizationStore;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Renders the account authorization, account information, and logout confirmation states.
 * Authorization requests and persistence remain in {@link AuthorizationPresenter}.
 */
public class AuthorizationDialog implements AuthorizationView {
    private static final String TAG = "AuthorizationDialog";
    private static final long SUCCESS_DISMISS_DELAY_MS = 1500L;
    private static final int AVATAR_CONNECT_TIMEOUT_MS = 8_000;
    private static final int AVATAR_READ_TIMEOUT_MS = 10_000;
    private static final int AVATAR_MAX_SIZE_PX = 512;

    public interface OnAuthorizationChangedListener {
        void onAuthorizationChanged();
    }

    private final Activity mActivity;
    private final OnAuthorizationChangedListener mAuthorizationChangedListener;

    private Dialog mDialog;
    private AuthorizationPresenter mPresenter;
    private View mAuthorizationFlowView;
    private View mSuccessView;
    private View mAccountInfoView;
    private View mExitConfirmationView;
    private ImageView mQrCodeView;
    private View mQrOverlayView;
    private ImageView mQrOverlayIconView;
    private TextView mQrOverlayTextView;
    private TextView mQrStatusView;
    private TextView mInstructionTitleView;
    private TextView mInstructionStep1View;
    private TextView mInstructionStep2View;
    private TextView mInstructionStep3View;
    private TextView mInstructionStep4View;
    private TextView mRefreshQrButton;
    private TextView mNotAuthorizeButton;
    private TextView mTitleView;
    private TextView mAccountAvatarView;
    private ImageView mAccountAvatarImageView;
    private TextView mAccountNameView;
    private TextView mAuthorizedAtView;
    private TextView mExitConfirmTitleView;
    private TextView mExitConfirmButton;
    private Runnable mSuccessDismissRunnable;
    private final ExecutorService mAvatarExecutor = Executors.newSingleThreadExecutor();
    private String mAvatarUrl = "";

    public AuthorizationDialog(Activity activity) {
        this(activity, null);
    }

    public AuthorizationDialog(Activity activity,
            OnAuthorizationChangedListener authorizationChangedListener) {
        mActivity = activity;
        mAuthorizationChangedListener = authorizationChangedListener;
    }

    public void show() {
        if (isShowing()) {
            return;
        }
        // The dialog content is recreated after each dismissal. Do not reuse the previous
        // content's avatar URL state, otherwise the new ImageView keeps its XML placeholder.
        mAvatarUrl = "";
        mDialog = new Dialog(mActivity);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(createContentView());
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnDismissListener(dialog -> stopPresenter());
        mDialog.show();
        configureWindow();

        mPresenter = new AuthorizationPresenter(mActivity, this, this::notifyAuthorizationChanged);
        mPresenter.start();
    }

    public boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    public void dismiss() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    public void release() {
        dismiss();
        mAvatarExecutor.shutdownNow();
    }

    @Override
    public void renderAuthorization(AuthorizationUiState state) {
        if (!isShowing() || state == null) {
            return;
        }
        switch (state.getPhase()) {
            case LOADING:
                showAuthorizationFlow();
                setQrCode(createQrCode("HiviLauncher", ui(170)));
                hideQrOverlay();
                setQrStatus(R.string.auth_status_loading);
                setInstruction(R.string.auth_instruction_waiting_title,
                        R.string.auth_step_scan, R.string.auth_step_confirm,
                        R.string.auth_step_success, 0);
                setAuthorizationActions(true);
                break;
            case WAITING_FOR_SCAN:
                showAuthorizationFlow();
                setQrCode(createQrCode(state.getQrPayload(), ui(170)));
                hideQrOverlay();
                setQrStatus(R.string.auth_status_waiting);
                setInstruction(R.string.auth_instruction_waiting_title,
                        R.string.auth_step_scan, R.string.auth_step_confirm,
                        R.string.auth_step_success, 0);
                setAuthorizationActions(true);
                break;
            case SCANNED:
                showAuthorizationFlow();
                showQrOverlay(0, R.string.auth_scanned_label);
                setQrStatus(R.string.auth_status_scanned);
                setInstruction(R.string.auth_instruction_scanned_title,
                        R.string.auth_step_scan, R.string.auth_step_confirm,
                        R.string.auth_step_success, R.string.auth_step_view_account);
                setAuthorizationActions(true);
                break;
            case AUTHORIZED:
                showSuccessState();
                notifyAuthorizationChanged();
                scheduleSuccessDismiss();
                break;
            case ACCOUNT_INFO:
                showAccountInfoState();
                break;
            case UNAUTHORIZED:
                notifyAuthorizationChanged();
                dismiss();
                break;
            case RETRYING:
                showRetryState(state.getRetryReason());
                break;
            case CANCELING:
                showExitConfirmationState();
                mExitConfirmTitleView.setText(R.string.auth_hint_canceling);
                mExitConfirmButton.setEnabled(false);
                break;
            case CANCEL_FAILED:
                showExitConfirmationState();
                mExitConfirmTitleView.setText(R.string.auth_hint_cancel_failed);
                mExitConfirmButton.setEnabled(true);
                break;
            default:
                break;
        }
    }

    @Override
    public void showToast(String message) {
        // Authorization uses explicit on-screen states rather than transient messages.
    }

    @Override
    public void onUserInfoUpdated() {
        if (isShowing() && mAccountInfoView.getVisibility() == View.VISIBLE) {
            showAccountInfoState();
        }
    }

    private View createContentView() {
        View root = LayoutInflater.from(mActivity).inflate(R.layout.dialog_authorization, null);
        ImageButton backButton = root.findViewById(R.id.authorization_back_button);
        backButton.setOnClickListener(view -> dismiss());

        mAuthorizationFlowView = root.findViewById(R.id.authorization_flow_content);
        mSuccessView = root.findViewById(R.id.authorization_success_content);
        mAccountInfoView = root.findViewById(R.id.authorization_account_info_content);
        mExitConfirmationView = root.findViewById(R.id.authorization_exit_confirmation_content);
        mQrCodeView = root.findViewById(R.id.authorization_qr_code);
        mQrOverlayView = root.findViewById(R.id.authorization_qr_overlay);
        mQrOverlayIconView = root.findViewById(R.id.authorization_qr_overlay_icon);
        mQrOverlayTextView = root.findViewById(R.id.authorization_qr_overlay_text);
        mQrStatusView = root.findViewById(R.id.authorization_qr_status);
        mInstructionTitleView = root.findViewById(R.id.authorization_instruction_title);
        mInstructionStep1View = root.findViewById(R.id.authorization_instruction_step_1);
        mInstructionStep2View = root.findViewById(R.id.authorization_instruction_step_2);
        mInstructionStep3View = root.findViewById(R.id.authorization_instruction_step_3);
        mInstructionStep4View = root.findViewById(R.id.authorization_instruction_step_4);
        mRefreshQrButton = root.findViewById(R.id.authorization_refresh_button);
        mNotAuthorizeButton = root.findViewById(R.id.authorization_not_now_button);
        mTitleView = root.findViewById(R.id.authorization_title);
        mAccountAvatarView = root.findViewById(R.id.authorization_account_avatar);
        mAccountAvatarImageView = root.findViewById(R.id.authorization_account_avatar_image);
        root.findViewById(R.id.authorization_account_avatar_container).setClipToOutline(true);
        mAccountNameView = root.findViewById(R.id.authorization_account_name);
        mAuthorizedAtView = root.findViewById(R.id.authorization_account_time);
        mExitConfirmTitleView = root.findViewById(R.id.authorization_exit_confirm_title);
        mExitConfirmButton = root.findViewById(R.id.authorization_exit_confirm_button);

        mRefreshQrButton.setOnClickListener(view -> {
            if (mPresenter != null) {
                mPresenter.refreshQrCode();
            }
        });
        mNotAuthorizeButton.setOnClickListener(view -> dismiss());
        root.findViewById(R.id.authorization_exit_button).setOnClickListener(
                view -> showExitConfirmationState());
        mExitConfirmButton.setOnClickListener(view -> {
            if (mPresenter != null) {
                mPresenter.cancelAuthorization();
            }
        });
        root.findViewById(R.id.authorization_exit_cancel_button).setOnClickListener(view -> {
            if (mPresenter != null) {
                mPresenter.showAccountInfo();
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
        window.setDimAmount(0.58f);
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
        window.setLayout(
                Math.min(UiUtils.dp(mActivity, 731), maxWidth),
                Math.min(UiUtils.dp(mActivity, 374), maxHeight));
        window.setGravity(Gravity.CENTER);
    }

    private void showAuthorizationFlow() {
        cancelSuccessDismiss();
        setVisibleContent(mAuthorizationFlowView);
        mTitleView.setText(R.string.auth_dialog_title);
        updateBackButtonEnabled(true);
    }

    private void showSuccessState() {
        setVisibleContent(mSuccessView);
        mTitleView.setText(R.string.auth_user_info_title);
        updateBackButtonEnabled(false);
    }

    private void showAccountInfoState() {
        cancelSuccessDismiss();
        setVisibleContent(mAccountInfoView);
        mTitleView.setText(R.string.auth_user_info_title);
        updateBackButtonEnabled(true);
        String accountName = AuthorizationStore.getAccountName(mActivity);
        if (TextUtils.isEmpty(accountName)) {
            accountName = mActivity.getString(R.string.auth_authorized_fallback);
        }
        mAccountNameView.setText(accountName);
        AuthorizedUserInfo userInfo = AuthorizationStore.getUserInfo(mActivity);
        String avatarUrl = userInfo == null ? "" : userInfo.getAvatarUrl();
        loadAccountAvatar(avatarUrl, accountName);
        long authorizedAt = AuthorizationStore.getAuthorizedAt(mActivity);
        String authorizedAtText = authorizedAt > 0L
                ? new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(new Date(authorizedAt))
                : mActivity.getString(R.string.auth_time_unknown);
        mAuthorizedAtView.setText(mActivity.getString(R.string.auth_authorized_time,
                authorizedAtText));
    }

    private void loadAccountAvatar(String avatarUrl, String accountName) {
        mAccountAvatarView.setText(accountName.substring(0, 1).toUpperCase(Locale.getDefault()));
        String normalizedAvatarUrl = avatarUrl == null ? "" : avatarUrl.trim();
        if (TextUtils.equals(mAvatarUrl, normalizedAvatarUrl)) {
            if (TextUtils.isEmpty(normalizedAvatarUrl)) {
                mAccountAvatarImageView.setImageBitmap(null);
                mAccountAvatarImageView.setVisibility(View.GONE);
                mAccountAvatarView.setVisibility(View.VISIBLE);
            }
            logAvatarDebug("Skip avatar download because the current content already uses this "
                    + "avatar state. avatarUrlPresent=" + !TextUtils.isEmpty(normalizedAvatarUrl));
            return;
        }

        if (TextUtils.isEmpty(normalizedAvatarUrl)) {
            mAvatarUrl = "";
            mAccountAvatarImageView.setImageBitmap(null);
            mAccountAvatarImageView.setVisibility(View.GONE);
            mAccountAvatarView.setVisibility(View.VISIBLE);
            logAvatarDebug("Avatar URL is empty; display the text placeholder.");
            return;
        }
        mAvatarUrl = normalizedAvatarUrl;
        mAccountAvatarView.setVisibility(View.GONE);
        mAccountAvatarImageView.setImageBitmap(null);
        mAccountAvatarImageView.setVisibility(View.GONE);
        final String requestedAvatarUrl = normalizedAvatarUrl;
        mAvatarExecutor.execute(() -> {
            Bitmap avatarBitmap = downloadAvatar(requestedAvatarUrl);
            mActivity.runOnUiThread(() -> {
                if (!isShowing() || mAccountInfoView.getVisibility() != View.VISIBLE
                        || !TextUtils.equals(mAvatarUrl, requestedAvatarUrl)) {
                    return;
                }
                if (avatarBitmap == null) {
                    logAvatarDebug("Avatar download did not produce a bitmap.");
                    return;
                }
                mAccountAvatarImageView.setImageBitmap(avatarBitmap);
                mAccountAvatarImageView.setVisibility(View.VISIBLE);
                mAccountAvatarView.setVisibility(View.GONE);
            });
        });
    }

    private Bitmap downloadAvatar(String avatarUrl) {
        Uri uri = Uri.parse(avatarUrl);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            logAvatarDebug("Avatar URL has an unsupported scheme.");
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            decodeAvatar(avatarUrl, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                logAvatarDebug("Avatar bounds could not be decoded.");
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateAvatarInSampleSize(bounds);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return decodeAvatar(avatarUrl, options);
        } catch (IOException e) {
            logAvatarDebug("Avatar download failed. reason=" + e.getClass().getSimpleName());
            return null;
        }
    }

    private Bitmap decodeAvatar(String avatarUrl, BitmapFactory.Options options) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(avatarUrl).openConnection();
        connection.setConnectTimeout(AVATAR_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(AVATAR_READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("avatar response code=" + responseCode);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(inputStream, null, options);
            }
        } finally {
            connection.disconnect();
        }
    }

    private int calculateAvatarInSampleSize(BitmapFactory.Options options) {
        int sampleSize = 1;
        while (options.outWidth / sampleSize > AVATAR_MAX_SIZE_PX
                || options.outHeight / sampleSize > AVATAR_MAX_SIZE_PX) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private void logAvatarDebug(String message) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message);
        }
    }

    private void showExitConfirmationState() {
        cancelSuccessDismiss();
        mExitConfirmationView.setVisibility(View.VISIBLE);
        mTitleView.setText(R.string.auth_user_info_title);
        updateBackButtonEnabled(true);
        mExitConfirmTitleView.setText(R.string.auth_exit_confirm_title);
        mExitConfirmButton.setEnabled(true);
    }

    private void showRetryState(AuthorizationUiState.RetryReason reason) {
        showAuthorizationFlow();
        if (reason == AuthorizationUiState.RetryReason.EXPIRED) {
            showQrOverlay(R.drawable.ic_refresh, 0);
            setQrStatus(R.string.auth_status_expired);
            setInstruction(R.string.auth_instruction_expired_title,
                    R.string.auth_step_refresh, R.string.auth_step_refresh_scan, 0, 0);
        } else if (reason == AuthorizationUiState.RetryReason.NETWORK) {
            showQrOverlay(R.drawable.ic_wifi_off, 0);
            setQrStatus(R.string.auth_status_network);
            setInstruction(R.string.auth_instruction_network_title,
                    R.string.auth_step_network_check, R.string.auth_step_network_refresh,
                    R.string.auth_step_network_back, 0);
        } else {
            hideQrOverlay();
            setQrStatus(R.string.auth_hint6);
            setInstruction(R.string.auth_instruction_waiting_title,
                    R.string.auth_step_scan, R.string.auth_step_confirm,
                    R.string.auth_step_success, 0);
        }
        setAuthorizationActions(false);
    }

    private void setVisibleContent(View visibleContent) {
        mAuthorizationFlowView.setVisibility(visibleContent == mAuthorizationFlowView
                ? View.VISIBLE : View.GONE);
        mSuccessView.setVisibility(visibleContent == mSuccessView ? View.VISIBLE : View.GONE);
        mAccountInfoView.setVisibility(visibleContent == mAccountInfoView
                ? View.VISIBLE : View.GONE);
        mExitConfirmationView.setVisibility(visibleContent == mExitConfirmationView
                ? View.VISIBLE : View.GONE);
    }

    private void updateBackButtonEnabled(boolean enabled) {
        View backButton = mDialog == null ? null
                : mDialog.findViewById(R.id.authorization_back_button);
        if (backButton != null) {
            backButton.setEnabled(enabled);
            backButton.setAlpha(enabled ? 1f : 0.45f);
        }
    }

    private void setQrCode(Bitmap bitmap) {
        mQrCodeView.setAlpha(1f);
        mQrCodeView.setImageBitmap(bitmap);
    }

    private void hideQrOverlay() {
        mQrCodeView.setAlpha(1f);
        mQrOverlayView.setVisibility(View.GONE);
    }

    private void showQrOverlay(int iconRes, int textRes) {
        mQrCodeView.setAlpha(0.48f);
        mQrOverlayView.setVisibility(View.VISIBLE);
        mQrOverlayIconView.setVisibility(iconRes == 0 ? View.GONE : View.VISIBLE);
        if (iconRes != 0) {
            mQrOverlayIconView.setImageResource(iconRes);
        }
        mQrOverlayTextView.setVisibility(textRes == 0 ? View.GONE : View.VISIBLE);
        if (textRes != 0) {
            mQrOverlayTextView.setText(textRes);
        }
    }

    private void setQrStatus(int stringRes) {
        mQrStatusView.setText(stringRes);
    }

    private void setInstruction(int titleRes, int step1Res, int step2Res, int step3Res,
            int step4Res) {
        mInstructionTitleView.setText(titleRes);
        setInstructionStep(mInstructionStep1View, step1Res);
        setInstructionStep(mInstructionStep2View, step2Res);
        setInstructionStep(mInstructionStep3View, step3Res);
        setInstructionStep(mInstructionStep4View, step4Res);
    }

    private void setInstructionStep(TextView view, int stringRes) {
        if (stringRes == 0) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(stringRes);
        }
    }

    private void setAuthorizationActions(boolean enabled) {
        mRefreshQrButton.setEnabled(enabled);
        mNotAuthorizeButton.setEnabled(enabled);
    }

    private void scheduleSuccessDismiss() {
        cancelSuccessDismiss();
        mSuccessDismissRunnable = new Runnable() {
            @Override
            public void run() {
                dismiss();
            }
        };
        mSuccessView.postDelayed(mSuccessDismissRunnable, SUCCESS_DISMISS_DELAY_MS);
    }

    private void cancelSuccessDismiss() {
        if (mSuccessDismissRunnable != null) {
            mSuccessView.removeCallbacks(mSuccessDismissRunnable);
            mSuccessDismissRunnable = null;
        }
    }

    private void notifyAuthorizationChanged() {
        if (mAuthorizationChangedListener != null) {
            mAuthorizationChangedListener.onAuthorizationChanged();
        }
    }

    private void stopPresenter() {
        cancelSuccessDismiss();
        if (mPresenter != null) {
            mPresenter.detach();
            mPresenter = null;
        }
        mDialog = null;
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
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int ui(int value) {
        return UiUtils.ui(mActivity, value);
    }
}
