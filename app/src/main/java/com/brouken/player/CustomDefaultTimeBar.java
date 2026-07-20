package com.brouken.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.media3.ui.DefaultTimeBar;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class CustomDefaultTimeBar extends DefaultTimeBar {

    Rect scrubberBar;
    private Rect progressBar;
    private boolean scrubbing;
    private int scrubbingStartX;
    private final Paint skipSegmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long skipDurationMs;
    private long[] skipStartsMs = new long[0];
    private long[] skipEndsMs = new long[0];

    public CustomDefaultTimeBar(Context context) {
        this(context, null);
    }

    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, attrs);
    }

    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr, @Nullable AttributeSet timebarAttrs) {
        this(context, attrs, defStyleAttr, timebarAttrs, 0);
    }

    public CustomDefaultTimeBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr, @Nullable AttributeSet timebarAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttr, timebarAttrs, defStyleRes);
        try {
            Field field = DefaultTimeBar.class.getDeclaredField("scrubberBar");
            field.setAccessible(true);
            scrubberBar = (Rect) field.get(this);
            Field progressField = DefaultTimeBar.class.getDeclaredField("progressBar");
            progressField.setAccessible(true);
            progressBar = (Rect) progressField.get(this);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        skipSegmentPaint.setColor(Color.rgb(30, 142, 214));
    }

    void setSkipSegments(long durationMs, long[] startsMs, long[] endsMs) {
        skipDurationMs = durationMs;
        skipStartsMs = startsMs == null ? new long[0] : startsMs.clone();
        skipEndsMs = endsMs == null ? new long[0] : endsMs.clone();
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progressBar == null || skipDurationMs <= 0 || skipStartsMs.length == 0) return;

        int count = Math.min(skipStartsMs.length, skipEndsMs.length);
        float width = progressBar.width();
        float centerY = progressBar.centerY();
        float halfHeight = Math.max(Utils.dpToPx(2), progressBar.height() / 2f);
        float minimumWidth = Utils.dpToPx(3);
        for (int index = 0; index < count; index++) {
            long start = Math.max(0, Math.min(skipDurationMs, skipStartsMs[index]));
            long end = Math.max(start, Math.min(skipDurationMs, skipEndsMs[index]));
            if (end <= start) continue;
            float left = progressBar.left + width * start / skipDurationMs;
            float right = progressBar.left + width * end / skipDurationMs;
            if (right - left < minimumWidth) right = Math.min(progressBar.right, left + minimumWidth);
            canvas.drawRect(left, centerY - halfHeight, right, centerY + halfHeight,
                    skipSegmentPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && scrubberBar != null) {
            scrubbing = false;
            scrubbingStartX = (int)event.getX();
            final int distanceFromScrubber = Math.abs(scrubberBar.right - scrubbingStartX);
            if (distanceFromScrubber > Utils.dpToPx(24))
                return true;
            else
                scrubbing = true;
        }
        if (!scrubbing && event.getAction() == MotionEvent.ACTION_MOVE && scrubberBar != null) {
            final int distanceFromStart = Math.abs(((int)event.getX()) - scrubbingStartX);
            if (distanceFromStart > Utils.dpToPx(6)) {
                scrubbing = true;
                try {
                    final Method method = DefaultTimeBar.class.getDeclaredMethod("startScrubbing", long.class);
                    method.setAccessible(true);
                    method.invoke(this, (long) 0);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else {
                return true;
            }
        }
        return super.onTouchEvent(event);
    }
}
