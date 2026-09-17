package com.kmz.shazamplayer.util

import org.junit.Assert.*
import org.junit.Test

class CsvParserTest {
    private fun parse(csv: String) = CsvParser.parse(csv.byteInputStream())

    @Test fun officialExportNeedsNoShazamId() {
        val track = parse("\uFEFFartist,title,status,date,longitude,latitude\r\nBjörk,Army of Me,N/A,2026-09-06T20:05:47.554Z,N/A,N/A\r\n").single()
        assertEquals("Björk", track.artist)
        assertEquals("Army of Me", track.title)
        assertEquals("2026-09-06T20:05:47.554Z", track.tagTime)
        assertEquals("", track.trackKey)
        assertEquals("", track.shazamUrl)
    }

    @Test fun minimalReorderedColumnsAndQuotedFields() {
        val track = parse("title,date,artist\n\"Song, \"\"live\"\"\nversion\",2026-09-06,Artist").single()
        assertEquals("Song, \"live\"\nversion", track.title)
        assertEquals("Artist", track.artist)
    }

    @Test fun legacyExportStillWorks() {
        val track = parse("Shazam Library\nIndex,TagTime,Title,Artist,URL,TrackKey\n1,2021-10-23,Clocks,Coldplay,https://www.shazam.com/track/1,1").single()
        assertEquals("1", track.trackKey)
        assertEquals("Clocks", track.title)
    }

    @Test fun repeatedDiscoveriesArePreserved() {
        assertEquals(2, parse("date,artist,title\n2026-09-06,A,B\n2026-09-07,A,B").size)
    }

    @Test fun invalidImportsAreRejected() {
        listOf("", "foo,bar\na,b", "date,artist,title", "date,artist,title\n2026-09-06,,X",
            "date,artist,title\n2026-09-06,A", "date,artist,title\n2026-09-06,A,\"X").forEach {
            assertThrows(IllegalArgumentException::class.java) { parse(it) }
        }
    }
}
