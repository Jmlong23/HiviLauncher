package com.hivi.launcher.bluetooth.ui;

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
import com.hivi.launcher.music.model.BluetoothPlaybackState;

public final class BluetoothFragment extends BaseFragment<BluetoothPresenter>
        implements BluetoothView {
    private static final int VOLUME_ADJUST_LOWER = -1;
    private static final int VOLUME_ADJUST_RAISE = 1;

    private FragmentBluetoothBinding mBinding;
    private boolean mBindingVolume;
    private Bitmap mArtwork;

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
        mArtwork = null;
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
        mBinding.bluetoothStatus.setSelected(connected);
        mBinding.bluetoothStatus.setText(connected
                ? R.string.bluetooth_status_connected : R.string.bluetooth_status_disconnected);
        mBinding.bluetoothStatus.setTextColor(getResources().getColor(connected
                ? R.color.status_connected : R.color.status_disconnect));
        mBinding.connectedDeviceName.setText(connected
                ? (TextUtils.isEmpty(deviceName)
                        ? getString(R.string.bluetooth_connected_device_default) : deviceName)
                : getString(R.string.bluetooth_device_disconnected));
        mBinding.signalStrengthValue.setText(connected
                ? R.string.bluetooth_signal_excellent : R.string.bluetooth_signal_unavailable);
        mBinding.signalStrengthValue.setTextColor(getResources().getColor(R.color.text_color));
        setActionEnabled(mBinding.disconnectButton, connected);
        setActionEnabled(mBinding.resetButton, connected);

        BluetoothPlaybackState playback = state.getPlaybackState();
        boolean hasTrackMetadata = playback.hasMetadata();
        mBinding.trackTitle.setText(TextUtils.isEmpty(playback.getTitle())
                ? getString(R.string.bluetooth_no_playback) : playback.getTitle());
        CharSequence artist = playback.getArtist();
        if (TextUtils.isEmpty(artist)) {
            artist = playback.getAlbum();
        }
        mBinding.trackArtist.setText(TextUtils.isEmpty(artist)
                ? getString(R.string.bluetooth_unknown_artist) : artist);
        mBinding.albumArtwork.setVisibility(hasTrackMetadata ? View.VISIBLE : View.GONE);
        if (mArtwork != playback.getArtwork()) {
            mArtwork = playback.getArtwork();
            if (mArtwork == null) {
                mBinding.albumArtwork.setImageBitmap(null);
            } else {
                mBinding.albumArtwork.setImageBitmap(mArtwork);
            }
        }
        boolean playbackControlAvailable = state.isPlaybackControlAvailable();
        mBinding.btnPlayOrPause.setImageResource(playback.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play);
        mBinding.btnPlayOrPause.setContentDescription(getString(playback.isPlaying()
                ? R.string.bluetooth_pause : R.string.bluetooth_play));
        setActionEnabled(mBinding.btnPlayOrPause, playbackControlAvailable);
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
            if (presenter != null) {
                presenter.disconnect();
            }
        });
        mBinding.resetButton.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.reset();
            }
        });
        mBinding.btnPlayOrPause.setOnClickListener(view -> {
            BluetoothPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.togglePlayback();
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
}
