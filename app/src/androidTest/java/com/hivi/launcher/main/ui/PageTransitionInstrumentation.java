package com.hivi.launcher.main.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;

import com.hivi.launcher.R;
import com.hivi.launcher.main.model.MainPage;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class PageTransitionInstrumentation extends Instrumentation {
    private final CountDownLatch mResumed = new CountDownLatch(1);
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
            awaitCondition(() -> mActivity.hasWindowFocus());
            runOnMainSync(mActivity::showHomePage);
            awaitPage(MainPage.WIFI);
            waitForIdleSync();
            Fragment home = fragment(MainPage.WIFI);
            for (MainPage page : new MainPage[] {MainPage.SETTINGS, MainPage.SYSTEM_APPS}) {
                for (int iteration = 0; iteration < 3; iteration++) {
                    verifyTransition(page, true, false);
                    verifyTransition(page, false, iteration == 2);
                    require(fragment(MainPage.WIFI) == home,
                            "Returning must preserve the original home fragment");
                }
                verifyInterruptedTransition(page);
            }
            verifyPageStackAndReuse();
            result.putString("stream", "Settings/apps short-slide and fade passed in both "
                    + "directions. Outgoing geometry and status bar stay fixed. Repeated taps, "
                    + "mid-animation back/re-entry, Home navigation, page stacks and settings "
                    + "instance reuse passed.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable throwable) {
            result.putString("stream", "Page transition verification failed: " + throwable + "\n");
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyTransition(MainPage page, boolean entering, boolean homeButton)
            throws Exception {
        View container = mActivity.findViewById(R.id.fragment_container);
        View statusBar = mActivity.findViewById(R.id.home_status_bar);
        Rect[] outgoingBounds = new Rect[1];
        View[] snapshot = new View[1];
        runOnMainSync(() -> {
            outgoingBounds[0] = new Rect(container.getLeft(), container.getTop(),
                    container.getRight(), container.getBottom());
            if (entering) {
                mActivity.showPage(page);
            } else if (homeButton) {
                mActivity.showHomePage();
            } else {
                mActivity.navigateBack();
            }
            snapshot[0] = snapshot();
            require(snapshot[0] != null, "Transitions must preserve the outgoing page");
        });
        boolean[] sawFade = new boolean[1];
        boolean[] sawRetreat = new boolean[1];
        int offset = Math.round(28f * getTargetContext().getResources().getDisplayMetrics().density);
        awaitCondition(() -> {
            float alpha = container.getAlpha();
            if (alpha > (entering ? 0f : 0.65f) && alpha < 1f) {
                require(Math.abs(container.getTranslationX()) <= offset,
                        "Incoming pages must use a short 28dp slide");
                require(entering ? container.getTranslationX() > 0f
                        : container.getTranslationX() < 0f, "Incoming slide direction is wrong");
                sawFade[0] = true;
            }
            View outgoing = snapshot[0];
            if (Math.abs(outgoing.getTranslationX()) > 0f) {
                require(entering ? outgoing.getTranslationX() < 0f
                        : outgoing.getTranslationX() > 0f, "Outgoing slide direction is wrong");
                require(outgoing.getAlpha() < 1f, "Outgoing pages must fade while moving");
                require(outgoing.getWidth() == outgoingBounds[0].width()
                        && outgoing.getHeight() == outgoingBounds[0].height()
                        && outgoing.getLeft() == outgoingBounds[0].left
                        && outgoing.getTop() == outgoingBounds[0].top,
                        "Outgoing geometry must not change with the shared container margins");
                sawRetreat[0] = true;
            }
            require(statusBar.getTranslationX() == 0f && statusBar.getAlpha() == 1f,
                    "The shared status bar must not move or fade");
            return snapshot() == null;
        });
        require(sawFade[0] && sawRetreat[0], "Transitions must render intermediate frames: "
                + page + ", entering=" + entering + ", incoming=" + sawFade[0]
                + ", outgoing=" + sawRetreat[0]);
        awaitPage(entering ? page : MainPage.WIFI);
        require(snapshot[0].getParent() == null, "Completed snapshots must be detached");
    }

    private void verifyInterruptedTransition(MainPage page) throws Exception {
        runOnMainSync(() -> {
            mActivity.showPage(page);
            mActivity.showPage(page);
            mActivity.getFragmentManager().executePendingTransactions();
            require(mActivity.getFragmentManager().getBackStackEntryCount() == 2,
                    "Repeated taps must not enqueue duplicate pages");
            mActivity.navigateBack();
        });
        awaitPage(MainPage.WIFI);
        runOnMainSync(() -> mActivity.showPage(page));
        awaitIntermediateFrame();
        runOnMainSync(mActivity::navigateBack);
        awaitPage(MainPage.WIFI);
        runOnMainSync(() -> mActivity.showPage(page));
        awaitPage(page);
        runOnMainSync(mActivity::navigateBack);
        awaitIntermediateFrame();
        runOnMainSync(() -> mActivity.showPage(page));
        awaitPage(page);
        runOnMainSync(() -> {
            require(mActivity.getFragmentManager().getBackStackEntryCount() == 2,
                    "Interrupted returns must not leave duplicate pages");
            mActivity.showHomePage();
        });
        awaitPage(MainPage.WIFI);
    }

    private void verifyPageStackAndReuse() throws Exception {
        runOnMainSync(() -> mActivity.showPage(MainPage.SETTINGS));
        awaitPage(MainPage.SETTINGS);
        Fragment settings = fragment(MainPage.SETTINGS);
        runOnMainSync(mActivity::showHomePage);
        awaitPage(MainPage.WIFI);
        runOnMainSync(() -> mActivity.showPage(MainPage.SETTINGS));
        awaitPage(MainPage.SETTINGS);
        require(fragment(MainPage.SETTINGS) == settings, "Settings must reuse its cached instance");
        runOnMainSync(() -> mActivity.showPage(MainPage.SYSTEM_APPS));
        awaitPage(MainPage.SYSTEM_APPS);
        runOnMainSync(mActivity::navigateBack);
        awaitPage(MainPage.SETTINGS);
        require(fragment(MainPage.SETTINGS) == settings, "Back must restore the previous settings");
        runOnMainSync(mActivity::showHomePage);
        awaitPage(MainPage.WIFI);
        runOnMainSync(() -> require(mActivity.getFragmentManager().getBackStackEntryCount() == 1,
                "Home must clear all pages above the WiFi root"));
    }

    private void awaitIntermediateFrame() throws Exception {
        awaitCondition(() -> {
            float alpha = mActivity.findViewById(R.id.fragment_container).getAlpha();
            return snapshot() != null && alpha > 0.1f && alpha < 0.95f;
        });
    }

    private void awaitPage(MainPage page) throws Exception {
        awaitCondition(() -> {
            Fragment fragment = fragment(page);
            View container = mActivity.findViewById(R.id.fragment_container);
            return fragment != null && fragment.isAdded() && !fragment.isHidden()
                    && fragment.getView() != null && fragment.getView().isShown()
                    && snapshot() == null && container.getAlpha() == 1f
                    && container.getTranslationX() == 0f && container.isLaidOut()
                    && !container.isLayoutRequested();
        });
    }

    private Fragment fragment(MainPage page) {
        return mActivity.getFragmentManager().findFragmentByTag(page.name());
    }

    private View snapshot() {
        try {
            Field field = MainActivity.class.getDeclaredField("mPageTransitionSnapshot");
            field.setAccessible(true);
            return (View) field.get(mActivity);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect the transition snapshot", exception);
        }
    }

    private void awaitCondition(BooleanSupplier condition) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 6000L;
        boolean[] ready = new boolean[1];
        Throwable[] failure = new Throwable[1];
        while (!ready[0] && failure[0] == null && SystemClock.uptimeMillis() < deadline) {
            runOnMainSync(() -> {
                try {
                    ready[0] = condition.getAsBoolean();
                } catch (Throwable throwable) {
                    failure[0] = throwable;
                }
            });
            if (!ready[0]) {
                SystemClock.sleep(10L);
            }
        }
        if (failure[0] != null) {
            throw new AssertionError("Page transition regression", failure[0]);
        }
        require(ready[0], "Page did not settle before the deadline");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
