package com.kmz.mesrecettes.data

import androidx.room.*

@Entity(tableName = "bases")
data class Base(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "ingredient_groups")
data class IngredientGroup(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(
        tableName = "ingredients",
        foreignKeys =
                [
                        ForeignKey(
                                entity = IngredientGroup::class,
                                parentColumns = ["id"],
                                childColumns = ["groupId"],
                                onDelete = ForeignKey.SET_NULL
                        )],
        indices = [Index("groupId"), Index(value = ["name"], unique = true)]
)
data class Ingredient(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val name: String,
        val groupId: Long? = null,
        val useCount: Int = 0
)

@Entity(
        tableName = "recipes",
        foreignKeys =
                [
                        ForeignKey(
                                entity = Base::class,
                                parentColumns = ["id"],
                                childColumns = ["baseId"],
                                onDelete = ForeignKey.RESTRICT
                        )],
        indices = [Index("baseId")]
)
data class Recipe(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val title: String,
        val baseId: Long,
        val instructions: String
)

@Entity(
        tableName = "recipe_ingredients",
        primaryKeys = ["recipeId", "ingredientId"],
        foreignKeys =
                [
                        ForeignKey(
                                entity = Recipe::class,
                                parentColumns = ["id"],
                                childColumns = ["recipeId"],
                                onDelete = ForeignKey.CASCADE
                        ),
                        ForeignKey(
                                entity = Ingredient::class,
                                parentColumns = ["id"],
                                childColumns = ["ingredientId"],
                                onDelete = ForeignKey.CASCADE
                        )],
        indices = [Index("recipeId"), Index("ingredientId")]
)
data class RecipeIngredient(
        val recipeId: Long,
        val ingredientId: Long,
        val quantity: String,
        val unit: String
)
