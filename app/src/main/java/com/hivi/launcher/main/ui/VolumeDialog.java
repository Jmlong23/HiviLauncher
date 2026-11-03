package com.hivi.launcher.main.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;
import android.widget.SeekBar;

import com.hivi.launcher.R;
import com.hivi.launcher.databinding.DialogVolumeBinding;

public final class VolumeDialog {
    private static final int VOLUME_ADJUST_LOWER = -1;
    private static final int VOLUME_ADJUST_RAISE = 1;
    /** Raising the volume past this value asks for an explicit confirmation (test issue #12). */
    private static final int VOLUME_PROTECT_THRESHOLD = 80;

    public interface Listener {
        void onVolumeAdjusted(int direction);

        void onVolumeChanged(int volumePercent);

        void onMuteToggleRequested();

        void onDialogDismissed();
    }

    private final Context mContext;
    private final Listener mListener;
    private Dialog mDialog;
    private DialogVolumeBinding mBinding;
    private AlertDialog mOverLimitDialog;
    /** Last volume actually applied to the amplifier; values beyond it need confirmation. */
    private int mAppliedVolume = -1;

    public VolumeDialog(Context context, Listener listener) {
        mContext = context;
        mListener = listener;
    }

    public void show(int volumePercent, boolean muted) {
        if (isShowing()) {
            updateVolume(volumePercent, muted);
            return;
        }

        mDialog = new Dialog(mContext);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mBinding = DialogVolumeBinding.inflate(LayoutInflater.from(mContext));
        mDialog.setContentView(mBinding.getRoot());
        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnDismissListener(dialog -> {
            dismissOverLimitDialog();
            mBinding = null;
            mDialog = null;
            mListener.onDialogDismissed();
        });
        bindListeners();
        updateVolume(volumePercent, muted);
        configureWindow();
        mDialog.show();
    }

    public void updateVolume(int volumePercent, boolean muted) {
        if (mBinding == null) {
            return;
        }
        int clampedVolume = Math.max(0, Math.min(100, volumePercent));
        int displayedVolume = muted ? 0 : clampedVolume;
        mAppliedVolume = displayedVolume;
        mBinding.volumeValue.setText(String.valueOf(displayedVolume));
        // Volume zero doubles as the muted visual state (test issue #15).
        mBinding.volumeValue.setAlpha(displayedVolume == 0 ? 0.4f : 1f);
        mBinding.volumeValue.setSelected(displayedVolume == 0);
        mBinding.volumeSeekBar.setProgress(displayedVolume);
        mBinding.volumeDownButton.setEnabled(displayedVolume > 0);
        mBinding.volumeUpButton.setEnabled(displayedVolume < 100);
        mBinding.volumeMuteButton.setSelected(muted);
        mBinding.volumeMuteButton.setContentDescription(mContext.getString(
                muted ? R.string.main_volume_unmute : R.string.main_volume_mute));
    }

    public void dismiss() {
        dismissOverLimitDialog();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    public boolean isShowing() {
        return mDialog != null && mDialog.isShowing();
    }

    private void bindListeners() {
        mBinding.volumeMuteButton.setOnClickListener(view -> {
            com.hivi.launcher.utils.FeedbackSound.playClick();
            mListener.onMuteToggleRequested();
        });
        mBinding.volumeDownButton.setOnClickListener(view -> {
            com.hivi.launcher.utils.FeedbackSound.playClick();
            mListener.onVolumeAdjusted(VOLUME_ADJUST_LOWER);
        });
        mBinding.volumeUpButton.setOnClickListener(view -> {
            com.hivi.launcher.utils.FeedbackSound.playClick();
            applyVolumeRaise(VOLUME_ADJUST_RAISE);
        });
        mBinding.volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                if (progress > VOLUME_PROTECT_THRESHOLD
                        && mAppliedVolume >= 0 && mAppliedVolume <= VOLUME_PROTECT_THRESHOLD) {
                    seekBar.setProgress(mAppliedVolume);
                    showOverLimitConfirm(progress);
                    return;
                }
                mAppliedVolume = progress;
                mListener.onVolumeChanged(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // The volume is applied continuously while the user drags the slider.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                com.hivi.launcher.utils.FeedbackSound.playClick();
            }
        });
    }

    /** The + button crosses the protection threshold the same way the slider does. */
    private void applyVolumeRaise(int direction) {
        int targetVolume = Math.max(0, Math.min(100,
                (mAppliedVolume < 0 ? 0 : mAppliedVolume) + direction));
        if (targetVolume > VOLUME_PROTECT_THRESHOLD
                && mAppliedVolume >= 0 && mAppliedVolume <= VOLUME_PROTECT_THRESHOLD) {
            showOverLimitConfirm(targetVolume);
            return;
        }
        mListener.onVolumeAdjusted(direction);
    }

    private void showOverLimitConfirm(int requestedVolume) {
        if (mOverLimitDialog != null && mOverLimitDialog.isShowing()) {
            return;
        }
        mOverLimitDialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.main_volume_over_limit_title)
                .setMessage(R.string.main_volume_over_limit_message)
                .setPositiveButton(R.string.main_volume_over_limit_ok, (dialog, which) -> {
                    mAppliedVolume = requestedVolume;
                    if (mBinding != null) {
                        mBinding.volumeSeekBar.setProgress(requestedVolume);
                    }
                    mListener.onVolumeChanged(requestedVolume);
                })
                .setNegativeButton(R.string.main_volume_over_limit_cancel, null)
                .create();
        mOverLimitDialog.setOnDismissListener(dialog -> mOverLimitDialog = null);
        mOverLimitDialog.getWindow().setType(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        mOverLimitDialog.setCanceledOnTouchOutside(true);
        mOverLimitDialog.show();
    }

    private void dismissOverLimitDialog() {
        if (mOverLimitDialog != null) {
            mOverLimitDialog.dismiss();
        }
    }

    private void configureWindow() {
        Window window = mDialog.getWindow();
        if (window == null) {
            return;
        }
        window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.68f;
        window.setAttributes(attributes);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        int availableWidth = mContext.getResources().getDisplayMetrics().widthPixels - dp(32);
        window.setLayout(Math.min(dp(500), availableWidth), dp(120));
    }

    private int dp(int value) {
        return Math.round(value * mContext.getResources().getDisplayMetrics().density);
    }
}
