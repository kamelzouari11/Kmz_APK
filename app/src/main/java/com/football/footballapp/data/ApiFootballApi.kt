package com.football.footballapp.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * API-FOOTBALL (https://www.api-football.com/) via RapidAPI ou direct.
 * Couverture la plus complète : 1100+ ligues, live, lineups, stats, odds, prédictions.
 */
interface ApiFootballApi {
    @GET("fixtures")
    suspend fun getFixtures(
        @Query("date") date: String,
        @Query("timezone") timezone: String = "UTC"
    ): ApiFootballFixturesResponse

    @GET("countries")
    suspend fun getCountries(): ApiFootballCountriesResponse

    @GET("leagues")
    suspend fun getLeaguesByCountry(
        @Query("country") country: String,
        @Query("current") current: String = "true"
    ): ApiFootballLeaguesResponse

    companion object {
        private const val DIRECT_HOST = "https://v3.football.api-sports.io/"
        private const val RAPID_HOST = "https://api-football-v1.p.rapidapi.com/v3/"

        fun create(apiKey: String?, useRapidApi: Boolean = false): ApiFootballApi? {
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
                .create(ApiFootballApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class ApiFootballFixturesResponse(
    val response: List<ApiFootballFixtureDto>
)

@JsonClass(generateAdapter = true)
data class ApiFootballCountriesResponse(
    val response: List<ApiFootballCountryDto>
)

@JsonClass(generateAdapter = true)
data class ApiFootballCountryDto(
    val name: String,
    val code: String?,
    val flag: String?
)

@JsonClass(generateAdapter = true)
data class ApiFootballLeaguesResponse(
    val response: List<ApiFootballLeagueEntryDto>
)

@JsonClass(generateAdapter = true)
data class ApiFootballLeagueEntryDto(
    val league: ApiFootballLeagueInfoDto,
    val country: ApiFootballCountryDto
)

@JsonClass(generateAdapter = true)
data class ApiFootballLeagueInfoDto(
    val id: Int,
    val name: String,
    val type: String?,   // "League" / "Cup"
    val logo: String?
)

@JsonClass(generateAdapter = true)
data class ApiFootballFixtureDto(
    val fixture: FixtureInfoDto,
    val league: LeagueDto,
    val teams: TeamsDto,
    val goals: GoalsDto
)

@JsonClass(generateAdapter = true)
data class FixtureInfoDto(
    val id: Long,
    val date: String,
    val status: FixtureStatusDto
)

@JsonClass(generateAdapter = true)
data class FixtureStatusDto(
    @Json(name = "long") val longLabel: String?,
    @Json(name = "short") val shortLabel: String?,
    val elapsed: Int?
)

@JsonClass(generateAdapter = true)
data class LeagueDto(
    val id: Int,
    val name: String,
    val country: String?,
    val logo: String?,
    val flag: String?
)

@JsonClass(generateAdapter = true)
data class TeamsDto(
    val home: ApiFootballTeamDto,
    val away: ApiFootballTeamDto
)

@JsonClass(generateAdapter = true)
data class ApiFootballTeamDto(
    val id: Int,
    val name: String,
    val logo: String?
)

@JsonClass(generateAdapter = true)
data class GoalsDto(
    val home: Int?,
    val away: Int?
)
