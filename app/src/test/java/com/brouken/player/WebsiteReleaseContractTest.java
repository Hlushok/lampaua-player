package com.brouken.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class WebsiteReleaseContractTest {

    private static String read(String relativePath) throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();
        Path root = Files.exists(cwd.resolve("settings.gradle")) ? cwd : cwd.getParent();
        return new String(Files.readAllBytes(root.resolve(relativePath)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }

    @Test
    public void websiteOffersOnlyTheStandardReleaseApk() throws Exception {
        String html = read("site/index.html");
        String script = read("site/app.js");
        String styles = read("site/styles.css");

        assertFalse(html.contains("data-download=\"legacy\""));
        assertFalse(html.contains("Legacy-версія"));
        assertFalse(script.contains("legacyApk"));
        assertFalse(script.contains("setDownloadUrl(\"legacy\""));
        assertFalse(styles.contains(".legacy-link"));
        assertTrue(script.contains("/^UA-Player-.+\\.apk$/i"));
        assertTrue(html.contains("UA-Player-2.0.3.apk"));
    }
}
