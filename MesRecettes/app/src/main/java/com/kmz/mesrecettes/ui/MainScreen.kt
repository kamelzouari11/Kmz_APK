package com.kmz.mesrecettes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmz.mesrecettes.data.RecipeIngredientFull
import com.kmz.mesrecettes.data.RecipeWithBase

sealed class NavScreen {
    object RecipeList : NavScreen()
    data class ViewRecipe(val recipe: RecipeWithBase) : NavScreen()
    data class EditRecipe(val recipe: RecipeWithBase) : NavScreen()
}

@Composable
fun MainScreen(viewModel: RecipeViewModel) {
    var currentScreen by remember { mutableStateOf<NavScreen>(NavScreen.RecipeList) }
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()

    when (val screen = currentScreen) {
        is NavScreen.RecipeList ->
                RecipeListScreen(
                        viewModel = viewModel,
                        onGoToView = { currentScreen = NavScreen.ViewRecipe(it) },
                        onGoToEdit = { currentScreen = NavScreen.EditRecipe(it) }
                )
        is NavScreen.ViewRecipe -> {
            val currentRecipe =
                    recipes.find { it.recipe.id == screen.recipe.recipe.id } ?: screen.recipe
            ViewRecipeScreen(
                    recipeItem = currentRecipe,
                    viewModel = viewModel,
                    onBack = { currentScreen = NavScreen.RecipeList },
                    onGoToEdit = { currentScreen = NavScreen.EditRecipe(currentRecipe) }
            )
        }
        is NavScreen.EditRecipe -> {
            val currentRecipe =
                    recipes.find { it.recipe.id == screen.recipe.recipe.id } ?: screen.recipe
            EditRecipeScreen(
                    recipeItem = currentRecipe,
                    viewModel = viewModel,
                    onBack = { currentScreen = NavScreen.ViewRecipe(currentRecipe) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
        viewModel: RecipeViewModel,
        onGoToView: (RecipeWithBase) -> Unit,
        onGoToEdit: (RecipeWithBase) -> Unit
) {
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text("Mes Recettes") },
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor =
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, "Ajouter une recette")
                }
            }
    ) { padding ->
        LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            val basesList = recipes.map { it.base.name }.distinct().sorted()
            basesList.forEach { baseName ->
                item {
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(
                                                    top = 16.dp,
                                                    bottom = 4.dp,
                                                    start = 8.dp,
                                                    end = 8.dp
                                            ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                text = "Base",
                                style = MaterialTheme.typography.titleSmall,
                                color = androidx.compose.ui.graphics.Color.Gray
                        )
                        Text(
                                text = baseName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
                val recipesInBase = recipes.filter { it.base.name == baseName }
                items(recipesInBase) { item ->
                    Card(
                            modifier =
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                                        onGoToView(item)
                                    }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                        item.recipe.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
            if (recipes.isEmpty()) {
                item {
                    Text(
                            "Aucune recette trouvée. Ajoutez-en une !",
                            modifier = Modifier.padding(16.dp)
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(48.dp)) }
        }

        if (showAddDialog) {
            AddRecipeDialog(
                    viewModel = viewModel,
                    onDismiss = { showAddDialog = false },
                    onRecipeCreated = { showAddDialog = false } // returns to list seamlessly
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecipeDialog(
        viewModel: RecipeViewModel,
        onDismiss: () -> Unit,
        onRecipeCreated: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var baseName by remember { mutableStateOf("") }
    val bases by viewModel.bases.collectAsStateWithLifecycle()
    var expandedBases by remember { mutableStateOf(false) }

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Nouvelle recette") },
            text = {
                Column {
                    OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Titre de la recette") },
                            modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                            expanded = expandedBases,
                            onExpandedChange = { expandedBases = !expandedBases }
                    ) {
                        OutlinedTextField(
                                value = baseName,
                                onValueChange = { baseName = it },
                                label = { Text("Base (ex: Viandes)") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = expandedBases
                                    )
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                                expanded = expandedBases,
                                onDismissRequest = { expandedBases = false }
                        ) {
                            bases.forEach { base ->
                                DropdownMenuItem(
                                        text = { Text(base.name) },
                                        onClick = {
                                            baseName = base.name
                                            expandedBases = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                        onClick = {
                            if (title.isNotBlank() && baseName.isNotBlank()) {
                                viewModel.addRecipeBasic(title, baseName) { onRecipeCreated() }
                            }
                        }
                ) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewRecipeScreen(
        recipeItem: RecipeWithBase,
        viewModel: RecipeViewModel,
        onBack: () -> Unit,
        onGoToEdit: () -> Unit
) {
    val ingredients by
            viewModel.getRecipeIngredients(recipeItem.recipe.id).collectAsState(emptyList())
    val groups by viewModel.ingredientGroups.collectAsStateWithLifecycle()

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text(recipeItem.recipe.title) },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
                        },
                        actions = {
                            IconButton(
                                    onClick = onGoToEdit,
                                    modifier =
                                            Modifier.padding(end = 8.dp)
                                                    .background(
                                                            color =
                                                                    androidx.compose.ui.graphics
                                                                            .Color(0xFF9C27B0),
                                                            shape =
                                                                    androidx.compose.foundation
                                                                            .shape.CircleShape
                                                    )
                            ) {
                                Icon(
                                        Icons.Filled.Edit,
                                        "Modifier",
                                        tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                )
            }
    ) { padding ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
        ) {
            // Grouping and displaying ingredients
            val groupedIngredients = ingredients.groupBy { it.ingredientGroupId }

            if (ingredients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                groupedIngredients.forEach { (groupId, itemsList) ->
                    val groupName =
                            groupId?.let { id -> groups.find { it.id == id }?.name } ?: "Autres"

                    Text(
                            text = groupName,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    )

                    itemsList.chunked(2).forEach { rowItems ->
                        androidx.compose.runtime.CompositionLocalProvider(
                                androidx.compose.ui.platform.LocalLayoutDirection provides
                                        if (groupName.any { it in '\u0600'..'\u06FF' } ||
                                                        (rowItems.isNotEmpty() &&
                                                                rowItems[0].ingredientName.any {
                                                                    it in '\u0600'..'\u06FF'
                                                                })
                                        )
                                                androidx.compose.ui.unit.LayoutDirection.Rtl
                                        else
                                                androidx.compose.ui.platform.LocalLayoutDirection
                                                        .current
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { item ->
                                    val textString =
                                            androidx.compose.ui.text.buildAnnotatedString {
                                                append(item.ingredientName)
                                                if (item.quantity.isNotBlank()) {
                                                    withStyle(
                                                            style =
                                                                    androidx.compose.ui.text
                                                                            .SpanStyle(
                                                                                    color =
                                                                                            androidx.compose
                                                                                                    .ui
                                                                                                    .graphics
                                                                                                    .Color
                                                                                                    .Gray
                                                                            )
                                                    ) { append(" - ${item.quantity}") }
                                                }
                                            }

                                    Text(
                                            textString,
                                            modifier =
                                                    Modifier.weight(1f)
                                                            .padding(
                                                                    bottom = 4.dp,
                                                                    start = 4.dp,
                                                                    end = 4.dp
                                                            ),
                                            style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Aucun ingrédient.")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                    text = "Instructions:",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
            Text(
                    if (recipeItem.recipe.instructions.isNotBlank()) recipeItem.recipe.instructions
                    else "Aucune instruction pour le moment."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(recipeItem: RecipeWithBase, viewModel: RecipeViewModel, onBack: () -> Unit) {
    var title by remember { mutableStateOf(recipeItem.recipe.title) }
    var baseName by remember { mutableStateOf(recipeItem.base.name) }
    var instructions by remember { mutableStateOf(recipeItem.recipe.instructions) }

    val recipeIngredients by
            viewModel.getRecipeIngredients(recipeItem.recipe.id).collectAsState(emptyList())
    val searchResults by viewModel.searchedIngredients.collectAsStateWithLifecycle()
    val groups by viewModel.ingredientGroups.collectAsStateWithLifecycle()
    val copiedList by viewModel.copiedIngredients.collectAsStateWithLifecycle()

    var ingredientSearchQuery by remember { mutableStateOf("") }
    var ingredientQuantity by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // State for Group Manager Dialog
    var showGroupManagerFor by remember { mutableStateOf<RecipeIngredientFull?>(null) }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Column {
                                Text(
                                        "modifier",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                )
                                Text(title)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Retour") }
                        },
                        actions = {
                            IconButton(
                                    onClick = {
                                        if (ingredientSearchQuery.isNotBlank()) {
                                            viewModel.addIngredientToRecipe(
                                                    recipeItem.recipe.id,
                                                    ingredientSearchQuery,
                                                    ingredientQuantity
                                            )
                                        }
                                        viewModel.updateRecipeDetails(
                                                recipeItem.recipe.id,
                                                title,
                                                baseName,
                                                instructions
                                        )
                                        onBack()
                                    },
                                    modifier =
                                            Modifier.padding(end = 8.dp)
                                                    .background(
                                                            color =
                                                                    androidx.compose.ui.graphics
                                                                            .Color(0xFF9C27B0),
                                                            shape =
                                                                    androidx.compose.foundation
                                                                            .shape.CircleShape
                                                    )
                            ) {
                                Icon(
                                        Icons.Filled.Check,
                                        "Sauvegarder",
                                        tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                )
            }
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Titre") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                    )
                    OutlinedTextField(
                            value = baseName,
                            onValueChange = { baseName = it },
                            label = { Text("Base") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            val groupedRecipeIngredients = recipeIngredients.groupBy { it.ingredientGroupId }

            groupedRecipeIngredients.forEach { (groupId, itemsList) ->
                val groupName = groupId?.let { id -> groups.find { it.id == id }?.name } ?: "Autres"

                item {
                    Text(
                            text = groupName,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                    )
                }

                items(itemsList.chunked(2)) { rowItems ->
                    androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalLayoutDirection provides
                                    if (groupName.any { it in '\u0600'..'\u06FF' } ||
                                                    (rowItems.isNotEmpty() &&
                                                            rowItems[0].ingredientName.any {
                                                                it in '\u0600'..'\u06FF'
                                                            })
                                    )
                                            androidx.compose.ui.unit.LayoutDirection.Rtl
                                    else androidx.compose.ui.platform.LocalLayoutDirection.current
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { ri ->
                                Row(
                                        modifier =
                                                Modifier.weight(1f)
                                                        .padding(vertical = 2.dp, horizontal = 4.dp)
                                                        .clickable { showGroupManagerFor = ri },
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val textString =
                                            androidx.compose.ui.text.buildAnnotatedString {
                                                append(ri.ingredientName)
                                                if (ri.quantity.isNotBlank()) {
                                                    withStyle(
                                                            style =
                                                                    androidx.compose.ui.text
                                                                            .SpanStyle(
                                                                                    color =
                                                                                            androidx.compose
                                                                                                    .ui
                                                                                                    .graphics
                                                                                                    .Color
                                                                                                    .Gray
                                                                            )
                                                    ) { append(" - ${ri.quantity}") }
                                                }
                                            }
                                    Text(
                                            textString,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign =
                                                    androidx.compose.ui.text.style.TextAlign.Start,
                                            style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Adding new ingredients
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                    "Ajouter un ingrédient",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                            )
                            if (recipeIngredients.isNotEmpty()) {
                                IconButton(
                                        onClick = { viewModel.copyIngredients(recipeIngredients) }
                                ) {
                                    Icon(
                                            androidx.compose.material.icons.Icons.Filled
                                                    .ContentCopy,
                                            "Copier",
                                            tint = androidx.compose.ui.graphics.Color(0xFF9C27B0)
                                    )
                                }
                            }
                            if (copiedList.isNotEmpty()) {
                                IconButton(
                                        onClick = {
                                            viewModel.pasteIngredients(recipeItem.recipe.id)
                                        }
                                ) {
                                    Icon(
                                            androidx.compose.material.icons.Icons.Filled
                                                    .ContentPaste,
                                            "Coller",
                                            tint = androidx.compose.ui.graphics.Color(0xFF9C27B0)
                                    )
                                }
                            }
                            IconButton(
                                    onClick = {
                                        if (ingredientSearchQuery.isNotBlank()) {
                                            viewModel.addIngredientToRecipe(
                                                    recipeItem.recipe.id,
                                                    ingredientSearchQuery,
                                                    ingredientQuantity
                                            )
                                            ingredientSearchQuery = ""
                                            ingredientQuantity = ""
                                            isSearchFocused = false
                                        }
                                    }
                            ) {
                                Icon(
                                        Icons.Filled.Add,
                                        "Ajouter à la recette",
                                        tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        ExposedDropdownMenuBox(
                                expanded = isSearchFocused && searchResults.isNotEmpty(),
                                onExpandedChange = { isSearchFocused = it }
                        ) {
                            OutlinedTextField(
                                    value = ingredientSearchQuery,
                                    onValueChange = {
                                        ingredientSearchQuery = it
                                        viewModel.updateSearchQuery(it)
                                        isSearchFocused = true
                                    },
                                    label = { Text("Nom ingrédient (ex: Sel)") },
                                    modifier =
                                            Modifier.menuAnchor()
                                                    .fillMaxWidth()
                                                    .focusRequester(focusRequester)
                            )

                            ExposedDropdownMenu(
                                    expanded =
                                            isSearchFocused &&
                                                    searchResults.isNotEmpty() &&
                                                    ingredientSearchQuery.isNotEmpty(),
                                    onDismissRequest = { isSearchFocused = false }
                            ) {
                                searchResults.take(6).forEach { res ->
                                    DropdownMenuItem(
                                            text = { Text(res.name) },
                                            onClick = {
                                                ingredientSearchQuery = res.name
                                                isSearchFocused = false
                                            }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                                value = ingredientQuantity,
                                onValueChange = { ingredientQuantity = it },
                                label = { Text("Quantité") },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("Instructions") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        maxLines = 10
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Edit Ingredient Dialog
        showGroupManagerFor?.let { targetIngredient ->
            EditIngredientOverlay(
                    recipeIngredient = targetIngredient,
                    viewModel = viewModel,
                    onDismiss = { showGroupManagerFor = null }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditIngredientOverlay(
        recipeIngredient: com.kmz.mesrecettes.data.RecipeIngredientFull,
        viewModel: RecipeViewModel,
        onDismiss: () -> Unit
) {
    val groups by viewModel.ingredientGroups.collectAsStateWithLifecycle()
    var selectedGroupName by remember {
        mutableStateOf(groups.find { it.id == recipeIngredient.ingredientGroupId }?.name ?: "")
    }
    var expandedGroup by remember { mutableStateOf(false) }

    var quantity by remember { mutableStateOf(recipeIngredient.quantity) }
    var expandedQuantity by remember { mutableStateOf(false) }

    var ingredientName by remember { mutableStateOf(recipeIngredient.ingredientName) }

    // Create a list of recently used quantities for autocomplete
    // In a real app we could query distinct quantities from DB, but for now we extract from current
    // recipe
    val existingRecipeIngredients by
            viewModel.getRecipeIngredients(recipeIngredient.recipeId).collectAsState(emptyList())
    val quantitySuggestions =
            existingRecipeIngredients.map { it.quantity }.filter { it.isNotBlank() }.distinct()

    AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Modifier l'ingrédient") },
            text = {
                Column {
                    // Name Field
                    OutlinedTextField(
                            value = ingredientName,
                            onValueChange = { ingredientName = it },
                            label = { Text("Nom de l'ingrédient") },
                            modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quantity with autocomplete
                    ExposedDropdownMenuBox(
                            expanded = expandedQuantity,
                            onExpandedChange = { expandedQuantity = !expandedQuantity }
                    ) {
                        OutlinedTextField(
                                value = quantity,
                                onValueChange = { quantity = it },
                                label = { Text("Quantité") },
                                trailingIcon = {
                                    if (quantitySuggestions.isNotEmpty()) {
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                                expanded = expandedQuantity
                                        )
                                    }
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        if (quantitySuggestions.isNotEmpty()) {
                            ExposedDropdownMenu(
                                    expanded = expandedQuantity,
                                    onDismissRequest = { expandedQuantity = false }
                            ) {
                                quantitySuggestions.forEach { qty ->
                                    DropdownMenuItem(
                                            text = { Text(qty) },
                                            onClick = {
                                                quantity = qty
                                                expandedQuantity = false
                                            }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Group Selection
                    ExposedDropdownMenuBox(
                            expanded = expandedGroup,
                            onExpandedChange = { expandedGroup = !expandedGroup }
                    ) {
                        OutlinedTextField(
                                value = selectedGroupName,
                                onValueChange = { selectedGroupName = it },
                                label = { Text("Nom du groupe (ex: Épices)") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = expandedGroup
                                    )
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                                expanded = expandedGroup,
                                onDismissRequest = { expandedGroup = false }
                        ) {
                            groups.forEach { grp ->
                                DropdownMenuItem(
                                        text = { Text(grp.name) },
                                        onClick = {
                                            selectedGroupName = grp.name
                                            expandedGroup = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                IconButton(
                        onClick = {
                            viewModel.updateRecipeIngredientFull(
                                    recipeIngredient,
                                    ingredientName,
                                    quantity,
                                    selectedGroupName
                            )
                            onDismiss()
                        }
                ) { Icon(Icons.Filled.Check, "Valider", tint = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                IconButton(
                        onClick = {
                            viewModel.removeIngredientFromRecipe(recipeIngredient)
                            onDismiss()
                        }
                ) { Icon(Icons.Filled.Close, "Supprimer", tint = MaterialTheme.colorScheme.error) }
            }
    )
}
