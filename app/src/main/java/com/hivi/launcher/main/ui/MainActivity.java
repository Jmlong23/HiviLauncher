package com.hivi.launcher.main.ui;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.account.ui.AuthorizationDialog;
import com.hivi.launcher.base.BaseActivity;
import com.hivi.launcher.customview.AppIconDrawable;
import com.hivi.launcher.customview.CircleDrawable;
import com.hivi.launcher.customview.PlayDrawable;
import com.hivi.launcher.customview.RoundRectDrawable;
import com.hivi.launcher.customview.StatusIconDrawable;
import com.hivi.launcher.databinding.ActivityMainBinding;
import com.hivi.launcher.main.presenter.MainPresenter;
import com.hivi.launcher.music.ui.MusicActivity;
import com.hivi.launcher.systemapps.ui.SystemAppsActivity;
import com.hivi.launcher.utils.UiUtils;

public class MainActivity extends BaseActivity<ActivityMainBinding, MainPresenter>
        implements MainView {
    private AuthorizationDialog mAuthorizationDialog;

    private final BroadcastReceiver mSystemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (presenter != null) {
                presenter.onSystemStateChanged();
            }
        }
    };

    @Override
    protected ActivityMainBinding createBinding() {
        return ActivityMainBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MainPresenter createPresenter() {
        return new MainPresenter(this, this);
    }

    @Override
    protected void initView(@Nullable Bundle savedInstanceState) {
        applyScaledLayout();
        applyMainBackgrounds();
        bindMainClickListeners();
    }

    @Override
    protected void initData() {
        presenter.init();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyLocalizedTexts();
        registerSystemReceiver();
        presenter.startTicker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiverQuietly();
        presenter.stopTicker();
    }

    @Override
    public void updateClock(String time, String date) {
        if (binding == null) {
            return;
        }
        binding.timeText.setText(time);
        binding.dateText.setText(date);
    }

    @Override
    public void updateConnectivity(String wifiLabel, boolean bluetoothConnected) {
        if (binding == null) {
            return;
        }
        binding.wifiText.setText(wifiLabel);
        binding.bluetoothText.setText(bluetoothConnected
                ? getString(R.string.main_connected)
                : getString(R.string.main_disconnected));
    }

    @Override
    public void updateVolume(int volumePercent) {
        if (binding == null) {
            return;
        }
        binding.volumeText.setText(String.valueOf(volumePercent));
        binding.volumeDialView.setValue(volumePercent);
    }

    @Override
    public void updateMusic(CharSequence title, CharSequence artist) {
        if (binding == null) {
            return;
        }
        binding.musicTitleText.setText(title);
        binding.musicArtistText.setText(artist);
    }

    @Override
    public void openMusicPlayer() {
        startActivity(new Intent(this, MusicActivity.class));
    }

    @Override
    public void openSystemApps() {
        startActivity(new Intent(this, SystemAppsActivity.class));
    }

    @Override
    public void showAuthorization() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (mAuthorizationDialog == null) {
            mAuthorizationDialog = new AuthorizationDialog(this);
        }
        mAuthorizationDialog.show();
    }

    @Override
    protected void onDestroy() {
        if (mAuthorizationDialog != null) {
            mAuthorizationDialog.dismiss();
            mAuthorizationDialog = null;
        }
        super.onDestroy();
    }

    private void applyLocalizedTexts() {
        binding.wifiText.setText(R.string.main_wifi_default);
        binding.bluetoothText.setText(R.string.main_bluetooth_default);
        binding.modeText.setText(R.string.main_current_mode_wifi);
        binding.accountText.setText(R.string.main_account);
        binding.timePeriodText.setText(R.string.main_time_period_morning);
        binding.weatherDescText.setText(R.string.main_weather_desc);
        binding.weatherTempText.setText(R.string.main_weather_temp);
        binding.cityText.setText(R.string.main_city_air_quality);
        binding.volumeLabelText.setText(R.string.main_volume);
        binding.volumeAdjustLabel.setText(R.string.main_volume_adjust);
        binding.musicTitleText.setText(R.string.main_music_empty_title);
        binding.musicArtistText.setText(R.string.main_music_empty_artist);
        binding.settingsText.setText(R.string.main_settings);
        binding.screensaverText.setText(R.string.main_screensaver_settings);
        binding.switchText.setText(R.string.main_switch);
    }

    private void applyScaledLayout() {
        binding.launcherRoot.setPadding(ui(50), ui(34), ui(50), ui(26));

        setLinearLayoutSize(binding.topBar, ViewGroup.LayoutParams.MATCH_PARENT, ui(54));
        setLinearLayoutSize(binding.topToCardsGap, ViewGroup.LayoutParams.MATCH_PARENT, ui(104));
        setLinearLayoutSize(binding.cardsRow, ViewGroup.LayoutParams.MATCH_PARENT, ui(325));
        setLinearLayoutSize(binding.cardsBottomGap, ViewGroup.LayoutParams.MATCH_PARENT, ui(38));
        setLinearLayoutSize(binding.bottomBar, ViewGroup.LayoutParams.MATCH_PARENT, ui(190));

        setLinearLayoutSize(binding.wifiIcon, ui(44), ui(44));
        setLinearLayoutSize(binding.wifiIconTextGap, ui(8), ui(1));
        setLinearLayoutSize(binding.wifiBtGap, ui(24), ui(1));
        setLinearLayoutSize(binding.bluetoothIcon, ui(44), ui(44));
        setLinearLayoutSize(binding.bluetoothIconTextGap, ui(8), ui(1));
        setWeightedLinearLayoutSize(binding.topBarFiller, 0, ui(1), 1f, 0, 0, 0, 0);
        setLinearLayoutSize(binding.modeText, ui(216), ui(54));
        setLinearLayoutSize(binding.topActionGap, ui(26), ui(1));
        setLinearLayoutSize(binding.accountText, ui(147), ui(54));

        setWeightedLinearLayoutSize(binding.clockCard, 0, ViewGroup.LayoutParams.MATCH_PARENT,
                1f, 0, 0, 0, 0);
        setWeightedLinearLayoutSize(binding.weatherCard, 0, ViewGroup.LayoutParams.MATCH_PARENT,
                1f, ui(40), 0, ui(40), 0);
        setWeightedLinearLayoutSize(binding.volumeCard, 0, ViewGroup.LayoutParams.MATCH_PARENT,
                1f, 0, 0, 0, 0);

        applyCardStyle(binding.clockCard);
        applyCardStyle(binding.weatherCard);
        applyCardStyle(binding.volumeCard);

        setLinearLayoutSize(binding.clockFace, ui(170), ui(170));
        setLinearLayoutSize(binding.timeGap, ui(16), ui(1));
        setLinearLayoutSize(binding.dateText, ViewGroup.LayoutParams.MATCH_PARENT, ui(46));
        setLinearLayoutMargins(binding.dateText, ui(26), ui(12), ui(26), 0);

        setLinearLayoutSize(binding.weatherIcon, ui(170), ui(122));
        setLinearLayoutSize(binding.weatherGap, ui(24), ui(1));
        setLinearLayoutSize(binding.cityText, ViewGroup.LayoutParams.MATCH_PARENT, ui(46));
        setLinearLayoutMargins(binding.cityText, ui(26), ui(12), ui(26), 0);

        setLinearLayoutSize(binding.volumeDialView, ui(190), ui(190));
        setLinearLayoutSize(binding.volumeGap, ui(28), ui(1));
        setLinearLayoutSize(binding.volumeControls, ViewGroup.LayoutParams.WRAP_CONTENT, ui(42));
        setLinearLayoutMargins(binding.volumeControls, 0, ui(12), 0, 0);
        setLinearLayoutSize(binding.volumeUpButton, ui(42), ui(42));
        setLinearLayoutSize(binding.volumeAdjustLabel, ui(92), ui(42));
        setLinearLayoutSize(binding.volumeDownButton, ui(42), ui(42));

        setWeightedLinearLayoutSize(binding.playerContainer, 0, ViewGroup.LayoutParams.MATCH_PARENT,
                1f, 0, 0, ui(99), 0);
        binding.playerContainer.setPadding(ui(28), 0, ui(28), 0);
        setLinearLayoutSize(binding.recordView, ui(154), ui(154));
        setWeightedLinearLayoutSize(binding.musicTextBox, 0, ViewGroup.LayoutParams.MATCH_PARENT,
                1f, ui(26), 0, ui(18), 0);
        setLinearLayoutSize(binding.playIcon, ui(54), ui(54));

        setLinearLayoutSize(binding.settingsButton, ui(112), ui(176));
        setLinearLayoutSize(binding.settingsIcon, ui(112), ui(112));
        setLinearLayoutSize(binding.settingsText, ViewGroup.LayoutParams.MATCH_PARENT, ui(46));
        setLinearLayoutSize(binding.settingsScreensaverGap, ui(92), ui(1));
        setLinearLayoutSize(binding.screensaverButton, ui(112), ui(176));
        setLinearLayoutSize(binding.screensaverIcon, ui(112), ui(112));
        setLinearLayoutSize(binding.screensaverText, ViewGroup.LayoutParams.MATCH_PARENT, ui(46));
        setLinearLayoutSize(binding.screensaverSwitchGap, ui(92), ui(1));
        setLinearLayoutSize(binding.switchButton, ui(112), ui(176));
        setLinearLayoutSize(binding.switchIcon, ui(112), ui(112));
        setLinearLayoutSize(binding.switchText, ViewGroup.LayoutParams.MATCH_PARENT, ui(46));
    }

    private void applyMainBackgrounds() {
        binding.wifiIcon.setImageDrawable(new StatusIconDrawable("wifi"));
        binding.bluetoothIcon.setImageDrawable(new StatusIconDrawable("bt"));
        binding.modeText.setBackground(new RoundRectDrawable(0x66454545, ui(24)));
        binding.accountText.setBackground(new RoundRectDrawable(0x66454545, ui(24)));
        binding.accountText.setCompoundDrawablesWithIntrinsicBounds(
                new CircleDrawable(0xffd9a35c), null, null, null);
        binding.accountText.setCompoundDrawablePadding(dp(8));

        binding.dateText.setBackground(new RoundRectDrawable(0x66454545, ui(24)));
        binding.cityText.setBackground(new RoundRectDrawable(0x66454545, ui(24)));
        binding.volumeAdjustLabel.setBackground(new RoundRectDrawable(0x66454545, ui(24)));
        binding.volumeUpButton.setBackground(new RoundRectDrawable(0xff454545, ui(14)));
        binding.volumeDownButton.setBackground(new RoundRectDrawable(0xff454545, ui(14)));

        binding.playerContainer.setBackground(new RoundRectDrawable(0xff45474a, ui(20)));
        binding.playIcon.setImageDrawable(new PlayDrawable());
        binding.settingsIcon.setImageDrawable(new AppIconDrawable("gear"));
        binding.screensaverIcon.setImageDrawable(new AppIconDrawable("image"));
        binding.switchIcon.setImageDrawable(new AppIconDrawable("switch"));
        binding.settingsButton.setBackground(new RoundRectDrawable(0x00303030, ui(16)));
        binding.screensaverButton.setBackground(new RoundRectDrawable(0x00303030, ui(16)));
        binding.switchButton.setBackground(new RoundRectDrawable(0x00303030, ui(16)));
    }

    private void bindMainClickListeners() {
        binding.accountText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.showAuthorizationDialog();
            }
        });
        binding.volumeUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.adjustVolume(AudioManager.ADJUST_RAISE);
            }
        });
        binding.volumeDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.adjustVolume(AudioManager.ADJUST_LOWER);
            }
        });
        binding.playerContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.openMusicPlayer();
            }
        });
        binding.settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.openSystemSettings();
            }
        });
        binding.screensaverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.openScreensaverSettings();
            }
        });
        binding.switchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.openSystemApps();
            }
        });
    }

    private void applyCardStyle(LinearLayout card) {
        card.setPadding(ui(26), ui(14), ui(26), ui(18));
        card.setBackground(new RoundRectDrawable(0xff3f3f3f, ui(18)));
    }

    private void registerSystemReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(MainPresenter.ACTION_VOLUME_CHANGED);
        registerReceiver(mSystemReceiver, filter);
    }

    private void unregisterReceiverQuietly() {
        try {
            unregisterReceiver(mSystemReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private int ui(int value) {
        return UiUtils.ui(this, value);
    }

    private int dp(int value) {
        return UiUtils.dp(this, value);
    }

    private void setLinearLayoutSize(View view, int width, int height) {
        UiUtils.setLinearLayoutSize(view, width, height);
    }

    private void setWeightedLinearLayoutSize(View view, int width, int height, float weight,
            int left, int top, int right, int bottom) {
        UiUtils.setWeightedLinearLayoutSize(view, width, height, weight, left, top, right, bottom);
    }

    private void setLinearLayoutMargins(View view, int left, int top, int right, int bottom) {
        UiUtils.setLinearLayoutMargins(view, left, top, right, bottom);
    }
}
