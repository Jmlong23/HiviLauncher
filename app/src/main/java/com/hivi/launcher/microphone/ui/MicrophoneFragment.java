package com.hivi.launcher.microphone.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.FragmentMicrophoneBinding;
import com.hivi.launcher.microphone.model.MicrophoneModel.VolumeChannel;
import com.hivi.launcher.microphone.presenter.MicrophonePresenter;

public final class MicrophoneFragment extends BaseFragment<MicrophonePresenter>
        implements MicrophoneView {
    private static final int VOLUME_ADJUST_LOWER = -1;
    private static final int VOLUME_ADJUST_RAISE = 1;

    private FragmentMicrophoneBinding mBinding;
    private boolean mBindingVolume;

    @Override
    protected MicrophonePresenter createPresenter() {
        return new MicrophonePresenter(this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_microphone;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.microphone_page_title;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentMicrophoneBinding.bind(view);
        bindVolumeControls();
        MicrophonePresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.init(getActivity());
        }
    }

    @Override
    public void onDestroyView() {
        mBinding = null;
        super.onDestroyView();
    }

    @Override
    public void renderMicrophonePage(int amplifierVolumePercent, boolean amplifierMuted,
            int microphoneVolumePercent, boolean microphoneMuted, int effectVolumePercent,
            boolean effectMuted, boolean microphoneConnected) {
        if (mBinding == null) {
            return;
        }
        mBinding.microphoneStatus.setText(microphoneConnected
                ? R.string.microphone_status_connected
                : R.string.microphone_status_disconnected);
        mBinding.microphoneStatus.setTextColor(getResources().getColor(microphoneConnected
                ? R.color.status_connected : R.color.status_disconnect));
        updateVolumeRow(mBinding.amplifierVolumeValue, mBinding.amplifierVolumeMuteButton,
                mBinding.amplifierVolumeDownButton, mBinding.amplifierVolumeSeekBar,
                mBinding.amplifierVolumeUpButton, amplifierVolumePercent, amplifierMuted);
        updateVolumeRow(mBinding.microphoneVolumeValue, mBinding.microphoneVolumeMuteButton,
                mBinding.microphoneVolumeDownButton, mBinding.microphoneVolumeSeekBar,
                mBinding.microphoneVolumeUpButton, microphoneVolumePercent, microphoneMuted);
        updateVolumeRow(mBinding.effectVolumeValue, mBinding.effectVolumeMuteButton,
                mBinding.effectVolumeDownButton, mBinding.effectVolumeSeekBar,
                mBinding.effectVolumeUpButton, effectVolumePercent, effectMuted);
    }

    private void bindVolumeControls() {
        bindVolumeControls(mBinding.amplifierVolumeMuteButton, mBinding.amplifierVolumeDownButton,
                mBinding.amplifierVolumeUpButton, mBinding.amplifierVolumeSeekBar,
                VolumeChannel.AMPLIFIER);
        bindVolumeControls(mBinding.microphoneVolumeMuteButton, mBinding.microphoneVolumeDownButton,
                mBinding.microphoneVolumeUpButton, mBinding.microphoneVolumeSeekBar,
                VolumeChannel.MICROPHONE);
        bindVolumeControls(mBinding.effectVolumeMuteButton, mBinding.effectVolumeDownButton,
                mBinding.effectVolumeUpButton, mBinding.effectVolumeSeekBar,
                VolumeChannel.EFFECT);
    }

    private void bindVolumeControls(ImageView muteButton, ImageView downButton, ImageView upButton,
            SeekBar seekBar, VolumeChannel channel) {
        muteButton.setOnClickListener(view -> {
            MicrophonePresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.toggleMute(channel);
            }
        });
        downButton.setOnClickListener(view -> {
            MicrophonePresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.adjustVolume(channel, VOLUME_ADJUST_LOWER);
            }
        });
        upButton.setOnClickListener(view -> {
            MicrophonePresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.adjustVolume(channel, VOLUME_ADJUST_RAISE);
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || mBindingVolume) {
                    return;
                }
                MicrophonePresenter presenter = getPresenter();
                if (presenter != null) {
                    presenter.setVolumePercent(channel, progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Volume is applied continuously while dragging.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // The final value was already applied in onProgressChanged.
            }
        });
    }

    private void updateVolumeRow(TextView volumeValue, ImageView muteButton, ImageView downButton,
            SeekBar seekBar, ImageView upButton, int volumePercent, boolean muted) {
        volumeValue.setText(String.valueOf(volumePercent));
        muteButton.setSelected(muted);
        muteButton.setContentDescription(getString(muted
                ? R.string.main_volume_unmute : R.string.main_volume_mute));
        setActionEnabled(downButton, volumePercent > 0);
        setActionEnabled(upButton, volumePercent < 100);
        mBindingVolume = true;
        seekBar.setProgress(volumePercent);
        mBindingVolume = false;
    }

    private void setActionEnabled(View action, boolean enabled) {
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : 0.42f);
    }
}
