package com.example.simpleradio.utils

object MetadataHelper {
    private val METADATA_URL_REGEX = Regex("(?i)https?://[\\w./?-]+")
    private val METADATA_RF_REGEX = Regex("(?i)RF\\s*\\d+")

    fun clean(s: String): String {
        var res = s.replace(METADATA_URL_REGEX, "").replace(METADATA_RF_REGEX, "").trim()

        if (res.contains("?")) {
            res =
                    res.replace("Ann?es", "Années", ignoreCase = true)
                            .replace("Ann?e", "Année", ignoreCase = true)
                            .replace("M?t?o", "Météo", ignoreCase = true)
                            .replace("Num?ro", "Numéro", ignoreCase = true)
                            .replace("Pr?sent", "Présent", ignoreCase = true)
                            .replace("No?l", "Noël", ignoreCase = true)
                            .replace("Vari?t?", "Variété", ignoreCase = true)
                            .replace("Fran?ais", "Français", ignoreCase = true)
                            .replace("?a", "Ça", ignoreCase = true)
        }
        return res
    }

    data class MetadataResult(val artist: String?, val title: String?)

    fun extractMetadata(
            rawTitle: String?,
            rawArtist: String?,
            stationName: String,
            country: String
    ): MetadataResult? {
        if (rawTitle.isNullOrBlank() && rawArtist.isNullOrBlank()) return null

        val cleanTitle = rawTitle?.let { clean(it) } ?: ""
        val cleanArtist = rawArtist?.let { clean(it) } ?: ""

        if (cleanTitle.equals(stationName, ignoreCase = true) ||
                        cleanTitle.equals(country, ignoreCase = true) ||
                        cleanArtist.equals(stationName, ignoreCase = true) ||
                        cleanArtist.equals(country, ignoreCase = true)
        ) {
            return null
        }

        val separators = listOf(" - ", " – ", " — ", " * ", " : ", " | ", " / ", "~")

        for (sep in separators) {
            if (cleanTitle.contains(sep)) {
                val parts = cleanTitle.split(sep, limit = 2)
                val p1 = parts[0].trim()
                val p2 = parts[1].trim()

                if (!p2.equals(stationName, ignoreCase = true) &&
                                !p2.contains(stationName, ignoreCase = true)
                ) {
                    return MetadataResult(p1, p2)
                }
                break
            }
        }

        return MetadataResult(cleanArtist.ifBlank { null }, cleanTitle.ifBlank { null })
    }
    fun sendUpdateMetadataCommand(
            controller: androidx.media3.session.MediaController,
            currentTitle: String?,
            currentArtist: String?,
            playingRadioName: String?,
            currentArtworkUrl: String?,
            radioFavicon: String?
    ) {
        if (!currentTitle.isNullOrBlank() ||
                        !currentArtist.isNullOrBlank() ||
                        playingRadioName != null
        ) {
            val args =
                    android.os.Bundle().apply {
                        putString("TITLE", currentTitle.takeIf { it?.isNotBlank() == true })
                        putString("ARTIST", currentArtist.takeIf { it?.isNotBlank() == true })
                        putString("ALBUM", playingRadioName)
                        putString("ARTWORK_URL", currentArtworkUrl ?: radioFavicon)
                    }
            try {
                controller.sendCustomCommand(
                        androidx.media3.session.SessionCommand(
                                "UPDATE_METADATA",
                                android.os.Bundle.EMPTY
                        ),
                        args
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun fetchArtwork(artist: String?, title: String?): String? {
        if (artist.isNullOrBlank()) return null
        return try {
            com.example.simpleradio.data.api.ImageScraper.findArtwork(artist, title)
        } catch (_: Exception) {
            null
        }
    }

    fun createMetadataListener(
            playingRadioName: String?,
            playingRadioCountry: String?,
            onMetadataFound: (String?, String?) -> Unit
    ): androidx.media3.common.Player.Listener {
        return object : androidx.media3.common.Player.Listener {
            override fun onMediaMetadataChanged(
                    mediaMetadata: androidx.media3.common.MediaMetadata
            ) {
                val result =
                        extractMetadata(
                                rawTitle = mediaMetadata.title?.toString(),
                                rawArtist = mediaMetadata.artist?.toString(),
                                stationName = playingRadioName ?: "",
                                country = playingRadioCountry ?: ""
                        )
                if (result != null) {
                    onMetadataFound(result.artist, result.title)
                }
            }
        }
    }
}
