package com.example.simpleiptv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveChannel(
        val stream_id: Int,
        val name: String,
        @Json(name = "category_id") val category_id: String?,
        val stream_icon: String?
)
