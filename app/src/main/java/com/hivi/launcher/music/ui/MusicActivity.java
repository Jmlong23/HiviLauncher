package com.hivi.launcher.music.ui;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseActivity;
import com.hivi.launcher.databinding.ActivityMusicPlayerBinding;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.music.presenter.MusicPresenter;
import com.hivi.launcher.utils.UiUtils;

import java.util.Locale;

public class MusicActivity extends BaseActivity<ActivityMusicPlayerBinding, MusicPresenter>
        implements MusicView {
    private boolean mUserSeeking;
    private boolean mBindingProgress;

    @Override
    protected ActivityMusicPlayerBinding createBinding() {
        return ActivityMusicPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MusicPresenter createPresenter() {
        return new MusicPresenter(this, this);
    }

    @Override
    protected void initView(@Nullable Bundle savedInstanceState) {
        applyScaledLayout();
        bindClickListeners();
    }

    @Override
    protected void initData() {
        presenter.init();
    }

    @Override
    public void renderPlayback(UpnpPlaybackState state) {
        if (binding == null || state == null) {
            return;
        }
        binding.titleText.setText(state.getTitle());
        binding.artistText.setText(state.getArtist());
        binding.lyricText.setText(state.getLyric());
        binding.playStateIcon.setImageResource(state.isPlaying()
                ? R.drawable.ic_music_pause : R.mipmap.music_play);
        long duration = state.getDurationMs();
        long position = state.getPositionMs();
        binding.currentTimeText.setText(formatTime(position));
        binding.remainingTimeText.setText("−" + formatTime(Math.max(0L, duration - position)));
        if (!mUserSeeking) {
            int progress = duration > 0 ? Math.round(position * 1000f / duration) : 0;
            mBindingProgress = true;
            binding.progressSeekBar.setProgress(Math.max(0, Math.min(1000, progress)));
            mBindingProgress = false;
        }
    }

    private void bindClickListeners() {
        binding.backButton.setOnClickListener(v -> finish());
        binding.playButton.setOnClickListener(v -> presenter.togglePlay());
        binding.playStateIcon.setOnClickListener(v -> presenter.togglePlay());
        binding.previousButton.setOnClickListener(v -> presenter.previous());
        binding.nextButton.setOnClickListener(v -> presenter.next());
        binding.volumeButton.setOnClickListener(v -> showToast(getString(R.string.main_volume_adjust)));
        binding.playlistButton.setOnClickListener(v -> showToast(getString(R.string.music_playlist)));
        binding.discModeButton.setOnClickListener(v -> showToast(getString(R.string.music_disc_mode)));
        binding.progressSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || mBindingProgress) {
                    return;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mUserSeeking = false;
                UpnpPlaybackState state = com.hivi.launcher.music.model.UpnpPlaybackManager
                        .getInstance().getCurrentState();
                long duration = state.getDurationMs();
                if (duration > 0) {
                    presenter.seekTo(Math.round(duration * seekBar.getProgress() / 1000f));
                }
            }
        });
    }

    private void applyScaledLayout() {
        binding.musicRoot.setPadding(ui(64), ui(34), ui(64), ui(56));

        setSize(binding.musicTopBar, ViewGroup.LayoutParams.MATCH_PARENT, ui(70));
        setSize(binding.backButton, ui(70), ui(70));
        setSize(binding.topActions, ViewGroup.LayoutParams.WRAP_CONTENT, ui(70));
        setSize(binding.playlistButton, ui(70), ui(70));
        setSize(binding.topActionGap, ui(72), ui(1));
        setSize(binding.discModeButton, ui(70), ui(70));

        setWeightedSize(binding.musicContent, ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
                0, 0, 0, 0);
        setLinearSize(binding.recordView, ui(402), ui(402), 0f,
                ui(11), 0, 0, 0);
        setLinearSize(binding.musicInfoPanel, 0, ViewGroup.LayoutParams.MATCH_PARENT, 1f,
                ui(132), 0, 0, 0);
        binding.musicInfoPanel.setGravity(Gravity.TOP);
        binding.musicInfoPanel.setPadding(0, ui(92), 0, 0);

        binding.titleText.setTextSize(TypedValue.COMPLEX_UNIT_PX, ui(64));
        binding.artistText.setTextSize(TypedValue.COMPLEX_UNIT_PX, ui(42));
        binding.lyricText.setTextSize(TypedValue.COMPLEX_UNIT_PX, ui(42));
        setLinearSize(binding.titleText, ViewGroup.LayoutParams.MATCH_PARENT, ui(76), 0f,
                0, 0, 0, 0);
        setLinearSize(binding.artistText, ViewGroup.LayoutParams.MATCH_PARENT, ui(52), 0f,
                0, ui(12), 0, 0);
        setLinearSize(binding.lyricText, ViewGroup.LayoutParams.MATCH_PARENT, ui(60), 0f,
                0, ui(42), 0, 0);
        setLinearSize(binding.controlRow, ViewGroup.LayoutParams.MATCH_PARENT, ui(120), 0f,
                0, ui(58), 0, 0);

        setSize(binding.previousButton, ui(70), ui(70));
        setSize(binding.previousPlayGap, ui(79), ui(1));
        setSize(binding.playButton, ui(120), ui(120));
        setSize(binding.playStateIcon, ui(70), ui(70));
        setSize(binding.playNextGap, ui(113), ui(1));
        setSize(binding.nextButton, ui(70), ui(70));
        setSize(binding.nextVolumeGap, ui(81), ui(1));
        setSize(binding.volumeButton, ui(70), ui(70));

        setLinearSize(binding.progressSeekBar, ui(1200), ui(34), 0f,
                -ui(24), 0, -ui(24), 0);
        binding.progressSeekBar.setThumbOffset(0);
        binding.progressSeekBar.setMaxHeight(ui(13));
        binding.progressSeekBar.setTranslationY(-ui(15));
        setSize(binding.timeRow, ViewGroup.LayoutParams.MATCH_PARENT, ui(44));
        binding.timeRow.setTranslationY(-ui(5));
        binding.currentTimeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, ui(32));
        binding.remainingTimeText.setTextSize(TypedValue.COMPLEX_UNIT_PX, ui(32));
    }

    private int ui(int value) {
        return UiUtils.ui(this, value);
    }

    private void setSize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    private void setWeightedSize(View view, int width, int height, float weight,
            int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
    }

    private void setLinearSize(View view, int width, int height, float weight,
            int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
    }

    private String formatTime(long timeMs) {
        long totalSeconds = Math.max(0L, timeMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
