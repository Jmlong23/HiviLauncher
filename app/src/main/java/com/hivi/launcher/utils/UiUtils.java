package com.hivi.launcher.utils;

import android.app.Activity;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimationControlListener;
import android.view.WindowInsetsAnimationController;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;

import androidx.annotation.RequiresApi;

import com.hivi.launcher.R;

public final class UiUtils {
    /**
     * 系统底部导航栏是否被用户隐藏。开机默认显示，底部栏最左侧按钮可切换；
     * 进程存活期间保持，重启后恢复默认显示。
     */
    private static boolean sNavBarHidden = false;

    private UiUtils() {
    }

    public static boolean isNavBarHidden() {
        return sNavBarHidden;
    }

    public static void setNavBarHidden(boolean hidden) {
        sNavBarHidden = hidden;
    }

    /**
     * 统一沉浸式设置：状态栏始终隐藏；底部导航栏默认显示，
     * 仅当用户通过底部栏按钮选择隐藏时才一并隐藏。Activity 与各弹窗共用。
     */
    public static void applyImmersiveVisibility(View decorView) {
        applyImmersiveVisibility(decorView, sNavBarHidden);
    }

    public static void applyImmersiveVisibility(View decorView, boolean hideNavigationBar) {
        int visibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (hideNavigationBar) {
            visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        decorView.setSystemUiVisibility(visibility);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ImmersiveSystemBars systemBars = (ImmersiveSystemBars) decorView.getTag(
                    R.id.tag_immersive_system_bars);
            if (systemBars == null) {
                systemBars = new ImmersiveSystemBars(decorView);
                decorView.setTag(R.id.tag_immersive_system_bars, systemBars);
            }
            systemBars.apply(hideNavigationBar);
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static final class ImmersiveSystemBars implements View.OnAttachStateChangeListener,
            WindowInsetsController.OnControllableInsetsChangedListener {
        private final View mDecorView;
        private WindowInsetsController mController;
        private boolean mHideNavigationBar;
        private int mControllableTypes;
        private final WindowInsetsAnimationControlListener mHideListener =
                new WindowInsetsAnimationControlListener() {
                    @Override
                    public void onReady(WindowInsetsAnimationController controller, int types) {
                        controller.finish(false);
                    }

                    @Override
                    public void onFinished(WindowInsetsAnimationController controller) {
                    }

                    @Override
                    public void onCancelled(WindowInsetsAnimationController controller) {
                    }
                };

        ImmersiveSystemBars(View decorView) {
            mDecorView = decorView;
            decorView.addOnAttachStateChangeListener(this);
        }

        void apply(boolean hideNavigationBar) {
            mHideNavigationBar = hideNavigationBar;
            WindowInsetsController controller = mDecorView.getWindowInsetsController();
            if (controller == null) {
                return;
            }
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(hiddenTypes());
            if (!hideNavigationBar) {
                controller.show(WindowInsets.Type.navigationBars());
            }
            if (mController != controller) {
                releaseController();
                mController = controller;
                controller.addOnControllableInsetsChangedListener(this);
            } else {
                finishHiding();
            }
        }

        @Override
        public void onControllableInsetsChanged(WindowInsetsController controller, int types) {
            mControllableTypes = types;
            finishHiding();
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            apply(mHideNavigationBar);
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            releaseController();
        }

        private int hiddenTypes() {
            return WindowInsets.Type.statusBars()
                    | (mHideNavigationBar ? WindowInsets.Type.navigationBars() : 0);
        }

        private void finishHiding() {
            int types = hiddenTypes() & mControllableTypes;
            if (types != 0) {
                mController.controlWindowInsetsAnimation(types, 0L, null, null, mHideListener);
            }
        }

        private void releaseController() {
            if (mController != null) {
                mController.removeOnControllableInsetsChangedListener(this);
                mController = null;
            }
            mControllableTypes = 0;
        }
    }

    public static int ui(Activity activity, int value) {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        float scale = Math.min(metrics.widthPixels / 1280f, metrics.heightPixels / 800f);
        return Math.round(value * scale);
    }

    public static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public static void setLinearLayoutSize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof LinearLayout.LayoutParams)) {
            params = new LinearLayout.LayoutParams(width, height);
        }
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    public static void setWeightedLinearLayoutSize(View view, int width, int height, float weight,
            int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height, weight);
        params.setMargins(left, top, right, bottom);
        view.setLayoutParams(params);
    }

    public static void setLinearLayoutMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) layoutParams;
            params.setMargins(left, top, right, bottom);
            view.setLayoutParams(params);
        }
    }
}
