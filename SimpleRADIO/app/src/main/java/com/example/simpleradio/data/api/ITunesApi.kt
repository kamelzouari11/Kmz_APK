package com.example.simpleradio.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface ITunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("country") country: String = "US",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 1
    ): ITunesSearchResponse
}

data class ITunesSearchResponse(
    val results: List<ITunesResult>?
)

data class ITunesResult(
    val artworkUrl100: String?,
    val collectionName: String?,
    val trackName: String?,
    val artistName: String?
)
