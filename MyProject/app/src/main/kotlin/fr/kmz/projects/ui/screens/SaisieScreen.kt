package fr.kmz.projects.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.kmz.projects.data.model.Depense
import fr.kmz.projects.ui.components.ChapitreDropdown
import fr.kmz.projects.ui.components.BeneficiaireDropdown
import fr.kmz.projects.ui.viewmodel.DepensesViewModel
import fr.kmz.projects.utils.FormattingUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaisieScreen(
    viewModel: DepensesViewModel,
    isTabletMode: Boolean = false
) {
    val chapitres by viewModel.chapitres.collectAsState()
    val beneficiaires by viewModel.beneficiaires.collectAsState()
    val depenses by viewModel.depenses.collectAsState()

    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedChapitreId by remember { mutableStateOf(0L) }
    var selectedBeneficiaireId by remember { mutableStateOf(0L) }
    var montantStr by remember { mutableStateOf("") }
    var selectedNature by remember { mutableStateOf("Espèces") }

    val natures = listOf("Chèque", "Espèces", "CB", "Virement")
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("fr", "FR"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saisie de dépense") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Date picker (simplified - just displays)
                    OutlinedTextField(
                        value = dateFormat.format(Date(selectedDate)),
                        onValueChange = {},
                        label = { Text("Date") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true
                    )
                }

                item {
                    ChapitreDropdown(
                        chapitres = chapitres,
                        selectedId = selectedChapitreId,
                        onSelected = { selectedChapitreId = it },
                        onCreateNew = { nom -> viewModel.createChapitre(nom) }
                    )
                }

                item {
                    BeneficiaireDropdown(
                        beneficiaires = beneficiaires,
                        selectedId = selectedBeneficiaireId,
                        onSelected = { selectedBeneficiaireId = it },
                        onCreateNew = { nom -> viewModel.createBeneficiaire(nom) }
                    )
                }

                item {
                    OutlinedTextField(
                        value = montantStr,
                        onValueChange = { montantStr = it },
                        label = { Text("Montant (€)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                item {
                    Column {
                        Text("Nature du paiement", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            natures.forEach { nature ->
                                FilterChip(
                                    selected = selectedNature == nature,
                                    onClick = { selectedNature = nature },
                                    label = { Text(nature) }
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (selectedChapitreId > 0 && selectedBeneficiaireId > 0 && montantStr.isNotBlank()) {
                                val montantEuros = montantStr.toDoubleOrNull() ?: 0.0
                                val montantCentimes = (montantEuros * 100).toLong()
                                viewModel.createDepense(
                                    date = selectedDate,
                                    chapitreId = selectedChapitreId,
                                    beneficiaireId = selectedBeneficiaireId,
                                    montant = montantCentimes,
                                    nature = selectedNature
                                )
                                montantStr = ""
                                selectedChapitreId = 0L
                                selectedBeneficiaireId = 0L
                                selectedNature = "Espèces"
                                selectedDate = System.currentTimeMillis()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Enregistrer")
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Dernières dépenses",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(depenses.take(10)) { depense ->
                    val chapitre = chapitres.find { it.id == depense.chapitreId }
                    val beneficiaire = beneficiaires.find { it.id == depense.beneficiaireId }
                    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale("fr", "FR")).format(Date(depense.date))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$dateStr • ${chapitre?.nom ?: "?"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    beneficiaire?.nom ?: "?",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${FormattingUtils.formatCurrencyNoDecimals(depense.montant)} • ${depense.nature}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteDepense(depense) }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}
