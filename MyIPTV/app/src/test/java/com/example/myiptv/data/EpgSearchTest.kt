package com.example.myiptv.data

import org.junit.Assert.*
import org.junit.Test

class EpgSearchTest {
    private fun matches(query: String, title: String, description: String = ""): Boolean {
        val text = EpgSearch.searchable("$title $description")
        return EpgSearch.words(query).all { text.contains(" $it ") }
    }

    @Test fun findsBothTeamsRegardlessOfOrderAndAccents() {
        val title = "LIVE : Réal Madrid – Inter"
        assertTrue(matches("real madrid inter", title))
        assertTrue(matches("inter real madrid", title))
        assertTrue(matches("MADRID inter réal", title))
        assertFalse(matches("real madrid betis", title))
    }

    @Test fun searchesDescriptionsAndRecognizesProviderSpellings() {
        assertTrue(matches("inter madrid real", "UEFA Champions League", "Real Madrid - Internazionale"))
        assertTrue(matches("milan real madrid inter", "Real Madrid - Inter Milano"))
        assertFalse(matches("inter madrid real", "Real Madrid : interview"))
    }

    @Test fun respectsXmltvOffsetsAndTunisianTime() {
        val (from, until) = EpgSearch.window("2026-09-08", "20:00")
        assertEquals(from, EpgSearch.timestamp("20260908210000 +0200"))
        assertEquals(from, EpgSearch.timestamp("20260908190000 +0000"))
        assertEquals(from, EpgSearch.timestamp("20260908190000"))
        assertEquals(4 * 60 * 60L, until - from)
        assertNull(EpgSearch.timestamp("invalid"))
    }

    @Test fun includesOngoingProgrammesButExcludesEndedAndNextDay() {
        val (from, until) = EpgSearch.window("2026-09-08", "20:00")
        fun overlaps(start: String, stop: String) =
            EpgSearch.timestamp(stop)!! > from && EpgSearch.timestamp(start)!! < until
        assertTrue(overlaps("20260908195500 +0100", "20260908220000 +0100"))
        assertFalse(overlaps("20260908190000 +0100", "20260908200000 +0100"))
        assertFalse(overlaps("20260909000000 +0100", "20260909020000 +0100"))
    }
}
