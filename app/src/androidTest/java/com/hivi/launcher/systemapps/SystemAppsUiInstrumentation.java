package com.hivi.launcher.systemapps;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.hivi.launcher.R;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.systemapps.model.AppDownloadState;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.model.SupportedApp;
import com.hivi.launcher.systemapps.ui.SystemAppsFragment;
import com.hivi.launcher.systemapps.ui.HeaderButtonBackgroundDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SystemAppsUiInstrumentation extends Instrumentation {
    private final CountDownLatch mActivityResumed = new CountDownLatch(1);
    private MainActivity mActivity;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (activity instanceof MainActivity) {
            mActivity = (MainActivity) activity;
            mActivityResumed.countDown();
        }
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            getTargetContext().startActivity(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            require(mActivityResumed.await(20L, TimeUnit.SECONDS), "Launcher did not resume");
            MainActivity activity = mActivity;
            waitForIdleSync();
            runOnMainSync(() -> {
                activity.showPage(MainPage.SYSTEM_APPS);
                activity.getFragmentManager().executePendingTransactions();
                View container = activity.findViewById(R.id.fragment_container);
                container.animate().cancel();
                container.setTranslationX(0f);
                container.setAlpha(1f);
            });
            waitForIdleSync();
            View decor = activity.getWindow().getDecorView();
            runOnMainSync(() -> assertCatalog(decor));
            capture("catalog.png");
            runOnMainSync(this::assertHeaderDesignParameters);
            capturePressedButton(decor.findViewById(R.id.apps_back_button), "back-pressed.png");
            capturePressedButton(decor.findViewById(R.id.apps_volume_button), "volume-pressed.png");
            capture("header-released.png");
            runOnMainSync(() -> renderStates(activity));
            waitForIdleSync();
            capture("download-states.png");
            runOnMainSync(() -> {
                GridLayout grid = activity.findViewById(R.id.apps_grid);
                for (int index = 0; index < grid.getChildCount(); index++) {
                    assertLogoOverlay(grid.getChildAt(index));
                }
            });
            runOnMainSync(() -> {
                SystemAppsFragment fragment = (SystemAppsFragment) activity.getFragmentManager()
                        .findFragmentById(R.id.fragment_container);
                fragment.closePage();
            });
            waitForIdleSync();
            result.putString("stream", "Nine-app catalog, cloud badges, black inset pressed states "
                    + "for back and volume, unchanged alpha contours and outer shadows, design parameters and "
                    + "all download state layouts passed. Screenshots: files/system-app-ui-tests\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable throwable) {
            result.putString("stream", "App UI verification failed: " + throwable + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void assertHeaderDesignParameters() {
        android.content.res.Resources resources = getTargetContext().getResources();
        float density = resources.getDisplayMetrics().density;
        require(Math.abs(resources.getDimension(R.dimen.system_apps_header_design_height)
                / density - 52f) < 0.01f, "Header design height must be 52dp");
        require(Math.abs(resources.getDimension(R.dimen.system_apps_header_pressed_corner_radius)
                / density - 31f) < 0.01f, "Pressed corner radius must be 31dp");
        require(Math.abs(resources.getDimension(R.dimen.system_apps_header_pressed_stroke_width)
                / density - 12f) < 0.01f, "Inner stroke width must be 12dp");
        require(Math.abs(resources.getDimension(R.dimen.system_apps_header_pressed_blur_radius)
                / density - 9f) < 0.01f, "Gaussian blur radius must be 9dp");
        require(Math.abs(resources.getFraction(R.fraction.system_apps_header_pressed_opacity, 1, 1)
                - 0.38f) < 0.001f, "Pressed fill opacity must be 38 percent");
    }

    private void capturePressedButton(View button, String name) throws Exception {
        int[] position = new int[2];
        runOnMainSync(() -> button.getLocationOnScreen(position));
        int width = button.getWidth();
        int height = button.getHeight();
        float centerX = position[0] + width / 2f;
        float centerY = position[1] + height / 2f;
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_DOWN,
                centerX, centerY, 0);
        sendPointerSync(down);
        down.recycle();
        waitForIdleSync();
        runOnMainSync(() -> {
            require(button.isPressed(), "Header button must expose a pressed state");
            require(button.getBackground() instanceof HeaderButtonBackgroundDrawable,
                    "Both header buttons must share the drawn inner-shadow background");
            require(button.getWidth() == width && button.getHeight() == height,
                    "Pressing must not resize the button");
            require(button.getAlpha() == 1f, "Pressing must not dim button content");
            assertBlackPressedPixels(button.getBackground(), width, height);
        });
        capture(name);
        MotionEvent cancel = MotionEvent.obtain(eventTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, centerX, centerY, 0);
        sendPointerSync(cancel);
        cancel.recycle();
        waitForIdleSync();
        runOnMainSync(() -> require(!button.isPressed(),
                "Cancelling a press must restore the normal background"));
    }

    private void assertBlackPressedPixels(Drawable background, int width, int height) {
        int[] originalState = background.getState().clone();
        Bitmap normal = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap pressed = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            background.setState(new int[] {android.R.attr.state_enabled});
            background.draw(new Canvas(normal));
            background.setState(new int[] {android.R.attr.state_enabled, android.R.attr.state_pressed});
            background.draw(new Canvas(pressed));
            int centerColor = pressed.getPixel(width / 2, height / 2);
            int normalColor = normal.getPixel(width / 2, height / 2);
            require(Color.red(centerColor) < Color.red(normalColor) - 8,
                    "Pressed state must darken the capsule");
            for (int sample = 1; sample <= 3; sample++) {
                int color = pressed.getPixel(width / 2, height * sample / 4);
                require(Math.abs(Color.red(color) - Color.green(color)) <= 2
                        && Math.abs(Color.green(color) - Color.blue(color)) <= 2,
                        "Pressed state must be black, without a blue highlight");
            }
            require(normal.getPixel(0, 0) == pressed.getPixel(0, 0),
                    "Inner shadow must remain clipped inside the capsule");
            int shadowPixels = 0;
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    int normalPixel = normal.getPixel(column, row);
                    int pressedPixel = pressed.getPixel(column, row);
                    int alpha = Color.alpha(normalPixel);
                    require(alpha == Color.alpha(pressedPixel),
                            "Pressed contour must retain the exact normal width, height and alpha");
                    if (alpha < 128) {
                        require(normalPixel == pressedPixel,
                                "Pressing must preserve transparent margins and the original outer shadow");
                        if (alpha > 0) {
                            shadowPixels++;
                        }
                    }
                }
            }
            require(shadowPixels > 0, "Contour regression must cover the PNG's soft shadow");
        } finally {
            background.setState(originalState);
            normal.recycle();
            pressed.recycle();
        }
    }

    private void assertCatalog(View decor) {
        GridLayout grid = decor.findViewById(R.id.apps_grid);
        require(grid.getChildCount() == 9, "Only nine apps may be visible");
        require(grid.getColumnCount() == 5, "App page must retain five columns");
        for (int index = 0; index < SupportedApp.values().length; index++) {
            TextView label = grid.getChildAt(index).findViewById(R.id.app_label);
            require(label.getText().toString().equals(getTargetContext().getString(
                    SupportedApp.values()[index].getLabelResId())), "Catalog order must be fixed");
            assertLogoOverlay(grid.getChildAt(index));
        }
        require(decor.findViewById(R.id.top_bar).getVisibility() == View.GONE,
                "Original status bar must be hidden");
        require(decor.findViewById(R.id.bottom_navigation).getVisibility() == View.GONE,
                "Original app navigation must be hidden");
    }

    private void assertLogoOverlay(View item) {
        ImageView icon = item.findViewById(R.id.app_icon);
        ImageView cloud = item.findViewById(R.id.app_download_icon);
        require(icon.getDrawable() instanceof BitmapDrawable,
                "App logos must use the supplied PNG assets");
        require(cloud.getParent() == icon.getParent(),
                "Download icon must overlay the logo, not the app name");
        if (cloud.getVisibility() == View.VISIBLE) {
            int[] iconPosition = new int[2];
            int[] cloudPosition = new int[2];
            icon.getLocationOnScreen(iconPosition);
            cloud.getLocationOnScreen(cloudPosition);
            require(Math.abs(iconPosition[0] * 2 + icon.getWidth()
                    - cloudPosition[0] * 2 - cloud.getWidth()) <= 2,
                    "Download icon must be horizontally centered on the logo");
            require(Math.abs(iconPosition[1] * 2 + icon.getHeight()
                    - cloudPosition[1] * 2 - cloud.getHeight()) <= 2,
                    "Download icon must be vertically centered on the logo");
            require(cloud.getWidth() < icon.getWidth(),
                    "Download icon must fit within the logo");
            require(cloud.getAlpha() == 1f && icon.getAlpha() < 1f,
                    "Only the logo should be dimmed, not the download icon");
        }
    }

    private void renderStates(MainActivity activity) {
        AppDownloadState[] states = {
                AppDownloadState.NOT_INSTALLED, AppDownloadState.WAITING,
                AppDownloadState.DOWNLOADING, AppDownloadState.PAUSED,
                AppDownloadState.INSTALLING, AppDownloadState.FAILED,
                AppDownloadState.INSTALLED, AppDownloadState.DOWNLOADING,
                AppDownloadState.PAUSED
        };
        List<AppEntry> apps = new ArrayList<>();
        for (int index = 0; index < SupportedApp.values().length; index++) {
            SupportedApp app = SupportedApp.values()[index];
            apps.add(new AppEntry(app, activity.getString(app.getLabelResId()), "", "",
                    activity.getDrawable(app.getIconResId()), states[index], index == 7 ? -1 : 37));
        }
        SystemAppsFragment fragment = (SystemAppsFragment) activity.getFragmentManager()
                .findFragmentById(R.id.fragment_container);
        fragment.showApps(apps);
        GridLayout grid = activity.findViewById(R.id.apps_grid);
        for (int index = 0; index < states.length; index++) {
            View item = grid.getChildAt(index);
            ImageView cloud = item.findViewById(R.id.app_download_icon);
            View progress = item.findViewById(R.id.app_download_progress);
            TextView status = item.findViewById(R.id.app_status);
            boolean hasCloud = states[index] == AppDownloadState.NOT_INSTALLED
                    || states[index] == AppDownloadState.FAILED;
            boolean hasProgress = states[index] == AppDownloadState.WAITING
                    || states[index] == AppDownloadState.DOWNLOADING
                    || states[index] == AppDownloadState.PAUSED
                    || states[index] == AppDownloadState.INSTALLING;
            require((cloud.getVisibility() == View.VISIBLE) == hasCloud,
                    "Cloud badge does not match download state");
            require((progress.getVisibility() == View.VISIBLE) == hasProgress,
                    "Progress overlay does not match download state");
            require((status.getVisibility() == View.VISIBLE)
                    == (hasProgress || states[index] == AppDownloadState.FAILED),
                    "Status label does not match download state");
        }
    }

    private void capture(String name) throws Exception {
        CountDownLatch rendered = new CountDownLatch(1);
        runOnMainSync(() -> mActivity.getWindow().getDecorView().postOnAnimation(
                () -> mActivity.getWindow().getDecorView().postOnAnimation(rendered::countDown)));
        require(rendered.await(3L, TimeUnit.SECONDS), "UI frame did not render");
        File directory = new File(getTargetContext().getFilesDir(), "system-app-ui-tests");
        require(directory.isDirectory() || directory.mkdirs(), "Cannot create screenshot directory");
        Bitmap screenshot = getUiAutomation().takeScreenshot();
        require(screenshot != null, "Screenshot capture failed");
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            screenshot.recycle();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
