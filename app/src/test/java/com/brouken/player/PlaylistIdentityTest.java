package com.brouken.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PlaylistIdentityTest {

    @Test
    public void differentEpisodesNeverShareResumePosition() {
        String first = PlaylistIdentity.key("https://a/stream", "tt123", "456", 1, 1);
        String second = PlaylistIdentity.key("https://a/stream", "tt123", "456", 1, 2);

        assertNotEquals(first, second);
    }

    @Test
    public void sameEpisodeKeepsIdentityWhenResolverUrlChanges() {
        assertEquals(
                PlaylistIdentity.key("https://a/stream", "tt123", "456", 1, 2),
                PlaylistIdentity.key("https://b/stream", "tt123", "456", 1, 2));
    }

    @Test
    public void transientResolverParametersDoNotChangeIdentity() {
        assertEquals(
                PlaylistIdentity.key(
                        "https://CDN.example/video/master.m3u8?quality=2160&token=first&expires=100",
                        null, null, -1, -1),
                PlaylistIdentity.key(
                        "https://cdn.example/video/master.m3u8?expires=200&quality=2160&token=second",
                        null, null, -1, -1));
    }

    @Test
    public void distinctUrlsWithoutMetadataRemainDistinct() {
        assertNotEquals(
                PlaylistIdentity.key("https://cdn.example/one.m3u8", null, null, -1, -1),
                PlaylistIdentity.key("https://cdn.example/two.m3u8", null, null, -1, -1));
    }
}
