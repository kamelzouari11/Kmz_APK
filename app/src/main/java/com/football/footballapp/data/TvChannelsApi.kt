package com.football.footballapp.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TvChannelsApi {
    @GET("tv")
    suspend fun getTvChannels(
        @Query("home") home: String,
        @Query("away") away: String,
        @Query("date") date: String
    ): TvChannelsResponse

    companion object {
        fun create(baseUrl: String): TvChannelsApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .build()

            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            return Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(client)
                .addConverterFactory(Network.moshiConverter)
                .build()
                .create(TvChannelsApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class TvChannelsResponse(
    val match: String?,
    val date: String?,
    val channels: List<TvChannelGroupDto>?
)

@JsonClass(generateAdapter = true)
data class TvChannelGroupDto(
    val country: String,
    val channels: List<String>
)
