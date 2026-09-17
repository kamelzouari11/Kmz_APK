package com.example.myiptv.data

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object EpgSearch {
    val tunis: ZoneId = ZoneId.of("Africa/Tunis")
    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    fun words(value: String): List<String> = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(marks, "").lowercase(Locale.ROOT)
        .split(separators).filter(String::isNotBlank)
        .map { when (it) { "internazionale" -> "inter"; "milano" -> "milan"; else -> it } }
    fun searchable(value: String) = " " + words(value).joinToString(" ") + " "
}

data class EpgSearchResult(
    val id: Long,
    val title: String,
    val description: String,
    val start: Long,
    val stop: Long,
    val channels: List<SavedChannel>,
) {
    val timeRange: String get() {
        val format = DateTimeFormatter.ofPattern("HH:mm").withZone(EpgSearch.tunis)
        return "${format.format(Instant.ofEpochSecond(start))}–${format.format(Instant.ofEpochSecond(stop))}"
    }
}

data class EpgSearchPage(
    val results: List<EpgSearchResult>,
    val syncedAt: Long,
    val coverage: String,
    val cacheBytes: Long,
)

/** Options arrive in provider order; partitioning does not alphabetize either group. */
data class EpgCategoryOption(
    val countryCode: String,
    val id: String,
    val name: String,
    val epgChannels: Int,
    val channels: List<SavedChannel>,
) {
    val key: String get() = "$countryCode\u0000$id"
}

data class EpgCountryOption(
    val code: String,
    val epgChannels: Int,
    val categories: List<EpgCategoryOption>,
)

data class EpgCountrySelection(
    val profileId: Int,
    val options: List<EpgCountryOption>,
    val selectedCountries: Set<String>,
    val selectedCategories: Set<String>,
) {
    /** Guide picker only: retain channels with an actual current or upcoming programme. */
    fun withAvailablePrograms(epgIds: Set<String>): EpgCountrySelection = copy(
        options = options.mapNotNull { country ->
            val categories = country.categories.mapNotNull { category ->
                val channels = category.channels.filter { it.epgChannelId?.trim() in epgIds }
                category.copy(channels = channels, epgChannels = channels.size)
                    .takeIf { channels.isNotEmpty() }
            }
            country.copy(categories = categories, epgChannels = categories.sumOf { it.epgChannels })
                .takeIf { categories.isNotEmpty() }
        },
    )

    val orderedOptions: List<EpgCountryOption> get() =
        options.filter { it.code in selectedCountries } +
            options.filterNot { it.code in selectedCountries }
    fun orderedCategories(country: EpgCountryOption): List<EpgCategoryOption> =
        country.categories.filter { it.key in selectedCategories } +
            country.categories.filterNot { it.key in selectedCategories }
    fun activeCategories(country: EpgCountryOption): List<EpgCategoryOption> =
        if (country.code in selectedCountries) {
            country.categories.filter { it.key in selectedCategories && it.epgChannels > 0 }
        } else {
            emptyList()
        }
    val activeOptions: List<EpgCountryOption> get() = orderedOptions.mapNotNull { country ->
        val categories = activeCategories(country)
        country.copy(
            epgChannels = categories.sumOf { it.epgChannels },
            categories = categories,
        ).takeIf { categories.isNotEmpty() }
    }
    val selectedCount: Int get() = options.count { it.code in selectedCountries }
    val selectedCategoryCount: Int get() = options.sumOf { country ->
        if (country.code in selectedCountries) {
            country.categories.count { it.key in selectedCategories }
        } else {
            0
        }
    }
    val hasEpgChannels: Boolean get() = activeOptions.isNotEmpty()
}

data class EpgGuidePage(
    val channel: SavedChannel,
    val programs: List<EpgSearchResult>,
    val syncedAt: Long,
    val coverage: String,
    val cacheBytes: Long,
)
