package com.brouken.player;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

public final class UaDialogStyler {
    public enum FocusTarget { POSITIVE, NEGATIVE, LIST, CONTENT }

    private UaDialogStyler() {}

    public static void style(Context context, android.app.AlertDialog dialog,
                             FocusTarget focusTarget, int checkedIndex) {
        if (dialog == null) return;
        styleWindow(context, dialog);
        styleMessage(dialog.findViewById(android.R.id.message));
        Button positive = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
        styleButton(positive);
        styleButton(negative);
        styleButton(neutral);
        focus(context, dialog.getListView(), focusTarget, checkedIndex, positive, negative);
    }

    public static void style(Context context, androidx.appcompat.app.AlertDialog dialog,
                             FocusTarget focusTarget, int checkedIndex) {
        if (dialog == null) return;
        styleWindow(context, dialog);
        styleMessage(dialog.findViewById(android.R.id.message));
        Button positive = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
        Button neutral = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);
        styleButton(positive);
        styleButton(negative);
        styleButton(neutral);
        focus(context, dialog.getListView(), focusTarget, checkedIndex, positive, negative);
    }

    private static void styleWindow(Context context, Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(R.drawable.ua_dialog_background);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.58f);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min((int) (screenWidth * (Utils.isTvBox(context) ? 0.62f : 0.76f)),
                Utils.dpToPx(760));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static void styleMessage(TextView message) {
        if (message == null) return;
        message.setTextColor(Color.WHITE);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        message.setLineSpacing(0f, 1.12f);
    }

    private static void styleButton(Button button) {
        if (button == null) return;
        button.setTextColor(Color.rgb(240, 183, 38));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.ua_dialog_button_background);
        button.setPadding(Utils.dpToPx(16), 0, Utils.dpToPx(16), 0);
    }

    private static void focus(Context context, ListView list, FocusTarget target,
                              int checkedIndex, Button positive, Button negative) {
        if (target == FocusTarget.CONTENT) return;
        if (target == FocusTarget.LIST && list != null) {
            int index = DialogFocusPolicy.preferredListIndex(checkedIndex, list.getCount());
            if (index >= 0) {
                GradientDrawable selector = new GradientDrawable();
                selector.setColor(Color.argb(222, 10, 39, 76));
                selector.setStroke(Utils.dpToPx(2), Color.rgb(240, 183, 38));
                selector.setCornerRadius(Utils.dpToPx(9));
                list.setSelector(selector);
                list.setDrawSelectorOnTop(false);
                list.setPadding(list.getPaddingLeft(), Utils.dpToPx(4),
                        list.getPaddingRight(), Utils.dpToPx(4));
                list.setClipToPadding(false);
                list.setFocusable(true);
                if (Utils.isTvBox(context)) list.setFocusableInTouchMode(true);
                list.setSelection(index);
                list.post(() -> {
                    list.requestFocus();
                    list.setSelection(index);
                });
                return;
            }
        }
        Button preferred = target == FocusTarget.POSITIVE ? positive : negative;
        if (preferred == null) preferred = positive != null ? positive : negative;
        if (preferred != null) preferred.requestFocus();
    }
}
