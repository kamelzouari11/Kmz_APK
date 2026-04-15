package com.example.simpleiptv.data.model

import com.squareup.moshi.Json

data class VodCategory(
        @Json(name = "category_id") val category_id: String?,
        @Json(name = "category_name") val category_name: String
)

data class VodMovie(
        @Json(name = "stream_id") val stream_id: Int,
        val name: String,
        @Json(name = "category_id") val category_id: String?,
        @Json(name = "stream_icon") val stream_icon: String?,
        val rating: String? = null,
        @Json(name = "added") val added: String? = null,
        @Json(name = "container_extension") val container_extension: String? = "mp4"
)
