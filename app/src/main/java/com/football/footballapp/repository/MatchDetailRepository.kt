package com.football.footballapp.repository

import android.util.Log
import com.football.footballapp.data.MatchDetailApi
import com.football.footballapp.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

class MatchDetailRepository(
    private val matchDetailApi: MatchDetailApi?
) {
    private val TAG = "MatchDetailRepository"
    private val detailCache = ConcurrentHashMap<Long, MatchDetail>()
    

    suspend fun getMatchDetail(
        matchId: Long,
        source: String,
        forceRefresh: Boolean = false
    ): Result<MatchDetail> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            detailCache[matchId]?.let { return@withContext Result.success(it) }
        }

        runCatching {
            coroutineScope {
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
                            Log.w(TAG, "Failed to fetch events: ${it.message}")
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }

                MatchDetail(
                    matchId = matchId,
                    source = source,
                    stats = emptyList(),
                    lineups = lineupsDeferred.await(),
                    events = eventsDeferred.await(),
                    tvChannels = emptyList()
                ).also { detailCache[matchId] = it }
            }
        }
    }

    
}
