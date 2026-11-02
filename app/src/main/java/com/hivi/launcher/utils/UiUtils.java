package com.hivi.launcher.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import com.hivi.launcher.utils.log.AppLog;

import java.lang.reflect.Method;

public final class UiUtils {
    private UiUtils() {
    }

    /**
     * 统一沉浸式设置：状态栏始终隐藏，系统底部导航栏始终显示。Activity 与各弹窗共用。
     */
    public static void applyImmersiveVisibility(View decorView) {
        ensureSystemNavigationBarVisible(decorView.getContext());
        int visibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(visibility);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ImmersiveSystemBars systemBars = (ImmersiveSystemBars) decorView.getTag(
                    R.id.tag_immersive_system_bars);
            if (systemBars == null) {
                systemBars = new ImmersiveSystemBars(decorView);
                decorView.setTag(R.id.tag_immersive_system_bars, systemBars);
            }
            systemBars.apply();
        }
    }

    private static void ensureSystemNavigationBarVisible(Context context) {
        try {
            Method getProperty = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class);
            String board = (String) getProperty.invoke(null, "ro.ztl.board.type", "");
            String hidden = (String) getProperty.invoke(null, "persist.ztl.hidenavigationbar", "");
            if (board.isEmpty() || "0".equals(hidden) || "false".equalsIgnoreCase(hidden)) {
                return;
            }
            Intent intent = new Intent("com.ztl.action.systembar");
            intent.setPackage("com.android.systemui");
            intent.putExtra("navigationbar", true);
            context.sendBroadcast(intent);
        } catch (ReflectiveOperationException | SecurityException error) {
            AppLog.w("UiUtils", "Unable to enable the device navigation bar", error);
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static final class ImmersiveSystemBars implements View.OnAttachStateChangeListener,
            WindowInsetsController.OnControllableInsetsChangedListener {
        private final View mDecorView;
        private WindowInsetsController mController;
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

        void apply() {
            WindowInsetsController controller = mDecorView.getWindowInsetsController();
            if (controller == null) {
                return;
            }
            controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsets.Type.statusBars());
            controller.show(WindowInsets.Type.navigationBars());
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
            apply();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            releaseController();
        }

        private void finishHiding() {
            int types = WindowInsets.Type.statusBars() & mControllableTypes;
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
