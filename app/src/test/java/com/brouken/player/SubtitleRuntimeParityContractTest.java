package com.brouken.player;

import org.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubtitleRuntimeParityContractTest {
    private static String readProjectFile(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("app").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue("Missing start marker: " + start, from >= 0);
        assertTrue("Missing end marker: " + end, to > from);
        return source.substring(from, to);
    }

    @Test public void subtitleOffTargetsOnlyThePrimaryRenderer() throws Exception {
        String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String disable = section(activity, "private void disableSubtitles()",
                "private void applySubtitle(TrackGroup group, int index)");

        assertTrue(disable.contains("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)"));
        assertTrue(disable.contains("setRendererDisabled(primary, true)"));
        assertTrue(disable.indexOf("setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)")
                < disable.indexOf("setRendererDisabled(primary, true)"));
    }

    @Test public void selectingEitherSubtitlePathRestoresThePrimaryLine() throws Exception {
        String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String paint = section(activity, "private void paintSubtitle(Uri subtitleUri)",
                "private void attachSubtitleTrack(Uri subtitleUri)");
        String apply = section(activity, "private void applySubtitle(TrackGroup group, int index)",
                "private boolean secondaryEnabled()");

        assertTrue(paint.contains("mainLineOff = false;"));
        assertTrue(paint.indexOf("mainLineOff = false;")
                < paint.indexOf("paintedSubtitleUri = subtitleUri;"));
        assertTrue(apply.contains("setRendererDisabled(primary, false)"));
        assertTrue(apply.contains("applyMainLineTrackSelection();"));
    }

    @Test public void trackChangesUseTheDonorSubtitleSelectionOrder() throws Exception {
        String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String tracks = section(activity, "public void onTracksChanged(@NonNull Tracks tracks)",
                "public void onPlayerError(PlaybackException error)");

        int timeline = tracks.indexOf("updateSubtitleTimeline(tracks)");
        int named = tracks.indexOf("selectSubtitleByName()");
        int remember = tracks.indexOf("rememberMainLineTrack()");
        int secondary = tracks.indexOf("applySecondaryTrackSelection()");
        int primary = tracks.indexOf("applyMainLineTrackSelection()");
        int verify = tracks.indexOf("verifySecondaryTrackReached()");
        int autoFill = tracks.indexOf("autoFillSecondarySubtitle()");
        int local = tracks.indexOf("startSubtitleGuess()");
        int online = tracks.indexOf("maybeSearchSubtitlesOnline(tracks)");

        assertTrue(timeline >= 0 && timeline < named);
        assertTrue(named < remember && remember < secondary && secondary < primary);
        assertTrue(primary < verify && verify < autoFill && autoFill < local && local < online);
    }

    @Test public void manualSearchUsesSeriesTitleAndDonorEpisodeCatalog() throws Exception {
        String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String search = section(activity, "private void showManualSubtitleSearch(boolean secondary)",
                "private void startManualSubtitleSearch(boolean secondary)");
        String playlist = readProjectFile("src/main/java/com/brouken/player/LampaPlaylist.java");

        assertTrue(activity.contains("lampaPlaylist.getTitle()"));
        assertTrue(search.contains("addTextChangedListener"));
        assertTrue(search.contains("TitleSearch.episodes"));
        assertTrue(search.contains("subtitle_search_specials"));
        assertTrue(activity.contains("manualSubtitleCoordinates(item, index)"));
        assertTrue(activity.contains("manualSubtitleAbsolute + (index - manualSubtitlePlaylistIndex)"));
        assertTrue(playlist.contains("String getTitle()"));
        assertTrue(playlist.contains("root.put(\"title\", title.trim())"));
    }

    @Test public void playlistSeriesTitleMetadataRoundTrips() throws Exception {
        Method read = LampaPlaylist.class.getDeclaredMethod("playlistTitle", JSONObject.class);
        Method write = LampaPlaylist.class.getDeclaredMethod(
                "putPlaylistTitle", JSONObject.class, String.class);
        read.setAccessible(true);
        write.setAccessible(true);

        JSONObject launch = new JSONObject()
                .put("title", "Назва серіалу")
                .put("items", new org.json.JSONArray().put(
                        new JSONObject().put("title", "Серія 5")));
        String seriesTitle = (String) read.invoke(null, launch);
        JSONObject snapshot = new JSONObject();
        write.invoke(null, snapshot, seriesTitle);

        assertEquals("Назва серіалу", seriesTitle);
        assertEquals("Назва серіалу", read.invoke(null, snapshot));
        assertEquals("Серія 5", launch.getJSONArray("items").getJSONObject(0).getString("title"));
    }

    @Test public void finishTimeUsesCompactSingleLineCopy() throws Exception {
        String activity = readProjectFile("src/main/java/com/brouken/player/PlayerActivity.java");
        String strings = readProjectFile("src/main/res/values/strings.xml");
        String uk = readProjectFile("src/main/res/values-uk/strings.xml");
        String verifier = readProjectFile("../scripts/verify_apk.py");
        String layout = section(activity, "lampaFinishTime = new TextView(this);",
                "lampaTopTimeBlock.addView(lampaFinishTime);");
        String adaptive = section(activity, "private void applyLampaTopLayout()",
                "private void openAppSettings()");
        String runtime = section(activity, "private void updateLampaRuntimeUi()",
                "private void updateLampaSkipUi()");

        assertTrue(strings.contains("name=\"playback_finishes_at_compact\""));
        assertTrue(uk.contains("name=\"playback_finishes_at_compact\">до %1$s</string>"));
        assertTrue(layout.contains("lampaFinishTime.setSingleLine(true)"));
        assertFalse(adaptive.contains("lampaFinishTime.setMaxLines(2)"));
        assertTrue(runtime.contains("R.string.playback_finishes_at_compact"));
        assertTrue(verifier.contains("resource_block(resources, \"playback_finishes_at_compact\")"));
        assertTrue(verifier.contains("\"до %1$s\" not in finish"));
    }
}
