package com.brouken.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourceContractTest {

    private static String readProjectFile(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) {
            path = Paths.get("app").resolve(relativePath);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void skipLabelsStayUkrainian() throws Exception {
        String xml = readProjectFile("src/main/res/values-uk/strings.xml");

        assertTrue(xml.contains("name=\"skip_action\">Пропустити</string>"));
        assertTrue(xml.contains("name=\"skip_available_in\">Пропуск через %1$d</string>"));
        assertFalse(xml.contains("SideSheetBehavior"));
    }
}
