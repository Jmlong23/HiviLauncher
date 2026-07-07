package com.hivi.launcher.utils;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public final class UiUtils {
    private UiUtils() {
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
