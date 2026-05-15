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
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("fr", "FR"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rapports") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    text = { Text("Par Chapitre") },
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 }
                )
                Tab(
                    text = { Text("Par Bénéficiaire") },
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 }
                )
                Tab(
                    text = { Text("Récapitulatif") },
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 }
                )
            }

            when (selectedTabIndex) {
                0 -> RapportParChapitre(depensesParChapitre, dateFormat)
                1 -> RapportParBeneficiaire(depensesParBeneficiaire, dateFormat)
                2 -> RapportRecapitulatif(chapitres, depensesParChapitre, total)
            }
        }
    }
}

@Composable
private fun RapportParChapitre(
    depensesParChapitre: Map<Chapitre, List<Depense>>,
    dateFormat: SimpleDateFormat
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        depensesParChapitre.forEach { (chapitre, depenses) ->
            item {
                ChapitreSection(chapitre, depenses, dateFormat)
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
    dateFormat: SimpleDateFormat
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        depensesParBeneficiaire.forEach { (beneficiaire, depenses) ->
            item {
                BeneficiaireSection(beneficiaire, depenses, dateFormat)
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
                chapitres.forEach { chapitre ->
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
                            Text(chapitre.nom, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${depenses.size} dépense${if (depenses.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            FormattingUtils.formatCurrencyNoDecimals(totalChapitre),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (chapitre != chapitres.last()) {
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
                Text("Total dépensé", style = MaterialTheme.typography.titleMedium)
                Text(
                    FormattingUtils.formatCurrencyNoDecimals(total),
                    style = MaterialTheme.typography.titleMedium,
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
    dateFormat: SimpleDateFormat
) {
    val total = depenses.sumOf { it.montant }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(chapitre.nom, style = MaterialTheme.typography.titleSmall)
                Text(
                    FormattingUtils.formatCurrencyNoDecimals(total),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            depenses.forEach { depense ->
                DepenseRow(depense, dateFormat)
            }
        }
    }
}

@Composable
private fun BeneficiaireSection(
    beneficiaire: Beneficiaire,
    depenses: List<Depense>,
    dateFormat: SimpleDateFormat
) {
    val total = depenses.sumOf { it.montant }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(beneficiaire.nom, style = MaterialTheme.typography.titleSmall)
                Text(
                    FormattingUtils.formatCurrencyNoDecimals(total),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            depenses.forEach { depense ->
                DepenseRow(depense, dateFormat)
            }
        }
    }
}

@Composable
private fun DepenseRow(depense: Depense, dateFormat: SimpleDateFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            dateFormat.format(Date(depense.date)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            depense.nature,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            FormattingUtils.formatCurrencyNoDecimals(depense.montant),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
