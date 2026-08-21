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
import androidx.media3.ui.TimeBar;

import java.lang.reflect.Field;

class CustomDefaultTimeBar extends DefaultTimeBar {

    Rect scrubberBar;
    private Rect progressBar;
    private boolean scrubbing;
    private int scrubbingStartX;
    private boolean scrubbingNow;
    private int playheadLeft;
    private int playheadRight;
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
        addListener(new OnScrubListener() {
            @Override public void onScrubStart(TimeBar timeBar, long position) {
                scrubbingNow = true;
            }

            @Override public void onScrubMove(TimeBar timeBar, long position) { }

            @Override public void onScrubStop(TimeBar timeBar, long position, boolean canceled) {
                scrubbingNow = false;
            }
        });
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
        int minimumWidth = Utils.dpToPx(3);
        if (scrubberBar == null) {
            playheadLeft = playheadRight = 0;
        } else {
            int radius = playheadRadius();
            int playheadX = Math.min(Math.max(scrubberBar.right, progressBar.left),
                    progressBar.right);
            playheadLeft = playheadX - radius;
            playheadRight = playheadX + radius;
        }
        for (int index = 0; index < count; index++) {
            long start = Math.max(0, Math.min(skipDurationMs, skipStartsMs[index]));
            long end = Math.max(start, Math.min(skipDurationMs, skipEndsMs[index]));
            if (end <= start) continue;
            int left = Math.round(progressBar.left + width * start / skipDurationMs);
            int right = Math.round(progressBar.left + width * end / skipDurationMs);
            if (right - left < minimumWidth) right = Math.min(progressBar.right, left + minimumWidth);
            drawBand(canvas, left, right);
        }
    }

    private void drawBand(Canvas canvas, int left, int right) {
        if (right <= left) return;
        float top = progressBar.centerY() - Math.max(Utils.dpToPx(2), progressBar.height() / 2f);
        float bottom = progressBar.centerY() + Math.max(Utils.dpToPx(2), progressBar.height() / 2f);
        if (right > playheadLeft && left < playheadRight) {
            if (left < playheadLeft) {
                canvas.drawRect(left, top, playheadLeft, bottom, skipSegmentPaint);
            }
            if (right > playheadRight) {
                canvas.drawRect(playheadRight, top, right, bottom, skipSegmentPaint);
            }
            return;
        }
        canvas.drawRect(left, top, right, bottom, skipSegmentPaint);
    }

    private int playheadRadius() {
        if (scrubbingNow || isFocused()) {
            return getResources().getDimensionPixelSize(
                    R.dimen.exo_styled_progress_dragged_thumb_size) / 2;
        }
        return isEnabled() ? getResources().getDimensionPixelSize(
                R.dimen.exo_styled_progress_enabled_thumb_size) / 2 : 0;
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
        if (!scrubbing && scrubberBar != null
                && (event.getAction() == MotionEvent.ACTION_MOVE
                || event.getAction() == MotionEvent.ACTION_UP)) {
            final int distanceFromStart = Math.abs(((int)event.getX()) - scrubbingStartX);
            if (event.getAction() == MotionEvent.ACTION_MOVE
                    && distanceFromStart <= Utils.dpToPx(6)) {
                return true;
            }
            scrubbing = true;
            startScrubbingAt(event);
        }
        return super.onTouchEvent(event);
    }

    private void startScrubbingAt(MotionEvent event) {
        MotionEvent down = MotionEvent.obtainNoHistory(event);
        down.setAction(MotionEvent.ACTION_DOWN);
        if (progressBar != null) {
            float x = Math.min(Math.max(event.getX(), progressBar.left), progressBar.right - 1);
            down.setLocation(x, progressBar.centerY());
        }
        super.onTouchEvent(down);
        down.recycle();
    }
}
