package com.example.simpleiptv.data

import com.example.simpleiptv.data.local.entities.CategoryEntity
import org.junit.Test
import org.junit.Assert.*

class CategoryEntityTest {

    @Test
    fun testCategoryEntityCreation() {
        val category = CategoryEntity(
            category_id = "1",
            category_name = "Sports",
            profileId = 1,
            type = "LIVE",
            sortOrder = 0
        )

        assertEquals("1", category.category_id)
        assertEquals("Sports", category.category_name)
        assertEquals(1, category.profileId)
        assertEquals("LIVE", category.type)
        assertEquals(0, category.sortOrder)
    }

    @Test
    fun testVodCategory() {
        val vodCategory = CategoryEntity(
            category_id = "movies",
            category_name = "Movies",
            profileId = 1,
            type = "VOD",
            sortOrder = 5
        )

        assertEquals("VOD", vodCategory.type)
        assertEquals(5, vodCategory.sortOrder)
    }

    @Test
    fun testCategoryWithSeparatorPrefix() {
        val separator = CategoryEntity(
            category_id = "-",
            category_name = "- - - -",
            profileId = 1,
            type = "LIVE",
            sortOrder = 0
        )

        assertTrue(separator.category_name.startsWith("-"))
    }
}