package com.example.simpleiptv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true) data class StalkerResponse<T>(@param:Json(name = "js") val js: T)

@JsonClass(generateAdapter = true) data class StalkerToken(@param:Json(name = "token") val token: String)

@JsonClass(generateAdapter = true)
data class StalkerGenre(
        @param:Json(name = "id") val id: String,
        @param:Json(name = "title") val title: String
)

@JsonClass(generateAdapter = true)
data class StalkerChannel(
        @param:Json(name = "id") val id: String,
        @param:Json(name = "number") val number: String?,
        @param:Json(name = "name") val name: String,
        @param:Json(name = "cmd") val cmd: String,
        @param:Json(name = "logo") val logo: String?
)

@JsonClass(generateAdapter = true) data class StalkerLink(@param:Json(name = "cmd") val cmd: String)
