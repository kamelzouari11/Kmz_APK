package com.football.footballapp.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface FootballDataApi {
    @GET("v4/matches")
    suspend fun getMatches(
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String
    ): FootballDataMatchesResponse

    @GET("v4/competitions/{code}/teams")
    suspend fun getCompetitionTeams(
        @retrofit2.http.Path("code") code: String
    ): FootballDataTeamsResponse

    companion object {
        fun create(apiKey: String?): FootballDataApi? {
            if (apiKey.isNullOrBlank()) return null
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("X-Auth-Token", apiKey)
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.football-data.org/")
                .client(client)
                .addConverterFactory(Network.moshiConverter)
                .build()

            return retrofit.create(FootballDataApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class FootballDataMatchesResponse(
    val matches: List<FootballDataMatchDto>
)

@JsonClass(generateAdapter = true)
data class FootballDataTeamsResponse(
    val teams: List<FootballDataCompetitionTeamDto>?
)

@JsonClass(generateAdapter = true)
data class FootballDataCompetitionTeamDto(
    val id: Int?,
    val name: String?,
    val shortName: String?,
    val tla: String?,
    val crest: String?
)

@JsonClass(generateAdapter = true)
data class FootballDataMatchDto(
    val id: Long,
    val utcDate: String,
    val status: String,
    val minute: Int?,
    val competition: CompetitionDto,
    val homeTeam: TeamDto,
    val awayTeam: TeamDto,
    val score: ScoreDto
)

@JsonClass(generateAdapter = true)
data class CompetitionDto(
    val id: Int,
    val name: String,
    val emblem: String?,
    val area: AreaDto?
)

@JsonClass(generateAdapter = true)
data class AreaDto(
    val name: String?
)

@JsonClass(generateAdapter = true)
data class TeamDto(
    val id: Int?,
    val name: String?,
    val shortName: String?,
    val tla: String?,
    val crest: String?
)

@JsonClass(generateAdapter = true)
data class ScoreDto(
    val fullTime: ScoreValueDto?,
    val halfTime: ScoreValueDto?
)

@JsonClass(generateAdapter = true)
data class ScoreValueDto(
    val home: Int?,
    val away: Int?
)
