package com.kmz.shazamplayer.model

data class Track(
        val index: String,
        val tagTime: String,
        var title: String,
        var artist: String,
        val shazamUrl: String,
        val trackKey: String,
        var streamUrl: String? = null,
        var artworkUrl: String? = null,
        // Métadonnées officielles (Deezer/iTunes)
        var officialDurationMs: Long? = null,
        var officialAlbum: String? = null,
        var officialCoverHD: String? = null,
        var metadataSource: String? = null // "deezer" ou "itunes"
)
