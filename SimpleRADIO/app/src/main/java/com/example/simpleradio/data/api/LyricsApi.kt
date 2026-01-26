package com.example.simpleradio.data.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// --- LRCLIB (Service de paroles moderne et stable) ---
data class LrcLibResponse(
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val artistName: String?,
    val trackName: String?
)

interface LrcLibApi {
    @GET("get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String
    ): LrcLibResponse
}

object LyricsClient {
    fun createLrcLib(): LrcLibApi {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
            
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/api/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LrcLibApi::class.java)
    }
}
