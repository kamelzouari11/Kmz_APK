package com.example.simpleiptv.data

import com.example.simpleiptv.data.local.entities.ProfileEntity
import org.junit.Test
import org.junit.Assert.*

class ProfileEntityTest {

    @Test
    fun testProfileEntityCreation() {
        val profile = ProfileEntity(
            id = 1,
            profileName = "Test Server",
            url = "http://example.com:8080",
            username = "user",
            password = "pass",
            type = "xtream",
            isSelected = true
        )

        assertEquals(1, profile.id)
        assertEquals("Test Server", profile.profileName)
        assertEquals("http://example.com:8080", profile.url)
        assertEquals("user", profile.username)
        assertEquals("pass", profile.password)
        assertEquals("xtream", profile.type)
        assertTrue(profile.isSelected)
    }

    @Test
    fun testDefaultProfileCreation() {
        val defaultProfile = ProfileEntity(
            profileName = "Mon Serveur IPTV",
            url = "",
            username = "",
            password = "",
            type = "xtream",
            isSelected = true
        )

        assertEquals("Mon Serveur IPTV", defaultProfile.profileName)
        assertEquals("", defaultProfile.url)
        assertEquals("", defaultProfile.username)
        assertEquals("", defaultProfile.password)
        assertEquals("xtream", defaultProfile.type)
        assertTrue(defaultProfile.isSelected)
    }

    @Test
    fun testStalkerProfileCreation() {
        val stalkerProfile = ProfileEntity(
            profileName = "Stalker Portal",
            url = "http://stalker.example.com",
            username = "",
            password = "",
            macAddress = "00:1A:2B:3C:4D:5E",
            type = "stalker",
            isSelected = false
        )

        assertEquals("stalker", stalkerProfile.type)
        assertEquals("00:1A:2B:3C:4D:5E", stalkerProfile.macAddress)
    }
}