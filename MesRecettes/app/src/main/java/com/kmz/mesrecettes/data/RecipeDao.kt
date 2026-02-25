package com.kmz.mesrecettes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertIngredient(ingredient: Ingredient): Long

        @Query(
                "SELECT * FROM ingredients WHERE name LIKE '%' || :query || '%' ORDER BY useCount DESC LIMIT 10"
        )
        fun searchIngredients(query: String): Flow<List<Ingredient>>

        @Query("SELECT * FROM ingredients WHERE LOWER(name) = LOWER(:name) LIMIT 1")
        suspend fun getIngredientByName(name: String): Ingredient?

        @Query("SELECT * FROM ingredients ORDER BY useCount DESC")
        fun getAllIngredients(): Flow<List<Ingredient>>

        @Update suspend fun updateIngredient(ingredient: Ingredient)

        @Query("SELECT * FROM ingredients") suspend fun getAllIngredientsSync(): List<Ingredient>

        @Query(
                "UPDATE recipe_ingredients SET ingredientId = :newIngredientId WHERE ingredientId = :oldIngredientId"
        )
        suspend fun updateRecipeIngredientId(oldIngredientId: Long, newIngredientId: Long)

        @Query("DELETE FROM ingredients WHERE id = :ingredientId")
        suspend fun deleteIngredient(ingredientId: Long)

        @Query(
                "DELETE FROM ingredients WHERE id NOT IN (SELECT DISTINCT ingredientId FROM recipe_ingredients)"
        )
        suspend fun deleteUnusedIngredients()

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertRecipe(recipe: Recipe): Long

        @Update suspend fun updateRecipe(recipe: Recipe)

        @Delete suspend fun deleteRecipe(recipe: Recipe)

        @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertBase(base: Base): Long

        @Query("SELECT * FROM bases ORDER BY name ASC") fun getBases(): Flow<List<Base>>

        @Query("SELECT * FROM bases") suspend fun getAllBasesSync(): List<Base>

        @Query("UPDATE recipes SET baseId = :newBaseId WHERE baseId = :oldBaseId")
        suspend fun updateRecipeBase(oldBaseId: Long, newBaseId: Long)

        @Query("DELETE FROM bases WHERE id = :baseId") suspend fun deleteBase(baseId: Long)

        @Query("DELETE FROM bases WHERE id NOT IN (SELECT DISTINCT baseId FROM recipes)")
        suspend fun deleteUnusedBases()

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertIngredientGroup(group: IngredientGroup): Long

        @Query("SELECT * FROM ingredient_groups ORDER BY name ASC")
        fun getIngredientGroups(): Flow<List<IngredientGroup>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertRecipeIngredient(recipeIngredient: RecipeIngredient)

        @Delete suspend fun deleteRecipeIngredient(recipeIngredient: RecipeIngredient)

        @Transaction
        @Query("SELECT * FROM recipes ORDER BY id DESC")
        fun getRecipes(): Flow<List<RecipeWithBase>>

        @Query(
                """
        SELECT ri.recipeId, ri.ingredientId, ri.quantity, ri.unit, 
               i.name as ingredientName, i.groupId as ingredientGroupId 
        FROM recipe_ingredients ri 
        INNER JOIN ingredients i ON ri.ingredientId = i.id 
        WHERE ri.recipeId = :recipeId
    """
        )
        fun getRecipeIngredients(recipeId: Long): Flow<List<RecipeIngredientFull>>

        @Query("UPDATE ingredients SET useCount = useCount + 1 WHERE id = :id")
        suspend fun incrementIngredientUseCount(id: Long)

        @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
        suspend fun deleteIngredientsForRecipe(recipeId: Long)
}

data class RecipeWithBase(
        @Embedded val recipe: Recipe,
        @Relation(parentColumn = "baseId", entityColumn = "id") val base: Base
)

data class RecipeIngredientFull(
        val recipeId: Long,
        val ingredientId: Long,
        val quantity: String,
        val unit: String,
        val ingredientName: String,
        val ingredientGroupId: Long? // null = No group
)
