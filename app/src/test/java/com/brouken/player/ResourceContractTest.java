package com.brouken.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

public class ResourceContractTest {

    private static Path projectPath(String relativePath) {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) {
            path = Paths.get("app").resolve(relativePath);
        }
        return path;
    }

    private static String readProjectFile(String relativePath) throws Exception {
        Path path = projectPath(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static int pngInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder text = new StringBuilder(digest.length * 2);
        for (byte value : digest) text.append(String.format("%02x", value & 0xff));
        return text.toString();
    }

    @Test
    public void skipLabelsStayUkrainian() throws Exception {
        String xml = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(xml.contains("name=\"skip_action\">Пропустити</string>"));
        assertTrue(xml.contains("name=\"skip_available_in\">Пропуск через %1$d</string>"));
        assertFalse(xml.contains("SideSheetBehavior"));
    }

    @Test
    public void tvBannerUsesUaPalette() throws Exception {
        byte[] image = Files.readAllBytes(projectPath(
                "src/main/res/mipmap-xhdpi/banner.png"));

        assertTrue(image.length > 8_000);
        assertEquals(320, pngInt(image, 16));
        assertEquals(180, pngInt(image, 20));
        assertEquals("67cff9977c881c0fd1a31060455c5841531f2188ae78cecdd628c90d0e3c4d15",
                sha256(image));
    }

    @Test
    public void bottomControlsHaveStrongTvFocusResources() throws Exception {
        String background = readProjectFile(
                "src/main/res/drawable/ua_tv_control_background.xml");
        String animator = readProjectFile(
                "src/main/res/animator/ua_tv_control_focus.xml");
        String activity = readProjectFile(
                "src/main/java/com/brouken/player/PlayerActivity.java");
        String controls = readProjectFile("src/main/res/layout/controls.xml");

        assertTrue(background.contains("state_focused=\"true\""));
        assertTrue(background.contains("android:width=\"3dp\""));
        assertTrue(background.contains("@color/ua_gold"));
        assertTrue(animator.contains("android:valueTo=\"1.1\""));
        assertTrue(activity.contains("styleTvBottomControls(controls)"));
        assertTrue(controls.contains("android:clipChildren=\"false\""));
    }

    @Test
    public void updaterDownloadsInsidePlayerAndUsesFileProvider() throws Exception {
        String updater = readProjectFile(
                "src/main/java/com/brouken/player/update/Updater.java");
        String updateUi = readProjectFile(
                "src/main/java/com/brouken/player/update/UpdateUi.java");
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(updater.contains("CLIENT.newCall(request).execute()"));
        assertTrue(updater.contains("context.getCacheDir()"));
        assertTrue(updater.contains("FileProvider.getUriForFile"));
        assertTrue(updater.contains("FLAG_GRANT_READ_URI_PERMISSION"));
        assertTrue(updateUi.contains("Updater.downloadApk"));
        assertTrue(ukrainian.contains(">Завантажити й установити</string>"));
        assertFalse(updater.contains("Intent.createChooser"));
        assertFalse(updateUi.contains("startActivity(new Intent(Intent.ACTION_VIEW"));
    }

    @Test
    public void tvBackHintStaysLocalized() throws Exception {
        String ukrainian = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(ukrainian.contains(
                "name=\"press_back_again\">Натисніть «Назад» ще раз для виходу</string>"));
    }
}
