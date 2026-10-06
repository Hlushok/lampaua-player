package com.brouken.player;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;

import com.brouken.player.together.Relay;
import com.brouken.player.together.Room;
import com.brouken.player.together.TogetherManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared lobby flow, kept on the page that asked to join until a room is chosen. */
final class RoomJoin {
    private final Activity activity;

    private RoomJoin(final Activity activity) {
        this.activity = activity;
    }

    static void show(final Activity activity) {
        final RoomJoin join = new RoomJoin(activity);
        final List<Dialogs.MenuItem> items = new ArrayList<>();
        items.add(new Dialogs.MenuItem(R.drawable.ic_search_24dp,
                activity.getString(R.string.together_find), null, false, join::findRooms));
        items.add(new Dialogs.MenuItem(R.drawable.ic_link_24dp,
                activity.getString(R.string.together_enter_code), null, false, join::askRoomCode));
        Dialogs.menu(activity, UiMetrics.of(activity, Utils.isTvBox(activity)), null,
                activity.getString(R.string.together_join), items);
    }

    private void findRooms() {
        Relay.setBase(Prefs.getTogetherRelay(activity));
        Notice.show(activity, R.string.together_searching, false, R.drawable.ic_together_24dp);
        TogetherManager.discover(rooms -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (rooms.isEmpty()) {
                Notice.show(activity, R.string.together_none_found, true, R.drawable.ic_together_24dp);
                return;
            }
            final List<Dialogs.MenuItem> items = new ArrayList<>();
            for (final org.json.JSONObject ad : rooms) {
                final String id = ad.optString("id");
                final boolean locked = ad.optInt("pwd") == 1;
                final String title = ad.optString("title", "").isEmpty()
                        ? ad.optString("name", id) : ad.optString("title");
                final String poster = ad.optString("poster", "");
                items.add(new Dialogs.MenuItem(
                        locked ? R.drawable.ic_lock_24dp
                                : poster.isEmpty() ? R.drawable.ic_together_24dp : 0,
                        poster, title,
                        activity.getString(R.string.together_room_summary,
                                ad.optString("owner", ""), ad.optInt("members")), false,
                        () -> {
                            if (locked) askRoomPassword(id);
                            else openRoom(id, "");
                        }));
            }
            Dialogs.menu(activity, UiMetrics.of(activity, Utils.isTvBox(activity)), null,
                    activity.getString(R.string.together_find), items);
        });
    }

    private void askRoomCode() {
        final Context context = Dialogs.dialogContext(activity);
        final ViewGroup fields = Dialogs.dialogFields(context);
        final EditText code = Dialogs.textField(fields, activity.getString(R.string.together_code), "ABC234");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText password = Dialogs.textField(fields, activity.getString(R.string.together_password));
        Dialogs.fields(activity, activity.getString(R.string.together_join), fields,
                activity.getString(android.R.string.ok), () -> {
                    final String entered = code.getText().toString().trim().toUpperCase(Locale.US);
                    if (Room.isCode(entered)) openRoom(entered, password.getText().toString());
                    else Notice.show(activity, R.string.together_code_invalid, true,
                            R.drawable.ic_together_24dp);
                });
    }

    private void askRoomPassword(final String code) {
        final ViewGroup fields = Dialogs.dialogFields(Dialogs.dialogContext(activity));
        final EditText input = Dialogs.textField(fields, activity.getString(R.string.together_password));
        input.setText(Prefs.getTogetherPassword(activity));
        input.setSelection(input.getText().length());
        Dialogs.fields(activity, code, fields, activity.getString(android.R.string.ok),
                () -> openRoom(code, input.getText().toString()));
    }

    private void openRoom(final String code, final String password) {
        activity.startActivity(new Intent(activity, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_JOIN_CODE, code)
                .putExtra(PlayerActivity.EXTRA_JOIN_PASSWORD, password));
    }
}
