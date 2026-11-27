package com.brouken.player.update;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/** Checks the offered asset and update identity before invoking Android's package installer. */
final class ApkUpdateVerifier {
    private ApkUpdateVerifier() { }

    static String digest(String githubDigest) {
        if (githubDigest == null || !githubDigest.matches("(?i)sha256:[0-9a-f]{64}")) return null;
        return githubDigest.substring(7).toLowerCase(java.util.Locale.ROOT);
    }

    static boolean matchesFile(File file, UpdateInfo info) throws Exception {
        if (info.size <= 0 || file.length() != info.size || info.sha256 == null
                || !info.sha256.matches("[0-9a-f]{64}")) return false;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024]; int count;
            while ((count = stream.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) return false;
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder actual = new StringBuilder();
        for (byte value : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return info.sha256.equals(actual.toString());
    }

    static boolean verify(Context context, File file, UpdateInfo info) throws Exception {
        if (!matchesFile(file, info)) return false;
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        return matchesPackage(installed, archive, info);
    }

    static boolean matchesPackage(PackageInfo installed, PackageInfo archive, UpdateInfo info) {
        if (installed == null || archive == null || !installed.packageName.equals(archive.packageName)
                || !info.versionName.equals(archive.versionName)) return false;
        long current = Build.VERSION.SDK_INT >= 28 ? installed.getLongVersionCode() : installed.versionCode;
        long next = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        Set<Signature> currentSigners = signers(installed), nextSigners = signers(archive);
        return next > current && !currentSigners.isEmpty() && currentSigners.equals(nextSigners);
    }

    private static Set<Signature> signers(PackageInfo info) {
        Signature[] values = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                ? info.signingInfo.getApkContentsSigners() : info.signatures;
        Set<Signature> result = new HashSet<>();
        if (values != null) java.util.Collections.addAll(result, values);
        return result;
    }
}
