package com.example.myiptv.data

import retrofit2.http.GET
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

interface XtreamApi {
    @GET("player_api.php")
    suspend fun liveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories",
    ): List<XtreamCategoryDto>

    @GET("player_api.php")
    suspend fun liveChannels(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
    ): List<XtreamChannelDto>

    @GET("player_api.php")
    suspend fun vodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories",
    ): List<XtreamCategoryDto>

    @GET("player_api.php")
    suspend fun vodMovies(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
    ): List<XtreamVodDto>

    @GET("player_api.php")
    suspend fun vodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("vod_id") vodId: Int,
        @Query("action") action: String = "get_vod_info",
    ): XtreamVodInfoResponse

    @FormUrlEncoded
    @POST("player_api.php")
    suspend fun shortEpg(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("stream_id") streamId: Int,
        @Query("limit") limit: Int = 4,
        @Field("action") action: String = "get_short_epg",
    ): XtreamEpgResponse

}
