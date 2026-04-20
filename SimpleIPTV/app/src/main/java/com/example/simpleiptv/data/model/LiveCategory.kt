package com.example.simpleiptv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveCategory(
        @Json(name = "category_id") val category_id: String?,
        val category_name: String
)
