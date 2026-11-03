package com.hivi.launcher.utils;

import android.view.View;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UiUtilsTest {
    @After
    public void tearDown() {
        UiUtils.setNavBarHidden(false);
    }

    @Test
    public void navigationBarIsVisibleByDefaultWhileStatusBarIsHidden() {
        RecordingView view = new RecordingView();

        UiUtils.applyImmersiveVisibility(view);

        assertFalse(UiUtils.isNavBarHidden());
        assertEquals(0, view.mVisibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        assertEquals(0, view.mVisibility & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        assertEquals(View.SYSTEM_UI_FLAG_FULLSCREEN,
                view.mVisibility & View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Test
    public void hiddenNavigationBarDoesNotReserveLayoutSpace() {
        RecordingView view = new RecordingView();
        UiUtils.setNavBarHidden(true);

        UiUtils.applyImmersiveVisibility(view);

        assertTrue(UiUtils.isNavBarHidden());
        assertEquals(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION,
                view.mVisibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        assertEquals(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION,
                view.mVisibility & View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Test
    public void showingNavigationBarClearsPreviousHiddenFlags() {
        RecordingView view = new RecordingView();
        UiUtils.setNavBarHidden(true);
        UiUtils.applyImmersiveVisibility(view);

        UiUtils.setNavBarHidden(false);
        UiUtils.applyImmersiveVisibility(view);

        assertFalse(UiUtils.isNavBarHidden());
        assertEquals(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE, view.mVisibility);
    }

    private static final class RecordingView extends View {
        private int mVisibility;

        private RecordingView() {
            super(null);
        }

        @Override
        public void setSystemUiVisibility(int visibility) {
            mVisibility = visibility;
        }
    }
}
