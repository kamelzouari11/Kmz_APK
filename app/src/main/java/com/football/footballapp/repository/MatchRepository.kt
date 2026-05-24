package com.football.footballapp.repository

import android.util.Log
import com.football.footballapp.data.ApiFootballApi
import com.football.footballapp.data.ApiFootballCountryDto
import com.football.footballapp.data.ApiFootballFixtureDto
import com.football.footballapp.data.ApiFootballLeagueEntryDto
import com.football.footballapp.data.FootballDataApi
import com.football.footballapp.data.FootballDataMatchDto
import com.football.footballapp.data.MatchCache
import com.football.footballapp.data.OpenFootballApi
import com.football.footballapp.data.OpenFootballMatchDto
import com.football.footballapp.data.model.Match
import com.football.footballapp.data.model.MatchStatus
import com.football.footballapp.data.model.Score
import com.football.footballapp.data.model.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

private const val TAG = "MatchRepository"

class MatchRepository(
    private val apiFootballApi: ApiFootballApi?,
    private val footballDataApi: FootballDataApi?,
    private val openFootballApi: OpenFootballApi,
    private val matchCache: MatchCache
) {
    private val dateCache = ConcurrentHashMap<String, List<Match>>()
    private val openFootballSeasonCache = ConcurrentHashMap<String, List<Match>>()
    // Cache pré-chargé des logos officiels via football-data.org (clé = nom normalisé)
    private val fdTeamLogoCache = ConcurrentHashMap<String, String>()
    @Volatile private var fdTeamLogoCacheLoaded = false

    fun getCachedMatch(matchId: Long): Match? {
        return dateCache.values.flatten().find { it.id == matchId }
    }


    /**
     * Routage:
     *  - Date dans ±1 jour → API-FOOTBALL (riche, live, Tunisie, coupes) ;
     *    si vide/erreur, fallback football-data.org
     *  - Date hors fenêtre → football-data.org (couvre toutes dates en plan gratuit)
     *  - Aucun client → mock
     */
    /**
     * @param forceRefresh true = fetch réseau forcé (cache écrasé). false = cache disque/mémoire
     *                     uniquement, AUCUNE requête réseau. Si pas de cache → liste vide.
     */
    suspend fun getMatchesForDate(date: String, forceRefresh: Boolean = false): Result<List<Match>> = withContext(Dispatchers.IO) {
        runCatching {
            // Cache mémoire (le plus rapide)
            dateCache[date]?.let {
                if (!forceRefresh) {
                    Log.d(TAG, "mem cache hit for $date (${it.size} matches)")
                    return@runCatching it
                }
            }
            // Cache disque (persistant entre lancements)
            if (!forceRefresh) {
                matchCache.load(date)?.let { disk ->
                    Log.d(TAG, "disk cache hit for $date (${disk.size} matches)")
                    dateCache[date] = disk
                    return@runCatching disk
                }
                // Pas de cache + pas de refresh demandé → liste vide (PAS de réseau)
                Log.d(TAG, "no cache for $date, no fetch (user must refresh)")
                return@runCatching emptyList<Match>()
            }
            Log.d(TAG, "forceRefresh=true → fetching $date from network")

            val target = runCatching { LocalDate.parse(date) }.getOrNull()
            val today = LocalDate.now()
            val withinApiFootballWindow = target != null && (
                target == today ||
                target == today.minusDays(1) ||
                target == today.plusDays(1)
            )

            Log.d(TAG, "fetch $date — withinWindow=$withinApiFootballWindow apiFootball=${apiFootballApi != null} footballData=${footballDataApi != null}")

            val matches = when {
                withinApiFootballWindow && apiFootballApi != null -> {
                    val af = runCatching { fetchFromApiFootball(date) }
                        .onFailure { Log.w(TAG, "api-football failed: ${it.message}") }
                        .getOrNull().orEmpty()
                    Log.d(TAG, "api-football returned ${af.size} matches")
                    if (af.isNotEmpty()) af
                    else {
                        // api-football vide (quota épuisé / pas de match dans son plan) :
                        // on profite des DEUX autres sources et on les merge.
                        val fd = fetchFromFootballDataOrEmpty(date)
                        val open = fetchFromOpenFootball(date)
                        val merged = mergeMatches(primary = fd, secondary = open)
                        Log.d(TAG, "fallback merged: fd=${fd.size}, open=${open.size}, total=${merged.size}")
                        merged
                    }
                }
                else -> {
                    // Hors fenêtre ±1j : football-data + openfootball
                    val fd = fetchFromFootballDataOrEmpty(date)
                    val open = fetchFromOpenFootball(date)
                    val merged = mergeMatches(primary = fd, secondary = open)
                    Log.d(TAG, "merged: fd=${fd.size}, open=${open.size}, total=${merged.size}")
                    if (merged.isEmpty() && apiFootballApi == null && footballDataApi == null) {
                        sampleMatches()
                    } else merged
                }
            }
            val enriched = enrichWithLogos(matches)
            if (enriched.isNotEmpty()) {
                dateCache[date] = enriched
                matchCache.save(date, enriched)  // persistance disque
            }
            Log.d(TAG, "final result for $date: ${enriched.size} matches")
            enriched
        }
    }

    fun clearCache() {
        dateCache.clear()
        openFootballSeasonCache.clear()
        matchCache.clear()
    }

    // === Settings : countries + leagues (API-FOOTBALL) ===

    @Volatile private var countriesCache: List<ApiFootballCountryDto>? = null
    private val leaguesByCountryCache = ConcurrentHashMap<String, List<ApiFootballLeagueEntryDto>>()

    suspend fun getAllCountries(): List<ApiFootballCountryDto> = withContext(Dispatchers.IO) {
        countriesCache?.let { return@withContext it }
        if (apiFootballApi == null) return@withContext emptyList()
        runCatching {
            apiFootballApi.getCountries().response
                .sortedBy { it.name }
                .also { countriesCache = it }
        }.onFailure { Log.w(TAG, "getCountries failed: ${it.message}") }
            .getOrNull() ?: emptyList()
    }

    suspend fun getLeaguesForCountry(country: String): List<ApiFootballLeagueEntryDto> = withContext(Dispatchers.IO) {
        leaguesByCountryCache[country]?.let { return@withContext it }
        if (apiFootballApi == null) return@withContext emptyList()
        runCatching {
            apiFootballApi.getLeaguesByCountry(country).response
                .also { leaguesByCountryCache[country] = it }
        }.onFailure { Log.w(TAG, "getLeaguesByCountry($country) failed: ${it.message}") }
            .getOrNull() ?: emptyList()
    }

    private suspend fun fetchFromApiFootball(date: String): List<Match> {
        val response = apiFootballApi!!.getFixtures(date)
        return response.response.map { it.toDomain() }
    }

    private suspend fun fetchFromFootballData(date: String): List<Match> {
        val response = footballDataApi!!.getMatches(date, date)
        return response.matches.map { it.toDomain() }
    }

    private suspend fun fetchFromFootballDataOrEmpty(date: String): List<Match> {
        if (footballDataApi == null) return emptyList()
        val list = runCatching { fetchFromFootballData(date) }
            .onFailure { Log.w(TAG, "football-data failed: ${it.message}") }
            .getOrNull().orEmpty()
        Log.d(TAG, "football-data returned ${list.size} matches")
        return list
    }

    /**
     * Charge les fichiers OpenFootball de la saison correspondant à la date
     * (lazy + cache par couple `<season>/<league>`). Filtre ensuite par date.
     */
    private suspend fun fetchFromOpenFootball(date: String): List<Match> = coroutineScope {
        val target = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@coroutineScope emptyList()
        val season = seasonForDate(target)
        // 5 grands championnats domestiques (la CL n'est pas dans ce repo openfootball ;
        // elle sera servie par API-FOOTBALL en ±1j autour du match)
        val leagues = listOf(
            OpenLeague("en.1", "Premier League", "England"),
            OpenLeague("es.1", "La Liga", "Spain"),
            OpenLeague("de.1", "Bundesliga", "Germany"),
            OpenLeague("it.1", "Serie A", "Italy"),
            OpenLeague("fr.1", "Ligue 1", "France")
        )
        val all = leagues.map { league ->
            async {
                val cacheKey = "$season/${league.code}"
                openFootballSeasonCache.getOrPut(cacheKey) {
                    runCatching {
                        val resp = openFootballApi.getSeason(season, league.code)
                        (resp.matches ?: emptyList()).mapNotNull { it.toDomain(league) }
                    }.onFailure {
                        Log.w(TAG, "openfootball $cacheKey failed: ${it.message}")
                    }.getOrNull().orEmpty()
                }
            }
        }.awaitAll().flatten()

        val filtered = all.filter { it.utcDate.startsWith(date) }
        Log.d(TAG, "openfootball ($season) returned ${filtered.size} matches for $date " +
            "(${all.size} in season cache)")
        filtered
    }

    /** Saison footballistique européenne : août → mai. */
    private fun seasonForDate(date: LocalDate): String {
        val startYear = if (date.monthValue >= 7) date.year else date.year - 1
        val endShort = (startYear + 1) % 100
        return "$startYear-${"%02d".format(endShort)}"
    }

    private data class OpenLeague(val code: String, val name: String, val country: String)

    private fun OpenFootballMatchDto.toDomain(league: OpenLeague): Match? {
        val d = date ?: return null
        val t1 = team1 ?: return null
        val t2 = team2 ?: return null
        val timePart = time?.takeIf { it.matches(Regex("""\d{1,2}:\d{2}""")) } ?: "00:00"
        val utc = "${d}T${timePart.padStart(5, '0')}:00Z"
        val ft = score?.ft
        val home = ft?.getOrNull(0)
        val away = ft?.getOrNull(1)
        // Si la date est passée et que le JSON OpenFootball n'a pas encore le score
        // commité, on marque quand même comme TERMINÉ (au lieu de SCHEDULED).
        val matchDate = runCatching { LocalDate.parse(d) }.getOrNull()
        val isPast = matchDate?.isBefore(LocalDate.now()) == true
        val status = when {
            home != null && away != null -> MatchStatus.FINISHED
            isPast -> MatchStatus.FINISHED
            else -> MatchStatus.SCHEDULED
        }
        return Match(
            id = ("${league.code}-$d-$t1-$t2").hashCode().toLong(),
            utcDate = utc,
            status = status,
            statusLabel = if (status == MatchStatus.FINISHED) "Terminé" else "À venir",
            competitionName = league.name,
            competitionCountry = league.country,
            homeTeam = Team(id = 0, name = t1),
            awayTeam = Team(id = 0, name = t2),
            score = Score(home, away),
            source = "openfootball"
        )
    }

    /**
     * Fusionne deux listes de matchs en préférant les entrées qui ont un score.
     * Clé d'identité : couple (homeTeam normalisé, awayTeam normalisé).
     */
    private fun mergeMatches(primary: List<Match>, secondary: List<Match>): List<Match> {
        if (secondary.isEmpty()) return primary
        if (primary.isEmpty()) return secondary

        fun key(m: Match): String =
            "${normalizeTeam(m.homeTeam.name)}|${normalizeTeam(m.awayTeam.name)}"

        val byKey = primary.associateBy { key(it) }.toMutableMap()
        for (m in secondary) {
            val k = key(m)
            val existing = byKey[k]
            if (existing == null) {
                byKey[k] = m
            } else {
                // Le primary est plus riche (logos/scores/heure). On ne complète que
                // si le primary a un trou que le secondary peut combler.
                val existingHasScore = existing.score.home != null && existing.score.away != null
                val newHasScore = m.score.home != null && m.score.away != null
                if (newHasScore && !existingHasScore) {
                    byKey[k] = existing.copy(
                        score = m.score,
                        status = if (m.status == MatchStatus.FINISHED) MatchStatus.FINISHED else existing.status,
                        statusLabel = if (m.status == MatchStatus.FINISHED) "Terminé" else existing.statusLabel
                    )
                }
            }
        }
        return byKey.values.toList()
    }

    private fun normalizeTeam(name: String): String =
        name.lowercase()
            .replace(Regex("""\s+(fc|cf|sc|bc|afc|cfc|ac)\b"""), "")
            .replace(Regex("""\b(fc|cf|sc|afc|ac)\s+"""), "")
            .replace(Regex("""[^a-z0-9 ]"""), "")
            .trim()

    /**
     * Pré-charge UN COUP les 5 grandes ligues côté football-data.org pour récupérer
     * tous les logos officiels d'équipes. ~5 requêtes en parallèle, puis 0 jusqu'à la
     * fin de la session.
     */
    private suspend fun ensureFdTeamLogosLoaded() = coroutineScope {
        if (fdTeamLogoCacheLoaded || footballDataApi == null) return@coroutineScope
        val codes = listOf("PL", "PD", "BL1", "SA", "FL1") // Premier League, Liga, Bundes, Serie A, L1
        codes.map { code ->
            async {
                runCatching {
                    val resp = footballDataApi.getCompetitionTeams(code)
                    resp.teams?.forEach { team ->
                        val name = team.name ?: return@forEach
                        val crest = team.crest?.takeIf { it.isNotBlank() } ?: return@forEach
                        fdTeamLogoCache[normalizeTeam(name)] = crest
                        team.shortName?.let { fdTeamLogoCache[normalizeTeam(it)] = crest }
                    }
                }.onFailure { Log.w(TAG, "fd /competitions/$code/teams failed: ${it.message}") }
            }
        }.awaitAll()
        fdTeamLogoCacheLoaded = true
        Log.d(TAG, "fd team logo cache loaded: ${fdTeamLogoCache.size} entries")
    }

    private suspend fun enrichWithLogos(matches: List<Match>): List<Match> = coroutineScope {
        // Pré-load /teams football-data.org seulement si on a au moins UN match sans logo
        // (cas : matchs OpenFootball pour dates lointaines). Sinon on évite 5 grosses
        // requêtes (~250 Ko + 5 requêtes du quota football-data) au démarrage pour rien.
        val hasMatchWithoutLogo = matches.any {
            it.homeTeam.logoUrl.isNullOrBlank() || it.awayTeam.logoUrl.isNullOrBlank()
        }
        if (hasMatchWithoutLogo) ensureFdTeamLogosLoaded()
        // Cross-référence : si une équipe a un logo dans un match du batch (ex. via
        // football-data.org) et apparaît sans logo dans un autre (ex. openfootball),
        // on partage le logo entre les deux.
        val localKnown: Map<String, String> = matches.flatMap { listOf(it.homeTeam, it.awayTeam) }
            .filter { !it.logoUrl.isNullOrBlank() }
            .associate { normalizeTeam(it.name) to it.logoUrl!! }

        fun logoFor(team: Team): String? {
            if (!team.logoUrl.isNullOrBlank()) return team.logoUrl
            val norm = normalizeTeam(team.name)
            localKnown[norm]?.let { return it }
            return fdTeamLogoCache[norm]
        }

        matches.map { match ->
            match.copy(
                homeTeam = match.homeTeam.copy(logoUrl = logoFor(match.homeTeam)),
                awayTeam = match.awayTeam.copy(logoUrl = logoFor(match.awayTeam))
            )
        }
    }

    private fun sampleMatches(): List<Match> = listOf(
        Match(
            id = 1,
            utcDate = "2026-05-17T20:00:00Z",
            status = MatchStatus.SCHEDULED,
            statusLabel = "À venir",
            competitionName = "Ligue 1",
            competitionCountry = "France",
            homeTeam = Team(101, "Paris Saint-Germain", "PSG"),
            awayTeam = Team(102, "Olympique de Marseille", "OM"),
            score = Score(null, null),
            source = "mock"
        ),
        Match(
            id = 2,
            utcDate = "2026-05-17T18:30:00Z",
            status = MatchStatus.FINISHED,
            statusLabel = "Terminé",
            competitionName = "Premier League",
            competitionCountry = "England",
            homeTeam = Team(201, "Manchester City", "MCI"),
            awayTeam = Team(202, "Arsenal", "ARS"),
            score = Score(2, 1),
            source = "mock"
        ),
        Match(
            id = 3,
            utcDate = "2026-05-17T19:45:00Z",
            status = MatchStatus.LIVE,
            statusLabel = "Live",
            minute = 67,
            competitionName = "Serie A",
            competitionCountry = "Italy",
            homeTeam = Team(301, "Juventus", "JUV"),
            awayTeam = Team(302, "Inter Milan", "INT"),
            score = Score(1, 0),
            source = "mock"
        ),
        Match(
            id = 4,
            utcDate = "2026-05-17T21:00:00Z",
            status = MatchStatus.SCHEDULED,
            statusLabel = "À venir",
            competitionName = "La Liga",
            competitionCountry = "Spain",
            homeTeam = Team(401, "Real Madrid", "RMA"),
            awayTeam = Team(402, "FC Barcelona", "BAR"),
            score = Score(null, null),
            source = "mock"
        )
    )
}

