package com.brouken.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

final class SwipeToUnlockView extends FrameLayout {
    private final ImageView icon;
    private final TextView text;
    private Runnable onUnlock;
    private boolean unlocking;

    SwipeToUnlockView(Context context) {
        super(context);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(235, 4, 18, 40));
        background.setStroke(Utils.dpToPx(1), Color.rgb(240, 183, 38));
        background.setCornerRadius(Utils.dpToPx(24));
        setBackground(background);
        setPadding(Utils.dpToPx(10), Utils.dpToPx(8), Utils.dpToPx(10), Utils.dpToPx(8));

        int iconSize = Utils.dpToPx(28);
        text = new TextView(context);
        text.setText(R.string.swipe_unlock);
        text.setTextColor(Color.WHITE);
        text.setTextSize(13);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setGravity(Gravity.CENTER);
        LayoutParams textParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER;
        textParams.leftMargin = textParams.rightMargin = iconSize + Utils.dpToPx(6);
        addView(text, textParams);

        icon = new ImageView(context);
        icon.setImageResource(R.drawable.ic_lock_24dp);
        icon.setColorFilter(Color.WHITE);
        LayoutParams iconParams = new LayoutParams(iconSize, iconSize);
        iconParams.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        addView(icon, iconParams);
    }

    void setOnUnlockListener(Runnable listener) {
        onUnlock = listener;
    }

    private float maxTranslation() {
        return Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight() - icon.getWidth());
    }

    private void render(float translation) {
        icon.setTranslationX(translation);
        float max = maxTranslation();
        text.setAlpha(max > 0 ? 1f - translation / max : 1f);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (unlocking) return true;
        float max = maxTranslation();
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float translation = Math.max(0, Math.min(max,
                    event.getX() - getPaddingLeft() - icon.getWidth() / 2f));
            if (max > 0 && translation >= max - Utils.dpToPx(2)) {
                unlocking = true;
                if (onUnlock != null) onUnlock.run();
                setVisibility(GONE);
            } else {
                render(translation);
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            ValueAnimator animator = ValueAnimator.ofFloat(icon.getTranslationX(), 0f);
            animator.setDuration(250);
            animator.addUpdateListener(value -> render((float) value.getAnimatedValue()));
            animator.start();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            unlocking = false;
            render(0f);
        }
    }
}
