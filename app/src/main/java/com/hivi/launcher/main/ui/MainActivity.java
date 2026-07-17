package com.hivi.launcher.main.ui;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;
import com.hivi.launcher.account.ui.AuthorizationDialog;
import com.hivi.launcher.base.BaseActivity;
import com.hivi.launcher.databinding.ActivityMainBinding;
import com.hivi.launcher.main.presenter.MainPresenter;
import com.hivi.launcher.music.model.BluetoothMediaController;
import com.hivi.launcher.music.ui.MusicActivity;
import com.hivi.launcher.systemapps.ui.SystemAppsActivity;

public class MainActivity extends BaseActivity<ActivityMainBinding, MainPresenter>
        implements MainView {
    private AuthorizationDialog mAuthorizationDialog;

    private final BroadcastReceiver mSystemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (presenter == null || intent == null) {
                return;
            }
            if (isBluetoothStateIntent(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int connectionState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
                presenter.onBluetoothStateChanged(device, intent.getAction(), connectionState);
            } else {
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
        presenter.onSystemStateChanged();
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
    public void updateConnectivity(String wifiLabel, boolean bluetoothConnected,
            String bluetoothDeviceName) {
        if (binding == null) {
            return;
        }
        binding.wifiText.setText(wifiLabel);
        binding.bluetoothText.setText(bluetoothConnected
                ? (android.text.TextUtils.isEmpty(bluetoothDeviceName)
                        ? getString(R.string.main_connected) : bluetoothDeviceName)
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

    private void registerSystemReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(android.net.wifi.WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothMediaController.ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(MainPresenter.ACTION_VOLUME_CHANGED);
        registerReceiver(mSystemReceiver, filter);
    }

    private void unregisterReceiverQuietly() {
        try {
            unregisterReceiver(mSystemReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean isBluetoothStateIntent(String action) {
        return BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                || BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action)
                || BluetoothMediaController.ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED.equals(action)
                || BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)
                || BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action);
    }

}
