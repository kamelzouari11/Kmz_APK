package com.football.footballapp.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface MatchDetailApi {
    @GET("fixtures/lineups")
    suspend fun getLineups(
        @Query("fixture") fixtureId: Long
    ): ApiFootballLineupsResponse

    @GET("fixtures/statistics")
    suspend fun getStatistics(
        @Query("fixture") fixtureId: Long
    ): ApiFootballStatisticsResponse

    @GET("fixtures/events")
    suspend fun getEvents(
        @Query("fixture") fixtureId: Long
    ): ApiFootballEventsResponse

    companion object {
        private const val DIRECT_HOST = "https://v3.football.api-sports.io/"
        private const val RAPID_HOST = "https://api-football-v1.p.rapidapi.com/v3/"

        fun create(apiKey: String?, useRapidApi: Boolean = false): MatchDetailApi? {
            if (apiKey.isNullOrBlank()) return null
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                    if (useRapidApi) {
                        builder.addHeader("x-rapidapi-key", apiKey)
                        builder.addHeader("x-rapidapi-host", "api-football-v1.p.rapidapi.com")
                    } else {
                        builder.addHeader("x-apisports-key", apiKey)
                    }
                    chain.proceed(builder.build())
                }
                .addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.HEADERS
                    }
                )
                .build()

            return Retrofit.Builder()
                .baseUrl(if (useRapidApi) RAPID_HOST else DIRECT_HOST)
                .client(client)
                .addConverterFactory(Network.moshiConverter)
                .build()
                .create(MatchDetailApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class ApiFootballLineupsResponse(val response: List<ApiFootballTeamLineupDto>)

@JsonClass(generateAdapter = true)
data class ApiFootballTeamLineupDto(
    val team: ApiFootballTeamDto,
    val coach: ApiFootballCoachDto,
    val formation: String?,
    val startXI: List<ApiFootballLineupPlayerDto>,
    val substitutes: List<ApiFootballLineupPlayerDto>
)

@JsonClass(generateAdapter = true)
data class ApiFootballCoachDto(val id: Int?, val name: String?, val photo: String?)

@JsonClass(generateAdapter = true)
data class ApiFootballLineupPlayerDto(val player: ApiFootballPlayerDto)

@JsonClass(generateAdapter = true)
data class ApiFootballPlayerDto(
    val id: Int,
    val name: String,
    val number: Int?,
    val pos: String?,
    val grid: String?
)

@JsonClass(generateAdapter = true)
data class ApiFootballStatisticsResponse(val response: List<ApiFootballTeamStatsDto>)

@JsonClass(generateAdapter = true)
data class ApiFootballTeamStatsDto(
    val team: ApiFootballTeamDto,
    val statistics: List<ApiFootballStatDto>
)

@JsonClass(generateAdapter = true)
data class ApiFootballStatDto(
    val type: String,
    val value: Any?
)

@JsonClass(generateAdapter = true)
data class ApiFootballEventsResponse(val response: List<ApiFootballEventDto>)

@JsonClass(generateAdapter = true)
data class ApiFootballEventDto(
    val time: ApiFootballEventTimeDto,
    val team: ApiFootballTeamDto,
    val player: ApiFootballEventPlayerDto,
    val assist: ApiFootballEventPlayerDto?,
    val type: String,
    val detail: String,
    val comments: String?
)

@JsonClass(generateAdapter = true)
data class ApiFootballEventTimeDto(
    val elapsed: Int,
    val extra: Int?
)

@JsonClass(generateAdapter = true)
data class ApiFootballEventPlayerDto(
    val id: Int?,
    val name: String?
)
