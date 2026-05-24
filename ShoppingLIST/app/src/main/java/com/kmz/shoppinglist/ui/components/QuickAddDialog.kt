package com.kmz.shoppinglist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kmz.shoppinglist.data.Article
import com.kmz.shoppinglist.data.LocalIconProvider
import com.kmz.shoppinglist.ui.theme.*

/**
 * Dialog de recherche rapide : tape les premiers caractères d'un article existant (français ou
 * arabe) pour le retrouver et l'ajouter à la liste d'achat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddDialog(
        allArticles: List<Article>, // Tous les articles de la BDD
        currentCategoryId: Long, // Catégorie cible
        onAddArticle: (Article) -> Unit, // Callback : article sélectionné → ajouter
        onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Filtrage : nom ou frenchName commence par la requête (insensible casse)
    val suggestions =
            remember(query) {
                if (query.isBlank()) emptyList()
                else
                        allArticles
                                .filter { article ->
                                    val q = query.trim().lowercase()
                                    article.name.lowercase().contains(q) ||
                                            article.frenchName?.lowercase()?.contains(q) == true
                                }
                                .distinctBy {
                                    it.name.lowercase()
                                } // Éviter doublons (même article dans plusieurs catégories)
                                .take(12)
            }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
                modifier = Modifier.fillMaxWidth(0.95f).wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = DarkGray
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // ── Titre ──────────────────────────────────────────────
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                            modifier =
                                    Modifier.size(32.dp).clip(CircleShape).background(AccentGreen),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = White,
                                modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                            text = "Ajouter un article existant",
                            color = White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer", tint = TextGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Champ de saisie ─────────────────────────────────────
                OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                    "Taper le nom (français ou arabe)…",
                                    color = TextGray,
                                    fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = AccentGreen
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Effacer",
                                            tint = TextGray
                                    )
                                }
                            }
                        },
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = White,
                                        unfocusedTextColor = White,
                                        focusedBorderColor = AccentGreen,
                                        unfocusedBorderColor = MediumGray,
                                        cursorColor = AccentGreen
                                ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Suggestions ─────────────────────────────────────────
                if (query.isNotBlank() && suggestions.isEmpty()) {
                    Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                    ) { Text("Aucun article trouvé", color = TextGray, fontSize = 14.sp) }
                } else if (suggestions.isNotEmpty()) {
                    LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(suggestions) { article ->
                            QuickAddSuggestionRow(
                                    article = article,
                                    query = query,
                                    onClick = {
                                        // Créer une copie de l'article dans la catégorie courante
                                        val newArticle =
                                                article.copy(
                                                        id = System.currentTimeMillis(),
                                                        categoryId = currentCategoryId,
                                                        isBought = false,
                                                        priority =
                                                                com.kmz.shoppinglist.data.Priority
                                                                        .NORMAL
                                                )
                                        onAddArticle(newArticle)
                                        onDismiss()
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Une ligne de suggestion dans le dialog de recherche rapide */
@Composable
private fun QuickAddSuggestionRow(article: Article, query: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val iconProvider = remember { LocalIconProvider(context) }
    val iconUrl = iconProvider.getIconPath(article.getIconIdSafe())

    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MediumGray)
                            .clickable { onClick() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // Icône de l'article
        Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(DarkGray),
                contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                    model = iconUrl ?: "",
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Nom principal + nom secondaire
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = article.name,
                    color = White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
            )
            val secondary = article.frenchName
            if (!secondary.isNullOrBlank() && secondary != article.name) {
                Text(text = secondary, color = TextGray, fontSize = 12.sp, maxLines = 1)
            }
        }

        // Indicateur "ajouter"
        Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(AccentGreen),
                contentAlignment = Alignment.Center
        ) { Text("+", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}
