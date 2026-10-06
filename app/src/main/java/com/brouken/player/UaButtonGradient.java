package com.brouken.player;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Moving UA colors for the launch button, keeping its pill shape and TV focus ring. */
final class UaButtonGradient extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix translation = new Matrix();
    private final RectF shape = new RectF();
    private final RectF outline = new RectF();
    private final int blue;
    private final int gold;
    private final float stroke;
    private LinearGradient gradient;
    private float phase;
    private boolean focused;

    UaButtonGradient(final int blue, final int gold, final float density) {
        this.blue = blue;
        this.gold = gold;
        stroke = 3f * density;
        ring.setColor(Color.WHITE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
    }

    void setPhase(final float phase) {
        this.phase = phase;
        updateTranslation();
        invalidateSelf();
    }

    @Override protected void onBoundsChange(final Rect bounds) {
        shape.set(bounds);
        outline.set(shape);
        outline.inset(stroke / 2f, stroke / 2f);
        if (bounds.isEmpty()) {
            gradient = null;
            return;
        }
        // Twice the button diagonal makes the resting frame blue-to-gold;
        // translating it reverses the colors smoothly without allocating per frame.
        gradient = new LinearGradient(bounds.right, bounds.top,
                bounds.left - bounds.width(), bounds.bottom + bounds.height(),
                new int[]{blue, gold, blue}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.MIRROR);
        fill.setShader(gradient);
        updateTranslation();
    }

    private void updateTranslation() {
        if (gradient == null) return;
        translation.setTranslate(-shape.width() * phase, shape.height() * phase);
        gradient.setLocalMatrix(translation);
    }

    @Override public void draw(final Canvas canvas) {
        if (gradient == null) return;
        final float radius = shape.height() / 2f;
        canvas.drawRoundRect(shape, radius, radius, fill);
        if (focused) canvas.drawRoundRect(outline, radius, radius, ring);
    }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(final int[] state) {
        boolean next = false;
        for (final int value : state) {
            if (value == android.R.attr.state_focused) next = true;
        }
        if (focused == next) return false;
        focused = next;
        invalidateSelf();
        return true;
    }

    @Override public void setAlpha(final int alpha) {
        fill.setAlpha(alpha);
        ring.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(final ColorFilter filter) {
        fill.setColorFilter(filter);
        ring.setColorFilter(filter);
        invalidateSelf();
    }

    @SuppressWarnings("deprecation")
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
