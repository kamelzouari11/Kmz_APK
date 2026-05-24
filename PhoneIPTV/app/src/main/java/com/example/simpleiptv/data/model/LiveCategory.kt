package com.example.simpleiptv.data.model

data class LiveCategory(
        @com.squareup.moshi.Json(name = "category_id") val category_id: String?,
        val category_name: String
)
