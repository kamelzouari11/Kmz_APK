package com.kmz.shoppinglist.ui.screens

import com.kmz.shoppinglist.data.Category

/** Navigation screens */
sealed class Screen {
    object Categories : Screen()
    object AllArticles : Screen()
    object IconManager : Screen()
    data class CategoryArticles(val category: Category) : Screen()
}
