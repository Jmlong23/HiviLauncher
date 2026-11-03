package com.hivi.launcher.systemapps;

import android.app.Instrumentation;
import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
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
import com.hivi.launcher.settings.ui.SettingsFragment;
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
import java.util.function.BooleanSupplier;

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
            waitForIdleSync();
            awaitCondition(() -> mActivity.hasWindowFocus());
            MainActivity activity = mActivity;
            runOnMainSync(activity::showHomePage);
            awaitPage(false);
            for (int iteration = 0; iteration < 3; iteration++) {
                verifyHomeTransition(true);
                if (iteration < 2) {
                    verifyHomeTransition(false);
                }
            }
            verifyHomeTransition(false);
            runOnMainSync(() -> {
                activity.showPage(MainPage.SYSTEM_APPS);
                activity.showPage(MainPage.SYSTEM_APPS);
                activity.getFragmentManager().executePendingTransactions();
                require(activity.getFragmentManager().getBackStackEntryCount() == 1,
                        "Repeated taps must not enqueue duplicate app pages");
                activity.navigateBack();
            });
            awaitPage(false);
            verifyPageStackAcrossContainers();
            verifyHomeTransition(true);
            runOnMainSync(() -> {
                activity.navigateBack();
                activity.showPage(MainPage.SYSTEM_APPS);
            });
            awaitPage(true);
            waitForIdleSync();
            View decor = activity.getWindow().getDecorView();
            runOnMainSync(() -> assertCatalog(decor));
            runOnMainSync(this::assertCatalogViewsAreReused);
            runOnMainSync(this::assertGridResizesWithoutRebuilding);
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
                        .findFragmentById(R.id.system_apps_container);
                fragment.closePage();
            });
            awaitPage(false);
            result.putString("stream", "Nine-app catalog, cloud badges, black inset pressed states "
                    + "for back and volume, unchanged alpha contours and outer shadows, design parameters and "
                    + "all download state layouts passed. Repeated page transitions keep home geometry, "
                    + "opaque page layers and app item identities stable. "
                    + "Screenshots: files/system-app-ui-tests\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable throwable) {
            result.putString("stream", "App UI verification failed: " + throwable + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyHomeTransition(boolean entering) throws Exception {
        View decor = mActivity.getWindow().getDecorView();
        View home = decor.findViewById(R.id.launcher_content);
        View cards = decor.findViewById(R.id.cards_row);
        View body = decor.findViewById(R.id.page_body);
        View container = decor.findViewById(R.id.system_apps_container);
        Rect[] originalBounds = new Rect[1];
        int[] originalPadding = new int[4];
        runOnMainSync(() -> {
            originalBounds[0] = bounds(cards);
            originalPadding[0] = home.getPaddingLeft();
            originalPadding[1] = home.getPaddingTop();
            originalPadding[2] = home.getPaddingRight();
            originalPadding[3] = home.getPaddingBottom();
            if (entering) {
                mActivity.showPage(MainPage.SYSTEM_APPS);
            } else {
                mActivity.navigateBack();
            }
        });
        CountDownLatch finished = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        long deadline = SystemClock.uptimeMillis() + 5000L;
        runOnMainSync(() -> decor.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                try {
                    require(originalBounds[0].equals(bounds(cards)),
                            "Home cards must not resize or jump during app navigation");
                    require(home.getPaddingLeft() == originalPadding[0]
                            && home.getPaddingTop() == originalPadding[1]
                            && home.getPaddingRight() == originalPadding[2]
                            && home.getPaddingBottom() == originalPadding[3],
                            "App navigation must not mutate the shared home padding");
                    require(body.getTranslationX() == 0f && body.getAlpha() == 1f,
                            "The home layer must remain stationary and opaque");
                    require(container.getAlpha() == 1f,
                            "The app page must not cross-fade with home content");
                    boolean complete = entering
                            ? home.getVisibility() == View.INVISIBLE
                            && container.getTranslationX() == 0f
                            : container.getVisibility() == View.GONE;
                    if (complete) {
                        if (entering) {
                            require(decor.findViewById(R.id.apps_grid).getWidth() > 0,
                                    "App items must be laid out before entry finishes");
                        }
                        finished.countDown();
                    } else if (SystemClock.uptimeMillis() < deadline) {
                        decor.postOnAnimation(this);
                    }
                } catch (Throwable throwable) {
                    failure[0] = throwable;
                    finished.countDown();
                }
            }
        }));
        require(finished.await(6L, TimeUnit.SECONDS), "App page transition did not finish");
        if (failure[0] != null) {
            throw new AssertionError("App transition regression", failure[0]);
        }
        waitForIdleSync();
    }

    private void awaitPage(boolean appsVisible) throws Exception {
        awaitCondition(() -> {
            View container = mActivity.findViewById(R.id.system_apps_container);
            View home = mActivity.findViewById(R.id.launcher_content);
            return appsVisible ? container.getVisibility() == View.VISIBLE
                    && container.getTranslationX() == 0f
                    && home.getVisibility() == View.INVISIBLE
                    : container.getVisibility() == View.GONE
                    && mActivity.findViewById(R.id.fragment_container).getVisibility() == View.GONE
                    && home.getVisibility() == View.VISIBLE;
        });
    }

    private void awaitCondition(BooleanSupplier condition) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        View decor = mActivity.getWindow().getDecorView();
        long deadline = SystemClock.uptimeMillis() + 5000L;
        runOnMainSync(() -> decor.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                if (condition.getAsBoolean()) {
                    ready.countDown();
                } else if (SystemClock.uptimeMillis() < deadline) {
                    decor.postOnAnimation(this);
                }
            }
        }));
        require(ready.await(6L, TimeUnit.SECONDS), "Page did not reach its settled state");
        waitForIdleSync();
    }

    private Rect bounds(View view) {
        return new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

    private void assertCatalogViewsAreReused() {
        SystemAppsFragment fragment = (SystemAppsFragment) mActivity.getFragmentManager()
                .findFragmentById(R.id.system_apps_container);
        GridLayout grid = mActivity.findViewById(R.id.apps_grid);
        View[] items = new View[grid.getChildCount()];
        for (int index = 0; index < items.length; index++) {
            items[index] = grid.getChildAt(index);
        }
        renderStates(mActivity);
        for (int index = 0; index < items.length; index++) {
            require(items[index] == grid.getChildAt(index),
                    "Refreshing app states must reuse existing grid items");
        }
        fragment.getView().requestLayout();
    }

    private void assertGridResizesWithoutRebuilding() {
        View root = mActivity.findViewById(R.id.apps_dialog_root);
        GridLayout grid = mActivity.findViewById(R.id.apps_grid);
        View firstItem = grid.getChildAt(0);
        int originalWidth = root.getWidth();
        int heightSpec = View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY);
        int smallerWidth = originalWidth * 3 / 4;
        root.measure(View.MeasureSpec.makeMeasureSpec(smallerWidth, View.MeasureSpec.EXACTLY),
                heightSpec);
        require(firstItem.findViewById(R.id.app_icon_frame).getLayoutParams().width
                == Math.round(136f * smallerWidth / 1280f),
                "App sizes must adapt in the measurement phase");
        require(grid.getChildAt(0) == firstItem,
                "Resizing the app page must not recreate its items");
        root.measure(View.MeasureSpec.makeMeasureSpec(originalWidth, View.MeasureSpec.EXACTLY),
                heightSpec);
        root.requestLayout();
    }

    private void verifyPageStackAcrossContainers() throws Exception {
        runOnMainSync(() -> mActivity.showPage(MainPage.SETTINGS));
        awaitCondition(() -> mActivity.getFragmentManager()
                .findFragmentById(R.id.fragment_container) instanceof SettingsFragment
                && mActivity.findViewById(R.id.fragment_container).getAlpha() == 1f
                && mActivity.findViewById(R.id.fragment_container).getTranslationX() == 0f);
        Fragment[] settings = new Fragment[1];
        runOnMainSync(() -> {
            settings[0] = mActivity.getFragmentManager().findFragmentById(R.id.fragment_container);
            mActivity.showPage(MainPage.SYSTEM_APPS);
        });
        awaitPage(true);
        runOnMainSync(mActivity::navigateBack);
        awaitCondition(() -> mActivity.findViewById(R.id.system_apps_container)
                .getVisibility() == View.GONE);
        runOnMainSync(() -> {
            require(mActivity.getFragmentManager().findFragmentById(R.id.fragment_container)
                    == settings[0], "Returning from apps must preserve the previous settings page");
            require(settings[0].isAdded(), "The previous page must remain on the back stack");
            mActivity.showPage(MainPage.SYSTEM_APPS);
        });
        awaitPage(true);
        runOnMainSync(mActivity::showHomePage);
        awaitPage(false);
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
        require(!decor.findViewById(R.id.top_bar).isShown(),
                "Original status bar must be hidden");
        require(!decor.findViewById(R.id.bottom_navigation).isShown(),
                "Original app navigation must be hidden");
        require(decor.findViewById(R.id.launcher_content).getParent()
                == decor.findViewById(R.id.system_apps_container).getParent(),
                "The app overlay must not share the padded home content container");
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
                AppDownloadState.INSTALL_FAILED
        };
        List<AppEntry> apps = new ArrayList<>();
        for (int index = 0; index < SupportedApp.values().length; index++) {
            SupportedApp app = SupportedApp.values()[index];
            apps.add(new AppEntry(app, activity.getString(app.getLabelResId()), "", "",
                    activity.getDrawable(app.getIconResId()), states[index], index == 7 ? -1 : 37));
        }
        SystemAppsFragment fragment = (SystemAppsFragment) activity.getFragmentManager()
                .findFragmentById(R.id.system_apps_container);
        fragment.showApps(apps);
        GridLayout grid = activity.findViewById(R.id.apps_grid);
        for (int index = 0; index < states.length; index++) {
            View item = grid.getChildAt(index);
            ImageView cloud = item.findViewById(R.id.app_download_icon);
            View progress = item.findViewById(R.id.app_download_progress);
            TextView status = item.findViewById(R.id.app_status);
            boolean hasCloud = states[index] == AppDownloadState.NOT_INSTALLED
                    || states[index] == AppDownloadState.FAILED
                    || states[index] == AppDownloadState.INSTALL_FAILED;
            boolean hasProgress = states[index] == AppDownloadState.WAITING
                    || states[index] == AppDownloadState.DOWNLOADING
                    || states[index] == AppDownloadState.PAUSED
                    || states[index] == AppDownloadState.INSTALLING;
            require((cloud.getVisibility() == View.VISIBLE) == hasCloud,
                    "Cloud badge does not match download state");
            require((progress.getVisibility() == View.VISIBLE) == hasProgress,
                    "Progress overlay does not match download state");
            require((status.getVisibility() == View.VISIBLE)
                    == (hasProgress || states[index] == AppDownloadState.FAILED
                    || states[index] == AppDownloadState.INSTALL_FAILED),
                    "Status label does not match download state");
            require(item.isEnabled() == (states[index] != AppDownloadState.INSTALLING),
                    "Installing apps must not accept repeated clicks");
            if (states[index] == AppDownloadState.INSTALL_FAILED) {
                require(status.getText().toString().equals(activity.getString(
                        R.string.system_app_install_failed)),
                        "Installation failure must use its own status label");
            }
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
