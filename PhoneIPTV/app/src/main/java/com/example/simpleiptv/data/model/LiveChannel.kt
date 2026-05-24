package com.example.simpleiptv.data.model

data class LiveChannel(
        val stream_id: Int,
        val name: String,
        @com.squareup.moshi.Json(name = "category_id") val category_id: String?,
        val stream_icon: String?
)
