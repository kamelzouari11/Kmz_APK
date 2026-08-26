package com.example.simpleiptv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VodCategory(
        @param:Json(name = "category_id") val category_id: String?,
        @param:Json(name = "category_name") val category_name: String
)

@JsonClass(generateAdapter = true)
data class VodMovie(
        @param:Json(name = "stream_id") val stream_id: Int,
        val name: String,
        @param:Json(name = "category_id") val category_id: String?,
        @param:Json(name = "stream_icon") val stream_icon: String?,
        val rating: String? = null,
        @param:Json(name = "added") val added: String? = null,
        @param:Json(name = "container_extension") val container_extension: String? = "mp4"
)
