package com.hivi.launcher.bluetooth.ui;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.bluetooth.model.BluetoothPageState;
import com.hivi.launcher.bluetooth.presenter.BluetoothPresenter;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.FragmentBluetoothBinding;

public final class BluetoothFragment extends BaseFragment<BluetoothPresenter>
        implements BluetoothView {
    private static final int VOLUME_ADJUST_LOWER = -1;
    private static final int VOLUME_ADJUST_RAISE = 1;

    private FragmentBluetoothBinding mBinding;
    private boolean mBindingVolume;
    private boolean mConnected;
    private AlertDialog mResetConfirmDialog;

    @Override
    protected BluetoothPresenter createPresenter() {
        return new BluetoothPresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_bluetooth;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_bluetooth_top;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentBluetoothBinding.bind(view);
        bindClickListeners();
        BluetoothPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.init(getActivity());
        }
    }

    @Override
    public void onDestroyView() {
        if (mResetConfirmDialog != null) {
            mResetConfirmDialog.dismiss();
            mResetConfirmDialog = null;
        }
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void renderBluetoothPage(BluetoothPageState state) {
        if (mBinding == null || state == null) {
            return;
        }

        boolean connected = state.isConnected();
        String deviceName = state.getDeviceName();
        // The connected-device field always shows the phone-side name reported by the MCU;
        // it falls back to a generic label only while connected but not yet named.
        if (connected && TextUtils.isEmpty(deviceName)) {
            mBinding.connectedDeviceName.setText(R.string.bluetooth_connected_device_default);
        } else if (TextUtils.isEmpty(deviceName)) {
            mBinding.connectedDeviceName.setText(R.string.bluetooth_device_disconnected);
        } else {
            mBinding.connectedDeviceName.setText(deviceName);
        }
        mConnected = connected;
        // One dual-purpose action: "disconnect" while connected, "connect" otherwise. The
        // button stays clickable in both states and only the icon/text follow the state.
        mBinding.disconnectButton.setText(connected
                ? R.string.bluetooth_disconnect : R.string.bluetooth_connect);
        mBinding.disconnectButton.setContentDescription(getString(connected
                ? R.string.bluetooth_disconnect : R.string.bluetooth_connect));
        mBinding.disconnectButton.setCompoundDrawablesWithIntrinsicBounds(null,
                getResources().getDrawable(connected
                        ? R.drawable.ic_bluetooth_disconnect
                        : R.drawable.ic_bluetooth_connect), null, null);
        setActionEnabled(mBinding.disconnectButton, true);
        setActionEnabled(mBinding.resetButton, connected);

        boolean playing = state.isPlaying();
        mBinding.trackTitle.setText(playing
                ? getString(R.string.bluetooth_now_playing) : getString(R.string.bluetooth_no_playback));
        mBinding.trackArtist.setText(playing ? "" : getString(R.string.bluetooth_unknown_artist));
        mBinding.albumArtwork.setVisibility(View.GONE);
        boolean playbackControlAvailable = state.isPlaybackControlAvailable();
        mBinding.btnPlayOrPause.setImageResource(playing
                ? R.drawable.ic_pause : R.drawable.ic_play);
        mBinding.btnPlayOrPause.setContentDescription(getString(playing
                ? R.string.bluetooth_pause : R.string.bluetooth_play));
        setActionEnabled(mBinding.btnPlayOrPause, playbackControlAvailable);
        setActionEnabled(mBinding.btnPreviousSong, playbackControlAvailable);
        setActionEnabled(mBinding.btnNextSong, playbackControlAvailable);

        int volumePercent = state.getVolumePercent();
        mBinding.volumeValue.setText(String.valueOf(volumePercent));
        mBinding.volumeMuteButton.setSelected(state.isMuted());
        mBinding.volumeMuteButton.setContentDescription(getString(state.isMuted()
                ? R.string.main_volume_unmute : R.string.main_volume_mute));
        setActionEnabled(mBinding.volumeDownButton, volumePercent > 0);
        setActionEnabled(mBinding.volumeUpButton, volumePercent < 100);
        mBindingVolume = true;
        mBinding.volumeSeekBar.setProgress(volumePercent);
        mBindingVolume = false;
    }

    private void bindClickListeners() {
        mBinding.disconnectButton.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter == null) {
                return;
            }
            if (mConnected) {
                presenter.disconnect();
            } else {
                presenter.connect();
            }
        });
        mBinding.resetButton.setOnClickListener(view -> showResetConfirmDialog());
        mBinding.btnPlayOrPause.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.togglePlayback();
            }
        });
        mBinding.btnPreviousSong.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.previousTrack();
            }
        });
        mBinding.btnNextSong.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.nextTrack();
            }
        });
        mBinding.volumeMuteButton.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.toggleMute();
            }
        });
        mBinding.volumeDownButton.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.adjustVolume(VOLUME_ADJUST_LOWER);
            }
        });
        mBinding.volumeUpButton.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.adjustVolume(VOLUME_ADJUST_RAISE);
            }
        });
        mBinding.volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || mBindingVolume) {
                    return;
                }
                BluetoothPresenter presenter = getPresenter();
                if (presenter != null) {
                    presenter.setVolumePercent(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Volume is applied continuously while dragging.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // The final value was applied by onProgressChanged.
            }
        });
    }

    private void setActionEnabled(View action, boolean enabled) {
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : 0.42f);
    }

    /**
     * Clearing pairings drops every bonded phone, so the request needs an explicit
     * confirmation before the reset command reaches the MCU.
     */
    private void showResetConfirmDialog() {
        if (mResetConfirmDialog != null || getActivity() == null) {
            return;
        }
        mResetConfirmDialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.bluetooth_reset_confirm_title)
                .setMessage(R.string.bluetooth_reset_confirm_message)
                .setPositiveButton(R.string.bluetooth_reset_confirm_ok,
                        (dialog, which) -> {
                            BluetoothPresenter presenter = getPresenter();
                            if (presenter != null) {
                                presenter.reset();
                            }
                        })
                .setNegativeButton(R.string.bluetooth_reset_confirm_cancel, null)
                .create();
        mResetConfirmDialog.setOnDismissListener(
                dialog -> mResetConfirmDialog = null);
        mResetConfirmDialog.show();
    }
}
