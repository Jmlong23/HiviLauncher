package com.hivi.launcher.account.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import com.hivi.launcher.account.model.AuthorizationUiState;
import com.hivi.launcher.account.presenter.AuthorizationPresenter;
import com.hivi.launcher.utils.UiUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders the account authorization flow. Networking, polling, and token persistence belong to
 * {@link AuthorizationPresenter}; this class only manages the dialog and QR bitmap.
 */
public class AuthorizationDialog implements AuthorizationView {
    private final Activity mActivity;

    private Dialog mDialog;
    private AuthorizationPresenter mPresenter;
    private ImageView mQrCodeView;
    private TextView mHintView;
    private TextView mCancelAuthorizationButton;

    public AuthorizationDialog(Activity activity) {
        mActivity = activity;
    }

    public void show() {
        if (isShowing()) {
            return;
        }
        mDialog = new Dialog(mActivity);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(createContentView());
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnDismissListener(dialog -> stopPresenter());
        mDialog.show();
        configureWindow();

        mPresenter = new AuthorizationPresenter(mActivity, this);
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

    @Override
    public void renderAuthorization(AuthorizationUiState state) {
        if (!isShowing() || state == null) {
            return;
        }
        switch (state.getPhase()) {
            case LOADING:
                mCancelAuthorizationButton.setVisibility(View.GONE);
                mQrCodeView.setImageBitmap(createQrCode("HiviLauncher", ui(170)));
                mQrCodeView.setVisibility(View.VISIBLE);
                updateHint(R.string.auth_hint2);
                break;
            case WAITING_FOR_SCAN:
                mCancelAuthorizationButton.setVisibility(View.GONE);
                mQrCodeView.setImageBitmap(createQrCode(state.getQrPayload(), ui(170)));
                mQrCodeView.setVisibility(View.VISIBLE);
                updateHint(R.string.auth_hint1);
                break;
            case SCANNED:
                mQrCodeView.setVisibility(View.INVISIBLE);
                updateHint(R.string.auth_hint3);
                break;
            case AUTHORIZED:
                mQrCodeView.setVisibility(View.INVISIBLE);
                mCancelAuthorizationButton.setEnabled(true);
                mCancelAuthorizationButton.setVisibility(View.VISIBLE);
                updateHint(R.string.auth_hint4);
                break;
            case RETRYING:
                mQrCodeView.setVisibility(View.INVISIBLE);
                mCancelAuthorizationButton.setVisibility(View.GONE);
                updateRetryHint(state.getRetryReason());
                break;
            case CANCELING:
                mCancelAuthorizationButton.setEnabled(false);
                mCancelAuthorizationButton.setVisibility(View.VISIBLE);
                updateHint(R.string.auth_hint_canceling);
                break;
            case CANCEL_FAILED:
                mCancelAuthorizationButton.setEnabled(true);
                mCancelAuthorizationButton.setVisibility(View.VISIBLE);
                updateHint(R.string.auth_hint_cancel_failed);
                break;
            default:
                break;
        }
    }

    @Override
    public void showToast(String message) {
        // The authorization dialog uses explicit state messages instead of transient toasts.
    }

    private View createContentView() {
        View root = LayoutInflater.from(mActivity).inflate(R.layout.dialog_authorization, null);
        mQrCodeView = root.findViewById(R.id.authorization_qr_code);
        mHintView = root.findViewById(R.id.authorization_hint);
        mCancelAuthorizationButton = root.findViewById(R.id.authorization_cancel_button);
        mCancelAuthorizationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mPresenter != null) {
                    mPresenter.cancelAuthorization();
                }
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

    private void updateRetryHint(AuthorizationUiState.RetryReason reason) {
        if (reason == AuthorizationUiState.RetryReason.EXPIRED) {
            updateHint(R.string.auth_hint5);
        } else if (reason == AuthorizationUiState.RetryReason.CANCELED) {
            updateHint(R.string.auth_hint6);
        } else {
            updateHint(R.string.auth_hint7);
        }
    }

    private void updateHint(int stringRes) {
        if (mHintView != null) {
            mHintView.setText(stringRes);
        }
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

    private void stopPresenter() {
        if (mPresenter != null) {
            mPresenter.detach();
            mPresenter = null;
        }
        mDialog = null;
    }

    private int ui(int value) {
        return UiUtils.ui(mActivity, value);
    }
}
