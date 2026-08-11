package com.brouken.player.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdatePolicy {
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private UpdatePolicy() {}

    public static boolean shouldOffer(int currentCode, UpdateInfo release,
                                      int skippedCode, boolean debugBuild) {
        return !debugBuild && release != null && release.versionCode > currentCode
                && release.versionCode != skippedCode
                && release.apkUrl != null && release.apkUrl.startsWith("https://")
                && release.apkUrl.toLowerCase().contains(".apk");
    }

    public static int versionCode(String version) {
        if (version == null) return 0;
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1)) * 1_000_000
                    + Integer.parseInt(matcher.group(2)) * 1_000
                    + Integer.parseInt(matcher.group(3));
        } catch (NumberFormatException error) {
            return 0;
        }
    }
}
