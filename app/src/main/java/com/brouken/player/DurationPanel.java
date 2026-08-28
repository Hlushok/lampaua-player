package com.brouken.player;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;

final class DurationPanel {
    interface Callback { void onMinutes(int minutes); }

    private DurationPanel() {}

    static AlertDialog create(Context context, Callback callback) {
        EditText minutes = new EditText(context);
        minutes.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutes.setHint(R.string.sleep_timer_minutes_hint);
        minutes.setSingleLine(true);
        int padding = Utils.dpToPx(24);
        minutes.setPadding(padding, padding / 2, padding, padding / 2);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.sleep_timer_custom)
                .setView(minutes)
                .setPositiveButton(android.R.string.ok, (selectedDialog, which) -> {
                    try {
                        int value = Integer.parseInt(minutes.getText().toString().trim());
                        if (value > 0 && callback != null) callback.onMinutes(value);
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            UaDialogStyler.style(context, dialog, UaDialogStyler.FocusTarget.CONTENT, -1);
            minutes.post(minutes::requestFocus);
        });
        return dialog;
    }
}
