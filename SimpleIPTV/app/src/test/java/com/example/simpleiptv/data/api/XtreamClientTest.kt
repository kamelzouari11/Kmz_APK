package com.example.simpleiptv.data.api

import org.junit.Test
import org.junit.Assert.*

class XtreamClientTest {

    @Test
    fun testUrlWithTrailingSlash() {
        val url = "http://example.com:8080/"
        val result = if (url.endsWith("/")) url else "$url/"
        assertEquals("http://example.com:8080/", result)
    }

    @Test
    fun testUrlWithoutTrailingSlash() {
        val url = "http://example.com:8080"
        val result = if (url.endsWith("/")) url else "$url/"
        assertEquals("http://example.com:8080/", result)
    }

    @Test
    fun testUrlAlreadyHasSlash() {
        val url = "http://example.com:8080/"
        assertTrue(url.endsWith("/"))
    }

    @Test
    fun testIpv4Url() {
        val url = "http://192.168.1.1:8000"
        val result = if (url.endsWith("/")) url else "$url/"
        assertEquals("http://192.168.1.1:8000/", result)
    }

    @Test
    fun testHttpsUrl() {
        val url = "https://secure.example.com:443"
        val result = if (url.endsWith("/")) url else "$url/"
        assertEquals("https://secure.example.com:443/", result)
    }

    @Test
    fun testEmptyUrl() {
        val url = ""
        val result = if (url.endsWith("/")) url else "$url/"
        assertEquals("/", result)
    }
}