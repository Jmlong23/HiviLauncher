package com.hivi.launcher.utils;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public final class UiUtils {
    /**
     * 系统底部导航栏默认显示；隐藏状态在进程存活期间保持，重启后恢复默认显示。
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
     * 统一沉浸式设置：状态栏始终隐藏，底部导航栏按隐藏状态设置。Activity 与各弹窗共用。
     */
    public static void applyImmersiveVisibility(View decorView) {
        int visibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (sNavBarHidden) {
            visibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        decorView.setSystemUiVisibility(visibility);
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
