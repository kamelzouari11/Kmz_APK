package com.example.simpleiptv.data.api

import com.example.simpleiptv.data.model.LiveCategory
import com.example.simpleiptv.data.model.LiveChannel
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApi {

    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("action") action: String = "get_live_categories"
    ): List<LiveCategory>

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") user: String,
        @Query("password") pass: String,
        @Query("category_id") categoryId: String? = null,
        @Query("action") action: String = "get_live_streams"
    ): List<LiveChannel>
}
