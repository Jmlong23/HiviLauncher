package com.hivi.launcher.customview;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.hivi.launcher.R;

public final class TextPopupView extends FrameLayout {
    private final TextView mText;
    private PopupWindow mPopupWindow;

    public TextPopupView(Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.popup_full_text, this, true);
        mText = findViewById(R.id.full_text_popup_text);
        setOnClickListener(view -> dismiss());
    }

    public void show(View anchor, CharSequence text) {
        dismiss();
        mText.setText(text);
        Rect visibleFrame = new Rect();
        anchor.getWindowVisibleDisplayFrame(visibleFrame);
        int maxWidth = Math.min(dp(440), visibleFrame.width() - dp(32));
        mText.setMaxWidth(Math.max(dp(80), maxWidth));
        measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        int anchorCenter = anchorLocation[0] + anchor.getWidth() / 2;
        int popupLeft = Math.max(visibleFrame.left + dp(16),
                Math.min(anchorCenter - getMeasuredWidth() / 2,
                        visibleFrame.right - dp(16) - getMeasuredWidth()));
        int popupTop = anchorLocation[1] - getMeasuredHeight() - dp(8);
        if (popupTop < visibleFrame.top + dp(16)) {
            popupTop = anchorLocation[1] + anchor.getHeight() + dp(8);
        }
        popupTop = Math.max(visibleFrame.top + dp(16),
                Math.min(popupTop, visibleFrame.bottom - dp(16) - getMeasuredHeight()));
        mPopupWindow = new PopupWindow(this, getMeasuredWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mPopupWindow.setAnimationStyle(0);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopupWindow.setOnDismissListener(() -> mPopupWindow = null);
        mPopupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupLeft, popupTop);
    }

    public boolean isShowing() {
        return mPopupWindow != null && mPopupWindow.isShowing();
    }

    public void dismiss() {
        if (mPopupWindow != null) {
            mPopupWindow.dismiss();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
