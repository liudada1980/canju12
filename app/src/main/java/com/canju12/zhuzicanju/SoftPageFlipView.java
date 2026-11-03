package com.canju12.zhuzicanju;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Lightweight soft-page transition used for question changes.
 *
 * direction > 0: bottom-right corner folds toward the left.
 * direction < 0: bottom-left corner folds toward the right.
 */
public final class SoftPageFlipView extends View {
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path visiblePath = new Path();
    private final Path foldPath = new Path();
    private final RectF sourceRect = new RectF();

    private Bitmap bitmap;
    private final int direction;
    private float progress;

    public SoftPageFlipView(Context context, Bitmap bitmap, int direction) {
        super(context);
        this.bitmap = bitmap;
        this.direction = direction >= 0 ? 1 : -1;
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bitmap.isRecycled() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        float width = getWidth();
        float height = getHeight();
        float p = progress;
        float hingeX = direction > 0 ? width * (1f - p) : width * p;
        float curve = width * 0.08f * (float) Math.sin(p * Math.PI);
        float lowerHingeY = height * (0.80f + 0.08f * p);

        sourceRect.set(0f, 0f, bitmap.getWidth(), bitmap.getHeight());

        buildVisiblePath(width, height, hingeX, lowerHingeY, curve);
        canvas.save();
        canvas.clipPath(visiblePath);
        canvas.drawBitmap(bitmap, null, new RectF(0f, 0f, width, height), bitmapPaint);
        canvas.restore();

        drawFoldShadow(canvas, width, height, hingeX, lowerHingeY, curve);
    }

    private void buildVisiblePath(
            float width, float height, float hingeX, float lowerHingeY, float curve) {
        visiblePath.reset();

        if (direction > 0) {
            visiblePath.moveTo(0f, 0f);
            visiblePath.lineTo(hingeX, 0f);
            visiblePath.lineTo(hingeX, lowerHingeY);
            visiblePath.quadTo(
                    hingeX + curve,
                    height * 0.94f,
                    hingeX + width * 0.018f,
                    height);
            visiblePath.lineTo(0f, height);
        } else {
            visiblePath.moveTo(hingeX, 0f);
            visiblePath.lineTo(width, 0f);
            visiblePath.lineTo(width, height);
            visiblePath.lineTo(hingeX - width * 0.018f, height);
            visiblePath.quadTo(
                    hingeX - curve,
                    height * 0.94f,
                    hingeX,
                    lowerHingeY);
        }
        visiblePath.close();
    }

    private void drawFoldShadow(
            Canvas canvas, float width, float height, float hingeX, float lowerHingeY, float curve) {
        float shadowWidth = Math.max(18f, width * 0.055f) * (1f - 0.25f * progress);
        float shadowAlpha = 0.16f + 0.16f * (float) Math.sin(progress * Math.PI);

        int transparent = Color.TRANSPARENT;
        int shadow = Color.argb(Math.round(shadowAlpha * 255f), 0, 0, 0);
        int highlight = Color.argb(34, 255, 255, 255);

        foldPath.reset();
        if (direction > 0) {
            foldPath.moveTo(hingeX, lowerHingeY);
            foldPath.quadTo(hingeX + curve, height * 0.94f, hingeX + width * 0.018f, height);
            foldPath.lineTo(hingeX + shadowWidth, height);
            foldPath.quadTo(
                    hingeX + curve + shadowWidth,
                    height * 0.92f,
                    hingeX + shadowWidth,
                    lowerHingeY);
        } else {
            foldPath.moveTo(hingeX, lowerHingeY);
            foldPath.quadTo(hingeX - curve, height * 0.94f, hingeX - width * 0.018f, height);
            foldPath.lineTo(hingeX - shadowWidth, height);
            foldPath.quadTo(
                    hingeX - curve - shadowWidth,
                    height * 0.92f,
                    hingeX - shadowWidth,
                    lowerHingeY);
        }
        foldPath.close();

        shadowPaint.setShader(new LinearGradient(
                direction > 0 ? hingeX : hingeX - shadowWidth,
                0f,
                direction > 0 ? hingeX + shadowWidth : hingeX,
                0f,
                direction > 0
                        ? new int[]{shadow, transparent}
                        : new int[]{transparent, shadow},
                null,
                Shader.TileMode.CLAMP));
        canvas.drawPath(foldPath, shadowPaint);
        shadowPaint.setShader(null);

        highlightPaint.setColor(highlight);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(Math.max(1f, width * 0.002f));
        highlightPaint.setShadowLayer(width * 0.006f, 0f, 0f, Color.argb(48, 255, 255, 255));
        canvas.drawPath(foldPath, highlightPaint);
        highlightPaint.clearShadowLayer();
        highlightPaint.setStyle(Paint.Style.FILL);
    }

    public void releaseBitmap() {
        bitmap = null;
    }

    public void disposeBitmap() {
        Bitmap current = bitmap;
        bitmap = null;
        if (current != null && !current.isRecycled()) {
            current.recycle();
        }
    }
}
