package com.example.myiptv.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

class CinemaTest {
    private fun program(start: String = "2026-09-13T20:00:00Z", stop: String = "2026-09-13T23:30:00Z") =
        CinemaProgram("source", "TCM.fr", "TCM Cinéma", "FR", "Film", "", Instant.parse(start).epochSecond, Instant.parse(stop).epochSecond)

    @Test fun cacheRemainsFreshForTwelveHours() {
        val updated = 1_000_000L
        assertTrue(cinemaCacheIsFresh(updated, updated + 12 * 60 * 60 * 1000 - 1))
        assertFalse(cinemaCacheIsFresh(updated, updated + 12 * 60 * 60 * 1000))
    }

    @Test fun catalogueSearchCoversGenresDescriptionsChannelsAndOneLetterTypos() {
        val item = program().copy(
            title = "Sunderland - Leeds",
            description = "Premier League evening fixture",
            genres = "Sports · Drama · Romantic",
        )
        assertTrue(catalogueMatches(item, "Sinderland"))
        assertTrue(catalogueMatches(item, "romantic"))
        assertTrue(catalogueMatches(item, "drama TCM"))
        assertFalse(catalogueMatches(item, "tennis"))
    }

    @Test fun fullSourceHorizonKeepsPastAndFollowingDays() {
        val now = Instant.parse("2026-09-13T19:00:00Z")
        assertTrue(cinemaInPeriod(program(), CinemaPeriod.NEXT, now))
        assertTrue(cinemaInPeriod(program("2026-09-14T23:00:00Z", "2026-09-15T01:00:00Z"), CinemaPeriod.NEXT, now))
        assertTrue(cinemaInPeriod(program("2026-09-13T08:00:00Z", "2026-09-13T09:00:00Z"), CinemaPeriod.PAST, now))
        assertFalse(cinemaInPeriod(program(), CinemaPeriod.NOW, Instant.parse("2026-09-13T23:30:00Z")))
        assertEquals(Instant.parse("2026-09-13T23:30:00Z").epochSecond, program().stop)
    }

    @Test fun nextKeepsFirstUpcomingFilmPerChannelAndLaterKeepsTheRest() {
        val now = Instant.parse("2026-09-13T10:00:00Z")
        val first = program("2026-09-13T16:00:00Z", "2026-09-13T18:00:00Z")
        val second = program("2026-09-13T18:00:00Z", "2026-09-13T20:00:00Z")
        assertEquals(listOf(first), cinemaProgramsInPeriod(listOf(second, first), CinemaPeriod.NEXT, now))
        assertEquals(listOf(second), cinemaProgramsInPeriod(listOf(second, first), CinemaPeriod.LATER, now))
        assertFalse(cinemaInPeriod(program("2026-09-13T08:00:00Z", "2026-09-13T09:00:00Z"), CinemaPeriod.NEXT, now))
    }

    @Test fun pastShowsMostRecentlyFinishedFilmFirst() {
        val now = Instant.parse("2026-09-13T20:00:00Z")
        val older = program("2026-09-13T08:00:00Z", "2026-09-13T10:00:00Z")
        val recent = program("2026-09-13T16:00:00Z", "2026-09-13T18:00:00Z")
        assertEquals(listOf(recent, older), cinemaProgramsInPeriod(listOf(older, recent), CinemaPeriod.PAST, now))
    }

    @Test fun nowShowsMostRecentlyStartedFilmFirst() {
        val now = Instant.parse("2026-09-13T20:00:00Z")
        val older = program("2026-09-13T18:00:00Z", "2026-09-13T21:00:00Z")
        val justStarted = program("2026-09-13T19:55:00Z", "2026-09-13T22:00:00Z")
        assertEquals(
            listOf(justStarted, older),
            cinemaProgramsInPeriod(listOf(older, justStarted), CinemaPeriod.NOW, now),
        )
    }

    @Test fun sportsCatalogueAcceptsSportsChannelsWithoutMixingCinema() = runBlocking {
        val xml = """
            <tv>
              <channel id="bein.fr"><display-name>beIN SPORTS 1</display-name></channel>
              <programme channel="bein.fr" start="20260913190000 +0000" stop="20260913210000 +0000">
                <title>Paris SG - Marseille</title><category>Sports</category>
              </programme>
            </tv>
        """.trimIndent().byteInputStream()
        val sports = parseCinemaXml(xml, "FR", "test", LocalDate.parse("2026-09-13"), sports = true)
        assertEquals(listOf("Paris SG - Marseille"), sports.map(CinemaProgram::title))

        val cinema = parseCinemaXml(
            ByteArrayInputStream(xmlTextForSports().toByteArray()),
            "FR",
            "test",
            LocalDate.parse("2026-09-13"),
        )
        assertTrue(cinema.isEmpty())
    }

    private fun xmlTextForSports() = """
        <tv>
          <channel id="bein.fr"><display-name>beIN SPORTS 1</display-name></channel>
          <programme channel="bein.fr" start="20260913190000 +0000" stop="20260913210000 +0000">
            <title>Paris SG - Marseille</title><category>Sports</category>
          </programme>
        </tv>
    """.trimIndent()

    @Test fun retainsAllVariantsWithoutConfusingShiftedOrForeignFeeds() {
        fun channel(id: Int, name: String, country: String = "FR") = SavedChannel(
            name = name, categoryId = "1", categoryName = "Cinema", countryCode = country,
            iconUrl = null, extension = "ts", epgChannelId = null,
            categoryOrder = 0, providerOrder = id, streamId = id,
        )
        val variants = listOf("SD", "HD", "FHD", "UHD", "4K").mapIndexed { i, quality -> channel(i, "FR | TCM CINEMA $quality") }
        val channels = variants + channel(6, "TCM CINEMA +1 HD") + channel(7, "TCM CINEMA HD", "CA") + channel(8, "TCM CINEMA HD")
        assertEquals(listOf(0, 1, 2, 3, 4, 8), cinemaMatches(program(), channels).map { it.streamId })
        assertNotEquals(cinemaName("HBO East HD"), cinemaName("HBO West HD"))
        assertNotEquals(cinemaName("Sky Cinema Uno"), cinemaName("Sky Cinema Uno +24"))
    }

    @Test fun parsesOffsetsAndRejectsUnknownTimezones() {
        assertEquals(Instant.parse("2026-09-13T19:00:00Z").epochSecond, cinemaXmlTime("20260913210000 +0200"))
        assertEquals(Instant.parse("2026-09-14T01:00:00Z").epochSecond, cinemaXmlTime("20260913210000 -0400"))
        assertNull(cinemaXmlTime("20260913210000"))
        assertNull(cinemaXmlTime("invalid"))
    }
}
