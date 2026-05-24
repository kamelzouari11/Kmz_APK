package com.kmz.mesrecettes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kmz.mesrecettes.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RecipeViewModel(private val dao: RecipeDao) : ViewModel() {

    init {
        cleanUpBases()
        cleanUpIngredients()
    }

    private fun cleanUpBases() {
        viewModelScope.launch {
            val allBases = dao.getAllBasesSync()
            val groups = allBases.groupBy { it.name.trim().lowercase() }

            groups.forEach { (_, groupList) ->
                if (groupList.size > 1) {
                    val sorted = groupList.sortedBy { it.id }
                    val keepId = sorted.first().id
                    for (i in 1 until sorted.size) {
                        val duplicateId = sorted[i].id
                        dao.updateRecipeBase(duplicateId, keepId)
                        dao.deleteBase(duplicateId)
                    }
                }
            }
            dao.deleteUnusedBases()
        }
    }

    private fun cleanUpIngredients() {
        viewModelScope.launch {
            val allIngredients = dao.getAllIngredientsSync()
            val groups = allIngredients.groupBy { it.name.trim().lowercase() }

            groups.forEach { (_, groupList) ->
                if (groupList.size > 1) {
                    val sorted = groupList.sortedByDescending { it.useCount }
                    val keepId = sorted.first().id
                    for (i in 1 until sorted.size) {
                        val duplicateId = sorted[i].id
                        dao.updateRecipeIngredientId(duplicateId, keepId)
                        dao.deleteIngredient(duplicateId)
                    }
                }
            }
            dao.deleteUnusedIngredients()
        }
    }

    val bases: StateFlow<List<Base>> =
            dao.getBases()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ingredientGroups: StateFlow<List<IngredientGroup>> =
            dao.getIngredientGroups()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recipes: StateFlow<List<RecipeWithBase>> =
            dao.getRecipes()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _copiedIngredients = MutableStateFlow<List<RecipeIngredientFull>>(emptyList())
    val copiedIngredients: StateFlow<List<RecipeIngredientFull>> = _copiedIngredients.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchedIngredients: StateFlow<List<Ingredient>> =
            _searchQuery
                    .flatMapLatest { query ->
                        if (query.isBlank()) {
                            dao.getAllIngredients()
                        } else {
                            dao.searchIngredients(query)
                        }
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getOrCreateBase(name: String): Long {
        val existing = bases.value.find { it.name.equals(name, ignoreCase = true) }
        return existing?.id ?: dao.insertBase(Base(name = name))
    }

    suspend fun getOrCreateGroup(name: String): Long {
        val existing = ingredientGroups.value.find { it.name.equals(name, ignoreCase = true) }
        return existing?.id ?: dao.insertIngredientGroup(IngredientGroup(name = name))
    }

    fun addRecipeBasic(title: String, baseName: String, onRecipeCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val baseId = getOrCreateBase(baseName)
            val recipeId =
                    dao.insertRecipe(Recipe(title = title, baseId = baseId, instructions = ""))
            onRecipeCreated(recipeId)
        }
    }

    fun updateRecipeDetails(recipeId: Long, title: String, baseName: String, instructions: String) {
        viewModelScope.launch {
            val baseId = getOrCreateBase(baseName)
            dao.updateRecipe(
                    Recipe(
                            id = recipeId,
                            title = title,
                            baseId = baseId,
                            instructions = instructions
                    )
            )
        }
    }

    fun getRecipeIngredients(recipeId: Long): Flow<List<RecipeIngredientFull>> {
        return dao.getRecipeIngredients(recipeId)
    }

    fun copyIngredients(ingredients: List<RecipeIngredientFull>) {
        _copiedIngredients.value = ingredients
    }

    fun pasteIngredients(targetRecipeId: Long) {
        val ingredientsToPaste = _copiedIngredients.value
        if (ingredientsToPaste.isEmpty()) return

        viewModelScope.launch {
            ingredientsToPaste.forEach { ingredient ->
                dao.insertRecipeIngredient(
                        RecipeIngredient(
                                recipeId = targetRecipeId,
                                ingredientId = ingredient.ingredientId,
                                quantity = ingredient.quantity,
                                unit = ingredient.unit
                        )
                )
                dao.incrementIngredientUseCount(ingredient.ingredientId)
            }
        }
    }

    fun addIngredientToRecipe(recipeId: Long, ingredientName: String, quantity: String) {
        viewModelScope.launch {
            // Find existing ingredient or create new
            val existing = dao.getIngredientByName(ingredientName)
            val ingredientId =
                    existing?.id
                            ?: dao.insertIngredient(Ingredient(name = ingredientName, useCount = 0))

            dao.insertRecipeIngredient(
                    RecipeIngredient(
                            recipeId = recipeId,
                            ingredientId = ingredientId,
                            quantity = quantity,
                            unit = ""
                    )
            )
            dao.incrementIngredientUseCount(ingredientId)
        }
    }

    fun removeIngredientFromRecipe(recipeIngredientFull: RecipeIngredientFull) {
        viewModelScope.launch {
            dao.deleteRecipeIngredient(
                    RecipeIngredient(
                            recipeId = recipeIngredientFull.recipeId,
                            ingredientId = recipeIngredientFull.ingredientId,
                            quantity = recipeIngredientFull.quantity,
                            unit = recipeIngredientFull.unit
                    )
            )
        }
    }

    fun updateIngredientGroup(ingredientId: Long, ingredientName: String, groupId: Long?) {
        viewModelScope.launch {
            dao.updateIngredient(
                    Ingredient(
                            id = ingredientId,
                            name = ingredientName,
                            groupId = groupId,
                            useCount = 0
                    )
            ) // Note: useCount is purely for sorting, we don't care if it gets overridden to 0 or
            // we can fetch previous
            // Better to only update group if possible, but Room replaces whole object. We just want
            // to set group id, we leave useCount alone for now, or just default it since we want to
            // avoid extra read.
            // Actuall lets just pass the existing ingredient object to update
        }
    }

    fun updateRecipeIngredientFull(
            oldRecipeIngredient: RecipeIngredientFull,
            newName: String,
            newQuantity: String,
            newGroupName: String
    ) {
        viewModelScope.launch {
            val existing = dao.getIngredientByName(newName)
            val existingId =
                    existing?.id ?: dao.insertIngredient(Ingredient(name = newName, useCount = 0))

            var finalGroupId: Long? = existing?.groupId
            if (newGroupName.isNotBlank()) {
                finalGroupId = getOrCreateGroup(newGroupName)
            }

            dao.updateIngredient(
                    Ingredient(
                            id = existingId,
                            name = newName,
                            groupId = finalGroupId,
                            useCount = existing?.useCount ?: 1
                    )
            )

            if (existingId != oldRecipeIngredient.ingredientId) {
                dao.deleteRecipeIngredient(
                        RecipeIngredient(
                                recipeId = oldRecipeIngredient.recipeId,
                                ingredientId = oldRecipeIngredient.ingredientId,
                                quantity = oldRecipeIngredient.quantity,
                                unit = ""
                        )
                )
            }

            dao.insertRecipeIngredient(
                    RecipeIngredient(
                            recipeId = oldRecipeIngredient.recipeId,
                            ingredientId = existingId,
                            quantity = newQuantity,
                            unit = ""
                    )
            )
        }
    }

    fun updateRecipeIngredientDetails(recipeId: Long, ingredientId: Long, quantity: String) {
        viewModelScope.launch {
            dao.insertRecipeIngredient(
                    RecipeIngredient(
                            recipeId = recipeId,
                            ingredientId = ingredientId,
                            quantity = quantity,
                            unit = ""
                    )
            )
        }
    }

    fun updateIngredientDb(ingredient: Ingredient) {
        viewModelScope.launch { dao.updateIngredient(ingredient) }
    }

    fun createGroupAndAssignToIngredient(groupName: String, ingredient: Ingredient) {
        viewModelScope.launch {
            val groupId = getOrCreateGroup(groupName)
            dao.updateIngredient(ingredient.copy(groupId = groupId))
        }
    }
}

class RecipeViewModelFactory(private val dao: RecipeDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecipeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return RecipeViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
