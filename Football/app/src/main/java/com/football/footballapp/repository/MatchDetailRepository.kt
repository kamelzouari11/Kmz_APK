package com.football.footballapp.repository

import android.util.Log
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import com.football.footballapp.data.MatchDetailApi
import com.football.footballapp.data.TvChannelsApi
import com.football.footballapp.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class MatchDetailRepository(
    private val matchDetailApi: MatchDetailApi?,
    private val tvChannelsApi: TvChannelsApi?
) {
    private val TAG = "MatchDetailRepository"
    private data class CachedDetail(val detail: MatchDetail, val expiresAt: Long)
    private val detailCache = ConcurrentHashMap<String, CachedDetail>()
    

    suspend fun getMatchDetail(
        matchId: Long,
        source: String,
        homeTeamName: String,
        awayTeamName: String,
        homeTeamId: Int,
        awayTeamId: Int,
        homeTeamLogo: String?,
        awayTeamLogo: String?,
        utcDate: String,
        tvSourceUrl: String? = null,
        forceRefresh: Boolean = false
    ): Result<MatchDetail> = withContext(Dispatchers.IO) {
        val cacheKey = "$source|$matchId|$utcDate|$tvSourceUrl"
        if (!forceRefresh) {
            detailCache[cacheKey]
                ?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }
                ?.let { return@withContext Result.success(it.detail) }
        }

        runCatching {
            coroutineScope {
                // Le serveur TV indexe le match par la journée UTC du fournisseur.
                // L'écran, lui, reste groupé par journée locale via Match.localCalendarDate().
                val formattedDate = runCatching {
                    OffsetDateTime.parse(utcDate).format(DateTimeFormatter.ISO_LOCAL_DATE)
                }.getOrElse {
                    utcDate.substringBefore('T')
                }

                val tvCoverageDeferred = async {
                    if (tvChannelsApi == null) {
                        TvFetchResult()
                    } else {
                        runCatching {
                            val response = tvChannelsApi.getTvChannels(
                                home = homeTeamName,
                                away = awayTeamName,
                                date = formattedDate,
                                sourceUrl = tvSourceUrl
                            )
                            val channels = response.channels.orEmpty()
                                .groupBy { canonicalTvCountry(it.country) }
                                .map { (country, groups) ->
                                    TvChannelGroup(
                                        country = country,
                                        channels = groups
                                            .flatMap { it.channels }
                                            .distinct()
                                    )
                                }
                                .filter { it.country in TV_VISIBLE_COUNTRIES }
                                .sortedWith(
                                    compareBy<TvChannelGroup> {
                                        when {
                                            it.country == "MENA (Middle East)" -> 0
                                            it.country in TV_EUROPE_COUNTRIES -> 1
                                            it.country in TV_MENA_COUNTRIES -> 2
                                            else -> 3
                                        }
                                    }.thenBy {
                                        TV_COUNTRY_PRIORITY[it.country] ?: Int.MAX_VALUE
                                    }.thenBy { it.country.lowercase() }
                                )
                            TvFetchResult(
                                channels = channels,
                                eventsSnapshot = response.eventsSnapshot,
                                events = response.events.orEmpty().map { event ->
                                    val isHome = event.teamSide.equals("home", ignoreCase = true)
                                    MatchEvent(
                                        time = EventTime(event.elapsed, event.extra),
                                        teamId = if (isHome) homeTeamId else awayTeamId,
                                        teamName = if (isHome) homeTeamName else awayTeamName,
                                        teamLogo = if (isHome) homeTeamLogo else awayTeamLogo,
                                        type = event.type,
                                        detail = event.detail,
                                        player = EventPlayer(id = null, name = event.player),
                                        assist = event.assist?.let {
                                            EventPlayer(id = null, name = it)
                                        }
                                    )
                                },
                                status = TvCoverageStatus.fromRaw(
                                    response.status,
                                    channels.isNotEmpty()
                                ),
                                source = response.source,
                                sourceUrl = response.sourceUrl,
                                verifiedAt = response.verifiedAt
                            )
                        }.getOrElse {
                            if (it is CancellationException) throw it
                            Log.w(TAG, "Failed to fetch TV channels: ${it.message}")
                            TvFetchResult()
                        }
                    }
                }

                val lineupsDeferred = async {
                    if (source == "api-football" && matchDetailApi != null) {
                        runCatching {
                            val response = matchDetailApi.getLineups(matchId).response
                            if (response.size >= 2) {
                                val homeDto = response[0]
                                val awayDto = response[1]

                                MatchLineups(
                                    home = TeamLineup(
                                        teamId = homeDto.team.id,
                                        teamName = homeDto.team.name,
                                        teamLogo = homeDto.team.logo,
                                        formation = homeDto.formation,
                                        coach = Coach(
                                            id = homeDto.coach.id,
                                            name = homeDto.coach.name,
                                            photo = homeDto.coach.photo
                                        ),
                                        startXI = homeDto.startXI.map {
                                            LineupPlayer(
                                                id = it.player.id,
                                                name = it.player.name,
                                                number = it.player.number,
                                                position = it.player.pos,
                                                grid = it.player.grid
                                            )
                                        },
                                        substitutes = homeDto.substitutes.map {
                                            LineupPlayer(
                                                id = it.player.id,
                                                name = it.player.name,
                                                number = it.player.number,
                                                position = it.player.pos,
                                                grid = it.player.grid
                                            )
                                        }
                                    ),
                                    away = TeamLineup(
                                        teamId = awayDto.team.id,
                                        teamName = awayDto.team.name,
                                        teamLogo = awayDto.team.logo,
                                        formation = awayDto.formation,
                                        coach = Coach(
                                            id = awayDto.coach.id,
                                            name = awayDto.coach.name,
                                            photo = awayDto.coach.photo
                                        ),
                                        startXI = awayDto.startXI.map {
                                            LineupPlayer(
                                                id = it.player.id,
                                                name = it.player.name,
                                                number = it.player.number,
                                                position = it.player.pos,
                                                grid = it.player.grid
                                            )
                                        },
                                        substitutes = awayDto.substitutes.map {
                                            LineupPlayer(
                                                id = it.player.id,
                                                name = it.player.name,
                                                number = it.player.number,
                                                position = it.player.pos,
                                                grid = it.player.grid
                                            )
                                        }
                                    )
                                )
                            } else null
                        }.getOrElse {
                            if (it is CancellationException) throw it
                            Log.w(TAG, "Failed to fetch lineups: ${it.message}")
                            null
                        }
                    } else {
                        null
                    }
                }

                val eventsDeferred = async {
                    if (source == "api-football" && matchDetailApi != null) {
                        runCatching {
                            matchDetailApi.getEvents(matchId).response.map { eventDto ->
                                MatchEvent(
                                    time = EventTime(
                                        elapsed = eventDto.time.elapsed,
                                        extra = eventDto.time.extra
                                    ),
                                    teamId = eventDto.team.id,
                                    teamName = eventDto.team.name,
                                    teamLogo = eventDto.team.logo,
                                    type = eventDto.type,
                                    detail = eventDto.detail,
                                    player = EventPlayer(
                                        id = eventDto.player.id,
                                        name = eventDto.player.name
                                    ),
                                    assist = eventDto.assist?.let {
                                        EventPlayer(id = it.id, name = it.name)
                                    },
                                    comments = eventDto.comments
                                )
                            }
                        }.getOrElse {
                            if (it is CancellationException) throw it
                            Log.w(TAG, "Failed to fetch events: ${it.message}")
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }

                val tvCoverage = tvCoverageDeferred.await()
                val apiEvents = eventsDeferred.await()
                MatchDetail(
                    matchId = matchId,
                    source = source,
                    stats = emptyList(),
                    lineups = lineupsDeferred.await(),
                    events = apiEvents.ifEmpty { tvCoverage.events },
                    eventsSnapshot = tvCoverage.eventsSnapshot,
                    tvChannels = tvCoverage.channels,
                    tvStatus = tvCoverage.status,
                    tvSource = tvCoverage.source,
                    tvSourceUrl = tvCoverage.sourceUrl,
                    tvVerifiedAt = tvCoverage.verifiedAt
                ).also { detail ->
                    val now = SystemClock.elapsedRealtime()
                    detailCache.entries.removeAll { it.value.expiresAt <= now }
                    val hasCoverage = detail.tvChannels.isNotEmpty() ||
                        detail.events.isNotEmpty() ||
                        !detail.eventsSnapshot.isNullOrBlank()
                    val cacheDuration = if (hasCoverage) 5 * 60_000L else 60_000L
                    // Empty listings are retried quickly; useful detail survives
                    // navigation between the list and the match screen.
                    detailCache[cacheKey] = CachedDetail(
                        detail = detail,
                        expiresAt = now + cacheDuration
                    )
                }
            }
        }.onFailure {
            if (it is CancellationException) throw it
        }
    }

    
}

private data class TvFetchResult(
    val channels: List<TvChannelGroup> = emptyList(),
    val events: List<MatchEvent> = emptyList(),
    val eventsSnapshot: String? = null,
    val status: TvCoverageStatus = TvCoverageStatus.UNKNOWN,
    val source: String? = null,
    val sourceUrl: String? = null,
    val verifiedAt: String? = null
)

private val TV_COUNTRY_PRIORITY = listOf(
    "MENA (Middle East)",
    "France", "Italy", "Spain", "United Kingdom", "United Kingdom & Ireland",
    "Germany", "Poland", "Romania", "Netherlands", "Portugal", "Switzerland",
    "Austria", "Belgium", "USA", "Canada", "Mexico"
).withIndex().associate { (index, country) -> country to index }

private val TV_EUROPE_COUNTRIES = setOf(
    "Albania", "Andorra", "Austria", "Belgium", "Bosnia and Herzegovina",
    "Bulgaria", "Croatia", "Cyprus", "Czech Republic", "Czechia", "Denmark",
    "Estonia", "Finland", "France", "Germany", "Greece", "Hungary", "Iceland",
    "Ireland", "Italy", "Kosovo", "Latvia", "Liechtenstein", "Lithuania",
    "Luxembourg", "Malta", "Moldova", "Monaco", "Montenegro", "Netherlands",
    "North Macedonia", "Norway", "Poland", "Portugal", "Romania", "San Marino",
    "Serbia", "Slovakia", "Slovenia", "Spain", "Sweden", "Switzerland",
    "Turkey", "Türkiye", "Ukraine", "United Kingdom", "United Kingdom & Ireland",
    "Vatican City", "Balkans", "Scandinavia & Baltics", "Europe"
)

private val TV_MENA_COUNTRIES = setOf(
    "Algeria", "Bahrain", "Egypt", "Iran", "Iraq", "Israel", "Jordan", "Kuwait",
    "Lebanon", "Libya", "Mauritania", "Morocco", "Oman", "Palestine",
    "Palestinian Territory", "Qatar", "Saudi Arabia", "Sudan", "Syria",
    "Tunisia", "United Arab Emirates", "Yemen", "MENA (Middle East)"
)

private val TV_NORTH_AMERICA_COUNTRIES = setOf(
    "Canada", "Mexico", "North America", "USA"
)

private val TV_VISIBLE_COUNTRIES =
    TV_EUROPE_COUNTRIES + TV_MENA_COUNTRIES + TV_NORTH_AMERICA_COUNTRIES

private fun canonicalTvCountry(country: String): String = when (country.trim().lowercase()) {
    "england", "great britain", "uk", "angleterre", "royaume-uni" ->
        "United Kingdom"
    "ireland republic" -> "Ireland"
    "macedonia" -> "North Macedonia"
    "united states", "united states of america", "us", "états-unis" -> "USA"
    "the netherlands", "holland", "pays-bas" -> "Netherlands"
    else -> country.trim().ifEmpty { "Other / International" }
}
