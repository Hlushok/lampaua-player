package com.brouken.player.together;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

/** Serializes the full launcher Bundle so existing LAMPA parsing remains the single source of truth. */
public final class SessionCodec {
    private static final String TYPE = "t";
    private static final String VALUE = "v";
    private static final int MAX_DEPTH = 4;

    /*
     * Generic encoding intentionally preserves launcher-owned video_list, headers, subs, segments,
     * season, episode, imdb_id, id, quality_levels and per-episode quality_urls keys.
     */
    private SessionCodec() {
    }

    public static JSONObject toJson(final Bundle bundle) {
        return toJson(bundle, 0);
    }

    public static Bundle toBundle(final JSONObject json) {
        return toBundle(json, 0);
    }

    private static JSONObject toJson(final Bundle bundle, final int depth) {
        final JSONObject output = new JSONObject();
        if (bundle == null || depth > MAX_DEPTH) {
            return output;
        }
        for (String key : bundle.keySet()) {
            final JSONObject encoded = encode(bundle.get(key), depth);
            if (encoded != null) {
                try {
                    output.put(key, encoded);
                } catch (Exception ignored) {
                    // Skip only the malformed entry, not the session.
                }
            }
        }
        return output;
    }

    private static Bundle toBundle(final JSONObject json, final int depth) {
        final Bundle output = new Bundle();
        if (json == null || depth > MAX_DEPTH) {
            return output;
        }
        final Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            final String key = keys.next();
            final JSONObject entry = json.optJSONObject(key);
            if (entry != null) {
                put(output, key, entry, depth);
            }
        }
        return output;
    }

    private static JSONObject encode(final Object value, final int depth) {
        try {
            if (value instanceof String || value instanceof CharSequence) {
                return entry("s", value.toString());
            }
            if (value instanceof Integer) {
                return entry("i", value);
            }
            if (value instanceof Long) {
                return entry("l", value);
            }
            if (value instanceof Boolean) {
                return entry("b", value);
            }
            if (value instanceof Float || value instanceof Double) {
                return entry("d", ((Number) value).doubleValue());
            }
            if (value instanceof Uri) {
                return entry("u", value.toString());
            }
            if (value instanceof Bundle) {
                return entry("bu", toJson((Bundle) value, depth + 1));
            }
            if (value instanceof String[]) {
                return entry("sa", strings((String[]) value));
            }
            if (value instanceof int[]) {
                final JSONArray values = new JSONArray();
                for (int item : (int[]) value) {
                    values.put(item);
                }
                return entry("ia", values);
            }
            if (value instanceof long[]) {
                final JSONArray values = new JSONArray();
                for (long item : (long[]) value) {
                    values.put(item);
                }
                return entry("la", values);
            }
            if (value instanceof ArrayList) {
                return encodeList((ArrayList<?>) value, depth);
            }
            if (value instanceof Parcelable[]) {
                final JSONArray values = new JSONArray();
                for (Parcelable item : (Parcelable[]) value) {
                    final JSONObject encoded = encode(item, depth + 1);
                    values.put(encoded == null ? JSONObject.NULL : encoded);
                }
                return entry("pa", values);
            }
        } catch (Exception ignored) {
            // Unsupported values are inert in the launch contract and can be omitted.
        }
        return null;
    }

    private static void put(final Bundle output, final String key, final JSONObject entry,
                            final int depth) {
        final String type = entry.optString(TYPE);
        try {
            switch (type) {
                case "s":
                    output.putString(key, entry.optString(VALUE));
                    break;
                case "i":
                    output.putInt(key, entry.optInt(VALUE));
                    break;
                case "l":
                    output.putLong(key, entry.optLong(VALUE));
                    break;
                case "b":
                    output.putBoolean(key, entry.optBoolean(VALUE));
                    break;
                case "d":
                    output.putDouble(key, entry.optDouble(VALUE));
                    break;
                case "u":
                    output.putParcelable(key, Uri.parse(entry.optString(VALUE)));
                    break;
                case "bu":
                    output.putBundle(key, toBundle(entry.optJSONObject(VALUE), depth + 1));
                    break;
                case "sa":
                case "sl":
                    output.putStringArray(key, stringArray(entry.optJSONArray(VALUE)));
                    break;
                case "ul":
                    output.putParcelableArrayList(key, uriList(entry.optJSONArray(VALUE)));
                    break;
                case "bl":
                    output.putParcelableArrayList(key,
                            bundleList(entry.optJSONArray(VALUE), depth));
                    break;
                case "ia":
                    output.putIntArray(key, intArray(entry.optJSONArray(VALUE)));
                    break;
                case "la":
                    output.putLongArray(key, longArray(entry.optJSONArray(VALUE)));
                    break;
                case "pa":
                    output.putParcelableArray(key, parcelables(entry.optJSONArray(VALUE), depth));
                    break;
                default:
                    break;
            }
        } catch (Exception ignored) {
            // Malformed peer data costs one field, never the whole playback session.
        }
    }

    private static JSONArray strings(final String[] items) {
        final JSONArray values = new JSONArray();
        for (String item : items) {
            values.put(item == null ? JSONObject.NULL : item);
        }
        return values;
    }

    private static JSONObject encodeList(final ArrayList<?> items, final int depth)
            throws Exception {
        boolean bundles = !items.isEmpty();
        boolean uris = !items.isEmpty();
        boolean strings = true;
        boolean typed = false;
        for (Object item : items) {
            if (item == null) {
                continue;
            }
            typed = true;
            bundles &= item instanceof Bundle;
            uris &= item instanceof Uri;
            strings &= item instanceof String || item instanceof CharSequence;
        }
        final JSONArray values = new JSONArray();
        if (typed && bundles) {
            for (Object item : items) {
                values.put(item == null ? JSONObject.NULL
                        : toJson((Bundle) item, depth + 1));
            }
            return entry("bl", values);
        }
        if (typed && uris) {
            for (Object item : items) {
                values.put(item == null ? JSONObject.NULL : item.toString());
            }
            return entry("ul", values);
        }
        if (strings) {
            for (Object item : items) {
                values.put(item == null ? JSONObject.NULL : item.toString());
            }
            return entry("sl", values);
        }
        return null;
    }

    private static String[] stringArray(final JSONArray values) {
        final String[] items = new String[values == null ? 0 : values.length()];
        for (int i = 0; i < items.length; i++) {
            items[i] = values.isNull(i) ? null : values.optString(i);
        }
        return items;
    }

    private static ArrayList<Uri> uriList(final JSONArray values) {
        final ArrayList<Uri> items = new ArrayList<>();
        for (int i = 0; values != null && i < values.length(); i++) {
            items.add(values.isNull(i) ? null : Uri.parse(values.optString(i)));
        }
        return items;
    }

    private static ArrayList<Bundle> bundleList(final JSONArray values, final int depth) {
        final ArrayList<Bundle> items = new ArrayList<>();
        for (int i = 0; values != null && i < values.length(); i++) {
            items.add(values.isNull(i) ? null
                    : toBundle(values.optJSONObject(i), depth + 1));
        }
        return items;
    }

    private static int[] intArray(final JSONArray values) {
        final int[] items = new int[values == null ? 0 : values.length()];
        for (int i = 0; i < items.length; i++) {
            items[i] = values.optInt(i);
        }
        return items;
    }

    private static long[] longArray(final JSONArray values) {
        final long[] items = new long[values == null ? 0 : values.length()];
        for (int i = 0; i < items.length; i++) {
            items[i] = values.optLong(i);
        }
        return items;
    }

    private static Parcelable[] parcelables(final JSONArray values, final int depth) {
        final Parcelable[] items = new Parcelable[values == null ? 0 : values.length()];
        for (int i = 0; i < items.length; i++) {
            final JSONObject item = values.optJSONObject(i);
            final String type = item == null ? "" : item.optString(TYPE);
            if ("u".equals(type)) {
                items[i] = Uri.parse(item.optString(VALUE));
            } else if ("bu".equals(type)) {
                items[i] = toBundle(item.optJSONObject(VALUE), depth + 1);
            }
        }
        return items;
    }

    private static JSONObject entry(final String type, final Object value) throws Exception {
        return new JSONObject().put(TYPE, type).put(VALUE, value);
    }
}
