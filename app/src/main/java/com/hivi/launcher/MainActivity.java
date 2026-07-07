package com.hivi.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String[] MUSIC_PACKAGES = {
            "com.tencent.qqmusic",
            "com.netease.cloudmusic",
            "com.kugou.android"
    };

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy.MM.dd", Locale.CHINA);
    private final SimpleDateFormat mWeekFormat = new SimpleDateFormat("E", Locale.CHINA);

    private AudioManager mAudioManager;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;
    private MediaSessionManager mMediaSessionManager;

    private TextView mWifiText;
    private TextView mBluetoothText;
    private TextView mTimeText;
    private TextView mDateText;
    private TextView mVolumeText;
    private TextView mMusicTitleText;
    private TextView mMusicArtistText;
    private VolumeDialView mVolumeDialView;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateMedia();
            mHandler.postDelayed(this, 1000L);
        }
    };

    private final BroadcastReceiver mSystemReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateConnectivity();
            updateVolume();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterFullscreen();

        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mMediaSessionManager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);

        setContentView(createContentView());
        updateClock();
        updateConnectivity();
        updateVolume();
        updateMedia();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullscreen();
        registerSystemReceiver();
        mHandler.removeCallbacks(mTicker);
        mTicker.run();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreen();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiverQuietly();
        mHandler.removeCallbacks(mTicker);
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ui(50), ui(34), ui(50), ui(26));
        root.setBackgroundColor(0xff303030);

        root.addView(createTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui(54)));

        root.addView(new Space(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui(104)));

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        cardParams.setMargins(0, 0, 0, 0);
        root.addView(cards, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui(325)));

        cards.addView(createClockCard(), cardParams);
        LinearLayout.LayoutParams middleCardParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        middleCardParams.setMargins(ui(40), 0, ui(40), 0);
        cards.addView(createWeatherCard(), middleCardParams);
        cards.addView(createVolumeCard(), cardParams);

        root.addView(new Space(this), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui(38)));

        root.addView(createBottomBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui(190)));
        return root;
    }

    private View createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        mWifiText = smallStatusText("WiFi");
        mBluetoothText = smallStatusText("蓝牙");
        bar.addView(createStatusPill("wifi", mWifiText), wrapContent());
        addGap(bar, 24, 1);
        bar.addView(createStatusPill("bt", mBluetoothText), wrapContent());

        Space spacer = new Space(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView mode = pillText("当前模式： WiFi", 18, true);
        bar.addView(mode, new LinearLayout.LayoutParams(ui(216), ui(54)));
        addGap(bar, 26, 1);
        TextView account = pillText("账号", 18, true);
        account.setCompoundDrawablesWithIntrinsicBounds(new CircleDrawable(0xffd9a35c), null, null, null);
        account.setCompoundDrawablePadding(dp(8));
        bar.addView(account, new LinearLayout.LayoutParams(ui(147), ui(54)));
        return bar;
    }

    private View createStatusPill(String iconType, TextView label) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setOrientation(LinearLayout.HORIZONTAL);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new StatusIconDrawable(iconType));
        box.addView(icon, new LinearLayout.LayoutParams(ui(44), ui(44)));
        addGap(box, 8, 1);
        box.addView(label, wrapContent());
        return box;
    }

    private View createClockCard() {
        LinearLayout card = cardContainer();
        ClockFaceView face = new ClockFaceView(this);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(ui(170), ui(170));
        faceParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(face, faceParams);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER);
        TextView period = text("上午", 18, 0xffe9e9e9, false);
        mTimeText = text("--:--", 30, 0xffffffff, true);
        timeRow.addView(period, wrapContent());
        addGap(timeRow, 16, 1);
        timeRow.addView(mTimeText, wrapContent());
        card.addView(timeRow, matchWrap());

        mDateText = pillText("----.--.--   --", 18, false);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui(46));
        dateParams.setMargins(ui(26), ui(12), ui(26), 0);
        card.addView(mDateText, dateParams);
        return card;
    }

    private View createWeatherCard() {
        LinearLayout card = cardContainer();
        WeatherView weather = new WeatherView(this);
        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(ui(170), ui(122));
        weatherParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(weather, weatherParams);

        LinearLayout weatherRow = new LinearLayout(this);
        weatherRow.setGravity(Gravity.CENTER);
        weatherRow.addView(text("晴转多云", 18, 0xffededed, false), wrapContent());
        addGap(weatherRow, 24, 1);
        weatherRow.addView(text("25°C", 31, 0xffffffff, true), wrapContent());
        card.addView(weatherRow, matchWrap());

        TextView city = pillText("珠海      空气质量良好", 18, false);
        LinearLayout.LayoutParams cityParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui(46));
        cityParams.setMargins(ui(26), ui(12), ui(26), 0);
        card.addView(city, cityParams);
        return card;
    }

    private View createVolumeCard() {
        LinearLayout card = cardContainer();
        mVolumeDialView = new VolumeDialView(this);
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(ui(190), ui(190));
        dialParams.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(mVolumeDialView, dialParams);

        LinearLayout volumeRow = new LinearLayout(this);
        volumeRow.setGravity(Gravity.CENTER);
        volumeRow.addView(text("音量", 18, 0xffededed, false), wrapContent());
        addGap(volumeRow, 28, 1);
        mVolumeText = text("--", 31, 0xffffffff, true);
        volumeRow.addView(mVolumeText, wrapContent());
        card.addView(volumeRow, matchWrap());

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(squareButton("+", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustVolume(AudioManager.ADJUST_RAISE);
            }
        }), new LinearLayout.LayoutParams(ui(42), ui(42)));
        TextView label = pillText("音量调节", 18, false);
        controls.addView(label, new LinearLayout.LayoutParams(ui(92), ui(42)));
        controls.addView(squareButton("-", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adjustVolume(AudioManager.ADJUST_LOWER);
            }
        }), new LinearLayout.LayoutParams(ui(42), ui(42)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ui(42));
        params.setMargins(0, ui(12), 0, 0);
        card.addView(controls, params);
        return card;
    }

    private View createBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout player = new LinearLayout(this);
        player.setOrientation(LinearLayout.HORIZONTAL);
        player.setGravity(Gravity.CENTER_VERTICAL);
        player.setPadding(ui(28), 0, ui(28), 0);
        player.setBackground(new RoundRectDrawable(0xff45474a, ui(20)));
        player.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchFirstAvailableMusicApp();
            }
        });

        RecordView record = new RecordView(this);
        player.addView(record, new LinearLayout.LayoutParams(ui(154), ui(154)));
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);
        mMusicTitleText = text("暂无播放", 26, 0xffffffff, true);
        mMusicArtistText = text("QQ音乐 / 网易云 / 酷狗", 22, 0xffbdbdbd, false);
        mMusicTitleText.setSingleLine(true);
        mMusicTitleText.setEllipsize(TextUtils.TruncateAt.END);
        mMusicArtistText.setSingleLine(true);
        mMusicArtistText.setEllipsize(TextUtils.TruncateAt.END);
        textBox.addView(mMusicTitleText, matchWrap());
        textBox.addView(mMusicArtistText, matchWrap());
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        textParams.setMargins(ui(26), 0, ui(18), 0);
        player.addView(textBox, textParams);

        ImageView play = new ImageView(this);
        play.setImageDrawable(new PlayDrawable());
        player.addView(play, new LinearLayout.LayoutParams(ui(54), ui(54)));

        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        playerParams.setMargins(0, 0, ui(99), 0);
        bar.addView(player, playerParams);

        bar.addView(appButton("系统设置", "gear", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivitySafe(new Intent(Settings.ACTION_SETTINGS), "无法打开系统设置");
            }
        }), new LinearLayout.LayoutParams(ui(112), ui(176)));
        addGap(bar, 92, 1);
        bar.addView(appButton("屏保设置", "image", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivitySafe(new Intent(Settings.ACTION_DREAM_SETTINGS), "无法打开屏保设置");
            }
        }), new LinearLayout.LayoutParams(ui(112), ui(176)));
        addGap(bar, 92, 1);
        bar.addView(appButton("切换", "switch", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInstalledAppsDialog();
            }
        }), new LinearLayout.LayoutParams(ui(112), ui(176)));
        return bar;
    }

    private void showInstalledAppsDialog() {
        final List<AppEntry> apps = loadLaunchableApps();
        if (apps.isEmpty()) {
            toast("没有找到可启动的应用");
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(createAppsDialogView(dialog, apps));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new RoundRectDrawable(0xff2f2f2f, dp(8)));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = getAppsDialogWidth();
            params.height = Math.min(getResources().getDisplayMetrics().heightPixels - dp(64), dp(600));
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
        dialog.show();
    }

    private View createAppsDialogView(final Dialog dialog, List<AppEntry> apps) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(22), dp(26), dp(24));
        root.setBackground(new DashedBorderDrawable(0x55ffffff, 0xee303030, dp(2), dp(8)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = text("全部应用", 24, 0xffffffff, true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = text("关闭", 18, 0xffffffff, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(new RoundRectDrawable(0x66454545, dp(20)));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(86), dp(42)));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);

        GridLayout grid = new GridLayout(this);
        int contentWidth = getAppsDialogWidth() - dp(52);
        int columnCount = Math.max(3, Math.min(6, contentWidth / dp(142)));
        int itemWidth = Math.max(dp(112), (contentWidth - columnCount * dp(16)) / columnCount);
        grid.setColumnCount(columnCount);
        grid.setPadding(0, dp(18), 0, 0);

        for (final AppEntry app : apps) {
            View item = createAppGridItem(app, dialog);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = itemWidth;
            params.height = dp(116);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            grid.addView(item, params);
        }

        scrollView.addView(grid, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView count = text("共 " + apps.size() + " 个应用", 16, 0xffcfcfcf, false);
        count.setGravity(Gravity.RIGHT);
        root.addView(count, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));
        return root;
    }

    private View createAppGridItem(final AppEntry app, final Dialog dialog) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(10), dp(8), dp(8));
        item.setBackground(new DashedBorderDrawable(0x44ffffff, 0x24454545, dp(1), dp(8)));
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                launchApp(app);
            }
        });

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        item.addView(icon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView label = text(app.label, 16, 0xffffffff, true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        labelParams.setMargins(0, dp(10), 0, 0);
        item.addView(label, labelParams);
        return item;
    }

    private List<AppEntry> loadLaunchableApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        final Collator collator = Collator.getInstance(Locale.CHINA);
        List<AppEntry> apps = new ArrayList<>();

        for (ResolveInfo info : activities) {
            if (info.activityInfo == null || TextUtils.isEmpty(info.activityInfo.packageName)
                    || TextUtils.isEmpty(info.activityInfo.name)) {
                continue;
            }
            String label = String.valueOf(info.loadLabel(pm));
            if (TextUtils.isEmpty(label)) {
                label = info.activityInfo.packageName;
            }
            apps.add(new AppEntry(label, info.activityInfo.packageName, info.activityInfo.name,
                    info.loadIcon(pm)));
        }

        Collections.sort(apps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry left, AppEntry right) {
                return collator.compare(left.label, right.label);
            }
        });
        return apps;
    }

    private void launchApp(AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setClassName(app.packageName, app.activityName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivitySafe(intent, "无法打开 " + app.label);
    }

    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(ui(26), ui(14), ui(26), ui(18));
        card.setBackground(new RoundRectDrawable(0xff3f3f3f, ui(18)));
        return card;
    }

    private View appButton(String label, String icon, View.OnClickListener listener) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(new RoundRectDrawable(0x00303030, ui(16)));
        button.setOnClickListener(listener);

        ImageView image = new ImageView(this);
        image.setImageDrawable(new AppIconDrawable(icon));
        button.addView(image, new LinearLayout.LayoutParams(ui(112), ui(112)));

        TextView text = text(label, 18, 0xffffffff, true);
        text.setGravity(Gravity.CENTER);
        button.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui(46)));
        return button;
    }

    private TextView squareButton(String label, View.OnClickListener listener) {
        TextView button = text(label, 28, 0xffffffff, false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(new RoundRectDrawable(0xff454545, ui(14)));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView smallStatusText(String value) {
        TextView text = text(value, 16, 0xffffffff, true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        return text;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(sp);
        text.setIncludeFontPadding(false);
        if (bold) {
            text.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        }
        return text;
    }

    private TextView pillText(String value, int sp, boolean bold) {
        TextView text = text(value, sp, 0xffffffff, bold);
        text.setGravity(Gravity.CENTER);
        text.setBackground(new RoundRectDrawable(0x66454545, ui(24)));
        return text;
    }

    private void addGap(LinearLayout parent, int dp, int height) {
        parent.addView(new Space(this), new LinearLayout.LayoutParams(ui(dp), ui(height)));
    }

    private LinearLayout.LayoutParams wrapContent() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void registerSystemReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(ACTION_VOLUME_CHANGED);
        registerReceiver(mSystemReceiver, filter);
    }

    private void unregisterReceiverQuietly() {
        try {
            unregisterReceiver(mSystemReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void updateClock() {
        Date now = new Date();
        mTimeText.setText(mTimeFormat.format(now));
        mDateText.setText(mDateFormat.format(now) + "   " + mWeekFormat.format(now));
    }

    private void updateConnectivity() {
        mWifiText.setText(getWifiLabel());
        mBluetoothText.setText(isBluetoothConnected() ? "已连接" : "未连接");
    }

    private String getWifiLabel() {
        if (mConnectivityManager != null) {
            Network network = mConnectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = mConnectivityManager.getNetworkCapabilities(network);
            if (capabilities == null || !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return "未连接";
            }
        }

        if (mWifiManager == null) {
            return "WiFi";
        }
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if (wifiInfo == null) {
            return "已连接";
        }
        String ssid = wifiInfo.getSSID();
        if (TextUtils.isEmpty(ssid) || "<unknown ssid>".equals(ssid)) {
            return "已连接";
        }
        return ssid.replace("\"", "");
    }

    private boolean isBluetoothConnected() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled()
                && adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP)
                == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
    }

    private void updateVolume() {
        if (mAudioManager == null) {
            return;
        }
        int current = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int max = Math.max(1, mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int percent = Math.round(current * 100f / max);
        mVolumeText.setText(String.valueOf(percent));
        mVolumeDialView.setValue(percent);
    }

    private void adjustVolume(int direction) {
        if (mAudioManager == null) {
            return;
        }
        mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction,
                AudioManager.FLAG_PLAY_SOUND);
        updateVolume();
    }

    private void updateMedia() {
        if (mMediaSessionManager == null) {
            return;
        }
        try {
            List<MediaController> controllers = mMediaSessionManager.getActiveSessions(null);
            for (MediaController controller : controllers) {
                if (controller.getMetadata() == null) {
                    continue;
                }
                CharSequence title = controller.getMetadata().getText(
                        android.media.MediaMetadata.METADATA_KEY_TITLE);
                CharSequence artist = controller.getMetadata().getText(
                        android.media.MediaMetadata.METADATA_KEY_ARTIST);
                if (!TextUtils.isEmpty(title)) {
                    mMusicTitleText.setText(title);
                    mMusicArtistText.setText(TextUtils.isEmpty(artist) ? controller.getPackageName() : artist);
                    return;
                }
            }
        } catch (SecurityException ignored) {
        }
    }

    private void launchFirstAvailableMusicApp() {
        for (String pkg : MUSIC_PACKAGES) {
            if (launchPackage(pkg)) {
                return;
            }
        }
        toast("未安装 QQ音乐、网易云音乐或酷狗音乐");
    }

    private boolean launchPackage(String packageName) {
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            return false;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launchIntent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    private void startActivitySafe(Intent intent, String error) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast(error);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void enterFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int ui(int value) {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        float scale = Math.min(metrics.widthPixels / 1280f, metrics.heightPixels / 800f);
        return Math.round(value * scale);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getAppsDialogWidth() {
        return Math.min(getResources().getDisplayMetrics().widthPixels - dp(72), dp(980));
    }

    private static class AppEntry {
        final String label;
        final String packageName;
        final String activityName;
        final Drawable icon;

        AppEntry(String label, String packageName, String activityName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.activityName = activityName;
            this.icon = icon;
        }
    }
}
