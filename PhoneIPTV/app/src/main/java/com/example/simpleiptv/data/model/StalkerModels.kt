package com.example.simpleiptv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true) data class StalkerResponse<T>(@Json(name = "js") val js: T)

@JsonClass(generateAdapter = true) data class StalkerToken(@Json(name = "token") val token: String)

@JsonClass(generateAdapter = true)
data class StalkerGenre(@Json(name = "id") val id: String, @Json(name = "title") val title: String)

@JsonClass(generateAdapter = true)
data class StalkerChannel(
        @Json(name = "id") val id: String,
        @Json(name = "number") val number: String?,
        @Json(name = "name") val name: String,
        @Json(name = "cmd") val cmd: String,
        @Json(name = "logo") val logo: String?
)

@JsonClass(generateAdapter = true) data class StalkerLink(@Json(name = "cmd") val cmd: String)
