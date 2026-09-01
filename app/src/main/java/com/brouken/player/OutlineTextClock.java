package com.brouken.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.TextClock;

/** A clock with a thin outline so it remains readable over every video frame. */
public class OutlineTextClock extends TextClock {
    private final int strokeWidth;

    public OutlineTextClock(Context context) {
        this(context, null);
    }

    public OutlineTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        strokeWidth = Utils.dpToPx(1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout == null) {
            super.onDraw(canvas);
            return;
        }

        TextPaint paint = getPaint();
        Paint.Style previousStyle = paint.getStyle();
        float previousStrokeWidth = paint.getStrokeWidth();
        int previousColor = paint.getColor();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(Color.BLACK);
        canvas.save();
        canvas.translate(getTotalPaddingLeft(), getTotalPaddingTop());
        layout.draw(canvas);
        canvas.restore();

        paint.setStyle(previousStyle);
        paint.setStrokeWidth(previousStrokeWidth);
        paint.setColor(previousColor);
        super.onDraw(canvas);
    }
}
