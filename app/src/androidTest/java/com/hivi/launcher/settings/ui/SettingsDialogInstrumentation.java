package com.hivi.launcher.settings.ui;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hivi.launcher.R;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.main.ui.VolumeDialog;
import com.hivi.launcher.settings.model.SettingsModel;
import com.hivi.launcher.settings.presenter.SettingsPresenter;
import com.hivi.launcher.update.SystemUpdateInfo;
import com.hivi.launcher.wifi.model.WifiNetwork;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SettingsDialogInstrumentation extends Instrumentation {
    private final CountDownLatch mResumed = new CountDownLatch(1);
    private final StringBuilder mMeasurements = new StringBuilder();
    private MainActivity mActivity;
    private SettingsFragment mSettings;
    private boolean mCaptureScreenshots;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mCaptureScreenshots = arguments != null
                && Boolean.parseBoolean(arguments.getString("capture"));
        start();
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (activity instanceof MainActivity) {
            mActivity = (MainActivity) activity;
            mResumed.countDown();
        }
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            getTargetContext().startActivity(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            require(mResumed.await(20L, TimeUnit.SECONDS), "Launcher did not resume");
            runOnMainSync(() -> {
                mActivity.showPage(MainPage.SETTINGS);
                mActivity.getFragmentManager().executePendingTransactions();
                mSettings = (SettingsFragment) mActivity.getFragmentManager()
                        .findFragmentByTag(MainPage.SETTINGS.name());
                mSettings.renderSettingsSection(SettingsPresenter.SECTION_DISPLAY);
            });
            waitForIdleSync();
            awaitSettingsLayout();
            verifyVolume();
            verifyPopup(true, "language");
            verifyPopup(false, "screen-saver");
            runOnMainSync(() -> ((android.widget.ScrollView) mSettings.getView()
                    .findViewById(R.id.settings_panel_display)).scrollTo(0, dp(144)));
            waitForIdleSync();
            verifyPopup(false, "screen-saver-scrolled");
            verifyWifiPassword();
            verifyProductInformation();
            verifyFactoryReset();
            verifyLogUpload();
            verifySystemUpdate();
            result.putString("stream", "Dialog height verification passed: " + mMeasurements + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable throwable) {
            result.putString("stream", "Dialog height verification failed: " + throwable
                    + "; " + mMeasurements + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyPopup(boolean language, String name) throws Exception {
        runOnMainSync(() -> mSettings.renderDisplaySettings(SettingsModel.LANGUAGE_CHINESE,
                language, SettingsModel.SCREEN_SAVER_TIMEOUT_ONE_MINUTE, !language,
                SettingsModel.SCREEN_SAVER_STYLE_SIMPLE));
        waitForIdleSync();
        runOnMainSync(() -> {
            PopupWindow popup = field("mDisplayOptionsPopupWindow", PopupWindow.class);
            require(popup != null && popup.isShowing(), name + " popup did not open");
            LinearLayout content = (LinearLayout) popup.getContentView();
            record(name, content);
            assertContentHeight(content);
            require(content.getChildCount() == (language ? 3 : 9), "Missing popup options");
            assertFullyVisible(content.getChildAt(content.getChildCount() - 1), content);
            int[] location = new int[2];
            content.getLocationOnScreen(location);
            Rect frame = new Rect();
            content.getWindowVisibleDisplayFrame(frame);
            require(location[1] >= frame.top
                    && location[1] + content.getHeight() <= frame.bottom,
                    "Popup must stay within the visible display area");
        });
        capture(name);
        runOnMainSync(() -> field("mDisplayOptionsPopupWindow", PopupWindow.class).dismiss());
        waitForIdleSync();
    }

    private void verifyWifiPassword() throws Exception {
        Constructor<WifiNetwork> constructor = WifiNetwork.class.getDeclaredConstructor(
                String.class, String.class, int.class, boolean.class, boolean.class,
                boolean.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        WifiNetwork network = constructor.newInstance("HiVi M5A_ABD6", "[WPA2-PSK]", -50,
                true, false, false, false, false);
        runOnMainSync(() -> {
            mSettings.renderSettingsSection(SettingsPresenter.SECTION_NETWORK);
            mSettings.showWifiPasswordDialog(network);
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            Dialog dialog = field("mWifiPasswordDialog", Dialog.class);
            LinearLayout content = (LinearLayout) dialog.findViewById(R.id.wifi_password_title)
                    .getParent();
            assertContentHeight(content);
            require(content.getWidth() == dp(690), "Wi-Fi password width changed");
            require(content.getHeight() < dp(350), "Wi-Fi password still has excess blank space");
            assertFullyVisible(dialog.findViewById(R.id.wifi_password_cancel), content);
            record("wifi-password", content);
        });
        capture("wifi-password");
        runOnMainSync(() -> field("mWifiPasswordDialog", Dialog.class).dismiss());
        waitForIdleSync();
    }

    private void verifyProductInformation() throws Exception {
        runOnMainSync(() -> {
            mSettings.renderSettingsSection(SettingsPresenter.SECTION_ABOUT);
            mSettings.getView().findViewById(R.id.settings_product_information).performClick();
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            Dialog dialog = field("mProductInformationDialog", Dialog.class);
            View content = (View) dialog.findViewById(R.id.product_information_close).getParent();
            assertSize(content, 460, 225);
            assertFullyVisible(dialog.findViewById(R.id.product_information_close), content);
            record("product-information", content);
        });
        capture("product-information");
        runOnMainSync(() -> field("mProductInformationDialog", Dialog.class).dismiss());
        waitForIdleSync();
    }

    private void verifyFactoryReset() throws Exception {
        runOnMainSync(() -> {
            mSettings.renderSettingsSection(SettingsPresenter.SECTION_MAINTENANCE);
            mSettings.showFactoryResetConfirmation();
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            Dialog dialog = field("mFactoryResetDialog", Dialog.class);
            View content = (View) dialog.findViewById(R.id.factory_reset_final_confirmation_content)
                    .getParent();
            assertSize(content, 620, 330);
            assertFullyVisible(dialog.findViewById(R.id.factory_reset_final_confirmation_confirm),
                    content);
            record("factory-reset", content);
        });
        capture("factory-reset");
        runOnMainSync(() -> mSettings.showFactoryResetProgress(50, "Height verification"));
        waitForIdleSync();
        runOnMainSync(() -> {
            Dialog dialog = field("mFactoryResetDialog", Dialog.class);
            View content = (View) dialog.findViewById(R.id.factory_reset_progress_content).getParent();
            assertSize(content, 620, 330);
            assertFullyVisible(dialog.findViewById(R.id.factory_reset_progress_bar), content);
            dialog.dismiss();
        });
    }

    private void verifyVolume() throws Exception {
        int[] adjustments = new int[1];
        int[] changedVolume = {-1};
        int[] muteRequests = new int[1];
        int[] dismissals = new int[1];
        VolumeDialog volume = new VolumeDialog(mActivity, new VolumeDialog.Listener() {
            @Override
            public void onVolumeAdjusted(int direction) {
                adjustments[0] = direction;
            }

            @Override
            public void onVolumeChanged(int volumePercent) {
                changedVolume[0] = volumePercent;
            }

            @Override
            public void onMuteToggleRequested() {
                muteRequests[0]++;
            }

            @Override
            public void onDialogDismissed() {
                dismissals[0]++;
            }
        });
        runOnMainSync(() -> volume.show(50, false));
        waitForIdleSync();
        Dialog original = field(volume, "mDialog", Dialog.class);
        runOnMainSync(() -> {
            LinearLayout content = volumeContent(volume);
            assertVolumeLayout(content);
            assertVolumeState(volume, 50, false);
            original.findViewById(R.id.volume_down_button).performClick();
            require(adjustments[0] == -1, "Volume down must request a lower volume");
            original.findViewById(R.id.volume_up_button).performClick();
            require(adjustments[0] == 1, "Volume up must request a higher volume");
            original.findViewById(R.id.volume_mute_button).performClick();
            require(muteRequests[0] == 1, "Mute must notify its listener");
            require(changedVolume[0] == -1, "Rendering volume must not change the amplifier");
            record("volume", content);
        });
        capture("volume");
        int[] location = new int[2];
        int[] sliderWidth = new int[1];
        runOnMainSync(() -> {
            SeekBar slider = original.findViewById(R.id.volume_seek_bar);
            slider.getLocationOnScreen(location);
            location[1] += slider.getHeight() / 2;
            sliderWidth[0] = slider.getWidth();
        });
        long gestureStart = SystemClock.uptimeMillis();
        for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP}) {
            float position = action == MotionEvent.ACTION_DOWN ? 0.5f : 0.75f;
            MotionEvent event = MotionEvent.obtain(gestureStart, SystemClock.uptimeMillis(),
                    action, location[0] + sliderWidth[0] * position, location[1], 0);
            sendPointerSync(event);
            event.recycle();
        }
        waitForIdleSync();
        runOnMainSync(() -> {
            require(changedVolume[0] >= 70 && changedVolume[0] <= 80,
                    "Dragging the volume slider must notify its listener");
            volume.show(100, false);
            require(field(volume, "mDialog", Dialog.class) == original,
                    "Volume changes must reuse the open dialog");
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            assertVolumeState(volume, 100, false);
            assertVolumeLayout(volumeContent(volume));
        });
        capture("volume-max");
        runOnMainSync(() -> volume.updateVolume(50, true));
        waitForIdleSync();
        runOnMainSync(() -> {
            assertVolumeState(volume, 0, true);
            assertVolumeLayout(volumeContent(volume));
        });
        capture("volume-muted");
        runOnMainSync(() -> {
            volume.updateVolume(-10, false);
            assertVolumeState(volume, 0, false);
            volume.updateVolume(110, false);
            assertVolumeState(volume, 100, false);
            volume.dismiss();
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            require(!volume.isShowing() && dismissals[0] == 1,
                    "Closing volume must release the dialog and notify its listener");
            volume.show(50, false);
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            assertVolumeLayout(volumeContent(volume));
            volume.dismiss();
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            require(dismissals[0] == 2, "Reopened volume dialog must dismiss normally");
            for (Locale locale : new Locale[] {Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH}) {
                for (float scale : new float[] {1f, 1.3f, 1.6f}) {
                    Configuration configuration = new Configuration(
                            mActivity.getResources().getConfiguration());
                    configuration.setLocale(locale);
                    configuration.fontScale = scale;
                    LinearLayout content = (LinearLayout) LayoutInflater.from(
                            mActivity.createConfigurationContext(configuration))
                            .inflate(R.layout.dialog_volume, null);
                    ((TextView) content.findViewById(R.id.volume_value)).setText("100");
                    content.measure(View.MeasureSpec.makeMeasureSpec(dp(500),
                                    View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    content.layout(0, 0, content.getMeasuredWidth(), content.getMeasuredHeight());
                    assertVolumeLayout(content);
                }
            }
        });
    }

    private LinearLayout volumeContent(VolumeDialog volume) {
        Dialog dialog = field(volume, "mDialog", Dialog.class);
        return (LinearLayout) dialog.findViewById(R.id.volume_value).getParent().getParent();
    }

    private void assertVolumeLayout(LinearLayout content) {
        assertContentHeight(content);
        require(content.getWidth() == dp(500), "Volume dialog must retain its 500dp width");
        for (int index = 0; index < content.getChildCount(); index++) {
            ViewGroup row = (ViewGroup) content.getChildAt(index);
            assertFullyVisible(row, content);
            for (int child = 0; child < row.getChildCount(); child++) {
                assertFullyVisible(row.getChildAt(child), content);
            }
        }
        for (int control : new int[] {R.id.volume_mute_button, R.id.volume_down_button,
                R.id.volume_up_button, R.id.volume_seek_bar}) {
            View target = content.findViewById(control);
            require(target.getWidth() >= dp(48) && target.getHeight() >= dp(48),
                    "Volume touch targets must be at least 48dp");
        }
    }

    private void assertVolumeState(VolumeDialog volume, int expected, boolean muted) {
        LinearLayout content = volumeContent(volume);
        TextView value = content.findViewById(R.id.volume_value);
        SeekBar slider = content.findViewById(R.id.volume_seek_bar);
        require(value.getText().toString().equals(String.valueOf(expected))
                        && slider.getProgress() == expected,
                "Volume value and slider must stay in sync");
        require(content.findViewById(R.id.volume_down_button).isEnabled() == (expected > 0)
                        && content.findViewById(R.id.volume_up_button).isEnabled() == (expected < 100),
                "Volume limits must disable the matching button");
        View mute = content.findViewById(R.id.volume_mute_button);
        require(mute.isSelected() == muted && mute.getContentDescription().equals(
                        mActivity.getString(muted ? R.string.main_volume_unmute
                                : R.string.main_volume_mute)),
                "Mute state and accessibility label must stay in sync");
        Rect thumb = slider.getThumb().getBounds();
        int thumbLeft = slider.getPaddingLeft() - slider.getThumbOffset() + thumb.left;
        require(thumbLeft >= 0 && thumbLeft + thumb.width() <= slider.getWidth(),
                "The slider thumb must remain fully visible at both ends");
    }

    private void verifyLogUpload() throws Exception {
        runOnMainSync(() -> {
            mSettings.renderSettingsSection(SettingsPresenter.SECTION_SYSTEM);
            mSettings.showLogUploadConfirmation();
        });
        verifyLogUploadState("log-confirmation", R.id.log_upload_confirmation_content);
        Dialog original = field("mLogUploadDialog", Dialog.class);
        runOnMainSync(() -> mSettings.showLogUploadProgress(50,
                getTargetContext().getString(R.string.log_upload_preparing)));
        verifyLogUploadState("log-progress", R.id.log_upload_progress_content);
        runOnMainSync(() -> {
            Dialog dialog = field("mLogUploadDialog", Dialog.class);
            require(dialog == original, "Log upload state changes must reuse the dialog");
            require(((ProgressBar) dialog.findViewById(R.id.log_upload_progress_bar))
                    .getProgress() == 50, "Log upload progress must be preserved");
            mSettings.showLogUploadSuccess();
        });
        verifyLogUploadState("log-success", R.id.log_upload_success_content);
        long deadline = SystemClock.uptimeMillis() + 6000L;
        boolean[] dismissed = new boolean[1];
        while (!dismissed[0] && SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> dismissed[0] = field("mLogUploadDialog", Dialog.class) == null);
            if (!dismissed[0]) {
                SystemClock.sleep(10L);
            }
        }
        require(dismissed[0], "Log upload success must dismiss automatically");
        runOnMainSync(() -> mSettings.showLogUploadProgress(50,
                getTargetContext().getString(R.string.log_upload_preparing)));
        verifyLogUploadState("log-progress-direct", R.id.log_upload_progress_content);
        runOnMainSync(() -> field("mLogUploadDialog", Dialog.class).dismiss());
        runOnMainSync(mSettings::showLogUploadSuccess);
        verifyLogUploadState("log-success-direct", R.id.log_upload_success_content);
        runOnMainSync(() -> field("mLogUploadDialog", Dialog.class).dismiss());
    }

    private void verifyLogUploadState(String name, int contentId) throws Exception {
        waitForIdleSync();
        runOnMainSync(() -> {
            Dialog dialog = field("mLogUploadDialog", Dialog.class);
            require(dialog != null && dialog.isShowing(), "Log upload dialog did not open");
            ViewGroup panel = dialog.findViewById(contentId);
            View content = (View) panel.getParent();
            assertSize(content, 520, 280);
            for (int index = 0; index < panel.getChildCount(); index++) {
                assertFullyVisible(panel.getChildAt(index), content);
            }
            record(name, content);
        });
        capture(name);
    }

    private void verifySystemUpdate() throws Exception {
        runOnMainSync(() -> mSettings.showSystemUpdateConfirmation(
                SystemUpdateInfo.currentVersion("1.0.0", 1L)));
        waitForIdleSync();
        runOnMainSync(() -> {
            Dialog dialog = field("mSystemUpdateConfirmationDialog", Dialog.class);
            View content = (View) dialog.findViewById(R.id.system_update_confirmation_message)
                    .getParent();
            assertSize(content, 460, 225);
            assertFullyVisible(dialog.findViewById(R.id.system_update_confirmation_confirm), content);
            record("update-confirmation", content);
        });
        capture("update-confirmation");
        runOnMainSync(() -> {
            field("mSystemUpdateConfirmationDialog", Dialog.class).dismiss();
            mSettings.showSystemUpdateProgress(50,
                    getTargetContext().getString(R.string.log_upload_preparing));
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            Dialog dialog = field("mSystemUpdateProgressDialog", Dialog.class);
            LinearLayout content = (LinearLayout) dialog.findViewById(R.id.system_update_progress_bar)
                    .getParent();
            assertSize(content, 620, 330);
            for (int index = 0; index < content.getChildCount(); index++) {
                assertFullyVisible(content.getChildAt(index), content);
            }
            record("update-progress", content);
        });
        capture("update-progress");
        getUiAutomation();
        runOnMainSync(() -> {
            mSettings.dismissSystemUpdateProgress();
            SystemUpdateSuccessDialog.show(mActivity);
        });
        waitForIdleSync();
        AccessibilityNodeInfo root = null;
        long deadline = SystemClock.uptimeMillis() + 4000L;
        String title = getTargetContext().getString(R.string.system_update_success_title);
        while (root == null && SystemClock.uptimeMillis() < deadline) {
            root = getUiAutomation().getRootInActiveWindow();
            if (root != null && root.findAccessibilityNodeInfosByText(title).isEmpty()) {
                root.recycle();
                root = null;
            }
            if (root == null) {
                SystemClock.sleep(10L);
            }
        }
        require(root != null, "System update success window did not open");
        try {
            Rect bounds = new Rect();
            root.getBoundsInScreen(bounds);
            require(bounds.width() == dp(600) && bounds.height() == dp(250),
                    "System update success must keep its 600x250dp design size: " + bounds);
            mMeasurements.append("update-success=").append(bounds.width())
                    .append('x').append(bounds.height()).append("px; ");
        } finally {
            root.recycle();
        }
        capture("update-success");
    }

    private void assertContentHeight(LinearLayout content) {
        int expected = content.getPaddingTop() + content.getPaddingBottom();
        for (int index = 0; index < content.getChildCount(); index++) {
            View child = content.getChildAt(index);
            if (child.getVisibility() != View.GONE) {
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                expected += child.getMeasuredHeight() + params.topMargin + params.bottomMargin;
            }
        }
        require(content.getBackground().getMinimumHeight() == 0,
                "Bitmap background must not impose its original height");
        require(content.getHeight() == expected, "Content height " + content.getHeight()
                + " does not match its children: " + expected);
    }

    private void assertSize(View content, int width, int height) {
        require(content.getWidth() == dp(width) && content.getHeight() == dp(height),
                "Expected " + width + "x" + height + "dp, got "
                        + content.getWidth() + "x" + content.getHeight() + "px");
    }

    private static void assertFullyVisible(View child, View content) {
        Rect bounds = new Rect(0, 0, child.getWidth(), child.getHeight());
        ((ViewGroup) content).offsetDescendantRectToMyCoords(child, bounds);
        require(child.getVisibility() == View.VISIBLE
                        && (content.getParent() == null || child.isShown())
                        && child.getWidth() > 0 && child.getHeight() > 0
                        && bounds.left >= 0 && bounds.right <= content.getWidth()
                        && bounds.top >= 0 && bounds.bottom <= content.getHeight(),
                "Dialog controls must not be clipped");
        if (child instanceof TextView) {
            TextView text = (TextView) child;
            require(text.getLayout() != null && text.getHeight() >= text.getLayout().getHeight()
                            + text.getCompoundPaddingTop() + text.getCompoundPaddingBottom(),
                    "Dialog text must not be clipped: " + text.getText());
        }
    }

    private void record(String name, View content) {
        mMeasurements.append(name).append('=')
                .append(content.getWidth()).append('x').append(content.getHeight()).append("px; ");
    }

    private void capture(String name) throws Exception {
        if (!mCaptureScreenshots) {
            return;
        }
        SystemClock.sleep(250L);
        waitForIdleSync();
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        require(screenshot != null, "Unable to capture " + name);
        File output = new File(getTargetContext().getExternalFilesDir(null),
                "dialog-height-" + name + ".png");
        mMeasurements.append(output.getAbsolutePath()).append("; ");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            require(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream),
                    "Unable to save " + name);
        } finally {
            screenshot.recycle();
        }
    }

    private <Value> Value field(String name, Class<Value> type) {
        return field(mSettings, name, type);
    }

    private <Value> Value field(Object target, String name, Class<Value> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect " + name, exception);
        }
    }

    private int dp(int value) {
        return Math.round(value * getTargetContext().getResources().getDisplayMetrics().density);
    }

    private void awaitSettingsLayout() {
        long deadline = SystemClock.uptimeMillis() + 6000L;
        boolean[] ready = new boolean[1];
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> {
                View container = mActivity.findViewById(R.id.fragment_container);
                ready[0] = container.getAlpha() == 1f && container.getTranslationX() == 0f
                        && mSettings.getView().isLaidOut()
                        && !mSettings.getView().isLayoutRequested();
            });
            if (!ready[0]) {
                SystemClock.sleep(10L);
            }
        }
        require(ready[0], "Settings layout did not settle");
    }

    @Override
    public void runOnMainSync(Runnable action) {
        Throwable[] failure = new Throwable[1];
        super.runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            }
        });
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
