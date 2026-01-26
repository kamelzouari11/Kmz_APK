package com.example.simpleradio.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface MusicBrainzApi {
    @GET("recording/")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json"
    ): MusicBrainzSearchResponse
}

data class MusicBrainzSearchResponse(
    val recordings: List<Recording>?
)

data class Recording(
    val id: String,
    val title: String,
    val releases: List<Release>?
)

data class Release(
    val id: String,
    val title: String
)
