package com.football.footballapp.data.model

enum class MatchStatus {
    SCHEDULED, LIVE, HALF_TIME, FINISHED, POSTPONED, CANCELLED, UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): MatchStatus = when (raw?.uppercase()) {
            "SCHEDULED", "TIMED", "NS", "TBD" -> SCHEDULED
            "IN_PLAY", "LIVE", "1H", "2H", "ET", "P", "BT" -> LIVE
            "PAUSED", "HT" -> HALF_TIME
            "FINISHED", "FT", "AET", "PEN" -> FINISHED
            "POSTPONED", "PST" -> POSTPONED
            "CANCELLED", "SUSPENDED", "CANC", "ABD" -> CANCELLED
            else -> UNKNOWN
        }
    }
}

data class Match(
    val id: Long,
    val utcDate: String,
    val status: MatchStatus,
    val statusLabel: String,
    val minute: Int? = null,
    val competitionId: Int? = null,
    val competitionName: String,
    val competitionCountry: String? = null,
    val competitionEmblem: String? = null,
    val competitionFlag: String? = null,
    val homeTeam: Team,
    val awayTeam: Team,
    val score: Score,
    val source: String
)

data class Team(
    val id: Int,
    val name: String,
    val shortName: String? = null,
    val logoUrl: String? = null
)

data class Score(
    val home: Int?,
    val away: Int?
)

data class MatchDetail(
    val matchId: Long,
    val source: String,
    val stats: List<MatchTeamStats> = emptyList(),
    val lineups: MatchLineups? = null,
    val events: List<MatchEvent> = emptyList(),
    val tvChannels: List<TvChannelGroup> = emptyList()
)

data class MatchTeamStats(
    val teamId: Int,
    val teamName: String,
    val teamLogo: String?,
    val stats: List<StatItem>
)

data class StatItem(
    val type: String,
    val value: String
)

data class MatchLineups(
    val home: TeamLineup,
    val away: TeamLineup
)

data class TeamLineup(
    val teamId: Int,
    val teamName: String,
    val teamLogo: String?,
    val formation: String?,
    val coach: Coach?,
    val startXI: List<LineupPlayer>,
    val substitutes: List<LineupPlayer>
)

data class Coach(
    val id: Int?,
    val name: String?,
    val photo: String?
)

data class LineupPlayer(
    val id: Int,
    val name: String,
    val number: Int?,
    val position: String?,
    val grid: String?
)

data class MatchEvent(
    val time: EventTime,
    val teamId: Int,
    val teamName: String,
    val teamLogo: String?,
    val type: String, // Goal, Card, subst, Var
    val detail: String,
    val player: EventPlayer,
    val assist: EventPlayer? = null,
    val comments: String? = null
)

data class EventTime(
    val elapsed: Int,
    val extra: Int? = null
)

data class EventPlayer(
    val id: Int?,
    val name: String?
)

data class TvChannelGroup(
    val country: String,
    val channels: List<String>
)

