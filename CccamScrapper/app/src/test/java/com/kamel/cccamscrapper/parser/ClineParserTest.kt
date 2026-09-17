package com.kamel.cccamscrapper.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class ClineParserTest {
    @Test
    fun parsesStandardCLines() {
        val result = ClineParser.parse(
            """
            C: demo.example.com 12000 user pass
            C: second.net 13000 u2 p2
            """.trimIndent()
        )

        assertEquals(2, result.servers.size)
        assertEquals("demo.example.com", result.servers.first().host)
        assertEquals(12000, result.servers.first().port)
    }

    @Test
    fun parsesFragmentedValues() {
        val result = ClineParser.parse(
            """
            host.example.com
            15000
            user1
            pass1
            """.trimIndent()
        )

        assertEquals(1, result.servers.size)
        assertEquals("C: host.example.com 15000 user1 pass1", result.servers.first().normalizedLine)
    }
}
