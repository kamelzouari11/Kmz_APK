package com.example.simpleradio.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RadioCountry(
    val name: String,
    val iso_3166_1: String,
    val stationcount: Int
)

@JsonClass(generateAdapter = true)
data class RadioTag(
    val name: String,
    val stationcount: Int
)

@JsonClass(generateAdapter = true)
data class RadioStation(
    val stationuuid: String,
    val name: String,
    val url: String,
    val url_resolved: String,
    val favicon: String?,
    val tags: String?,
    val country: String?,
    val bitrate: Int?,
    val codec: String?,
    val countrycode: String?
)
