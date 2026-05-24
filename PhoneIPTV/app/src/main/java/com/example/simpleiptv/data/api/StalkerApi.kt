package com.example.simpleiptv.data.api

import com.example.simpleiptv.data.model.*
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface StalkerApi {

        @GET("server/load.php?type=stb&action=handshake&JsHttpRequest=1-xml")
        suspend fun handshake(
                @Query("mac") mac: String,
                @Header("Cookie") cookie: String = "mac=$mac; stb_lang=en; timezone=Europe/Paris;"
        ): StalkerResponse<StalkerToken>

        @GET("server/load.php?type=itv&action=get_genres&JsHttpRequest=1-xml")
        suspend fun getGenres(
                @Header("Authorization") token: String
        ): StalkerResponse<List<StalkerGenre>>

        @GET("server/load.php?type=itv&action=get_ordered_list&JsHttpRequest=1-xml")
        suspend fun getChannels(
                @Header("Authorization") token: String,
                @Query("genre") genreId: String,
                @Query("force_ch_link_check") forceCheck: Int = 0
        ): StalkerResponse<Any>

        @GET("server/load.php?type=itv&action=get_all_channels&JsHttpRequest=1-xml")
        suspend fun getAllChannels(@Header("Authorization") token: String): StalkerResponse<Any>

        @GET("server/load.php?type=itv&action=create_link&JsHttpRequest=1-xml")
        suspend fun createLink(
                @Header("Authorization") token: String,
                @Query("cmd") cmd: String,
                @Query("type") type: String = "itv" // Default to itv (live) instead of vod
        ): StalkerResponse<StalkerLink>
}
