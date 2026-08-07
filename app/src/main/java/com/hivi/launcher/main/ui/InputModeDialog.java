package com.hivi.launcher.main.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import com.hivi.launcher.utils.log.AppLog;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.hivi.launcher.R;
import com.hivi.launcher.databinding.DialogInputModeBinding;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.utils.UiUtils;

/**
 * Displays the full input-mode selector opened from the top mode indicator.
 */
final class InputModeDialog {
    private static final String TAG = "InputModeDialog";

    interface Listener {
        void onModeSelected(MainPage page);
    }

    private final Activity mActivity;
    private final Listener mListener;

    private Dialog mDialog;
    private DialogInputModeBinding mBinding;
    private MainPage mSelectedPage;

    InputModeDialog(Activity activity, Listener listener) {
        mActivity = activity;
        mListener = listener;
    }

    void show(MainPage selectedPage, boolean bluetoothConnected, boolean wifiConnected) {
        if (isShowing()) {
            updateState(selectedPage, bluetoothConnected, wifiConnected);
            return;
        }

        mDialog = new Dialog(mActivity);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mBinding = DialogInputModeBinding.inflate(mActivity.getLayoutInflater());
        mDialog.setContentView(mBinding.getRoot());
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnDismissListener(dialog -> {
            mBinding = null;
            mDialog = null;
        });
        bindListeners();
        updateState(selectedPage, bluetoothConnected, wifiConnected);
        mDialog.show();
        configureWindow();
    }

    void updateState(MainPage selectedPage, boolean bluetoothConnected, boolean wifiConnected) {
        if (mBinding == null) {
            return;
        }
        mSelectedPage = selectedPage;
        updateCardBackgrounds();
        updateConnectionStatus(bluetoothConnected, wifiConnected);
    }

    void dismiss() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    private boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    private void bindListeners() {
        mBinding.inputModeDialogBack.setOnClickListener(view -> dismiss());
        bindModeClick(mBinding.inputModeDialogLine, MainPage.LINE);
        bindModeClick(mBinding.inputModeDialogMicrophone, MainPage.MICROPHONE);
        bindModeClick(mBinding.inputModeDialogOptical, MainPage.OPTICAL);
        bindModeClick(mBinding.inputModeDialogCoax, MainPage.COAX);
        bindModeClick(mBinding.inputModeDialogHdmi, MainPage.HDMI);
        bindModeClick(mBinding.inputModeDialogBluetooth, MainPage.BLUETOOTH);
        bindModeClick(mBinding.inputModeDialogWifi, MainPage.WIFI);
    }

    private void bindModeClick(View card, MainPage page) {
        card.setOnClickListener(view -> {
            AppLog.i(TAG, "Input mode dialog selected: " + page);
            dismiss();
            mListener.onModeSelected(page);
        });
    }

    private void updateCardBackgrounds() {
        updateCardBackground(mBinding.inputModeDialogLineBackground, MainPage.LINE,
                R.drawable.card_mode_line, R.drawable.card_mode_line_selected);
        updateCardBackground(mBinding.inputModeDialogMicrophoneBackground, MainPage.MICROPHONE,
                R.drawable.card_mode_microphone, R.drawable.card_mode_microphone_selected);
        updateCardBackground(mBinding.inputModeDialogOpticalBackground, MainPage.OPTICAL,
                R.drawable.card_mode_optical, R.drawable.card_mode_optical_selected);
        updateCardBackground(mBinding.inputModeDialogCoaxBackground, MainPage.COAX,
                R.drawable.card_mode_coax, R.drawable.card_mode_coax_selected);
        updateCardBackground(mBinding.inputModeDialogHdmiBackground, MainPage.HDMI,
                R.drawable.card_mode_hdmi, R.drawable.card_mode_hdmi_selected);
        updateCardBackground(mBinding.inputModeDialogBluetoothBackground, MainPage.BLUETOOTH,
                R.drawable.card_mode_bluetooth, R.drawable.card_mode_bluetooth_selected);
        updateCardBackground(mBinding.inputModeDialogWifiBackground, MainPage.WIFI,
                R.drawable.card_mode_wifi, R.drawable.card_mode_wifi_selected);
    }

    private void updateCardBackground(android.widget.ImageView background, MainPage page,
            int defaultBackgroundResId, int selectedBackgroundResId) {
        background.setImageResource(mSelectedPage == page
                ? selectedBackgroundResId : defaultBackgroundResId);
    }

    private void updateConnectionStatus(boolean bluetoothConnected, boolean wifiConnected) {
        updateConnectionStatus(mBinding.inputModeDialogBluetoothStatus, bluetoothConnected);
        updateConnectionStatus(mBinding.inputModeDialogWifiStatus, wifiConnected);
    }

    private void updateConnectionStatus(TextView status, boolean connected) {
        status.setText(connected ? R.string.input_mode_status_connected
                : R.string.input_mode_status_disconnected);
        status.setTextColor(mActivity.getColor(connected
                ? R.color.status_connected : R.color.text_color));
    }

    private void configureWindow() {
        Window window = mDialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0.68f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        int maxWidth = mActivity.getResources().getDisplayMetrics().widthPixels - UiUtils.dp(mActivity, 48);
        int maxHeight = mActivity.getResources().getDisplayMetrics().heightPixels
                - UiUtils.dp(mActivity, 48);
        window.setLayout(Math.min(UiUtils.dp(mActivity, 765), maxWidth),
                Math.min(UiUtils.dp(mActivity, 373), maxHeight));
        window.setGravity(Gravity.CENTER);
    }
}
