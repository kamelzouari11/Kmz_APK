package com.football.footballapp.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Données statiques OpenFootball :
 * - https://github.com/openfootball/football.json pour les championnats
 * - https://github.com/upbound-web/worldcup-live.json pour la Coupe du Monde 2026
 * Pas de live, pas de minute, scores finaux seulement.
 */
interface OpenFootballApi {
    @GET("openfootball/football.json/master/{season}/{league}.json")
    suspend fun getSeason(
        @Path("season") season: String,    // ex. "2025-26"
        @Path("league") league: String      // ex. "en.1", "fr.1", "cl"
    ): OpenFootballSeasonDto

    @GET("openfootball/worldcup.json/master/{year}/worldcup.json")
    suspend fun getWorldCupSeason(
        @Path("year") year: String         // ex. "2026"
    ): WorldCupSeasonDto

    @GET("upbound-web/worldcup-live.json/master/{year}/worldcup.json")
    suspend fun getWorldCupSeasonLive(
        @Path("year") year: String         // ex. "2026"
    ): WorldCupSeasonDto

    companion object {
        fun create(): OpenFootballApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                )
                .build()
            return Retrofit.Builder()
                .baseUrl("https://raw.githubusercontent.com/")
                .client(client)
                .addConverterFactory(Network.moshiConverter)
                .build()
                .create(OpenFootballApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class OpenFootballSeasonDto(
    val name: String?,
    val matches: List<OpenFootballMatchDto>?
)

@JsonClass(generateAdapter = true)
data class OpenFootballMatchDto(
    val round: String?,
    val date: String?,                       // "2026-05-30"
    val time: String?,                        // "21:00" (parfois absent)
    val team1: String?,
    val team2: String?,
    val score: OpenFootballScoreDto?
)

@JsonClass(generateAdapter = true)
data class OpenFootballScoreDto(
    val ft: List<Int>?  // [home, away] si terminé
)

@JsonClass(generateAdapter = true)
data class WorldCupSeasonDto(
    val name: String?,
    val matches: List<WorldCupMatchDto>?
)

@JsonClass(generateAdapter = true)
data class WorldCupMatchDto(
    val round: String?,
    val date: String?,                       // "2026-06-11"
    val time: String?,                       // "13:00 UTC-6"
    val team1: String?,
    val team2: String?,
    val score: WorldCupScoreDto?,
    val group: String?,
    val ground: String?
)

@JsonClass(generateAdapter = true)
data class WorldCupScoreDto(
    val ft: List<Int>?,
    val ht: List<Int>?
)
