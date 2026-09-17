package com.football.footballapp.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface EspnSoccerApi {
    @GET("apis/site/v2/sports/soccer/{league}/scoreboard")
    suspend fun getScoreboard(
        @Path("league") league: String,
        @Query("dates") date: String,
        @Query("limit") limit: Int = 1000
    ): EspnScoreboardResponse

    companion object {
        fun create(): EspnSoccerApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                )
                .build()

            return Retrofit.Builder()
                .baseUrl("https://site.api.espn.com/")
                .client(client)
                .addConverterFactory(Network.moshiConverter)
                .build()
                .create(EspnSoccerApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class EspnScoreboardResponse(
    val leagues: List<EspnLeagueDto>?,
    val events: List<EspnEventDto>?
)

@JsonClass(generateAdapter = true)
data class EspnLeagueDto(
    val name: String?
)

@JsonClass(generateAdapter = true)
data class EspnEventDto(
    val id: String,
    val date: String,
    val competitions: List<EspnCompetitionDto>?
)

@JsonClass(generateAdapter = true)
data class EspnCompetitionDto(
    val competitors: List<EspnCompetitorDto>?,
    val status: EspnStatusDto?
)

@JsonClass(generateAdapter = true)
data class EspnCompetitorDto(
    val homeAway: String?,
    val score: String?,
    val team: EspnTeamDto
)

@JsonClass(generateAdapter = true)
data class EspnTeamDto(
    val id: String?,
    val displayName: String?,
    val shortDisplayName: String?,
    val abbreviation: String?,
    val logo: String?
)

@JsonClass(generateAdapter = true)
data class EspnStatusDto(
    val clock: Double?,
    val displayClock: String?,
    val type: EspnStatusTypeDto?
)

@JsonClass(generateAdapter = true)
data class EspnStatusTypeDto(
    val name: String?,
    val state: String?,
    val completed: Boolean?,
    val description: String?,
    val detail: String?,
    val shortDetail: String?
)
