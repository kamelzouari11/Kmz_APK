package com.example.simpleradio.data.api

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
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

    @GET("search")
    suspend fun searchLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String
    ): List<LrcLibResponse>
}

object LyricsClient {
    fun createLrcLib(): LrcLibApi {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val httpClient =
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request =
                        chain.request()
                            .newBuilder()
                            // LRCLIB can return HTTP 520 for OkHttp's default User-Agent.
                            .header("User-Agent", "SimpleRADIO/1.0 (Android; lyrics client)")
                            .header("Accept", "application/json")
                            .build()
                    chain.proceed(request)
                }
                .build()
            
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/api/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LrcLibApi::class.java)
    }
}
