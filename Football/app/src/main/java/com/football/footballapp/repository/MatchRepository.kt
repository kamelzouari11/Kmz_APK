package com.football.footballapp.repository

import android.util.Log
import com.football.footballapp.data.ApiFootballApi
import com.football.footballapp.data.ApiFootballCountryDto
import com.football.footballapp.data.ApiFootballFixtureDto
import com.football.footballapp.data.ApiFootballLeagueEntryDto
import com.football.footballapp.data.EspnEventDto
import com.football.footballapp.data.EspnSoccerApi
import com.football.footballapp.data.FootballDataApi
import com.football.footballapp.data.FootballDataMatchDto
import com.football.footballapp.data.MatchCache
import com.football.footballapp.data.OpenFootballApi
import com.football.footballapp.data.OpenFootballMatchDto
import com.football.footballapp.data.ScrapedMatchDto
import com.football.footballapp.data.TvChannelsApi
import com.football.footballapp.data.WorldCupMatchDto
import com.football.footballapp.data.model.Match
import com.football.footballapp.data.model.MatchStatus
import com.football.footballapp.data.model.Score
import com.football.footballapp.data.model.Team
import com.football.footballapp.data.model.TopDivisionCatalog
import com.football.footballapp.data.model.identityNames
import com.football.footballapp.data.model.isReserveOrYouthTeam
import com.football.footballapp.data.model.isReserveOrYouthTeamName
import com.football.footballapp.data.model.isWomenTeam
import com.football.footballapp.data.model.isWomenTeamName
import com.football.footballapp.data.model.localCalendarDate
import com.football.footballapp.data.model.normalizeTeamName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "MatchRepository"
private const val MAX_LIVE_WINDOW_MINUTES = 180L
private const val OFFICIAL_MATCH_SOURCE = "api-football"
private const val RENDER_MATCH_SOURCE = "tv-scraper"

private fun LocalDate.usesApiFootball(today: LocalDate = LocalDate.now()): Boolean =
    !isBefore(today.minusDays(1)) && !isAfter(today.plusDays(1))
private data class EspnLeagueSource(val slug: String, val name: String)

private val ESPN_SOCCER_LEAGUES = listOf(
    EspnLeagueSource("club.friendly", "Club Friendly"),
    EspnLeagueSource("uefa.champions_qual", "UEFA Champions League Qualifying"),
    EspnLeagueSource("uefa.europa_qual", "UEFA Europa League Qualifying"),
    EspnLeagueSource("uefa.europa.conf_qual", "UEFA Conference League Qualifying"),
    EspnLeagueSource("uefa.champions", "UEFA Champions League"),
    EspnLeagueSource("uefa.europa", "UEFA Europa League"),
    EspnLeagueSource("uefa.europa.conf", "UEFA Conference League")
)

