package com.brouken.player.update;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class ApkUpdateVerifierTest {
    private UpdateInfo info(String sha, long size) {
        return new UpdateInfo(2_000_011, "v2.0.11", "2.0.11", "", "https://example/apk", size, "", sha);
    }
    private PackageInfo pkg(String name, String version, int code, String signer) {
        PackageInfo value = new PackageInfo(); value.packageName = name; value.versionName = version;
        value.versionCode = code; value.signatures = new Signature[]{new Signature(signer.getBytes())}; return value;
    }
    @Test public void onlyTheOfferedSizeAndDigestAreAccepted() throws Exception {
        byte[] bytes = "verified release asset".getBytes("UTF-8");
        StringBuilder sha = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) sha.append(String.format("%02x", value & 255));
        File file = File.createTempFile("update-", ".apk");
        try {
            Files.write(file.toPath(), bytes);
            assertTrue(ApkUpdateVerifier.matchesFile(file, info(sha.toString(), bytes.length)));
            assertFalse(ApkUpdateVerifier.matchesFile(file, info(sha.toString(), bytes.length + 1)));
            byte[] damaged = bytes.clone(); damaged[0] ^= 1; Files.write(file.toPath(), damaged);
            assertFalse(ApkUpdateVerifier.matchesFile(file, info(sha.toString(), bytes.length)));
            assertFalse(ApkUpdateVerifier.matchesFile(file, info(null, bytes.length)));
        } finally { file.delete(); }
    }
    @Test public void packageVersionAndInstalledSignerMustAllMatch() {
        PackageInfo installed = pkg("com.lampaua.player", "2.0.10", 30, "ours");
        assertTrue(ApkUpdateVerifier.matchesPackage(installed, pkg(installed.packageName, "2.0.11", 31, "ours"), info(null, 1)));
        assertFalse(ApkUpdateVerifier.matchesPackage(installed, pkg("com.justplus.player", "2.0.11", 31, "ours"), info(null, 1)));
        assertFalse(ApkUpdateVerifier.matchesPackage(installed, pkg(installed.packageName, "2.0.11", 31, "other"), info(null, 1)));
        assertFalse(ApkUpdateVerifier.matchesPackage(installed, pkg(installed.packageName, "2.0.10", 31, "ours"), info(null, 1)));
        assertFalse(ApkUpdateVerifier.matchesPackage(installed, pkg(installed.packageName, "2.0.11", 30, "ours"), info(null, 1)));
        assertFalse(ApkUpdateVerifier.matchesPackage(installed, null, info(null, 1)));
    }
    @Test public void githubDigestAndCachedOfferRoundTrip() {
        String hash = new String(new char[64]).replace('\0', 'a');
        assertEquals(hash, ApkUpdateVerifier.digest("sha256:" + hash));
        assertNull(ApkUpdateVerifier.digest("sha256:bad"));
        assertEquals(hash, UpdateInfo.fromJson(info(hash, 42).toJson()).sha256);
    }
}
