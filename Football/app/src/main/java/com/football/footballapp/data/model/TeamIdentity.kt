package com.football.footballapp.data.model

import java.text.Normalizer
import java.util.Locale

data class TopDivisionCatalog(
    val leagueName: String,
    val teamNames: Set<String>
)

private val RESERVE_TEAM_SUFFIXES = setOf(
    "ii", "iii", "iv", "b", "c", "2", "3",
    "reserve", "reserves", "youth", "academy",
    "castilla", "atletic", "futuro", "primavera"
)
private val YOUTH_TEAM_AGES = setOf("18", "19", "20", "21", "23")
private val YOUTH_TEAM_SUFFIX = Regex("""u(?:18|19|20|21|23)""")
private val WOMEN_TEAM_MARKERS = setOf(
    "women", "woman", "lady", "ladies", "frauen", "frau",
    "femme", "femmes", "feminin", "feminine", "feminines",
    "feminino", "feminina", "femenino", "femenina",
    "femminile", "donne", "dames", "wfc"
)
private val WOMEN_COMPETITION_MARKERS = setOf(
    "nwsl", "wsl", "uwcl", "damallsvenskan"
)

/** Identité stable d'un club, indépendante des identifiants propres à chaque API. */
fun normalizeTeamName(name: String): String {
    val ascii = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .replace(".", "")
        .replace(Regex("""[^a-z0-9]+"""), " ")
    val clubMarkers = setOf(
        "fc", "cf", "sc", "cs", "bc", "afc", "cfc", "ac", "as", "cd", "ud",
        "rc", "rcd", "club", "sportif"
    )
    val normalized = ascii.split(Regex("""\s+"""))
        .filter { it.isNotBlank() && it !in clubMarkers }
        .joinToString(" ")
    return when (normalized) {
        "psg", "paris sg" -> "paris saint germain"
        "olympique marseille", "olympique de marseille", "om" -> "marseille"
        "olympique lyonnais", "olympique lyon", "ol lyonnais", "ol" -> "lyon"
        "internazionale", "internazionale milano", "inter milan" -> "inter"
        "borussia m gladbach" -> "borussia monchengladbach"
        "bayern munich" -> "bayern munchen"
        "newcastle united" -> "newcastle"
        "leeds united" -> "leeds"
        "coventry city" -> "coventry"
        else -> normalized
    }
}

fun Team.identityNames(): Set<String> = listOfNotNull(name, shortName)
    .map(::normalizeTeamName)
    .filter { it.isNotBlank() }
    .toSet()

private const val FAVORITE_IDENTITY_PREFIX = "fav2|"

private data class FavoriteIdentity(
    val source: String,
    val teamId: Int,
    val logoUrl: String,
    val name: String
)

/**
 * Un nom seul n'est pas une identité : Arsenal (Angleterre) et Arsenal
 * (Belarus) doivent rester deux clubs différents.
 */
fun Team.favoriteIdentity(source: String): String = listOf(
    "fav2",
    source.trim().lowercase(Locale.ROOT).replace('|', '_'),
    id.toString(),
    logoUrl.orEmpty().trim().lowercase(Locale.ROOT).replace('|', '_'),
    normalizeTeamName(name).replace('|', '_')
).joinToString("|")

fun String.isStructuredFavoriteIdentity(): Boolean = startsWith(FAVORITE_IDENTITY_PREFIX)

fun Team.favoriteIdentitySignature(source: String): String {
    val logo = logoUrl.orEmpty().trim().lowercase(Locale.ROOT)
    return when {
        logo.isNotBlank() -> "logo:$logo"
        id > 0 -> "id:${source.trim().lowercase(Locale.ROOT)}:$id"
        else -> "name:${normalizeTeamName(name)}"
    }
}

fun Team.matchesFavorite(favorites: Set<String>, source: String): Boolean {
    if (favorites.isEmpty()) return false
    val aliases = identityNames()
    val normalizedSource = source.trim().lowercase(Locale.ROOT)
    val normalizedLogo = logoUrl.orEmpty().trim().lowercase(Locale.ROOT)

    return favorites.any { stored ->
        val identity = stored.toFavoriteIdentity()
        if (identity == null) {
            // Ancien format : conservé uniquement le temps de sa migration.
            stored in aliases
        } else {
            val sameLogo = normalizedLogo.isNotBlank() &&
                identity.logoUrl.isNotBlank() && normalizedLogo == identity.logoUrl
            val sameSourceId = id > 0 && identity.teamId > 0 &&
                normalizedSource == identity.source && id == identity.teamId
            when {
                sameLogo || sameSourceId -> true
                // Deux identifiants disponibles mais différents : même nom,
                // équipe différente.
                hasStableIdentity(normalizedSource, normalizedLogo) &&
                    identity.hasStableIdentity() -> false
                else -> identity.name in aliases
            }
        }
    }
}

private fun Team.hasStableIdentity(source: String, logo: String): Boolean =
    logo.isNotBlank() || (id > 0 && source.isNotBlank())

private fun FavoriteIdentity.hasStableIdentity(): Boolean =
    logoUrl.isNotBlank() || (teamId > 0 && source.isNotBlank())

private fun String.toFavoriteIdentity(): FavoriteIdentity? {
    if (!isStructuredFavoriteIdentity()) return null
    val parts = split('|', limit = 5)
    if (parts.size != 5) return null
    return FavoriteIdentity(
        source = parts[1],
        teamId = parts[2].toIntOrNull() ?: 0,
        logoUrl = parts[3],
        name = parts[4]
    )
}

/** Écarte les équipes réserves et de jeunes d'un catalogue de division élite. */
fun isReserveOrYouthTeamName(name: String): Boolean {
    val normalized = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
    val tokens = normalized.split(Regex("""\s+""")).filter { it.isNotBlank() }
    val suffix = tokens.lastOrNull() ?: return false
    val youthSuffix = suffix.matches(YOUTH_TEAM_SUFFIX)
    val underAge = tokens.windowed(2).any { (first, second) ->
        first == "under" && second in YOUTH_TEAM_AGES
    }
    val separatedYouthAge = tokens.windowed(2).any { (first, second) ->
        first == "u" && second in YOUTH_TEAM_AGES
    }
    val nextGen = tokens.takeLast(2) == listOf("next", "gen")
    return suffix in RESERVE_TEAM_SUFFIXES || youthSuffix || underAge ||
        separatedYouthAge || nextGen
}

fun Team.isReserveOrYouthTeam(): Boolean = listOfNotNull(name, shortName)
    .any(::isReserveOrYouthTeamName)

fun isWomenTeamName(name: String): Boolean {
    val normalized = Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
    val tokens = normalized.split(Regex("""\s+""")).filter { it.isNotBlank() }
    return tokens.any(WOMEN_TEAM_MARKERS::contains) || tokens.lastOrNull() == "w"
}

fun isWomenCompetitionName(name: String): Boolean {
    if (isWomenTeamName(name)) return true
    val normalized = normalizeTeamName(name)
    val tokens = normalized.split(Regex("""\s+""")).filter { it.isNotBlank() }
    return tokens.any(WOMEN_COMPETITION_MARKERS::contains) ||
        tokens.windowed(2).any { it == listOf("liga", "f") }
}

fun Team.isWomenTeam(): Boolean = listOfNotNull(name, shortName)
    .any(::isWomenTeamName)