class MatchRepository(
    private val apiFootballApi: ApiFootballApi?,
    private val footballDataApi: FootballDataApi?,
    private val openFootballApi: OpenFootballApi,
    private val espnSoccerApi: EspnSoccerApi?,
    private val tvChannelsApi: TvChannelsApi?,
    private val matchCache: MatchCache
) {
    private val dateCache = ConcurrentHashMap<String, List<Match>>()
    private val openFootballSeasonCache = ConcurrentHashMap<String, List<Match>>()
    // Cache pré-chargé des logos officiels via football-data.org (clé = nom normalisé)
    private val fdTeamLogoCache = ConcurrentHashMap<String, String>()
    private val fdTopDivisionTeamNamesByCountry = ConcurrentHashMap<String, Set<String>>()
    fun getCachedMatch(matchId: Long): Match? {
        return dateCache.values.flatten().find { it.id == matchId }
    }

    suspend fun getCachedMatchesForDate(date: String): List<Match>? =
        withContext(Dispatchers.IO) {
            val cached = dateCache[date] ?: matchCache.load(date)
            cached?.let {
                sanitizeMatches(it, date).also { sanitized ->
                    if (sanitized != it) persistDate(date, sanitized)
                    else dateCache[date] = sanitized
                }
            }
        }

    /**
     * Retourne toutes les journées connues sans appel réseau. Les journées disque
     * sont aussi placées en mémoire afin qu'un résultat de recherche puisse ouvrir
     * directement son écran de détail.
     */
    suspend fun getAllCachedMatches(): List<Match> = withContext(Dispatchers.IO) {
        val matchesByDate = matchCache.loadAll().toMutableMap()
        dateCache.forEach { (date, matches) -> matchesByDate[date] = matches }
        val sanitizedByDate = matchesByDate.mapValues { (date, matches) ->
            sanitizeMatches(matches, date).also { sanitized ->
                if (sanitized != matches) persistDate(date, sanitized)
            }
        }
        dateCache.putAll(sanitizedByDate)
        sanitizedByDate.values
            .flatten()
            .distinctBy { match ->
                "${match.utcDate}|${match.homeTeam.name}|${match.awayTeam.name}"
            }
    }


    /**
     * Charge uniquement la journée demandée. Le ViewModel peut publier le cache avant
     * d'appeler cette méthode avec forceRefresh=true pour une mise à jour non bloquante.
     */
    suspend fun getMatchesForDate(
        date: String,
        forceRefresh: Boolean = false,
        includeApiFootball: Boolean = forceRefresh
    ): Result<List<Match>> = withContext(Dispatchers.IO) {
        runCatching {
            val target = LocalDate.parse(date)
            val memoryCached = dateCache[date]
            val staleCached = memoryCached ?: matchCache.load(date)?.also { disk ->
                dateCache[date] = disk
            }
            if (!forceRefresh) {
                staleCached?.let {
                    val source = if (memoryCached != null) "mem" else "disk"
                    Log.d(TAG, "$source cache hit for $date (${it.size} matches)")
                    val sanitized = sanitizeMatches(it, date)
                    if (sanitized != it) persistDate(date, sanitized)
                    return@runCatching sanitized
                }
            }

            val officialMatches = runCatching {
                fetchMergedRange(
                    start = target,
                    end = target,
                    includeApiFootball = includeApiFootball || forceRefresh
                )[date].orEmpty()
            }.onFailure {
                Log.w(TAG, "official daily refresh failed for $date: ${it.message}")
            }.getOrDefault(emptyList())

            val enrichedMatches = enrichWithKnownLogos(officialMatches)
            val finalMatches = if (enrichedMatches.isNotEmpty() || staleCached == null) {
                persistDate(date, enrichedMatches)
                enrichedMatches
            } else {
                sanitizeMatches(staleCached, date).also { sanitizedStale ->
                    if (sanitizedStale != staleCached) persistDate(date, sanitizedStale)
                }
            }
            Log.d(TAG, "final result for $date: ${finalMatches.size} matches")
            finalMatches
        }
    }

    suspend fun getTvScheduleForDate(date: String): List<Match> =
        withContext(Dispatchers.IO) { fetchFromTvSchedule(date) }

    /** Fusionne et persiste un complément déjà chargé sans refaire le réseau. */
    suspend fun mergeSupplementalMatches(
        date: String,
        baseMatches: List<Match>,
        supplementalMatches: List<Match>
    ): List<Match> = withContext(Dispatchers.IO) {
        val requestedDate = LocalDate.parse(date)
        val supplementalForDate = supplementalMatches.filter {
            it.localCalendarDate() == requestedDate
        }
        if (!requestedDate.usesApiFootball()) {
            val freshRenderMatches = supplementalForDate.filter {
                it.source == RENDER_MATCH_SOURCE
            }
            val cachedRenderMatches = (baseMatches + dateCache[date].orEmpty())
                .filter { it.source == RENDER_MATCH_SOURCE }
            val renderMatches = if (freshRenderMatches.isNotEmpty()) {
                freshRenderMatches
            } else {
                cachedRenderMatches
            }
            val mergedRenderMatches = reconcileCurrentStatuses(
                enrichWithKnownLogos(
                    mergeMatches(primary = renderMatches, secondary = emptyList())
                        .map { it.normalizeRenderCompetition() }
                )
            )
            if (mergedRenderMatches.isNotEmpty()) persistDate(date, mergedRenderMatches)
            return@withContext mergedRenderMatches
        }

        val officialMatches = mergeMatches(
            primary = baseMatches.filter { it.source == OFFICIAL_MATCH_SOURCE },
            secondary = dateCache[date].orEmpty().filter {
                it.source == OFFICIAL_MATCH_SOURCE
            }
        )

        fun matchKey(match: Match): String = listOf(
            normalizeTeamName(match.homeTeam.name),
            normalizeTeamName(match.awayTeam.name)
        ).sorted().joinToString("|")

        val tvMetadataByMatch = supplementalForDate.associateBy(::matchKey)
        // Render/LiveSoccerTV ne définit jamais la liste. Il peut uniquement
        // rattacher son URL TV à un match officiel API-FOOTBALL déjà présent.
        val merged = officialMatches.map { official ->
            val tvMatch = tvMetadataByMatch[matchKey(official)]
            official.copy(
                tvSourceUrl = official.tvSourceUrl ?: tvMatch?.tvSourceUrl
            )
        }
        if (merged.isNotEmpty()) persistDate(date, merged)
        merged
    }

    /**
     * LiveSoccerTV peut rester momentanément sur HT, ou perdre le statut tout en
     * conservant le lien du match. Dans cette fenêtre, l'heure fiable du match
     * officiel empêche la carte de revenir à "À venir" après le coup d'envoi.
     */
    private fun reconcileCurrentStatuses(
        matches: List<Match>,
        now: Instant = Instant.now()
    ): List<Match> = matches.map { match ->
        val kickoff = runCatching { OffsetDateTime.parse(match.utcDate).toInstant() }
            .getOrNull() ?: return@map match
        val zone = ZoneId.systemDefault()
        val matchDate = kickoff.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val elapsedMinutes = Duration.between(kickoff, now).toMinutes()
        val insideMatchWindow = matchDate == today &&
            elapsedMinutes in 0..MAX_LIVE_WINDOW_MINUTES
        val sourceLostLiveStatus = match.status == MatchStatus.SCHEDULED ||
            match.status == MatchStatus.UNKNOWN
        val sourceClaimsLive = match.status == MatchStatus.LIVE ||
            match.status == MatchStatus.HALF_TIME
        val staleHalfTime = match.status == MatchStatus.HALF_TIME && elapsedMinutes >= 65

        // Cette garde vaut pour toutes les sources : aucun ancien cache ne peut
        // maintenir un match en direct hors de sa date et de sa fenêtre horaire.
        if (sourceClaimsLive && !insideMatchWindow) {
            val kickoffIsFuture = elapsedMinutes < 0
            return@map match.copy(
                status = if (kickoffIsFuture) MatchStatus.SCHEDULED else MatchStatus.UNKNOWN,
                statusLabel = if (kickoffIsFuture) "À venir" else "Score indisponible",
                minute = null,
                score = Score(home = null, away = null)
            )
        }

        if (match.tvSourceUrl.isNullOrBlank()) return@map match

        if (insideMatchWindow && (sourceLostLiveStatus || staleHalfTime)) {
            match.copy(
                status = MatchStatus.LIVE,
                statusLabel = "Live"
            )
        } else if (
            sourceLostLiveStatus &&
            (matchDate.isBefore(today) || elapsedMinutes > MAX_LIVE_WINDOW_MINUTES)
        ) {
            match.copy(
                status = MatchStatus.UNKNOWN,
                statusLabel = "Score indisponible",
                minute = null
            )
        } else {
            match
        }
    }

    private fun sanitizeMatches(matches: List<Match>, expectedDate: String): List<Match> {
        val localDate = runCatching { LocalDate.parse(expectedDate) }.getOrNull()
        val expectedSource = if (localDate?.usesApiFootball() != false) {
            OFFICIAL_MATCH_SOURCE
        } else {
            RENDER_MATCH_SOURCE
        }
        val matchesForDate = matches.filter {
            it.source == expectedSource &&
                (localDate == null || it.localCalendarDate() == localDate)
        }.map { it.normalizeRenderCompetition() }
        return reconcileCurrentStatuses(
            enrichWithKnownLogos(
                mergeMatches(primary = matchesForDate, secondary = emptyList())
            )
        )
    }

    private suspend fun fetchFromTvSchedule(date: String): List<Match> = coroutineScope {
        val scheduleApi = tvChannelsApi ?: return@coroutineScope emptyList()
        val target = LocalDate.parse(date)
        providerDatesForLocalDate(target).map { providerDate ->
            async {
                val providerDateText = providerDate.toString()
                runCatching {
                    val response = scheduleApi.getSchedule(providerDateText)
                    if (response.date != providerDateText) {
                        Log.w(
                            TAG,
                            "TV schedule returned ${response.date} for requested date " +
                                providerDateText
                        )
                        return@runCatching emptyList()
                    }
                    response.matches.orEmpty()
                        .filter { it.date == providerDateText }
                        .mapNotNull { it.toDomain() }
                }.onFailure {
                    Log.w(
                        TAG,
                        "TV schedule fallback failed for $providerDateText: ${it.message}"
                    )
                }.getOrDefault(emptyList())
            }
        }.awaitAll()
            .flatten()
            .filter { it.localCalendarDate() == target }
    }

    private suspend fun fetchFromEspn(date: String): List<Match> = coroutineScope {
        val api = espnSoccerApi ?: return@coroutineScope emptyList()
        val target = LocalDate.parse(date)
        providerDatesForLocalDate(target).flatMap { providerDate ->
            ESPN_SOCCER_LEAGUES.map { league ->
                async {
                    runCatching {
                        val response = api.getScoreboard(
                            league = league.slug,
                            date = providerDate.toString().replace("-", "")
                        )
                        val competitionName = response.leagues
                            .orEmpty()
                            .firstOrNull()
                            ?.name
                            ?: league.name
                        response.events.orEmpty().mapNotNull { event ->
                            event.toDomain(competitionName)
                        }
                    }.onFailure {
                        Log.w(
                            TAG,
                            "ESPN ${league.slug} failed for $providerDate: ${it.message}"
                        )
                    }.getOrDefault(emptyList())
                }
            }
        }.awaitAll()
            .flatten()
            .filter { it.localCalendarDate() == target }
    }

    /**
     * Les fournisseurs datent leurs calendriers dans leur jour UTC. Un jour local
     * peut donc commencer dans la veille UTC ou finir dans le lendemain UTC.
     */
    private fun providerDatesForLocalDate(target: LocalDate): List<LocalDate> {
        val zone = ZoneId.systemDefault()
        val offsetSeconds = target.atStartOfDay(zone).offset.totalSeconds
        return when {
            offsetSeconds > 0 -> listOf(target.minusDays(1), target)
            offsetSeconds < 0 -> listOf(target, target.plusDays(1))
            else -> listOf(target)
        }
    }

    private suspend fun fetchMergedRange(
        start: LocalDate,
        end: LocalDate,
        includeApiFootball: Boolean
    ): Map<String, List<Match>> = coroutineScope {
        val allDates = datesBetween(start, end).map { it.toString() }
        val apiDates = datesBetween(start, end).filter {
            includeApiFootball && apiFootballApi != null && it.usesApiFootball()
        }
        val apiResult = runCatching { fetchFromApiFootball(apiDates) }
            .onFailure { Log.w(TAG, "api-football daily fetch failed: ${it.message}") }
        if (apiDates.isNotEmpty() && apiResult.isFailure) {
            throw IllegalStateException(
                "API-FOOTBALL est indisponible",
                apiResult.exceptionOrNull()
            )
        }
        val apiMatches = apiResult.getOrDefault(emptyList())

        Log.d(
            TAG,
            "range $start..$end: api-football=${apiMatches.size}, " +
                "eligibleDates=${apiDates.joinToString()}"
        )

        val merged = allDates.associateWith { day ->
            val localDay = LocalDate.parse(day)
            apiMatches.filter { it.localCalendarDate() == localDay }
        }
        val enriched = enrichWithKnownLogos(merged.values.flatten())
            .groupBy { it.localCalendarDate()?.toString() }
        allDates.associateWith { enriched[it].orEmpty().sortedBy { match -> match.utcDate } }
    }

    @Synchronized
    private fun persistDate(date: String, matches: List<Match>) {
        val expectedDate = runCatching { LocalDate.parse(date) }.getOrNull()
        val sorted = matches
            .filter { match ->
                expectedDate == null || match.localCalendarDate() == expectedDate
            }
            .sortedBy { match -> match.utcDate }
        dateCache[date] = sorted
        matchCache.save(date, sorted)
    }

    private fun datesBetween(start: LocalDate, end: LocalDate): List<LocalDate> {
        if (end.isBefore(start)) return emptyList()
        return generateSequence(start) { current ->
            current.plusDays(1).takeUnless { it.isAfter(end) }
        }.toList()
    }

    fun clearCache() {
        dateCache.clear()
        openFootballSeasonCache.clear()
        teamNamesByLeagueSeasonCache.clear()
        matchCache.clear()
    }

    // === Settings : countries + leagues (API-FOOTBALL) ===

    @Volatile private var countriesCache: List<ApiFootballCountryDto>? = null
    private val leaguesByCountryCache = ConcurrentHashMap<String, List<ApiFootballLeagueEntryDto>>()
    private val teamNamesByLeagueSeasonCache = ConcurrentHashMap<String, Set<String>>()

    suspend fun getAllCountries(): List<ApiFootballCountryDto> = withContext(Dispatchers.IO) {
        countriesCache?.let { return@withContext it }
        val remote = if (apiFootballApi != null) {
            runCatching {
                apiFootballApi.getCountries().response
            }.onFailure {
                Log.w(TAG, "getCountries failed: ${it.message}")
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        val result = remote.ifEmpty { countriesFromCachedMatches() }
            .sortedBy { it.name }
        if (result.isNotEmpty()) {
            countriesCache = result
        }
        result
    }

    private fun countriesFromCachedMatches(): List<ApiFootballCountryDto> =
        dateCache.values
            .flatten()
            .filter { it.source == OFFICIAL_MATCH_SOURCE }
            .mapNotNull { match ->
                match.settingsCompetitionCountry()?.let { country -> country to match }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .map { (country, matches) ->
                val flag = matches.firstNotNullOfOrNull { it.competitionFlag }
                ApiFootballCountryDto(
                    name = country,
                    code = flag
                        ?.substringAfterLast("/")
                        ?.substringBefore(".")
                        ?.uppercase(),
                    flag = flag
                )
            }

    private fun leaguesFromCachedMatches(country: String): List<ApiFootballLeagueEntryDto> {
        val countryMatches = dateCache.values
            .flatten()
            .filter {
                it.source == OFFICIAL_MATCH_SOURCE &&
                    it.settingsCompetitionCountry() == country
            }
        val countryFlag = countryMatches.firstNotNullOfOrNull { it.competitionFlag }
        val countryDto = ApiFootballCountryDto(
            name = country,
            code = countryFlag
                ?.substringAfterLast("/")
                ?.substringBefore(".")
                ?.uppercase(),
            flag = countryFlag
        )
        return countryMatches
            .distinctBy { it.competitionId to it.competitionName }
            .map { match ->
                val lowerName = match.competitionName.lowercase()
                ApiFootballLeagueEntryDto(
                    league = com.football.footballapp.data.ApiFootballLeagueInfoDto(
                        id = match.competitionId ?: match.competitionName.hashCode(),
                        name = match.competitionName,
                        type = if (
                            "cup" in lowerName ||
                            "friendl" in lowerName ||
                            "trophy" in lowerName ||
                            "shield" in lowerName
                        ) {
                            "Cup"
                        } else {
                            "League"
                        },
                        logo = match.competitionEmblem
                    ),
                    country = countryDto
                )
            }
            .sortedBy { it.league.name.lowercase() }
    }

    suspend fun getLeaguesForCountry(country: String): List<ApiFootballLeagueEntryDto> = withContext(Dispatchers.IO) {
        leaguesByCountryCache[country]?.let { return@withContext it }
        val remote = if (apiFootballApi != null) {
            runCatching {
                apiFootballApi.getLeaguesByCountry(country).response
            }.onFailure {
                Log.w(TAG, "getLeaguesByCountry($country) failed: ${it.message}")
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        val result = remote.ifEmpty { leaguesFromCachedMatches(country) }
        if (result.isNotEmpty()) {
            leaguesByCountryCache[country] = result
        }
        result
    }

    suspend fun getTopDivisionCatalogs(
        countries: Set<String>
    ): Map<String, TopDivisionCatalog> = withContext(Dispatchers.IO) {
        if (countries.isEmpty()) return@withContext emptyMap()

        coroutineScope {
            countries.map { country ->
                async {
                    if (country == "World") return@async null
                    runCatching {
                        val footballDataCatalog = getFootballDataTopDivisionCatalog(country)
                        if (footballDataCatalog != null) {
                            Log.d(
                                TAG,
                                "top division $country: football-data teams=" +
                                    footballDataCatalog.teamNames.size
                            )
                            return@runCatching (country to footballDataCatalog)
                        }
                        val api = apiFootballApi ?: return@runCatching null

                        val leagues = getLeaguesForCountry(country)
                            .filter { it.isSeniorDomesticLeague() }
                        val rankedLeagues = leagues
                            .sortedWith(
                                compareByDescending<ApiFootballLeagueEntryDto> {
                                    topDivisionNameScore(it.league.name)
                                }.thenBy { it.league.id }
                            )
                        val topDivision = rankedLeagues.firstOrNull {
                            topDivisionNameScore(it.league.name) > 0
                        } ?: rankedLeagues.firstOrNull()
                            ?: return@runCatching null

                        val fallbackSeason = seasonForDate(LocalDate.now())
                            .substringBefore("-")
                            .toInt()
                        val seasonCandidates = buildList {
                            topDivision.seasons.firstOrNull { it.current }?.year?.let(::add)
                            addAll(topDivision.seasons.map { it.year }.sortedDescending())
                            add(fallbackSeason)
                            add(fallbackSeason - 1)
                        }.distinct().take(3)

                        for (season in seasonCandidates) {
                            val cacheKey = "${topDivision.league.id}-$season"
                            val cachedNames = teamNamesByLeagueSeasonCache[cacheKey]
                            val teamsForSeason = if (cachedNames == null) {
                                api.getTeamsByLeague(topDivision.league.id, season)
                                    .response
                            } else {
                                emptyList()
                            }
                            teamsForSeason.forEach { entry ->
                                entry.team.logo
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { logo ->
                                        fdTeamLogoCache[normalizeTeamName(entry.team.name)] = logo
                                    }
                            }
                            val namesForSeason = cachedNames ?: teamsForSeason
                                .filterNot {
                                    isReserveOrYouthTeamName(it.team.name) ||
                                        isWomenTeamName(it.team.name)
                                }
                                .map { normalizeTeamName(it.team.name) }
                                .filter { it.isNotBlank() }
                                .toSet()
                                .also { teamNamesByLeagueSeasonCache[cacheKey] = it }
                            Log.d(
                                TAG,
                                "top division $country: ${topDivision.league.name} " +
                                    "season=$season teams=${namesForSeason.size}"
                            )
                            if (namesForSeason.isNotEmpty()) {
                                val knownNames = namesForSeason + cachedCompetitionTeamNames(
                                    country = country,
                                    leagueName = topDivision.league.name
                                )
                                return@runCatching (
                                    country to TopDivisionCatalog(
                                        leagueName = topDivision.league.name,
                                        teamNames = knownNames
                                    )
                                )
                            }
                        }

                        null
                    }.onFailure {
                        Log.w(TAG, "top division teams for $country failed: ${it.message}")
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private suspend fun getFootballDataTopDivisionCatalog(
        country: String
    ): TopDivisionCatalog? {
        val competition = when (country) {
            "England" -> "PL" to "Premier League"
            "Spain" -> "PD" to "La Liga"
            "Germany" -> "BL1" to "Bundesliga"
            "Italy" -> "SA" to "Serie A"
            "France" -> "FL1" to "Ligue 1"
            else -> return null
        }
        val fdApi = footballDataApi ?: return null

        val normalizedNames = fdTopDivisionTeamNamesByCountry[country] ?: runCatching {
            fdApi.getCompetitionTeams(competition.first)
                .teams
                .orEmpty()
                .filterNot { team ->
                    team.name?.let { name ->
                        isReserveOrYouthTeamName(name) || isWomenTeamName(name)
                    } == true
                }
                .flatMap { team ->
                    listOfNotNull(team.name, team.shortName)
                }
                .map(::normalizeTeamName)
                .filter { it.isNotBlank() }
                .toSet()
                .also { fdTopDivisionTeamNamesByCountry[country] = it }
        }.onFailure {
            Log.w(TAG, "football-data top division teams for $country failed: ${it.message}")
        }.getOrNull().orEmpty()

        return normalizedNames.takeIf { it.isNotEmpty() }?.let { names ->
            TopDivisionCatalog(
                leagueName = competition.second,
                teamNames = names + cachedCompetitionTeamNames(country, competition.second)
            )
        }
    }

    private fun cachedCompetitionTeamNames(
        country: String,
        leagueName: String
    ): Set<String> = dateCache.values
        .asSequence()
        .flatten()
        .filter { match ->
            match.competitionCountry == country &&
                match.competitionName.equals(leagueName, ignoreCase = true) &&
                !match.homeTeam.isReserveOrYouthTeam() &&
                !match.awayTeam.isReserveOrYouthTeam() &&
                !match.homeTeam.isWomenTeam() &&
                !match.awayTeam.isWomenTeam()
        }
        .flatMap { match ->
            (match.homeTeam.identityNames() + match.awayTeam.identityNames()).asSequence()
        }
        .toSet()

    private suspend fun fetchFromApiFootball(dates: List<LocalDate>): List<Match> =
        coroutineScope {
            dates.map { date ->
                async {
                    apiFootballApi!!.getFixtures(
                        date = date.toString(),
                        timezone = ZoneId.systemDefault().id
                    ).response.map { it.toDomain() }
                }
            }.awaitAll().flatten()
        }

    private suspend fun fetchFromFootballData(
        start: LocalDate,
        end: LocalDate
    ): List<Match> = coroutineScope {
        // football-data refuse les grandes périodes sur certains forfaits. Le découpage
        // reste utile pour les appels de plage, même si l'écran charge maintenant un jour.
        val queryStart = start.minusDays(1)
        val queryEnd = end.plusDays(1)
        generateSequence(queryStart) { chunkStart ->
            chunkStart.plusDays(7).takeUnless { it.isAfter(queryEnd) }
        }.map { chunkStart ->
            async {
                val chunkEnd = minOf(chunkStart.plusDays(6), queryEnd)
                runCatching {
                    footballDataApi!!
                        .getMatches(chunkStart.toString(), chunkEnd.toString())
                        .matches
                        .map { it.toDomain() }
                }.onFailure {
                    Log.w(
                        TAG,
                        "football-data chunk $chunkStart..$chunkEnd failed: ${it.message}"
                    )
                }.getOrDefault(emptyList())
            }
        }.toList().awaitAll().flatten()
    }

    /**
     * Charge les fichiers OpenFootball de la saison correspondant à la date
     * (lazy + cache par couple `<season>/<league>`). Filtre ensuite par date.
     */
    private suspend fun fetchFromOpenFootball(date: String): List<Match> = coroutineScope {
        val target = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@coroutineScope emptyList()
        val season = seasonForDate(target)
        if (target.isDuringWorldCup2026()) {
            val cacheKey = "worldcup/${target.year}"
            val worldCupMatches = openFootballSeasonCache.getOrPut(cacheKey) {
                fetchWorldCupSeason(target.year)
            }
            val filtered = worldCupMatches.filter { it.localCalendarDate() == target }
            Log.d(TAG, "openfootball worldcup-only returned ${filtered.size} matches for $date " +
                "(${worldCupMatches.size} in cache)")
            return@coroutineScope filtered
        }

        val leagueMatches = OPEN_FOOTBALL_LEAGUES.map { league ->
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

        val worldCupMatches = if (target.year == 2026) {
            listOf(async {
                val cacheKey = "worldcup/${target.year}"
                openFootballSeasonCache.getOrPut(cacheKey) {
                    fetchWorldCupSeason(target.year)
                }
            }).awaitAll().flatten()
        } else {
            emptyList()
        }

        val all = leagueMatches + worldCupMatches
        val filtered = all.filter { it.localCalendarDate() == target }
        Log.d(TAG, "openfootball ($season) returned ${filtered.size} matches for $date " +
            "(${all.size} in season cache)")
        filtered
    }

    private fun LocalDate.isDuringWorldCup2026(): Boolean {
        val firstMatch = LocalDate.of(2026, 6, 11)
        val finalMatch = LocalDate.of(2026, 7, 19)
        return this in firstMatch..finalMatch
    }

    private suspend fun fetchWorldCupSeason(year: Int): List<Match> {
        val season = year.toString()
        val liveMirror = runCatching {
            openFootballApi.getWorldCupSeasonLive(season).matches.orEmpty()
        }.onFailure {
            Log.w(TAG, "worldcup-live $season failed: ${it.message}")
        }.getOrNull().orEmpty()

        val upstream = if (liveMirror.isEmpty()) {
            runCatching {
                openFootballApi.getWorldCupSeason(season).matches.orEmpty()
            }.onFailure {
                Log.w(TAG, "openfootball worldcup $season failed: ${it.message}")
            }.getOrNull().orEmpty()
        } else {
            emptyList()
        }

        return (liveMirror.ifEmpty { upstream }).mapNotNull { it.toDomain(year) }
    }

    /** Saison footballistique européenne : août → mai. */
    private fun seasonForDate(date: LocalDate): String {
        val startYear = if (date.monthValue >= 7) date.year else date.year - 1
        val endShort = (startYear + 1) % 100
        return "$startYear-${"%02d".format(endShort)}"
    }

    private data class OpenLeague(val code: String, val name: String, val country: String)

    private companion object {
        /**
         * Jeux JSON officiellement publiés dans openfootball/football.json.
         * Une saison pas encore publiée renvoie simplement une liste vide pour ce code.
         */
        val OPEN_FOOTBALL_LEAGUES = listOf(
            OpenLeague("en.1", "Premier League", "England"),
            OpenLeague("en.2", "Championship", "England"),
            OpenLeague("en.3", "League One", "England"),
            OpenLeague("en.4", "League Two", "England"),
            OpenLeague("es.1", "La Liga", "Spain"),
            OpenLeague("es.2", "Segunda División", "Spain"),
            OpenLeague("de.1", "Bundesliga", "Germany"),
            OpenLeague("de.2", "2. Bundesliga", "Germany"),
            OpenLeague("de.3", "3. Liga", "Germany"),
            OpenLeague("it.1", "Serie A", "Italy"),
            OpenLeague("it.2", "Serie B", "Italy"),
            OpenLeague("fr.1", "Ligue 1", "France"),
            OpenLeague("fr.2", "Ligue 2", "France")
        )
    }

    private fun OpenFootballMatchDto.toDomain(league: OpenLeague): Match? {
        val d = date ?: return null
        val t1 = team1 ?: return null
        val t2 = team2 ?: return null
        val timePart = time?.takeIf { it.matches(Regex("""\d{1,2}:\d{2}""")) } ?: "00:00"
        val utc = "${d}T${timePart.padStart(5, '0')}:00Z"
        val ft = score?.ft
        val home = ft?.getOrNull(0)
        val away = ft?.getOrNull(1)
        val matchDate = runCatching { LocalDate.parse(d) }.getOrNull()
        val isPast = matchDate?.isBefore(LocalDate.now()) == true
        val status = when {
            home != null && away != null -> MatchStatus.FINISHED
            isPast -> MatchStatus.UNKNOWN
            else -> MatchStatus.SCHEDULED
        }
        return Match(
            id = ("${league.code}-$d-$t1-$t2").hashCode().toLong(),
            utcDate = utc,
            status = status,
            statusLabel = when (status) {
                MatchStatus.FINISHED -> "Terminé"
                MatchStatus.UNKNOWN -> "Score indisponible"
                else -> "À venir"
            },
            competitionName = league.name,
            competitionCountry = league.country,
            homeTeam = Team(id = 0, name = t1),
            awayTeam = Team(id = 0, name = t2),
            score = Score(home, away),
            source = "openfootball"
        )
    }

    private fun WorldCupMatchDto.toDomain(year: Int): Match? {
        val d = date ?: return null
        val t1 = team1 ?: return null
        val t2 = team2 ?: return null
        val utc = parseWorldCupUtc(d, time)
        val ft = score?.ft
        val home = ft?.getOrNull(0)
        val away = ft?.getOrNull(1)
        val matchDate = runCatching { LocalDate.parse(d) }.getOrNull()
        val isPast = matchDate?.isBefore(LocalDate.now()) == true
        val status = when {
            home != null && away != null -> MatchStatus.FINISHED
            isPast -> MatchStatus.UNKNOWN
            else -> MatchStatus.SCHEDULED
        }
        return Match(
            id = ("worldcup-$year-$d-$t1-$t2").hashCode().toLong(),
            utcDate = utc,
            status = status,
            statusLabel = when (status) {
                MatchStatus.FINISHED -> "Terminé"
                MatchStatus.UNKNOWN -> "Score indisponible"
                else -> "À venir"
            },
            competitionName = "FIFA World Cup",
            competitionCountry = "World",
            homeTeam = Team(id = 0, name = t1),
            awayTeam = Team(id = 0, name = t2),
            score = Score(home, away),
            source = "openfootball-worldcup"
        )
    }

    private fun parseWorldCupUtc(date: String, time: String?): String {
        val timePart = time?.substringBefore("UTC")?.trim()?.takeIf { it.matches(Regex("\\d{1,2}:\\d{2}")) } ?: "00:00"
        val rawOffset = time?.substringAfter("UTC", "")?.trim()?.replace(" ", "") ?: ""
        val offset = when {
            rawOffset.matches(Regex("[+-]\\d{1,2}")) -> {
                val sign = rawOffset.first()
                val digits = rawOffset.drop(1).padStart(2, '0')
                "$sign$digits:00"
            }
            rawOffset.matches(Regex("[+-]\\d{1,2}:\\d{2}")) -> rawOffset
            else -> "+00:00"
        }
        return "${date}T${timePart}:00$offset"
    }

    /**
     * Fusionne deux listes de matchs. La source primaire donne la couverture de base,
     * la source secondaire complète les scores, logos et identifiants officiels.
     * Clé d'identité : paire d'équipes normalisée, indépendante de l'ordre.
     * Certaines sources inversent domicile/extérieur pour les matchs amicaux.
     */
    private fun mergeMatches(
        primary: List<Match>,
        secondary: List<Match>,
        includeUnmatchedSecondary: Boolean = true
    ): List<Match> {
        fun key(m: Match): String = listOf(
            normalizeTeamName(m.homeTeam.name),
            normalizeTeamName(m.awayTeam.name)
        ).sorted().joinToString("|")

        val byKey = linkedMapOf<String, Match>()
        for (m in primary) {
            val k = key(m)
            val existing = byKey[k]
            if (existing == null) {
                byKey[k] = m
            } else {
                byKey[k] = mergeMatch(existing, m)
            }
        }
        for (m in secondary) {
            val k = key(m)
            val existing = byKey[k]
            if (existing != null) {
                byKey[k] = mergeMatch(existing, m)
            } else if (includeUnmatchedSecondary) {
                byKey[k] = m
            }
        }
        return byKey.values.sortedBy { it.utcDate }
    }

    private fun mergeMatch(existing: Match, incomingRaw: Match): Match {
        val incoming = incomingRaw.alignedWith(existing)
        val existingHasScore = existing.score.home != null && existing.score.away != null
        val incomingHasScore = incoming.score.home != null && incoming.score.away != null
        val existingHasLogos = !existing.homeTeam.logoUrl.isNullOrBlank() && !existing.awayTeam.logoUrl.isNullOrBlank()
        val incomingHasLogos = !incoming.homeTeam.logoUrl.isNullOrBlank() && !incoming.awayTeam.logoUrl.isNullOrBlank()
        val base = if (incomingHasLogos && !existingHasLogos) incoming else existing
        val score = when {
            incomingHasScore && !existingHasScore -> incoming.score
            else -> base.score
        }
        val status = when {
            incoming.status == MatchStatus.FINISHED || existing.status == MatchStatus.FINISHED -> MatchStatus.FINISHED
            incoming.status == MatchStatus.LIVE || existing.status == MatchStatus.LIVE -> MatchStatus.LIVE
            incoming.status == MatchStatus.HALF_TIME || existing.status == MatchStatus.HALF_TIME -> MatchStatus.HALF_TIME
            else -> base.status
        }
        val statusLabel = when (status) {
            MatchStatus.FINISHED -> "Terminé"
            MatchStatus.LIVE -> listOf(incoming, existing)
                .firstOrNull { it.status == MatchStatus.LIVE }
                ?.statusLabel
                ?.takeIf { it.isNotBlank() && !it.equals("À venir", ignoreCase = true) }
                ?: "Live"
            MatchStatus.HALF_TIME -> "Mi-temps"
            else -> base.statusLabel
        }
        return base.copy(
            score = score,
            status = status,
            statusLabel = statusLabel,
            minute = if (
                status == MatchStatus.LIVE || status == MatchStatus.HALF_TIME
            ) {
                listOfNotNull(existing.minute, incoming.minute).maxOrNull()
            } else {
                null
            },
            competitionCountry = base.competitionCountry
                ?: existing.competitionCountry
                ?: incoming.competitionCountry,
            competitionEmblem = base.competitionEmblem ?: existing.competitionEmblem ?: incoming.competitionEmblem,
            competitionFlag = base.competitionFlag ?: existing.competitionFlag ?: incoming.competitionFlag,
            homeTeam = base.homeTeam.copy(
                logoUrl = base.homeTeam.logoUrl ?: existing.homeTeam.logoUrl ?: incoming.homeTeam.logoUrl
            ),
            awayTeam = base.awayTeam.copy(
                logoUrl = base.awayTeam.logoUrl ?: existing.awayTeam.logoUrl ?: incoming.awayTeam.logoUrl
            ),
            tvSourceUrl = base.tvSourceUrl ?: existing.tvSourceUrl ?: incoming.tvSourceUrl
        )
    }

    private fun Match.alignedWith(reference: Match): Match {
        val home = normalizeTeamName(homeTeam.name)
        val away = normalizeTeamName(awayTeam.name)
        val referenceHome = normalizeTeamName(reference.homeTeam.name)
        val referenceAway = normalizeTeamName(reference.awayTeam.name)
        val isReversed = home == referenceAway && away == referenceHome
        if (!isReversed) return this
        return copy(
            homeTeam = awayTeam,
            awayTeam = homeTeam,
            score = Score(home = score.away, away = score.home)
        )
    }

    fun enrichWithKnownLogos(matches: List<Match>): List<Match> {
        // Cross-référence : si une équipe a un logo dans un match du batch (ex. via
        // football-data.org) et apparaît sans logo dans un autre (ex. openfootball),
        // on partage le logo entre les deux.
        val localKnown: Map<String, String> = matches.flatMap { listOf(it.homeTeam, it.awayTeam) }
            .filter { !it.logoUrl.isNullOrBlank() }
            .associate { normalizeTeamName(it.name) to it.logoUrl!! }

        fun logoFor(team: Team): String? {
            if (!team.logoUrl.isNullOrBlank()) return team.logoUrl
            val keys = listOfNotNull(team.name, team.shortName)
                .map(::normalizeTeamName)
                .filter { it.isNotBlank() }
            keys.firstNotNullOfOrNull { localKnown[it] }?.let { return it }
            return keys.firstNotNullOfOrNull { fdTeamLogoCache[it] }
        }

        return matches.map { match ->
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

private fun ApiFootballLeagueEntryDto.isSeniorDomesticLeague(): Boolean {
    if (!league.type.equals("League", ignoreCase = true)) return false
    val name = league.name.lowercase()
    val excludedMarkers = listOf(
        "women", "fémin", "feminin", "femenin", "u17", "u18", "u19", "u20", "u21",
        "u23", "youth", "reserve", "development", "academy", "premier league 2"
    )
    return excludedMarkers.none { it in name }
}

private fun topDivisionNameScore(name: String): Int {
    val normalized = name.lowercase()
        .replace('é', 'e')
        .replace('è', 'e')
        .replace('í', 'i')
        .replace('ó', 'o')

    val topDivisionMarkers = listOf(
        "premier league", "premiership", "la liga", "primera division", "primeira liga",
        "liga portugal",
        "first division", "1st division", "division 1", "ligue 1", "serie a",
        "bundesliga", "eredivisie", "super lig", "superliga", "liga mx",
        "a-league", "major league soccer", "pro league", "j1 league",
        "k league 1", "eliteserien", "allsvenskan", "ekstraklasa"
    )
    val lowerDivisionMarkers = listOf(
        "second division", "2nd division", "division 2", "ligue 2", "liga 2",
        "serie b", "2. bundesliga", "league one", "league two", "championship"
    )

    return topDivisionMarkers.count { it in normalized } * 100 -
        lowerDivisionMarkers.count { it in normalized } * 100
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

private fun ScrapedMatchDto.toDomain(): Match? {
    val kickoff = utcDate?.takeIf { it.isNotBlank() } ?: return null
    val identity = "$date|$home|$away"
    val unsignedIdentityHash = identity.hashCode().toLong() and 0xffffffffL
    val rawCompetition = league?.takeIf { it.isNotBlank() && it != "Unknown" }
        ?: "Autres compétitions"
    val competition = normalizeRenderCompetition(rawCompetition)
    val matchStatus = MatchStatus.fromRaw(status)

    return Match(
        id = 8_000_000_000L + unsignedIdentityHash,
        utcDate = kickoff,
        status = matchStatus,
        statusLabel = statusLabel?.takeIf { it.isNotBlank() } ?: when (matchStatus) {
            MatchStatus.LIVE -> "Live"
            MatchStatus.HALF_TIME -> "Mi-temps"
            MatchStatus.FINISHED -> "Terminé"
            else -> "À venir"
        },
        minute = minute.takeIf {
            matchStatus == MatchStatus.LIVE || matchStatus == MatchStatus.HALF_TIME
        },
        competitionId = competition.id,
        competitionName = competition.name,
        competitionCountry = competition.country,
        competitionEmblem = competition.emblem,
        homeTeam = Team(
            id = home.hashCode().ushr(1),
            name = home
        ),
        awayTeam = Team(
            id = away.hashCode().ushr(1),
            name = away
        ),
        score = Score(homeScore, awayScore),
        source = "tv-scraper",
        tvSourceUrl = sourceUrl
    )
}

private data class RenderCompetition(
    val id: Int,
    val name: String,
    val country: String?,
    val emblem: String?
)

private fun normalizeRenderCompetition(
    rawName: String,
    rawCountry: String? = null
): RenderCompetition {
    val separator = " - "
    val explicitCountry = rawName.substringBefore(separator, missingDelimiterValue = "")
        .trim()
        .takeIf { it.isNotBlank() }
    val name = if (explicitCountry != null) {
        rawName.substringAfter(separator).trim()
    } else {
        rawName.trim()
    }
    val country = when {
        name.contains("friendl", ignoreCase = true) -> "World"
        name.contains("uefa", ignoreCase = true) -> "World"
        name.contains("fifa", ignoreCase = true) -> "World"
        !rawCountry.isNullOrBlank() -> rawCountry
        explicitCountry != null -> explicitCountry
        name.equals("Premier League", ignoreCase = true) -> "England"
        name.equals("La Liga", ignoreCase = true) -> "Spain"
        name.equals("Serie A", ignoreCase = true) -> "Italy"
        name.equals("Bundesliga", ignoreCase = true) -> "Germany"
        name.equals("Ligue 1", ignoreCase = true) -> "France"
        else -> null
    }
    val emblem = when {
        country == "England" && name.equals("Premier League", ignoreCase = true) -> 39
        country == "France" && name.equals("Ligue 1", ignoreCase = true) -> 61
        country == "Germany" && name.equals("Bundesliga", ignoreCase = true) -> 78
        country == "Italy" && name.equals("Serie A", ignoreCase = true) -> 135
        country == "Spain" && name.equals("La Liga", ignoreCase = true) -> 140
        country == "Tunisia" && name.equals("Ligue 1", ignoreCase = true) -> 202
        else -> null
    }?.let { leagueId ->
        "https://media.api-sports.io/football/leagues/$leagueId.png"
    }
    return RenderCompetition(
        id = "$country|$name".hashCode(),
        name = name,
        country = country,
        emblem = emblem
    )
}

private fun Match.normalizeRenderCompetition(): Match {
    if (source != RENDER_MATCH_SOURCE) return this
    val competition = normalizeRenderCompetition(competitionName, competitionCountry)
    return copy(
        competitionId = competition.id,
        competitionName = competition.name,
        competitionCountry = competition.country,
        competitionEmblem = competitionEmblem ?: competition.emblem
    )
}

private fun EspnEventDto.toDomain(competitionName: String): Match? {
    val competition = competitions.orEmpty().firstOrNull() ?: return null
    val home = competition.competitors.orEmpty()
        .firstOrNull { it.homeAway.equals("home", ignoreCase = true) }
        ?: return null
    val away = competition.competitors.orEmpty()
        .firstOrNull { it.homeAway.equals("away", ignoreCase = true) }
        ?: return null
    val homeName = home.team.displayName ?: home.team.shortDisplayName ?: return null
    val awayName = away.team.displayName ?: away.team.shortDisplayName ?: return null
    val statusType = competition.status?.type
    val status = when {
        statusType?.completed == true -> MatchStatus.FINISHED
        statusType?.state.equals("in", ignoreCase = true) -> MatchStatus.LIVE
        statusType?.name.equals("STATUS_HALFTIME", ignoreCase = true) -> MatchStatus.HALF_TIME
        else -> MatchStatus.SCHEDULED
    }
    val hasScore = status == MatchStatus.FINISHED ||
        status == MatchStatus.LIVE ||
        status == MatchStatus.HALF_TIME
    val elapsedMinute = competition.status?.displayClock
        ?.substringBefore("'")
        ?.trim()
        ?.toIntOrNull()
        ?: statusType?.shortDetail
            ?.substringBefore("'")
            ?.trim()
            ?.toIntOrNull()
        ?: competition.status?.clock
            ?.div(60.0)
            ?.toInt()

    fun teamId(raw: String?, name: String): Int =
        raw?.toIntOrNull() ?: name.hashCode().ushr(1)

    return Match(
        id = id.toLongOrNull()
            ?: (7_000_000_000L + (id.hashCode().toLong() and 0xffffffffL)),
        utcDate = date,
        status = status,
        statusLabel = when (status) {
            MatchStatus.FINISHED -> "Terminé"
            MatchStatus.LIVE -> "En direct"
            MatchStatus.HALF_TIME -> "Mi-temps"
            else -> "À venir"
        },
        minute = elapsedMinute.takeIf {
            status == MatchStatus.LIVE || status == MatchStatus.HALF_TIME
        },
        competitionId = competitionName.hashCode(),
        competitionName = competitionName,
        competitionCountry = "World",
        homeTeam = Team(
            id = teamId(home.team.id, homeName),
            name = homeName,
            shortName = home.team.abbreviation,
            logoUrl = home.team.logo?.takeIf { it.isNotBlank() }
        ),
        awayTeam = Team(
            id = teamId(away.team.id, awayName),
            name = awayName,
            shortName = away.team.abbreviation,
            logoUrl = away.team.logo?.takeIf { it.isNotBlank() }
        ),
        score = Score(
            home = home.score?.toIntOrNull().takeIf { hasScore },
            away = away.score?.toIntOrNull().takeIf { hasScore }
        ),
        source = "espn"
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

/** Même convention pour les données neuves et les anciens fichiers de cache. */
private fun Match.settingsCompetitionCountry(): String? = when {
    competitionName.contains("friendl", ignoreCase = true) -> "World"
    competitionName.contains("uefa", ignoreCase = true) -> "World"
    competitionName.contains("fifa", ignoreCase = true) -> "World"
    else -> competitionCountry
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
