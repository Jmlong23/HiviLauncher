package com.hivi.launcher.customview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Scroller;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.core.content.res.ResourcesCompat;

import com.hivi.launcher.R;

public class FlipLayout extends CardView {
    private final GradientTextView visibleText = new GradientTextView(getContext());
    private final GradientTextView invisibleText = new GradientTextView(getContext());
    private final Scroller scroller;
    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();
    private final Rect topRect = new Rect();
    private final Rect bottomRect = new Rect();
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean flipping;
    private boolean initialized;
    private int targetValue;

    public FlipLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        scroller = new Scroller(context, new android.view.animation.DecelerateInterpolator());
        TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.FlipLayout);
        int background = values.getResourceId(R.styleable.FlipLayout_flipTextBackground, 0);
        int color = Color.WHITE;
        if (background == 0) {
            color = values.getColor(R.styleable.FlipLayout_flipTextBackground, Color.WHITE);
        }
        float textSize = values.getDimension(R.styleable.FlipLayout_flipTextSize,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 36,
                        getResources().getDisplayMetrics()));
        int textColor = values.getColor(R.styleable.FlipLayout_flipTextColor, Color.BLACK);
        values.recycle();
        configureText(visibleText, background, color, textSize, textColor);
        configureText(invisibleText, background, color, textSize, textColor);
        addView(invisibleText);
        addView(visibleText);
        shadePaint.setColor(Color.BLACK);
        setWillNotDraw(false);
    }

    private void configureText(TextView text, int background, int color, float textSize,
            int textColor) {
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        text.setText("00");
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        text.setTextColor(textColor);
        text.setTypeface(ResourcesCompat.getFont(getContext(), R.font.flip_clock_font));
        if (background != 0) {
            text.setBackgroundResource(background);
        } else {
            text.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        visibleText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        invisibleText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        visibleText.layout(0, 0, getWidth(), getHeight());
        invisibleText.layout(0, 0, getWidth(), getHeight());
        topRect.set(0, 0, getWidth(), getHeight() / 2);
        bottomRect.set(0, getHeight() / 2, getWidth(), getHeight());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!flipping || !scroller.computeScrollOffset()) {
            if (flipping) {
                visibleText.setText(invisibleText.getText());
                flipping = false;
            }
            drawChild(canvas, visibleText, getDrawingTime());
            return;
        }
        float degrees = getFlipDegrees();
        canvas.save();
        canvas.clipRect(topRect);
        drawChild(canvas, invisibleText, getDrawingTime());
        canvas.restore();
        canvas.save();
        canvas.clipRect(bottomRect);
        drawChild(canvas, visibleText, getDrawingTime());
        canvas.restore();
        drawFlipHalf(canvas, degrees);
        postInvalidateOnAnimation();
    }

    private void drawFlipHalf(Canvas canvas, float degrees) {
        boolean firstHalf = degrees < 90f;
        View view = firstHalf ? visibleText : invisibleText;
        Rect clip = firstHalf ? topRect : bottomRect;
        camera.save();
        camera.rotateX(firstHalf ? -degrees : -(degrees - 180f));
        camera.getMatrix(matrix);
        matrix.preScale(.25f, .25f);
        matrix.postScale(4f, 4f);
        matrix.preTranslate(-getWidth() / 2f, -getHeight() / 2f);
        matrix.postTranslate(getWidth() / 2f, getHeight() / 2f);
        canvas.save();
        canvas.clipRect(clip);
        canvas.concat(matrix);
        drawChild(canvas, view, getDrawingTime());
        drawFlipShade(canvas, degrees, firstHalf);
        camera.restore();
        canvas.restore();
    }

    private float getFlipDegrees() {
        return scroller.getCurrY() * 180f / Math.max(1, getHeight());
    }

    private void drawFlipShade(Canvas canvas, float degrees, boolean firstHalf) {
        float progress = firstHalf ? degrees / 90f : (180f - degrees) / 90f;
        shadePaint.setAlpha((int) (progress * 100));
        canvas.drawRect(firstHalf ? topRect : bottomRect, shadePaint);
    }

    public void setValue(int value) {
        String text = formatValue(value);
        visibleText.setText(text);
        invisibleText.setText(text);
        targetValue = value;
        initialized = true;
        flipping = false;
        scroller.abortAnimation();
        invalidate();
    }

    public void flipTo(int value) {
        String text = formatValue(value);
        if (!initialized) {
            setValue(value);
            return;
        }
        if (text.equals(visibleText.getText().toString()) || flipping) {
            if (flipping && value != targetValue) {
                setValue(value);
            }
            return;
        }
        targetValue = value;
        invisibleText.setText(text);
        flipping = true;
        scroller.startScroll(0, 0, 0, getHeight(), 480);
        postInvalidateOnAnimation();
    }

    private String formatValue(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