private fun FootballDataMatchDto.toDomain(): Match {
    val mapped = MatchStatus.fromRaw(status)
    return Match(
        id = id,
        utcDate = utcDate,
        status = mapped,
        statusLabel = status.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
        minute = minute,
        competitionId = competition.id,
        competitionName = competition.name,
        competitionCountry = mapFootballDataArea(competition.area?.name, competition.name),
        competitionEmblem = competition.emblem,
        homeTeam = Team(
            id = homeTeam.id ?: 0,
            name = homeTeam.name ?: "Domicile",
            shortName = homeTeam.tla ?: homeTeam.shortName,
            logoUrl = homeTeam.crest
        ),
        awayTeam = Team(
            id = awayTeam.id ?: 0,
            name = awayTeam.name ?: "Extérieur",
            shortName = awayTeam.tla ?: awayTeam.shortName,
            logoUrl = awayTeam.crest
        ),
        score = Score(score.fullTime?.home, score.fullTime?.away),
        source = "football-data.org"
    )
}

/**
 * football-data.org renvoie `area = "Europe"` pour la Champions League et UEFA EL,
 * alors que notre filtre Countries attend `"World"` (convention API-FOOTBALL).
 * On normalise ici pour que l'UCL apparaisse aussi sur les dates couvertes par
 * football-data.org (hors ±1j).
 */
private fun mapFootballDataArea(area: String?, competitionName: String): String? {
    val name = competitionName.lowercase()
    return when {
        name.startsWith("uefa champions league") -> "World"
        name.startsWith("uefa europa") -> "World"
        name.contains("fifa world cup") || name.contains("club world cup") -> "World"
        else -> area
    }
}

private fun ApiFootballFixtureDto.toDomain() = Match(
    id = fixture.id,
    utcDate = fixture.date,
    status = MatchStatus.fromRaw(fixture.status.shortLabel),
    statusLabel = fixture.status.longLabel ?: fixture.status.shortLabel ?: "—",
    minute = fixture.status.elapsed,
    competitionId = league.id,
    competitionName = league.name,
    competitionCountry = league.country,
    competitionEmblem = league.logo,
    competitionFlag = league.flag,
    homeTeam = Team(
        id = teams.home.id,
        name = teams.home.name,
        logoUrl = teams.home.logo
    ),
    awayTeam = Team(
        id = teams.away.id,
        name = teams.away.name,
        logoUrl = teams.away.logo
    ),
    score = Score(goals.home, goals.away),
    source = "api-football"
)
