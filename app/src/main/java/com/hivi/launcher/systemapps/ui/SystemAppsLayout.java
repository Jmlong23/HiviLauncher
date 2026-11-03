package com.hivi.launcher.systemapps.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hivi.launcher.R;

public final class SystemAppsLayout extends LinearLayout {
    private int mLayoutWidth;
    private int mItemCount = -1;
    private float mScale = 1f;

    public SystemAppsLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void onItemsChanged() {
        mItemCount = -1;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        GridLayout grid = findViewById(R.id.apps_grid);
        if (width > 0 && grid != null) {
            boolean widthChanged = width != mLayoutWidth;
            if (widthChanged) {
                mLayoutWidth = width;
                mScale = width / 1280f;
                applyHeaderLayout();
            }
            if (widthChanged || grid.getChildCount() != mItemCount) {
                mItemCount = grid.getChildCount();
                int itemWidth = (width - scaled(54) * 2 - scaled(16) * 2)
                        / grid.getColumnCount();
                for (int index = 0; index < mItemCount; index++) {
                    View item = grid.getChildAt(index);
                    setSize(item, itemWidth, scaled(276));
                    applyItemLayout(item);
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void applyHeaderLayout() {
        setPadding(scaled(54), scaled(30), scaled(54), 0);
        setSize(findViewById(R.id.apps_dialog_header),
                ViewGroup.LayoutParams.MATCH_PARENT, scaled(66));
        setSize(findViewById(R.id.apps_back_button),
                scaled(156), ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(findViewById(R.id.apps_back_icon), scaled(30), scaled(30));
        setSize(findViewById(R.id.apps_volume_button),
                scaled(132), ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(findViewById(R.id.apps_volume_icon), scaled(38), scaled(38));
        setStartMargin(findViewById(R.id.apps_volume_button), scaled(28));
        setStartMargin(findViewById(R.id.apps_back_text), scaled(14));
        setStartMargin(findViewById(R.id.apps_volume_text), scaled(14));
        setTextSize(findViewById(R.id.apps_back_text), 26);
        setTextSize(findViewById(R.id.apps_volume_text), 26);
        View scrollView = findViewById(R.id.apps_scroll_view);
        LayoutParams scrollParams = (LayoutParams) scrollView.getLayoutParams();
        scrollParams.topMargin = scaled(56);
        scrollView.setLayoutParams(scrollParams);
        scrollView.setPadding(scaled(16), 0, scaled(16), 0);
    }

    private void applyItemLayout(View item) {
        setSize(item.findViewById(R.id.app_icon_frame), scaled(136), scaled(136));
        setSize(item.findViewById(R.id.app_icon_content), scaled(84), scaled(84));
        View labelRow = item.findViewById(R.id.app_label_row);
        setSize(labelRow, ViewGroup.LayoutParams.MATCH_PARENT, scaled(42));
        LayoutParams labelParams = (LayoutParams) labelRow.getLayoutParams();
        labelParams.topMargin = scaled(18);
        labelRow.setLayoutParams(labelParams);
        labelRow.setPadding(scaled(4), 0, scaled(4), 0);
        TextView label = item.findViewById(R.id.app_label);
        setTextSize(label, 30);
        label.setMaxWidth(scaled(212));
        setSize(item.findViewById(R.id.app_download_icon), scaled(48), scaled(48));
        setSize(item.findViewById(R.id.app_status),
                ViewGroup.LayoutParams.MATCH_PARENT, scaled(30));
        setTextSize(item.findViewById(R.id.app_status), 20);
    }

    private void setSize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            view.setLayoutParams(params);
        }
    }

    private void setStartMargin(View view, int margin) {
        LayoutParams params = (LayoutParams) view.getLayoutParams();
        params.leftMargin = margin;
        view.setLayoutParams(params);
    }

    private void setTextSize(TextView view, int size) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, scaled(size));
    }

    private int scaled(int value) {
        return Math.round(value * mScale);
    }
}
