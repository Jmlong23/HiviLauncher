package com.hivi.launcher.wifi.ui;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import com.hivi.launcher.utils.log.AppLog;
import android.view.View;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.base.BaseFragment;
import com.hivi.launcher.databinding.FragmentWifiBinding;
import com.hivi.launcher.music.model.UpnpPlaybackState;
import com.hivi.launcher.wifi.model.WifiMusicApp;
import com.hivi.launcher.wifi.presenter.WifiMusicPresenter;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WifiFragment extends BaseFragment<WifiMusicPresenter>
        implements WifiMusicView {
    private static final String TAG = "WifiFragment";
    private static final String STATE_SELECTED_MUSIC_APP = "selected_music_app";
    private static final int COVER_CONNECT_TIMEOUT_MS = 8_000;
    private static final int COVER_READ_TIMEOUT_MS = 10_000;
    private static final int COVER_MAX_SIZE_PX = 512;
    private static final long ALBUM_ARTWORK_ROTATION_DURATION_MS = 10_000L;

    private FragmentWifiBinding mBinding;
    private ExecutorService mCoverExecutor;
    private ObjectAnimator mAlbumArtworkAnimator;
    private WifiMusicApp mSelectedMusicApp;
    private String mCoverUrl;

    @Override
    protected WifiMusicPresenter createPresenter() {
        return new WifiMusicPresenter(getActivity(), this);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_wifi;
    }

    @Override
    protected int getPageTitleResId() {
        return R.string.input_mode_wifi_music;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBinding = FragmentWifiBinding.bind(view);
        mBinding.albumArtwork.setClipToOutline(true);
        mCoverExecutor = Executors.newSingleThreadExecutor();
        restoreSelectedMusicApp(savedInstanceState);
        bindClickListeners();
        renderMusicAppSelection(mSelectedMusicApp);

        WifiMusicPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.init();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectedMusicApp != null) {
            outState.putString(STATE_SELECTED_MUSIC_APP, mSelectedMusicApp.name());
        }
    }

    @Override
    public void onDestroyView() {
        stopAlbumArtworkRotation();
        mAlbumArtworkAnimator = null;
        if (mCoverExecutor != null) {
            mCoverExecutor.shutdownNow();
            mCoverExecutor = null;
        }
        mBinding = null;
        mCoverUrl = null;
        super.onDestroyView();
    }

    @Override
    public void renderWifiPlayback(UpnpPlaybackState state) {
        if (mBinding == null || state == null) {
            return;
        }

        boolean hasPlayback = state.hasRealSong();
        mBinding.trackTitle.setText(hasPlayback ? state.getTitle()
                : getString(R.string.wifi_music_no_playback));
        CharSequence artist = TextUtils.isEmpty(state.getArtist())
                ? state.getAlbum() : state.getArtist();
        mBinding.trackArtist.setText(hasPlayback && !TextUtils.isEmpty(artist)
                ? artist : getString(R.string.wifi_music_unknown_artist));
        mBinding.btnPlayOrPause.setImageResource(state.isPlaying()
                ? R.drawable.ic_pause : R.drawable.ic_play);
        mBinding.btnPlayOrPause.setContentDescription(getString(state.isPlaying()
                ? R.string.wifi_music_pause : R.string.wifi_music_play));
        setPlaybackActionEnabled(mBinding.btnPlayOrPause, hasPlayback);
        setPlaybackActionEnabled(mBinding.btnNextSong, hasPlayback);
        mBinding.albumArtwork.setVisibility(hasPlayback ? View.VISIBLE : View.GONE);
        setAlbumArtworkRotation(hasPlayback && state.isPlaying());
        loadCover(hasPlayback ? state.getCoverUrl() : "");
    }

    @Override
    public void renderMusicAppSelection(WifiMusicApp app) {
        mSelectedMusicApp = app;
        if (mBinding == null) {
            return;
        }
        mBinding.btnQqMusic.setSelected(app == WifiMusicApp.QQ_MUSIC);
        mBinding.btnWyyMusic.setSelected(app == WifiMusicApp.NETEASE_CLOUD_MUSIC);
        mBinding.btnKugouMusic.setSelected(app == WifiMusicApp.KUGOU_MUSIC);
    }

    private void bindClickListeners() {
        mBinding.btnQqMusic.setOnClickListener(view -> selectMusicApp(WifiMusicApp.QQ_MUSIC));
        mBinding.btnWyyMusic.setOnClickListener(
                view -> selectMusicApp(WifiMusicApp.NETEASE_CLOUD_MUSIC));
        mBinding.btnKugouMusic.setOnClickListener(
                view -> selectMusicApp(WifiMusicApp.KUGOU_MUSIC));
        mBinding.btnPlayOrPause.setOnClickListener(view -> {
            WifiMusicPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.togglePlayback();
            }
        });
        mBinding.btnNextSong.setOnClickListener(view -> {
            WifiMusicPresenter presenter = getPresenter();
            if (presenter != null) {
                presenter.nextTrack();
            }
        });
    }

    private void selectMusicApp(WifiMusicApp app) {
        WifiMusicPresenter presenter = getPresenter();
        if (presenter != null) {
            presenter.selectMusicApp(app);
        }
    }

    private void restoreSelectedMusicApp(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String appName = savedInstanceState.getString(STATE_SELECTED_MUSIC_APP);
        if (TextUtils.isEmpty(appName)) {
            return;
        }
        try {
            mSelectedMusicApp = WifiMusicApp.valueOf(appName);
        } catch (IllegalArgumentException ignored) {
            // Ignore an obsolete enum value from an earlier app version.
        }
    }

    private void setPlaybackActionEnabled(View action, boolean enabled) {
        action.setEnabled(enabled);
        action.setAlpha(enabled ? 1f : 0.42f);
    }

    private void setAlbumArtworkRotation(boolean rotating) {
        if (!rotating) {
            stopAlbumArtworkRotation();
            return;
        }
        if (mAlbumArtworkAnimator == null) {
            mAlbumArtworkAnimator = ObjectAnimator.ofFloat(mBinding.albumArtwork,
                    View.ROTATION, 0f, 360f);
            mAlbumArtworkAnimator.setDuration(ALBUM_ARTWORK_ROTATION_DURATION_MS);
            mAlbumArtworkAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        }
        if (!mAlbumArtworkAnimator.isStarted()) {
            mAlbumArtworkAnimator.start();
        }
    }

    private void stopAlbumArtworkRotation() {
        if (mAlbumArtworkAnimator != null) {
            mAlbumArtworkAnimator.cancel();
        }
        if (mBinding != null) {
            mBinding.albumArtwork.setRotation(0f);
        }
    }

    private void loadCover(String coverUrl) {
        String requestedUrl = coverUrl == null ? "" : coverUrl;
        if (TextUtils.equals(mCoverUrl, requestedUrl)) {
            return;
        }
        mCoverUrl = requestedUrl;
        mBinding.albumArtwork.setImageBitmap(null);
        if (TextUtils.isEmpty(requestedUrl) || mCoverExecutor == null) {
            return;
        }

        final View artworkView = mBinding.albumArtwork;
        mCoverExecutor.execute(() -> {
            Bitmap cover = downloadCover(requestedUrl);
            artworkView.post(() -> {
                if (mBinding == null || mBinding.albumArtwork != artworkView
                        || !TextUtils.equals(mCoverUrl, requestedUrl)) {
                    return;
                }
                mBinding.albumArtwork.setImageBitmap(cover);
            });
        });
    }

    @Nullable
    private Bitmap downloadCover(String coverUrl) {
        Uri uri = Uri.parse(coverUrl);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            decodeCover(coverUrl, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(bounds);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return decodeCover(coverUrl, options);
        } catch (IOException e) {
            AppLog.w(TAG, "load cover failed", e);
            return null;
        }
    }

    @Nullable
    private Bitmap decodeCover(String coverUrl, BitmapFactory.Options options) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(coverUrl).openConnection();
        connection.setConnectTimeout(COVER_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(COVER_READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode < HttpURLConnection.HTTP_OK
                    || responseCode >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("cover response code=" + responseCode);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(inputStream, null, options);
            }
        } finally {
            connection.disconnect();
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options) {
        int sampleSize = 1;
        while (options.outWidth / sampleSize > COVER_MAX_SIZE_PX
                || options.outHeight / sampleSize > COVER_MAX_SIZE_PX) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
