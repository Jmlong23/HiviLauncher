package com.hivi.launcher.bluetooth.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.bluetooth.model.BluetoothPageState;
import com.hivi.launcher.bluetooth.presenter.BluetoothPresenter;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.FragmentBluetoothBinding;

public final class BluetoothFragment extends BaseFragment<BluetoothPresenter>
        implements BluetoothView {
    private FragmentBluetoothBinding mBinding;
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
        mConnected = connected;
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
        // Line 1 shows the song title, line 2 the lyrics line pushed by the MCU; each
        // falls back to the previous placeholder strings while data has not arrived.
        String trackTitle = state.getTrackTitle();
        String trackLyric = state.getTrackLyric();
        mBinding.trackTitle.setText(!playing ? getString(R.string.bluetooth_no_playback)
                : TextUtils.isEmpty(trackTitle)
                        ? getString(R.string.bluetooth_now_playing) : trackTitle);
        mBinding.trackArtist.setText(!playing ? getString(R.string.bluetooth_unknown_artist)
                : trackLyric);
        mBinding.albumArtwork.setVisibility(View.GONE);
        boolean playbackControlAvailable = state.isPlaybackControlAvailable();
        mBinding.btnPlayOrPause.setImageResource(playing
                ? R.drawable.ic_pause : R.drawable.ic_play);
        mBinding.btnPlayOrPause.setContentDescription(getString(playing
                ? R.string.bluetooth_pause : R.string.bluetooth_play));
        setActionEnabled(mBinding.btnPlayOrPause, playbackControlAvailable);
        setActionEnabled(mBinding.btnPreviousSong, playbackControlAvailable);
        setActionEnabled(mBinding.btnNextSong, playbackControlAvailable);
    }

    @Override
    public void renderBluetoothFftData(byte[] fftData) {
        if (mBinding != null) {
            mBinding.bluetoothVisualizerPanel.setFftData(fftData);
        }
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
