package com.example.myiptv.data

import org.junit.Assert.*
import org.junit.Test

class EpgCountrySelectionTest {
    private fun category(country: String, id: String, count: Int) = EpgCategoryOption(
        countryCode = country,
        id = id,
        name = id,
        epgChannels = count,
        channels = emptyList(),
    )

    private val options = listOf(
        EpgCountryOption("IT", 20, listOf(category("IT", "sport", 20))),
        EpgCountryOption("FR", 30, listOf(category("FR", "sport", 30))),
        EpgCountryOption("UK", 10, listOf(category("UK", "news", 10))),
        EpgCountryOption("ES", 0, listOf(category("ES", "empty", 0))),
    )

    @Test fun enabledCountriesComeFirstWithoutAlphabetizingEitherGroup() {
        val selection = EpgCountrySelection(
            3,
            options,
            setOf("ES", "FR"),
            setOf("FR\u0000sport", "ES\u0000empty"),
        )
        assertEquals(listOf("FR", "ES", "IT", "UK"), selection.orderedOptions.map { it.code })
        assertEquals(listOf("IT", "FR", "UK", "ES"), options.map { it.code })
        assertEquals(2, selection.selectedCategoryCount)
        assertEquals(listOf("FR"), selection.activeOptions.map { it.code })
    }

    @Test fun emptySelectionMeansNoCountriesRatherThanAllCountries() {
        val selection = EpgCountrySelection(3, options, emptySet(), emptySet())
        assertEquals(0, selection.selectedCount)
        assertFalse(selection.hasEpgChannels)
        assertTrue(selection.activeOptions.isEmpty())
        assertEquals(options, selection.orderedOptions)
    }

    @Test fun unavailableCountriesDoNotEnableSearchOrInflateCounts() {
        val selection = EpgCountrySelection(
            3,
            options,
            setOf("ES", "XX"),
            setOf("ES\u0000empty"),
        )
        assertEquals(1, selection.selectedCount)
        assertEquals(1, selection.selectedCategoryCount)
        assertFalse(selection.hasEpgChannels)
        assertTrue(
            selection.copy(
                selectedCountries = setOf("FR"),
                selectedCategories = setOf("FR\u0000sport"),
            ).hasEpgChannels,
        )
    }

    @Test fun enabledCategoriesComeFirstAndKeepProviderOrder() {
        val categories = listOf(
            category("FR", "general", 5),
            category("FR", "sport", 8),
            category("FR", "cinema", 4),
            category("FR", "news", 6),
        )
        val country = EpgCountryOption("FR", 23, categories)
        val selection = EpgCountrySelection(
            3,
            listOf(country),
            setOf("FR"),
            setOf("FR\u0000sport", "FR\u0000news"),
        )

        assertEquals(
            listOf("sport", "news", "general", "cinema"),
            selection.orderedCategories(country).map { it.id },
        )
    }

    @Test fun guidePickerRequiresProgramsAndPreservesVariantsAndFilters() {
        fun channel(id: Int, epgId: String?, category: String = "news") = SavedChannel(
            name = "FR channel $id", categoryId = category, categoryName = category,
            countryCode = "FR", iconUrl = null, extension = "ts", epgChannelId = epgId,
            categoryOrder = 0, providerOrder = id, streamId = id,
        )
        val channels = listOf(channel(1, " LCI.fr "), channel(2, "empty.fr"), channel(3, "LCI.fr"), channel(4, null))
        val news = EpgCategoryOption("FR", "news", "News", channels.size, channels)
        val other = EpgCategoryOption("FR", "other", "Other", 1, listOf(channel(5, "empty.fr", "other")))
        val original = EpgCountrySelection(
            3, listOf(EpgCountryOption("FR", 5, listOf(news, other))),
            setOf("FR"), setOf("FR\u0000news"),
        )
        val filtered = original.withAvailablePrograms(setOf("LCI.fr", "unknown.fr"))
        assertEquals(listOf(1, 3), filtered.activeOptions.single().categories.single().channels.map { it.streamId })
        assertEquals(2, filtered.activeOptions.single().epgChannels)
        assertEquals(original.selectedCategories, filtered.selectedCategories)
        assertEquals(5, original.options.single().epgChannels)
        assertFalse(original.withAvailablePrograms(emptySet()).hasEpgChannels)
        assertFalse(filtered.copy(selectedCountries = emptySet()).hasEpgChannels)
    }
}
