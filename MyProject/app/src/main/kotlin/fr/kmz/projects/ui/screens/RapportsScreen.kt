package fr.kmz.projects.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre
import fr.kmz.projects.data.model.Depense
import fr.kmz.projects.ui.viewmodel.DepensesViewModel
import fr.kmz.projects.utils.FormattingUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RapportsScreen(
    viewModel: DepensesViewModel,
    isTabletMode: Boolean = false
) {
    val chapitres by viewModel.chapitres.collectAsState()
    val beneficiaires by viewModel.beneficiaires.collectAsState()
    val depensesParChapitre by viewModel.depensesParChapitre.collectAsState()
    val depensesParBeneficiaire by viewModel.depensesParBeneficiaire.collectAsState()
    val total by viewModel.total.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val dateFormat = SimpleDateFormat("dd MMM", Locale("fr", "FR"))

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                text = { Text("Chapitre", style = MaterialTheme.typography.labelSmall) },
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 }
            )
            Tab(
                text = { Text("Bénéficiaire", style = MaterialTheme.typography.labelSmall) },
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 }
            )
            Tab(
                text = { Text("Récap", style = MaterialTheme.typography.labelSmall) },
                selected = selectedTabIndex == 2,
                onClick = { selectedTabIndex = 2 }
            )
        }

        when (selectedTabIndex) {
            0 -> RapportParChapitre(depensesParChapitre, beneficiaires, dateFormat)
            1 -> RapportParBeneficiaire(depensesParBeneficiaire, chapitres, dateFormat)
            2 -> RapportRecapitulatif(chapitres, depensesParChapitre, total)
        }
    }
}

@Composable
private fun RapportParChapitre(
    depensesParChapitre: Map<Chapitre, List<Depense>>,
    beneficiaires: List<Beneficiaire>,
    dateFormat: SimpleDateFormat
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        depensesParChapitre.entries
            .sortedByDescending { (_, depenses) -> depenses.sumOf { it.montant } }
            .forEach { (chapitre, depenses) ->
            item {
                ChapitreSection(chapitre, depenses, beneficiaires, dateFormat)
            }
        }
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun RapportParBeneficiaire(
    depensesParBeneficiaire: Map<Beneficiaire, List<Depense>>,
    chapitres: List<Chapitre>,
    dateFormat: SimpleDateFormat
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        depensesParBeneficiaire.entries
            .sortedByDescending { (_, depenses) -> depenses.sumOf { it.montant } }
            .forEach { (beneficiaire, depenses) ->
            item {
                BeneficiaireSection(beneficiaire, depenses, chapitres, dateFormat)
            }
        }
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun RapportRecapitulatif(
    chapitres: List<Chapitre>,
    depensesParChapitre: Map<Chapitre, List<Depense>>,
    total: Long
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Résumé par chapitre", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                val chapitresTries = chapitres.sortedByDescending { chapitre ->
                    depensesParChapitre[chapitre]?.sumOf { it.montant } ?: 0L
                }
                chapitresTries.forEachIndexed { index, chapitre ->
                    val depenses = depensesParChapitre[chapitre] ?: emptyList()
                    val totalChapitre = depenses.sumOf { it.montant }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(chapitre.nom, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${depenses.size} dépense${if (depenses.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Text(
                            "${FormattingUtils.formatNumber(totalChapitre)} DT",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (index < chapitresTries.lastIndex) {
                        Divider()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total dépensé", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${FormattingUtils.formatNumber(total)} DT",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun ChapitreSection(
    chapitre: Chapitre,
    depenses: List<Depense>,
    beneficiaires: List<Beneficiaire>,
    dateFormat: SimpleDateFormat
) {
    val total = depenses.sumOf { it.montant }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(chapitre.nom, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${FormattingUtils.formatNumber(total)} DT",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            depenses.forEach { depense ->
                val beneficiaire = beneficiaires.find { it.id == depense.beneficiaireId }
                DepenseRow(
                    dateStr = dateFormat.format(Date(depense.date)),
                    middleLabel = beneficiaire?.nom ?: "?",
                    objet = depense.objet,
                    montant = depense.montant
                )
            }
        }
    }
}

@Composable
private fun BeneficiaireSection(
    beneficiaire: Beneficiaire,
    depenses: List<Depense>,
    chapitres: List<Chapitre>,
    dateFormat: SimpleDateFormat
) {
    val total = depenses.sumOf { it.montant }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(beneficiaire.nom, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${FormattingUtils.formatNumber(total)} DT",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            depenses.forEach { depense ->
                val chapitre = chapitres.find { it.id == depense.chapitreId }
                DepenseRowWithNature(
                    dateStr = dateFormat.format(Date(depense.date)),
                    middleLabel = chapitre?.nom ?: "?",
                    nature = depense.nature,
                    objet = depense.objet,
                    montant = depense.montant
                )
            }
        }
    }
}

@Composable
private fun DepenseRow(dateStr: String, middleLabel: String, objet: String, montant: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dateStr,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.6f)
        )
        Text(
            if (objet.isBlank()) middleLabel else "$middleLabel - $objet",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${FormattingUtils.formatNumber(montant)} DT",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DepenseRowWithNature(
    dateStr: String,
    middleLabel: String,
    nature: String,
    objet: String,
    montant: Long
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dateStr,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.55f)
        )
        Text(
            if (objet.isBlank()) middleLabel else "$middleLabel - $objet",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            nature,
            style = MaterialTheme.typography.labelSmall,
            color = paymentModeColor(nature),
            modifier = Modifier.weight(0.35f)
        )
        Text(
            "${FormattingUtils.formatNumber(montant)} DT",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun paymentModeColor(nature: String): Color =
    when (nature.uppercase()) {
        "CHQ" -> Color(0xFF81C784)
        "ESP", "ESPC" -> Color(0xFFFFB74D)
        "VIR", "VIRM" -> Color(0xFF64B5F6)
        else -> Color(0xFFB0BEC5)
    }
