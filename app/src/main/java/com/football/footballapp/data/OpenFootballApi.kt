package com.football.footballapp.data

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

/**
 * Données statiques OpenFootball : https://github.com/openfootball/football.json
 * Calendriers complets multi-saisons des 5 grands championnats + Champions League.
 * Pas de live, pas de minute, scores finaux seulement.
 */
interface OpenFootballApi {
    @GET("openfootball/football.json/master/{season}/{league}.json")
    suspend fun getSeason(
        @Path("season") season: String,    // ex. "2025-26"
        @Path("league") league: String      // ex. "en.1", "fr.1", "cl"
    ): OpenFootballSeasonDto

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
