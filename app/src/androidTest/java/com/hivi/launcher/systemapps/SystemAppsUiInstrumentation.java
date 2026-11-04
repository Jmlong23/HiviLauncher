package com.hivi.launcher.systemapps;

import android.app.Instrumentation;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.hivi.launcher.R;
import com.hivi.launcher.account.ui.AuthorizationDialog;
import com.hivi.launcher.ai.ui.AiFragment;
import com.hivi.launcher.ai.wakeup.AiWakeupController;
import com.hivi.launcher.main.model.MainPage;
import com.hivi.launcher.main.ui.MainActivity;
import com.hivi.launcher.settings.ui.SettingsFragment;
import com.hivi.launcher.systemapps.model.AppDownloadState;
import com.hivi.launcher.systemapps.model.AppEntry;
import com.hivi.launcher.systemapps.model.AppUninstallReceiver;
import com.hivi.launcher.systemapps.model.SupportedApp;
import com.hivi.launcher.systemapps.ui.SystemAppsFragment;
import com.hivi.launcher.systemapps.ui.HeaderButtonBackgroundDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
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
            for (int iteration = 0; iteration < 3; iteration++) {
                verifySettingsTransition(true);
                verifySettingsTransition(false);
            }
            verifyInterruptedSettingsTransition();
            verifyPageStackAcrossContainers();
            verifyAiAuthorizationAndNavigation();
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
            runOnMainSync(this::assertUninstallStateAndMenus);
            capture("uninstall-state.png");
            runOnMainSync(() -> {
                SystemAppsFragment fragment = (SystemAppsFragment) activity.getFragmentManager()
                        .findFragmentById(R.id.system_apps_container);
                fragment.closePage();
            });
            awaitPage(false);
            result.putString("stream", "Nine-app catalog, cloud badges, black inset pressed states "
                    + "for back and volume, unchanged alpha contours and outer shadows, design parameters and "
                    + "all download state layouts, uninstall state, protected app menus and private "
                    + "uninstall receiver passed. Repeated page transitions keep home geometry, "
                    + "app item identities stable. Settings and app pages use the AI-style short slide "
                    + "and fade, with home cards, navigation and divider synchronized in both directions. "
                    + "Unauthorized AI entries preserve the current page and open authorization. "
                    + "AI transitions keep navigation and its divider synchronized with home content, "
                    + "including repeated entry and interrupted returns. "
                    + "Screenshots: files/system-app-ui-tests\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable throwable) {
            result.putString("stream", "App UI verification failed: " + throwable + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyHomeTransition(boolean entering) throws Exception {
        verifyStackedHomeTransition(MainPage.SYSTEM_APPS, entering);
    }

    private void awaitPage(boolean appsVisible) throws Exception {
        awaitCondition(() -> {
            View container = mActivity.findViewById(R.id.system_apps_container);
            View home = mActivity.findViewById(R.id.launcher_content);
            return appsVisible ? container.getVisibility() == View.VISIBLE
                    && container.getAlpha() == 1f
                    && container.getTranslationX() == 0f
                    && home.getVisibility() == View.INVISIBLE
                    : container.getVisibility() == View.GONE
                    && mActivity.findViewById(R.id.settings_container).getVisibility() == View.GONE
                    && mActivity.findViewById(R.id.fragment_container).getVisibility() == View.GONE
                    && home.getVisibility() == View.VISIBLE;
        });
    }

    private void awaitCondition(BooleanSupplier condition) throws Exception {
        awaitCondition(condition, true);
    }

    private void awaitCondition(BooleanSupplier condition, boolean waitForIdle) throws Exception {
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
        if (waitForIdle) {
            waitForIdleSync();
        }
    }

    private Rect bounds(View view) {
        return new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

    private void verifySettingsTransition(boolean entering) throws Exception {
        verifyStackedHomeTransition(MainPage.SETTINGS, entering);
    }

    private void verifyStackedHomeTransition(MainPage page, boolean entering) throws Exception {
        View decor = mActivity.getWindow().getDecorView();
        View home = decor.findViewById(R.id.launcher_content);
        View cards = decor.findViewById(R.id.cards_row);
        View body = decor.findViewById(R.id.page_body);
        View navigation = decor.findViewById(R.id.bottom_navigation);
        View divider = decor.findViewById(R.id.bottom_navigation_divider);
        View topBar = decor.findViewById(R.id.top_bar);
        boolean appsPage = page == MainPage.SYSTEM_APPS;
        View container = decor.findViewById(appsPage
                ? R.id.system_apps_container : R.id.settings_container);
        int transitionOffset = Math.round(28f * getTargetContext().getResources()
                .getDisplayMetrics().density);
        Rect[] originalBounds = new Rect[3];
        int[] originalPadding = new int[4];
        runOnMainSync(() -> {
            originalBounds[0] = bounds(cards);
            originalBounds[1] = bounds(navigation);
            originalBounds[2] = bounds(topBar);
            originalPadding[0] = home.getPaddingLeft();
            originalPadding[1] = home.getPaddingTop();
            originalPadding[2] = home.getPaddingRight();
            originalPadding[3] = home.getPaddingBottom();
            if (entering) {
                mActivity.showPage(page);
            } else {
                mActivity.navigateBack();
            }
            assertBottomNavigationVisibility(View.VISIBLE);
        });
        CountDownLatch finished = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        boolean[] sawTransitionFrame = new boolean[1];
        boolean[] sawHomeTransitionFrame = new boolean[1];
        long deadline = SystemClock.uptimeMillis() + 5000L;
        runOnMainSync(() -> decor.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                try {
                    require(originalBounds[0].equals(bounds(cards))
                            && originalBounds[1].equals(bounds(navigation))
                            && originalBounds[2].equals(bounds(topBar)),
                            "Page transitions must preserve home layout geometry");
                    require(home.getPaddingLeft() == originalPadding[0]
                            && home.getPaddingTop() == originalPadding[1]
                            && home.getPaddingRight() == originalPadding[2]
                            && home.getPaddingBottom() == originalPadding[3],
                            "Page transitions must not mutate shared home padding");
                    assertHomeTransitionState(body, navigation, divider);
                    require(topBar.getTranslationX() == 0f && topBar.getAlpha() == 1f,
                            "The top status bar must remain stationary");
                    require(container.getBackground() instanceof ColorDrawable
                            && Color.alpha(((ColorDrawable) container.getBackground()).getColor()) == 255,
                            "Page backgrounds must stay opaque after the fade completes");
                    if (container.getVisibility() == View.VISIBLE
                            && container.getAlpha() > 0f && container.getAlpha() < 1f) {
                        assertBottomNavigationVisibility(View.VISIBLE);
                        require(container.getTranslationX() > 0f
                                && container.getTranslationX() <= transitionOffset,
                                "Pages must slide a short distance while fading like AI");
                        sawTransitionFrame[0] = true;
                    }
                    if (body.getTranslationX() < 0f && body.getAlpha() < 1f) {
                        sawHomeTransitionFrame[0] = true;
                    }
                    boolean complete = entering
                            ? container.getVisibility() == View.VISIBLE
                            && container.getTranslationX() == 0f
                            && container.getAlpha() == 1f
                            && (appsPage ? home.getVisibility() == View.INVISIBLE
                            : navigation.getVisibility() == View.INVISIBLE)
                            : container.getVisibility() == View.GONE
                            && navigation.getVisibility() == View.VISIBLE;
                    if (complete) {
                        require(sawTransitionFrame[0] && sawHomeTransitionFrame[0],
                                "Pages and home must visibly animate instead of switching instantly");
                        require(body.getTranslationX() == (entering ? -transitionOffset : 0f)
                                && body.getAlpha() == (entering ? 0.65f : 1f),
                                "Home must settle into the matching AI transition state");
                        if (entering && appsPage) {
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
        require(finished.await(6L, TimeUnit.SECONDS), "Page transition did not finish: " + page);
        if (failure[0] != null) {
            throw new AssertionError("Page transition regression: " + page, failure[0]);
        }
        waitForIdleSync();
    }

    private void verifyInterruptedSettingsTransition() throws Exception {
        runOnMainSync(() -> {
            mActivity.showPage(MainPage.SETTINGS);
            mActivity.showPage(MainPage.SETTINGS);
            mActivity.getFragmentManager().executePendingTransactions();
            require(mActivity.getFragmentManager().getBackStackEntryCount() == 1,
                    "Repeated settings taps must not enqueue duplicate pages");
            mActivity.navigateBack();
        });
        awaitPage(false);
        runOnMainSync(() -> mActivity.showPage(MainPage.SETTINGS));
        awaitSettingsPage();
        runOnMainSync(() -> {
            mActivity.navigateBack();
            mActivity.showPage(MainPage.SETTINGS);
        });
        awaitSettingsPage();
        runOnMainSync(mActivity::showHomePage);
        awaitPage(false);
    }

    private void awaitSettingsPage() throws Exception {
        awaitCondition(() -> mActivity.getFragmentManager()
                .findFragmentById(R.id.settings_container) instanceof SettingsFragment
                && mActivity.findViewById(R.id.settings_container).getVisibility() == View.VISIBLE
                && mActivity.findViewById(R.id.settings_container).getAlpha() == 1f
                && mActivity.findViewById(R.id.settings_container).getTranslationX() == 0f
                && mActivity.findViewById(R.id.bottom_navigation).getVisibility() == View.INVISIBLE);
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
        runOnMainSync(() -> {
            mActivity.showPage(MainPage.SETTINGS);
            require(mActivity.findViewById(R.id.bottom_navigation).getVisibility() == View.VISIBLE,
                    "Navigation must remain behind settings during entry");
        });
        awaitSettingsPage();
        Fragment[] settings = new Fragment[1];
        runOnMainSync(() -> {
            settings[0] = mActivity.getFragmentManager().findFragmentById(R.id.settings_container);
            require(mActivity.findViewById(R.id.bottom_navigation).getVisibility() == View.INVISIBLE,
                    "The settings page must keep the app navigation buttons hidden");
            mActivity.showPage(MainPage.SYSTEM_APPS);
        });
        awaitPage(true);
        runOnMainSync(mActivity::navigateBack);
        awaitCondition(() -> mActivity.findViewById(R.id.system_apps_container)
                .getVisibility() == View.GONE);
        runOnMainSync(() -> {
            require(mActivity.getFragmentManager().findFragmentById(R.id.settings_container)
                    == settings[0], "Returning from apps must preserve the previous settings page");
            require(settings[0].isAdded(), "The previous page must remain on the back stack");
            require(mActivity.findViewById(R.id.bottom_navigation).getVisibility() == View.INVISIBLE,
                    "Returning to settings must hide the app navigation buttons again");
            mActivity.showPage(MainPage.SYSTEM_APPS);
        });
        awaitPage(true);
        runOnMainSync(mActivity::showHomePage);
        awaitPage(false);
        runOnMainSync(() -> require(mActivity.findViewById(R.id.bottom_navigation)
                .getVisibility() == View.VISIBLE,
                "Returning home must restore the app navigation buttons"));
    }

    private void verifyAiAuthorizationAndNavigation() throws Exception {
        SharedPreferences preferences = mActivity.getApplicationContext().getSharedPreferences(
                "account_authorization", Context.MODE_PRIVATE);
        String originalToken = preferences.getString("authorization_token", null);
        try {
            runOnMainSync(() -> require(preferences.edit().remove("authorization_token").commit(),
                    "Cannot prepare unauthorized AI entry"));
            verifyAiAuthorizationGate();
            runOnMainSync(() -> require(preferences.edit()
                    .putString("authorization_token", "Bearer ai-navigation-test").commit(),
                    "Cannot prepare authorized AI navigation"));
            verifyAiConversationNavigation();
        } finally {
            runOnMainSync(() -> {
                require(preferences.edit().putString("authorization_token", originalToken).commit(),
                        "Cannot restore the original authorization token");
                AuthorizationDialog dialog = getAuthorizationDialog();
                if (dialog != null) {
                    dialog.dismiss();
                }
            });
        }
    }

    private void verifyAiAuthorizationGate() throws Exception {
        runOnMainSync(() -> {
            mActivity.findViewById(R.id.ai_chat_entry).performClick();
            AuthorizationDialog dialog = getAuthorizationDialog();
            require(dialog != null && dialog.isShowing(),
                    "Unauthorized AI click must open the authorization dialog");
            mActivity.findViewById(R.id.ai_chat_entry).performClick();
            require(dialog == getAuthorizationDialog() && dialog.isShowing(),
                    "Repeated unauthorized AI clicks must reuse the authorization dialog");
            mActivity.getFragmentManager().executePendingTransactions();
            require(mActivity.getFragmentManager().getBackStackEntryCount() == 0,
                    "Unauthorized AI clicks must not enqueue a page");
            require(mActivity.getFragmentManager().findFragmentById(R.id.fragment_container) == null,
                    "Unauthorized AI clicks must keep the home page active");
            require(mActivity.findViewById(R.id.ai_chat_entry).getVisibility() == View.VISIBLE,
                    "Unauthorized AI clicks must keep the home AI entry visible");
            assertBottomNavigationVisibility(View.VISIBLE);
            dialog.dismiss();
            mActivity.showPage(MainPage.WIFI);
            mActivity.getFragmentManager().executePendingTransactions();
        });
        waitForIdleSync();
        runOnMainSync(() -> {
            Fragment previous = mActivity.getFragmentManager()
                    .findFragmentById(R.id.fragment_container);
            int backStackCount = mActivity.getFragmentManager().getBackStackEntryCount();
            mActivity.bringToFrontAndShowPage(MainPage.AI);
            mActivity.getFragmentManager().executePendingTransactions();
            require(mActivity.getFragmentManager().findFragmentById(R.id.fragment_container)
                    == previous, "Unauthorized AI navigation must preserve the previous page");
            require(mActivity.getFragmentManager().getBackStackEntryCount() == backStackCount,
                    "Unauthorized AI navigation must preserve the back stack");
            AuthorizationDialog dialog = getAuthorizationDialog();
            require(dialog != null && dialog.isShowing(),
                    "Programmatic AI navigation must require authorization too");
            assertBottomNavigationVisibility(View.VISIBLE);
            dialog.dismiss();
            mActivity.onWakeupRejected(AiWakeupController.Reason.NOT_AUTHORIZED);
            require(getAuthorizationDialog().isShowing(),
                    "Unauthorized voice wakeup must open the authorization dialog");
            dialog.dismiss();
            mActivity.showHomePage();
        });
        awaitPage(false);
    }

    private AuthorizationDialog getAuthorizationDialog() {
        try {
            Field field = MainActivity.class.getDeclaredField("mAuthorizationDialog");
            field.setAccessible(true);
            return (AuthorizationDialog) field.get(mActivity);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect the authorization dialog", exception);
        }
    }

    private void verifyAiConversationNavigation() throws Exception {
        for (int iteration = 0; iteration < 3; iteration++) {
            verifyAiHomeTransition(true);
            if (iteration == 0) {
                capture("ai-navigation-hidden.png");
            }
            verifyAiHomeTransition(false);
        }
        runOnMainSync(() -> {
            assertBottomNavigationVisibility(View.VISIBLE);
            mActivity.showPage(MainPage.AI);
            assertBottomNavigationVisibility(View.VISIBLE);
            mActivity.getFragmentManager().executePendingTransactions();
            require(mActivity.getFragmentManager().findFragmentById(R.id.fragment_container)
                    instanceof AiFragment, "AI conversation page must be active");
            assertBottomNavigationVisibility(View.VISIBLE);
            require(mActivity.findViewById(R.id.top_bar).getVisibility() == View.VISIBLE,
                    "AI conversation must keep the top status bar visible");
            mActivity.navigateBack();
        });
        awaitPage(false);
        verifyAiHomeTransition(true);
        runOnMainSync(() -> {
            mActivity.navigateBack();
            mActivity.showPage(MainPage.AI);
        });
        awaitCondition(() -> mActivity.findViewById(R.id.bottom_navigation)
                .getVisibility() == View.GONE, false);
        runOnMainSync(mActivity::onBackPressed);
        awaitPage(false);
        runOnMainSync(() -> {
            assertBottomNavigationVisibility(View.VISIBLE);
            mActivity.showPage(MainPage.WIFI);
            mActivity.getFragmentManager().executePendingTransactions();
            assertBottomNavigationVisibility(View.VISIBLE);
            mActivity.showPage(MainPage.AI);
            assertBottomNavigationVisibility(View.GONE);
            mActivity.getFragmentManager().executePendingTransactions();
            assertBottomNavigationVisibility(View.GONE);
            mActivity.navigateBack();
            mActivity.getFragmentManager().executePendingTransactions();
            require(!(mActivity.getFragmentManager().findFragmentById(R.id.fragment_container)
                    instanceof AiFragment), "Back must restore the previous input-mode page");
            assertBottomNavigationVisibility(View.VISIBLE);
            mActivity.showPage(MainPage.AI);
            mActivity.getFragmentManager().executePendingTransactions();
            assertBottomNavigationVisibility(View.GONE);
            mActivity.showPage(MainPage.WIFI);
            mActivity.getFragmentManager().executePendingTransactions();
            assertBottomNavigationVisibility(View.VISIBLE);
            mActivity.showHomePage();
        });
        awaitPage(false);
        runOnMainSync(() -> assertBottomNavigationVisibility(View.VISIBLE));
    }

    private void verifyAiHomeTransition(boolean entering) throws Exception {
        View decor = mActivity.getWindow().getDecorView();
        View body = decor.findViewById(R.id.page_body);
        View navigation = decor.findViewById(R.id.bottom_navigation);
        View divider = decor.findViewById(R.id.bottom_navigation_divider);
        View container = decor.findViewById(R.id.fragment_container);
        runOnMainSync(() -> {
            if (entering) {
                mActivity.showPage(MainPage.AI);
                mActivity.showPage(MainPage.AI);
                mActivity.getFragmentManager().executePendingTransactions();
                require(mActivity.getFragmentManager().getBackStackEntryCount() == 1,
                        "Repeated AI taps must not enqueue duplicate pages");
                int backButtonId = getTargetContext().getResources().getIdentifier(
                        "ai_conversation_back", "id", getTargetContext().getPackageName());
                require(backButtonId == 0 || mActivity.findViewById(backButtonId) == null,
                        "AI conversation must not display an in-page back button");
                View parent = (View) container.getParent();
                require(parent instanceof android.view.ViewGroup,
                        "The AI page and navigation must share a view group");
                android.view.ViewGroup layers = (android.view.ViewGroup) parent;
                require(layers.indexOfChild(container) > layers.indexOfChild(navigation)
                        && layers.indexOfChild(container) > layers.indexOfChild(divider),
                        "The AI page must cover navigation instead of hiding it ahead of home");
            } else {
                mActivity.onBackPressed();
            }
            assertBottomNavigationVisibility(View.VISIBLE);
            assertHomeTransitionState(body, navigation, divider);
        });
        CountDownLatch finished = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        boolean[] sawTransitionFrame = new boolean[1];
        long deadline = SystemClock.uptimeMillis() + 5000L;
        runOnMainSync(() -> decor.postOnAnimation(new Runnable() {
            @Override
            public void run() {
                try {
                    assertHomeTransitionState(body, navigation, divider);
                    float pageAlpha = container.getAlpha();
                    if (pageAlpha > 0f && pageAlpha < 1f) {
                        assertBottomNavigationVisibility(View.VISIBLE);
                        sawTransitionFrame[0] = true;
                    }
                    boolean complete = entering
                            ? pageAlpha == 1f && navigation.getVisibility() == View.GONE
                            : container.getVisibility() == View.GONE;
                    if (complete) {
                        require(sawTransitionFrame[0],
                                "AI navigation must follow rendered home transition frames");
                        assertBottomNavigationVisibility(entering ? View.GONE : View.VISIBLE);
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
        require(finished.await(6L, TimeUnit.SECONDS), "AI home transition did not finish");
        if (failure[0] != null) {
            throw new AssertionError("AI home transition regression", failure[0]);
        }
    }

    private void assertHomeTransitionState(View body, View navigation, View divider) {
        require(Math.abs(body.getTranslationX() - navigation.getTranslationX()) < 0.01f
                && Math.abs(body.getTranslationX() - divider.getTranslationX()) < 0.01f,
                "Page transitions must move home cards, navigation and divider together");
        require(Math.abs(body.getAlpha() - navigation.getAlpha()) < 0.001f
                && Math.abs(body.getAlpha() - divider.getAlpha()) < 0.001f,
                "Page transitions must fade home cards, navigation and divider together");
    }

    private void assertBottomNavigationVisibility(int visibility) {
        require(mActivity.findViewById(R.id.bottom_navigation).getVisibility() == visibility,
                "App navigation visibility must match the active page");
        require(mActivity.findViewById(R.id.bottom_navigation_divider).getVisibility() == visibility,
                "The divider must share app navigation visibility");
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

    private void assertUninstallStateAndMenus() {
        SystemAppsFragment fragment = (SystemAppsFragment) mActivity.getFragmentManager()
                .findFragmentById(R.id.system_apps_container);
        GridLayout grid = mActivity.findViewById(R.id.apps_grid);
        SupportedApp app = SupportedApp.NETEASE_MUSIC;
        int index = app.ordinal();
        View item = grid.getChildAt(index);
        require(item.isLongClickable(), "App items must register a long-press listener");
        fragment.updateApp(new AppEntry(app, mActivity.getString(app.getLabelResId()),
                app.getPackageNames()[0], "MainActivity", mActivity.getDrawable(app.getIconResId()),
                AppDownloadState.UNINSTALLING, -1));
        require(!item.isEnabled(), "Uninstalling apps must not accept repeated interaction");
        require(item.findViewById(R.id.app_download_progress).getVisibility() == View.VISIBLE,
                "Uninstalling must show a progress overlay");
        require(item.findViewById(R.id.app_download_icon).getVisibility() == View.GONE,
                "Uninstalling must not show a download icon");
        TextView status = item.findViewById(R.id.app_status);
        require(status.getVisibility() == View.VISIBLE && status.getText().toString().equals(
                mActivity.getString(R.string.system_app_uninstalling)),
                "Uninstalling must show its own status label");
        require(item.getContentDescription().toString().contains(status.getText()),
                "Accessibility must announce the uninstall state");
        try {
            require(!mActivity.getPackageManager().getReceiverInfo(
                    new ComponentName(mActivity, AppUninstallReceiver.class), 0).exported,
                    "Uninstall callbacks must use a private receiver");
            Field field = SystemAppsFragment.class.getDeclaredField("mAppActionsDialog");
            field.setAccessible(true);
            for (SupportedApp selected : new SupportedApp[]{app, SupportedApp.QQ_MUSIC}) {
                fragment.showAppActions(new AppEntry(selected,
                        mActivity.getString(selected.getLabelResId()), selected.getPackageNames()[0],
                        "MainActivity", mActivity.getDrawable(selected.getIconResId()),
                        AppDownloadState.INSTALLED, 100));
                AlertDialog dialog = (AlertDialog) field.get(fragment);
                require(dialog != null && dialog.isShowing(), "Long-press menu must be visible");
                int count = selected.isUninstallable() ? 2 : 1;
                require(dialog.getListView().getCount() == count,
                        "Protected apps must only offer app info");
                require(dialog.getListView().getAdapter().getItem(count - 1).toString().equals(
                        mActivity.getString(R.string.system_app_action_app_info)),
                        "App info must be available for all installed apps");
                if (selected.isUninstallable()) {
                    require(dialog.getListView().getAdapter().getItem(0).toString().equals(
                            mActivity.getString(R.string.system_app_action_uninstall)),
                            "Uninstallable apps must offer an uninstall action");
                }
                dialog.dismiss();
            }
        } catch (Exception exception) {
            throw new AssertionError("Cannot verify uninstall menus or receiver", exception);
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
