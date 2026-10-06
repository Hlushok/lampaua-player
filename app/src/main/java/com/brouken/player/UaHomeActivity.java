package com.brouken.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.brouken.player.update.UpdateUi;
import com.brouken.player.update.Updater;

/** UA Player's launch page; browsing and playback remain separate destinations. */
public class UaHomeActivity extends AppCompatActivity {
    private ObjectAnimator pulse;
    private Drawable restingButtonBackground;
    private boolean attentionPlayed;

    @Override
    protected void onCreate(final Bundle state) {
        getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(state);
        attentionPlayed = state != null && state.getBoolean("ua.attention_played", false);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        setContentView(R.layout.activity_ua_home);
        final boolean tv = Utils.isTvBox(this);
        final boolean compact = !tv && getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        final View root = findViewById(R.id.ua_home_root);
        final int horizontal = dp(tv ? 48 : 24);
        final int vertical = dp(tv ? 27 : compact ? 12 : 24);
        root.setPadding(horizontal, vertical, horizontal, vertical);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(horizontal + bars.left, vertical + bars.top,
                    horizontal + bars.right, vertical + bars.bottom);
            return insets;
        });
        final View actions = findViewById(R.id.ua_home_actions);
        if (compact) {
            final ScrollView scroll = findViewById(R.id.ua_home_scroll);
            scroll.getChildAt(0).setPadding(0, dp(6), 0, dp(6));
            ((LinearLayout.LayoutParams) actions.getLayoutParams()).topMargin = dp(12);
            final ViewGroup column = (ViewGroup) actions;
            for (int i = 1; i < column.getChildCount(); i++) {
                ((LinearLayout.LayoutParams) column.getChildAt(i).getLayoutParams()).topMargin = dp(6);
            }
        }
        root.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            final int available = r - l - view.getPaddingLeft() - view.getPaddingRight();
            final int width = Math.min(Math.max(0, available), dp(tv ? 440 : 360));
            if (actions.getLayoutParams().width != width) {
                actions.getLayoutParams().width = width;
                actions.requestLayout();
            }
        });
        if (tv) {
            ((TextView) findViewById(R.id.ua_home_title)).setTextSize(24);
            ((TextView) findViewById(R.id.ua_home_subtitle)).setTextSize(24);
            final ViewGroup column = (ViewGroup) actions;
            for (int i = 0; i < column.getChildCount(); i++) {
                final ViewGroup button = (ViewGroup) column.getChildAt(i);
                button.setMinimumHeight(dp(60));
                // Google TV boxes sometimes advertise a touchscreen as well as Leanback.
                button.setFocusableInTouchMode(true);
                ((TextView) button.getChildAt(1)).setTextSize(20);
            }
        }
        findViewById(R.id.ua_home_open).setOnClickListener(v ->
                startActivity(new Intent(this, BrowserActivity.class)
                        .putExtra(BrowserActivity.EXTRA_OPEN_FILES, true)));
        findViewById(R.id.ua_home_link).setOnClickListener(v -> OpenLink.ask(this, this::play));
        findViewById(R.id.ua_home_room).setOnClickListener(v -> RoomJoin.show(this));
        findViewById(R.id.ua_home_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        if (tv) findViewById(R.id.ua_home_open).requestFocus();
        ViewCompat.requestApplyInsets(root);
        if (state == null) checkForUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!attentionPlayed) {
            attentionPlayed = true;
            if (!Utils.isReducedMotion(this)) startPulse(findViewById(R.id.ua_home_open));
        }
    }

    private void startPulse(final View view) {
        restingButtonBackground = view.getBackground();
        final RippleDrawable moving = (RippleDrawable) restingButtonBackground.getConstantState()
                .newDrawable(getResources()).mutate();
        final UaButtonGradient colors = new UaButtonGradient(
                getColor(R.color.brand_ramp_start), getColor(R.color.brand_ramp_end),
                getResources().getDisplayMetrics().density);
        moving.setDrawableByLayerId(android.R.id.background, colors);
        view.setBackground(moving);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        pulse = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f));
        pulse.setStartDelay(610);
        pulse.setDuration(1400);
        pulse.setRepeatCount(5);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.addUpdateListener(animation -> colors.setPhase(animation.getAnimatedFraction()));
        pulse.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(final Animator animation) { stopPulse(); }
        });
        pulse.start();
    }

    private void stopPulse() {
        if (pulse != null) {
            final ObjectAnimator running = pulse;
            pulse = null;
            running.cancel();
        }
        final View open = findViewById(R.id.ua_home_open);
        if (open != null) {
            if (restingButtonBackground != null) {
                open.setBackground(restingButtonBackground);
                restingButtonBackground = null;
            }
            open.setScaleX(1f);
            open.setScaleY(1f);
            open.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }

    @Override protected void onPause() {
        stopPulse();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopPulse();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(final Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("ua.attention_played", attentionPlayed);
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void play(final Uri uri) {
        startActivity(new Intent(Intent.ACTION_VIEW, uri).setClass(this, PlayerActivity.class));
    }

    private void checkForUpdate() {
        final Prefs prefs = new Prefs(this);
        final long now = System.currentTimeMillis();
        if (!BuildConfig.ENABLE_UPDATE || !prefs.autoUpdate
                || now - prefs.updateLastCheck < Updater.CHECK_INTERVAL_MS) return;
        prefs.setUpdateLastCheck(now);
        Updater.find(info -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            final boolean offer = info != null && info.versionCode != prefs.updateSkippedVersionCode;
            prefs.setUpdatePending(offer ? info : null);
            if (offer) UpdateUi.showAvailableDialog(this, Dialogs.dialogContext(this), info, () -> {
                prefs.setUpdateSkippedVersionCode(info.versionCode);
                prefs.setUpdatePending(null);
            }, false);
        }));
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.getRepeatCount() == 0) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
