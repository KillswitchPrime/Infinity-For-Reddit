package com.libRG;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

import ml.docilealligator.infinityforreddit.R;

/**
 * Full re-implementation of the com.libRG:customtextview library.
 * Supports: custom fonts, corner radius, border stroke color/width, oval/rectangle shape.
 */
public class CustomTextView extends AppCompatTextView {

    public static final int RECTANGLE = 0;
    public static final int OVAL = 1;

    private int mShape = RECTANGLE;
    private int mStrokeColor = Color.TRANSPARENT;
    private float mStrokeWidth = 0f;
    private float mRadius = 0f;
    private int mSolidColor = Color.TRANSPARENT;
    private boolean mRoundedView = false;

    private Paint mBorderPaint;
    private Paint mFillPaint;
    private RectF mRectF;
    private Path mClipPath;

    public CustomTextView(Context context) {
        super(context);
        init(context, null);
    }

    public CustomTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CustomTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomTextView);
            try {
                String shapeStr = a.getString(R.styleable.CustomTextView_lib_setShape);
                if ("oval".equalsIgnoreCase(shapeStr)) {
                    mShape = OVAL;
                } else {
                    mShape = RECTANGLE;
                }
                mRadius = a.getDimension(R.styleable.CustomTextView_lib_setRadius, 0f);
                mStrokeColor = a.getColor(R.styleable.CustomTextView_lib_setStrokeColor, Color.TRANSPARENT);
                mStrokeWidth = a.getDimension(R.styleable.CustomTextView_lib_setStrokeWidth, 0f);
                mSolidColor = a.getColor(R.styleable.CustomTextView_lib_setSolidColor, Color.TRANSPARENT);
                mRoundedView = a.getBoolean(R.styleable.CustomTextView_lib_setRoundedView, false);

                String fontPath = a.getString(R.styleable.CustomTextView_lib_setFont);
                if (fontPath != null && !fontPath.isEmpty()) {
                    try {
                        Typeface tf = Typeface.createFromAsset(context.getAssets(), fontPath);
                        setTypeface(tf);
                    } catch (Exception ignored) {
                    }
                }
            } finally {
                a.recycle();
            }
        }

        mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setColor(mStrokeColor);
        mBorderPaint.setStrokeWidth(mStrokeWidth);

        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(mSolidColor);

        mRectF = new RectF();
        mClipPath = new Path();

        setBackgroundResource(0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float half = mStrokeWidth / 2f;
        mRectF.set(half, half, w - half, h - half);

        if (mShape == OVAL) {
            if (mSolidColor != Color.TRANSPARENT) canvas.drawOval(mRectF, mFillPaint);
            if (mStrokeWidth > 0) canvas.drawOval(mRectF, mBorderPaint);
            if (mRoundedView) {
                mClipPath.reset();
                mClipPath.addOval(mRectF, Path.Direction.CW);
                canvas.clipPath(mClipPath);
            }
        } else {
            if (mSolidColor != Color.TRANSPARENT) canvas.drawRoundRect(mRectF, mRadius, mRadius, mFillPaint);
            if (mStrokeWidth > 0) canvas.drawRoundRect(mRectF, mRadius, mRadius, mBorderPaint);
            if (mRoundedView && mRadius > 0) {
                mClipPath.reset();
                mClipPath.addRoundRect(mRectF, mRadius, mRadius, Path.Direction.CW);
                canvas.clipPath(mClipPath);
            }
        }

        super.onDraw(canvas);
    }

    // Public API

    public void setShape(int shape) { mShape = shape; invalidate(); }
    public void setRadius(float radius) { mRadius = radius; invalidate(); }
    public void setStrokeColor(int color) { mStrokeColor = color; mBorderPaint.setColor(color); invalidate(); }
    /** Alias for setStrokeColor — matches original library API */
    public void setBorderColor(int color) { setStrokeColor(color); }
    public void setStrokeWidth(float width) { mStrokeWidth = width; mBorderPaint.setStrokeWidth(width); invalidate(); }
    public void setSolidColor(int color) { mSolidColor = color; mFillPaint.setColor(color); invalidate(); }
    public void setRoundedView(boolean rounded) { mRoundedView = rounded; invalidate(); }

    public void setFont(String fontPath) {
        try {
            setTypeface(Typeface.createFromAsset(getContext().getAssets(), fontPath));
        } catch (Exception ignored) {}
    }
}
