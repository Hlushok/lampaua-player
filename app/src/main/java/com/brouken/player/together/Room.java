package com.brouken.player.together;

import android.net.Uri;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * A LAMPA-compatible room code and its password-derived relay channel.
 * Protocol behavior is adapted from Just+ Player by Oleksandr Zhyzhchenko.
 */
public final class Room {
    public static final String DEFAULT_INVITE_PAGE = "https://siaivo.isroot.in/lparty/";

    private static final String CHANNEL_PREFIX = "lparty-r-";
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String PARAM_ROOM = "room";
    private static final int CODE_LENGTH = 6;
    private static final int CHANNEL_HEX = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile String invitePage = DEFAULT_INVITE_PAGE;

    private final String code;
    private final String password;
    private final String channel;

    public Room(final String code, final String password) {
        if (!isCode(code)) {
            throw new IllegalArgumentException("Invalid room code");
        }
        this.code = code.toUpperCase(Locale.US);
        this.password = password == null ? "" : password;
        this.channel = CHANNEL_PREFIX + hex(sha256(this.code + "|" + this.password), CHANNEL_HEX);
    }

    public static String newCode() {
        final StringBuilder value = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            value.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return value.toString();
    }

    public String code() {
        return code;
    }

    public String channel() {
        return channel;
    }

    public boolean hasPassword() {
        return !password.isEmpty();
    }

    public String invite() {
        final String value = password.isEmpty() ? code : code + ':' + password;
        final String encoded = Base64.encodeToString(utf8(value), Base64.NO_WRAP);
        return invitePage + "?" + PARAM_ROOM + "=" + Uri.encode(encoded);
    }

    public static void setInvitePage(final String url) {
        final String value = url == null ? "" : url.trim();
        final String lower = value.toLowerCase(Locale.US);
        if (value.isEmpty() || !(lower.startsWith("http://") || lower.startsWith("https://"))) {
            invitePage = DEFAULT_INVITE_PAGE;
            return;
        }
        final int query = value.indexOf('?');
        invitePage = query < 0 ? value : value.substring(0, query);
    }

    public static String invitePage() {
        return invitePage;
    }

    public static Invite inviteFrom(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        final String raw = value.trim();
        if (isCode(raw)) {
            return new Invite(raw.toUpperCase(Locale.US), "");
        }
        final String decoded = decodeBase64(raw);
        if (decoded == null) {
            return null;
        }
        final int separator = decoded.indexOf(':');
        final String code = (separator < 0 ? decoded : decoded.substring(0, separator)).trim();
        if (!isCode(code)) {
            return null;
        }
        final String password = separator < 0 ? "" : decoded.substring(separator + 1);
        return new Invite(code.toUpperCase(Locale.US), password);
    }

    public static Invite inviteFrom(final Uri uri) {
        return uri == null ? null : inviteFrom(uri.getQueryParameter(PARAM_ROOM));
    }

    public static boolean isCode(final String text) {
        if (text == null || text.length() != CODE_LENGTH) {
            return false;
        }
        final String upper = text.toUpperCase(Locale.US);
        for (int i = 0; i < upper.length(); i++) {
            final char c = upper.charAt(i);
            if ((c < '0' || c > '9') && (c < 'A' || c > 'Z')) {
                return false;
            }
        }
        return true;
    }

    public static final class Invite {
        public final String code;
        public final String password;

        Invite(final String code, final String password) {
            this.code = code;
            this.password = password;
        }
    }

    private static String decodeBase64(final String value) {
        for (int flags : new int[]{Base64.DEFAULT, Base64.URL_SAFE}) {
            try {
                return new String(Base64.decode(value, flags), "UTF-8");
            } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
                // Try the other alphabet before rejecting the invite.
            }
        }
        return null;
    }

    private static byte[] sha256(final String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(utf8(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] utf8(final String value) {
        try {
            return value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value.getBytes();
        }
    }

    private static String hex(final byte[] bytes, final int chars) {
        final StringBuilder result = new StringBuilder(chars);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >> 4) & 0x0f, 16));
            if (result.length() == chars) {
                break;
            }
            result.append(Character.forDigit(value & 0x0f, 16));
            if (result.length() == chars) {
                break;
            }
        }
        return result.toString();
    }
}
