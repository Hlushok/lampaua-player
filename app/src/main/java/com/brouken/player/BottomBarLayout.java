package com.brouken.player;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Bottom control bar that travels by its actual laid-out height.
 *
 * <p>Media3 builds its hide animation from {@code exo_styled_bottom_bar_height}. The bar is
 * later extended by the navigation-bar or TV overscan inset, so the original translation would
 * stop short and leave part of the controls over the picture. Scaling that translation keeps the
 * visible and parked endpoints exact.</p>
 */
public class BottomBarLayout extends FrameLayout {

    private float travelScale = 1f;
    private float travel;

    public BottomBarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** Laid-out height divided by the resource height Media3 uses for its animation. */
    void setTravelScale(float scale) {
        if (scale != travelScale) {
            travelScale = scale;
            super.setTranslationY(travel * scale);
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        travel = translationY;
        super.setTranslationY(translationY * travelScale);
    }
}
