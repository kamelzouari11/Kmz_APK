package com.example.simpleradio.data.api

import com.example.simpleradio.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path

interface RadioBrowserApi {

    @GET("json/countries")
    suspend fun getCountries(): List<RadioCountry>

    @GET("json/tags")
    suspend fun getTags(
        @Query("order") order: String = "stationcount",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioTag>

    @GET("json/tags/{filter}")
    suspend fun getTagsFiltered(
        @Path("filter") filter: String,
        @Query("order") order: String = "stationcount",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioTag>

    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("countrycode") countryCode: String? = null,
        @Query("tag") tag: String? = null,
        @Query("name") name: String? = null,
        @Query("bitrateMin") bitrateMin: Int? = null,
        @Query("bitrateMax") bitrateMax: Int? = null,
        @Query("limit") limit: Int = 100,
        @Query("order") order: String = "clickcount",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioStation>
}
