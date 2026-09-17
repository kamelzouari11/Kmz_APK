package com.football.footballapp.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TvChannelsApi {
    @GET("schedule")
    suspend fun getSchedule(
        @Query("date") date: String
    ): ScrapedScheduleResponse

    @GET("tv")
    suspend fun getTvChannels(
        @Query("home") home: String,
        @Query("away") away: String,
        @Query("date") date: String,
        @Query("source_url") sourceUrl: String? = null
    ): TvChannelsResponse

    companion object {
        fun create(baseUrl: String): TvChannelsApi {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
                .build()

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
    val status: String? = null,
    val source: String? = null,
    val sourceUrl: String? = null,
    val verifiedAt: String? = null,
    val channels: List<TvChannelGroupDto>?,
    val events: List<LiveSoccerEventDto>? = null,
    val eventsSnapshot: String? = null
)

@JsonClass(generateAdapter = true)
data class LiveSoccerEventDto(
    val elapsed: Int,
    val extra: Int? = null,
    val teamSide: String,
    val type: String,
    val detail: String,
    val player: String? = null,
    val assist: String? = null
)

@JsonClass(generateAdapter = true)
data class ScrapedScheduleResponse(
    val date: String,
    val matches: List<ScrapedMatchDto>?
)

@JsonClass(generateAdapter = true)
data class ScrapedMatchDto(
    val date: String,
    val utcDate: String?,
    val league: String?,
    val home: String,
    val away: String,
    val time: String?,
    val status: String? = null,
    val statusLabel: String? = null,
    val minute: Int? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val channels: List<String>?,
    val sourceUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class TvChannelGroupDto(
    val country: String,
    val channels: List<String>
)
