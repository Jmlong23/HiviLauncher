package com.hivi.launcher.main.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;

import com.hivi.launcher.R;
import com.hivi.launcher.databinding.DialogVolumeBinding;
import com.hivi.launcher.utils.UiUtils;

public final class VolumeDialog {
    private static final int VOLUME_ADJUST_LOWER = -1;
    private static final int VOLUME_ADJUST_RAISE = 1;

    public interface Listener {
        void onVolumeAdjusted(int direction);

        void onVolumeChanged(int volumePercent);

        void onMuteToggleRequested();

        void onDialogDismissed();
    }

    private final Activity mActivity;
    private final Listener mListener;
    private Dialog mDialog;
    private DialogVolumeBinding mBinding;

    public VolumeDialog(Activity activity, Listener listener) {
        mActivity = activity;
        mListener = listener;
    }

    public void show(int volumePercent, boolean muted) {
        if (isShowing()) {
            updateVolume(volumePercent, muted);
            return;
        }

        mDialog = new Dialog(mActivity);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mBinding = DialogVolumeBinding.inflate(mActivity.getLayoutInflater());
        mDialog.setContentView(mBinding.getRoot());
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnDismissListener(dialog -> {
            mBinding = null;
            mDialog = null;
            mListener.onDialogDismissed();
        });
        bindListeners();
        updateVolume(volumePercent, muted);
        mDialog.show();
        configureWindow();
    }

    public void updateVolume(int volumePercent, boolean muted) {
        if (mBinding == null) {
            return;
        }
        int clampedVolume = Math.max(0, Math.min(100, volumePercent));
        int displayedVolume = muted ? 0 : clampedVolume;
        mBinding.volumeValue.setText(String.valueOf(displayedVolume));
        mBinding.volumeSeekBar.setProgress(displayedVolume);
        mBinding.volumeDownButton.setEnabled(displayedVolume > 0);
        mBinding.volumeUpButton.setEnabled(displayedVolume < 100);
        mBinding.volumeMuteButton.setSelected(muted);
        mBinding.volumeMuteButton.setContentDescription(mActivity.getString(
                muted ? R.string.main_volume_unmute : R.string.main_volume_mute));
    }

    public void dismiss() {
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    public boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    private void bindListeners() {
        mBinding.volumeMuteButton.setOnClickListener(view -> mListener.onMuteToggleRequested());
        mBinding.volumeDownButton.setOnClickListener(
                view -> mListener.onVolumeAdjusted(VOLUME_ADJUST_LOWER));
        mBinding.volumeUpButton.setOnClickListener(
                view -> mListener.onVolumeAdjusted(VOLUME_ADJUST_RAISE));
        mBinding.volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mListener.onVolumeChanged(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // The volume is applied continuously while the user drags the slider.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // The final value has already been applied in onProgressChanged.
            }
        });
    }

    private void configureWindow() {
        Window window = mDialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.68f;
        window.setAttributes(attributes);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        window.setLayout(UiUtils.dp(mActivity, 500), UiUtils.dp(mActivity, 120));
    }
}
