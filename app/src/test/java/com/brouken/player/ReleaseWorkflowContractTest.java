package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ReleaseWorkflowContractTest {

    private static int count(String source, String token) {
        int matches = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            matches++;
            offset += token.length();
        }
        return matches;
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        if (from < 0 || to < 0 || to <= from) {
            throw new IllegalArgumentException("Missing workflow section: " + start);
        }
        return source.substring(from, to);
    }

    private static String readReleaseWorkflow() throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();
        Path root = Files.exists(cwd.resolve("settings.gradle")) ? cwd : cwd.getParent();
        return new String(
                Files.readAllBytes(root.resolve(".github/workflows/android-build.yml")),
                StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test
    public void manualDispatchUsesReadOnlyBuildJobAndCannotPublish() throws Exception {
        String workflow = readReleaseWorkflow();
        String globalPermissions = section(workflow, "permissions:\n", "jobs:\n");
        String build = section(workflow, "  build:\n", "  publish:\n");

        assertTrue(workflow.contains("  workflow_dispatch:\n"));
        assertTrue(globalPermissions.contains("  contents: read"));
        assertTrue(build.startsWith(
                "  build:\n    if: github.event_name != 'workflow_dispatch'"
                        + " || github.ref == 'refs/heads/main'\n"));
        assertFalse(build.contains("contents: write"));
        assertFalse(build.contains("softprops/action-gh-release"));
        assertTrue(build.indexOf("- name: Test and lint")
                < build.indexOf("- name: Restore release signing key"));
        assertEquals(1, count(workflow, "contents: write"));
    }

    @Test
    public void signingAndApkVerificationRequireCompatibleCertificate() throws Exception {
        String workflow = readReleaseWorkflow();
        String build = section(workflow, "  build:\n", "  publish:\n");
        String restore = section(build,
                "      - name: Restore release signing key\n",
                "      - name: Build signed universal APKs\n");
        String assemble = section(build,
                "      - name: Build signed universal APKs\n",
                "      - name: Ensure v1 v2 and v3 signatures\n");
        String resign = section(build,
                "      - name: Ensure v1 v2 and v3 signatures\n",
                "      - name: Verify release APKs\n");
        String verify = section(build,
                "      - name: Verify release APKs\n",
                "      - name: Upload release artifacts\n");
        String upload = section(build,
                "      - name: Upload release artifacts\n",
                "      - name: Remove release signing key\n");
        String cleanup = build.substring(build.indexOf(
                "      - name: Remove release signing key\n"));

        assertEquals(4, count(build,
                "SIGNING_KEYSTORE: ${{ runner.temp }}/ua-player-release.jks"));
        assertFalse(build.contains(
                "runs-on: ubuntu-latest\n    env:\n      SIGNING_KEYSTORE:"));
        assertTrue(restore.contains("KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}"));
        assertTrue(restore.contains("KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}"));
        assertTrue(restore.contains("KEYSTORE_BASE64 secret is missing"));
        assertTrue(restore.contains("KEYSTORE_PASSWORD secret is missing"));
        assertTrue(restore.contains("-alias androiddebugkey"));
        assertTrue(build.contains(
                "749D118BC8A16A7C0464B8DD0498C53DA8A86A668D8F09F551E60CF7D88EE15E"));
        assertTrue(restore.contains("Signing certificate SHA-256 mismatch"));

        assertTrue(assemble.contains(":app:assembleLatestUniversalRelease"));
        assertTrue(assemble.contains(":app:assembleLegacyUniversalRelease"));
        assertTrue(assemble.contains("-Pandroid.injected.signing.key.alias=androiddebugkey"));
        assertFalse(assemble.contains("-Pandroid.injected.signing.key.alias=key"));

        assertTrue(resign.contains("$build_tools/apksigner\" sign"));
        assertTrue(resign.contains("--ks-key-alias androiddebugkey"));
        assertTrue(resign.contains("--ks-pass env:KEYSTORE_PASSWORD"));
        assertTrue(resign.contains("--v1-signing-enabled true"));
        assertTrue(resign.contains("--v2-signing-enabled true"));
        assertTrue(resign.contains("--v3-signing-enabled true"));
        assertTrue(resign.contains("--v4-signing-enabled false"));

        assertTrue(verify.contains(
                "latest_dir=\"app/build/outputs/apk/latestUniversal/release\""));
        assertTrue(verify.contains(
                "legacy_dir=\"app/build/outputs/apk/legacyUniversal/release\""));
        assertTrue(verify.contains("test \"${#latest_apks[@]}\" -eq 1"));
        assertTrue(verify.contains("test \"${#legacy_apks[@]}\" -eq 1"));
        assertTrue(verify.contains("--certificate \"$EXPECTED_SIGNING_CERT_SHA256\""));

        assertTrue(upload.contains(
                "app/build/outputs/apk/latestUniversal/release/*.apk"));
        assertTrue(upload.contains(
                "app/build/outputs/apk/legacyUniversal/release/*.apk"));
        assertTrue(upload.contains("if-no-files-found: error"));
        assertTrue(cleanup.contains("if: always()"));
        assertTrue(cleanup.contains("rm -f \"$SIGNING_KEYSTORE\""));
    }

    @Test
    public void tagPublishJobIsIsolatedAndCreatesStableRelease() throws Exception {
        String workflow = readReleaseWorkflow();
        String publish = workflow.substring(workflow.indexOf("  publish:\n"));

        assertTrue(publish.startsWith(
                "  publish:\n"
                        + "    if: github.event_name == 'push'"
                        + " && startsWith(github.ref, 'refs/tags/v')\n"));
        assertTrue(publish.contains("needs: build"));
        assertTrue(publish.contains("permissions:\n      contents: write"));
        assertTrue(publish.contains("uses: actions/download-artifact@v8"));
        assertTrue(publish.contains(
                "find release/latestUniversal/release -maxdepth 1"));
        assertTrue(publish.contains(
                "find release/legacyUniversal/release -maxdepth 1"));
        assertTrue(publish.contains(
                "uses: softprops/action-gh-release@efb35369e0ad2afab669f228072c1b0d510eae64"));
        assertTrue(publish.contains("# v3.0.3"));
        assertTrue(publish.contains("fail_on_unmatched_files: true"));
        assertTrue(publish.contains("prerelease: false"));
        assertTrue(publish.contains("draft: false"));
        assertFalse(workflow.contains("prerelease: true"));
        assertFalse(workflow.contains("draft: true"));
    }
}
