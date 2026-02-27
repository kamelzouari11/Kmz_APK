package fr.kmz.projects.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.kmz.projects.data.model.Article
import fr.kmz.projects.ui.components.ArticleCard
import fr.kmz.projects.ui.components.ArticleInputForm
import fr.kmz.projects.ui.components.RecapCard
import fr.kmz.projects.ui.viewmodel.ArticleViewModel
import fr.kmz.projects.utils.FormattingUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen(
        viewModel: ArticleViewModel,
        sousLotName: String,
        onBackClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    val articles by viewModel.articles.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var editingArticle by remember { mutableStateOf<Article?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingArticle by remember { mutableStateOf<Article?>(null) }
    var nomInput by remember { mutableStateOf("") }
    var quantiteInput by remember { mutableStateOf("1") }
    var uniteInput by remember { mutableStateOf("") }
    var prixInput by remember { mutableStateOf("0") }

    val total = articles.sumOf { it.quantite * it.prixUnitaire }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = { Text(sousLotName) },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Retour"
                                )
                            }
                        }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                        onClick = {
                            editingArticle = null
                            nomInput = ""
                            quantiteInput = "1"
                            uniteInput = ""
                            prixInput = "0"
                            showDialog = true
                        }
                ) { Icon(Icons.Filled.Add, contentDescription = "Ajouter un Article") }
            },
            modifier = modifier
    ) { padding ->
        if (articles.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text("Aucun article pour ce sous-lot.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(articles) { article ->
                    ArticleCard(
                            nom = article.nom,
                            quantite = article.quantite,
                            unite = article.unite,
                            prixUnitaire =
                                    FormattingUtils.formatCurrencyNoDecimals(article.prixUnitaire),
                            total =
                                    FormattingUtils.formatCurrencyNoDecimals(
                                            article.calculerTotal()
                                    ),
                            onEdit = {
                                editingArticle = article
                                nomInput = article.nom
                                quantiteInput = article.quantite.toString()
                                uniteInput = article.unite
                                prixInput = (article.prixUnitaire / 100.0).toString()
                                showDialog = true
                            }
                    )
                }

                item {
                    RecapCard(
                            title = "Total $sousLotName",
                            total = FormattingUtils.formatCurrencyNoDecimals(total)
                    )
                }

                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
                onDismissRequest = { showDialog = false },
                title = {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (editingArticle != null) "Modifier l'Article" else "Nouvel Article")
                        if (editingArticle != null) {
                            IconButton(
                                    onClick = {
                                        deletingArticle = editingArticle
                                        showDialog = false
                                        showDeleteDialog = true
                                    }
                            ) {
                                Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Supprimer",
                                        tint = Color.Red
                                )
                            }
                        }
                    }
                },
                text = {
                    ArticleInputForm(
                            nom = nomInput,
                            onNomChange = { nomInput = it },
                            quantite = quantiteInput,
                            onQuantiteChange = { quantiteInput = it },
                            unite = uniteInput,
                            onUniteChange = { uniteInput = it },
                            prixUnitaire = prixInput,
                            onPrixChange = { prixInput = it },
                            total =
                                    try {
                                        val q = quantiteInput.toIntOrNull() ?: 0
                                        val p = FormattingUtils.stringToCentimes(prixInput)
                                        FormattingUtils.formatCurrencyNoDecimals(q * p)
                                    } catch (e: Exception) {
                                        "0"
                                    }
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (nomInput.isNotBlank() &&
                                                quantiteInput.isNotBlank() &&
                                                prixInput.isNotBlank()
                                ) {
                                    val q = quantiteInput.toIntOrNull() ?: 1
                                    val p = FormattingUtils.stringToCentimes(prixInput)
                                    val currentArticle = editingArticle
                                    if (currentArticle != null) {
                                        viewModel.updateArticle(
                                                currentArticle.copy(
                                                        nom = nomInput,
                                                        quantite = q,
                                                        unite = uniteInput,
                                                        prixUnitaire = p
                                                )
                                        )
                                    } else {
                                        viewModel.createArticle(
                                                nom = nomInput,
                                                quantite = q,
                                                unite = uniteInput,
                                                prixUnitaire = p
                                        )
                                    }
                                    showDialog = false
                                }
                            }
                    ) { Text("Enregistrer") }
                },
                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Annuler") } }
        )
    }

    if (showDeleteDialog && deletingArticle != null) {
        AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    deletingArticle = null
                },
                title = { Text("Confirmer la suppression") },
                text = {
                    Text(
                            "Voulez-vous vraiment supprimer cet article ? Cette action est irréversible."
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                viewModel.deleteArticle(deletingArticle!!)
                                showDeleteDialog = false
                                deletingArticle = null
                            }
                    ) { Text("Supprimer") }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showDeleteDialog = false
                                deletingArticle = null
                            }
                    ) { Text("Annuler") }
                }
        )
    }
}
