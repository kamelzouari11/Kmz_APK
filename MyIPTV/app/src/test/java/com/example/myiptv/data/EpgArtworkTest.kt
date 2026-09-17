package com.example.myiptv.data

import org.junit.Assert.*
import org.junit.Test

class EpgArtworkTest {
    @Test fun preservesOriginalFilmTitleWhileRemovingProviderTags() {
        assertEquals("Live and Let Die", artworkQuery("Live and Let Die"))
        assertEquals("La vita è bella", artworkQuery("[IT] La vita è bella HD"))
        assertEquals("Inter - Juventus", artworkQuery("LIVE: FOOTBALL: Inter - Juventus 4K"))
    }

    @Test fun rejectsUnrelatedArtAndSoundtracks() {
        assertEquals(2.0, artworkMatchScore("Inception", "Inception"), 0.0)
        assertEquals(0.0, artworkMatchScore("Inception", "Inception (soundtrack)"), 0.0)
        assertEquals(0.0, artworkMatchScore("La vita è bella", "Juventus FC"), 0.0)
        assertTrue(artworkMatchScore("Real Madrid - Inter", "Real Madrid CF") < 0.8)
        assertTrue(artworkMatchScore("Inter Milan", "Inter Milan") >= 0.8)
    }
}
