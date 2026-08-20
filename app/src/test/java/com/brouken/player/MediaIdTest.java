package com.brouken.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MediaIdTest {

    @Test
    public void normalizesBlankIdsAndStripsImdbPrefixForApiCalls() {
        MediaId id = new MediaId(" tt14688458 ", " ", 1, 5);

        assertEquals("tt14688458", id.imdb);
        assertEquals("14688458", id.imdbNumeric());
        assertEquals(null, id.tmdb);
        assertFalse(id.isMovie());
    }

    @Test
    public void cacheKeyIncludesEpisodeIdentity() {
        MediaId first = new MediaId("tt1", "42", 2, 3);
        MediaId second = new MediaId("tt1", "42", 2, 4);

        assertFalse(first.sameAs(second));
        assertTrue(first.sameAs(new MediaId("tt1", "42", 2, 3)));
        assertFalse(first.key().equals(second.key()));
    }
}
