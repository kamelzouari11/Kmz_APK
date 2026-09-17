package fr.kmz.projects.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.kmz.projects.SnackbarHostStateGlobal
import fr.kmz.projects.data.model.Depense
import fr.kmz.projects.ui.components.ChapitreDropdown
import fr.kmz.projects.ui.components.BeneficiaireDropdown
import fr.kmz.projects.ui.viewmodel.DepensesViewModel
import fr.kmz.projects.utils.FormattingUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaisieScreen(
    viewModel: DepensesViewModel,
    isTabletMode: Boolean = false,
    showFormDialog: Boolean = false,
    onShowFormDialog: (Boolean) -> Unit = {}
) {
    val chapitres by viewModel.chapitres.collectAsState()
    val beneficiaires by viewModel.beneficiaires.collectAsState()
    val depenses by viewModel.depenses.collectAsState()

    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedChapitreId by remember { mutableStateOf(0L) }
    var selectedBeneficiaireId by remember { mutableStateOf(0L) }
    var montantStr by remember { mutableStateOf("") }
    var selectedNature by remember { mutableStateOf("ESPC") }
    var objet by remember { mutableStateOf("") }
    var editingDepenseId by remember { mutableStateOf(0L) }
    var editingProjetId by remember { mutableStateOf(0L) }
    var selectedDepenseId by remember { mutableStateOf(0L) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var depenseToDelete by remember { mutableStateOf<Depense?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val natures = listOf("CHQ", "ESPC", "VIRM")
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("fr", "FR"))
    val dateFormatShort = SimpleDateFormat("dd MMM", Locale("fr", "FR"))

    val greenColor = Color(0xFF4CAF50)

    fun resetForm() {
        selectedDate = System.currentTimeMillis()
        selectedChapitreId = 0L
        selectedBeneficiaireId = 0L
        montantStr = ""
        selectedNature = "ESPC"
        objet = ""
        editingDepenseId = 0L
        editingProjetId = 0L
        showErrors = false
    }

    fun loadDepenseForEdit(depense: Depense) {
        editingDepenseId = depense.id
        editingProjetId = depense.projetId
        selectedDate = depense.date
        selectedChapitreId = depense.chapitreId
        selectedBeneficiaireId = depense.beneficiaireId
        montantStr = depense.montant.toString()
        selectedNature = when (depense.nature.uppercase()) {
            "ESP" -> "ESPC"
            "VIR" -> "VIRM"
            else -> depense.nature
        }
        objet = depense.objet
        showErrors = false
        onShowFormDialog(true)
    }

    fun validate() {
        showErrors = true
        if (selectedChapitreId > 0 && selectedBeneficiaireId > 0 && montantStr.isNotBlank()) {
            val montant = montantStr.toDoubleOrNull()?.toLong() ?: 0L
            val isEditing = editingDepenseId > 0

            if (isEditing) {
                viewModel.updateDepense(Depense(
                    id = editingDepenseId,
                    date = selectedDate,
                    chapitreId = selectedChapitreId,
                    beneficiaireId = selectedBeneficiaireId,
                    montant = montant,
                    nature = selectedNature,
                    objet = objet.trim(),
                    projetId = editingProjetId
                ))
            } else {
                viewModel.createDepense(
                    selectedDate,
                    selectedChapitreId,
                    selectedBeneficiaireId,
                    montant,
                    selectedNature,
                    objet.trim()
                )
            }
            resetForm()
            onShowFormDialog(false)
            selectedDepenseId = 0L
            scope.launch {
                SnackbarHostStateGlobal.showSnackbar(
                    if (isEditing) "Dépense modifiée" else "Dépense ajoutée"
                )
            }
        }
    }

    val normalizedQuery = searchQuery.normalizedForSearch()
    val chapitreParId = chapitres.associateBy { it.id }
    val beneficiaireParId = beneficiaires.associateBy { it.id }
    val depensesChronologiques = depenses
        .filter { depense ->
            normalizedQuery.isBlank() ||
                chapitreParId[depense.chapitreId]?.nom
                    .orEmpty()
                    .normalizedForSearch()
                    .contains(normalizedQuery) ||
                beneficiaireParId[depense.beneficiaireId]?.nom
                    .orEmpty()
                    .normalizedForSearch()
                    .contains(normalizedQuery) ||
                depense.objet.normalizedForSearch().contains(normalizedQuery)
        }
        .sortedByDescending { it.date }
    val totalJournal = depensesChronologiques.sumOf { it.montant }

    // Journal des dépenses (liste seulement)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Chapitre, bénéficiaire ou objet") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Rechercher")
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotEmpty()) {
                                    searchQuery = ""
                                } else {
                                    showSearch = false
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    singleLine = true
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Rechercher dans le journal")
                    }
                }
            }
        }

        if (depensesChronologiques.isEmpty() && normalizedQuery.isNotBlank()) {
            item {
                Text(
                    "Aucune dépense ne correspond à « ${searchQuery.trim()} »",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(depensesChronologiques) { depense ->
            val chapitre = chapitreParId[depense.chapitreId]
            val beneficiaire = beneficiaireParId[depense.beneficiaireId]
            val dateStr = dateFormatShort.format(Date(depense.date))
            val isSelected = depense.id == selectedDepenseId
            val paymentColor = paymentModeColor(depense.nature)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedDepenseId = if (isSelected) 0L else depense.id
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) paymentColor.copy(alpha = 0.24f)
                    else paymentColor.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Text(
                                "  ${chapitre?.nom ?: "?"}  ${beneficiaire?.nom ?: "?"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Text(
                                "  ${depense.nature.lowercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = paymentColor
                            )
                        }
                        Text(
                            "${FormattingUtils.formatNumber(depense.montant)} DT",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (depense.objet.isNotBlank()) {
                            Text(
                                depense.objet,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    if (isSelected) {
                        IconButton(
                            onClick = { loadDepenseForEdit(depense) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Modifier",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                depenseToDelete = depense
                                showDeleteConfirm = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Supprimer",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (normalizedQuery.isBlank()) "Total journal" else "Total filtré",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    "${FormattingUtils.formatNumber(totalJournal)} DT",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Overlay formulaire de saisie
    if (showFormDialog) {
        Dialog(
            onDismissRequest = {
                onShowFormDialog(false)
                resetForm()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (editingDepenseId > 0) "Modifier dépense" else "Nouvelle dépense",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = dateFormat.format(Date(selectedDate)),
                            onValueChange = {},
                            label = { Text("Date") },
                            modifier = Modifier
                                .weight(1.3f)
                                .clickable { showDatePicker = true },
                            readOnly = true,
                            textStyle = MaterialTheme.typography.titleMedium,
                            trailingIcon = { Text("📅", modifier = Modifier.clickable { showDatePicker = true }) }
                        )

                        OutlinedTextField(
                            value = montantStr,
                            onValueChange = { montantStr = it },
                            label = { Text("Montant DT") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.titleMedium,
                            isError = showErrors && montantStr.isBlank()
                        )
                    }

                    ChapitreDropdown(
                        chapitres = chapitres,
                        selectedId = selectedChapitreId,
                        onSelected = { selectedChapitreId = it },
                        onCreateNew = { nom -> viewModel.createChapitre(nom) },
                        isError = showErrors && selectedChapitreId == 0L
                    )

                    BeneficiaireDropdown(
                        beneficiaires = beneficiaires,
                        selectedId = selectedBeneficiaireId,
                        onSelected = { selectedBeneficiaireId = it },
                        onCreateNew = { nom -> viewModel.createBeneficiaire(nom) },
                        isError = showErrors && selectedBeneficiaireId == 0L
                    )

                    OutlinedTextField(
                        value = objet,
                        onValueChange = { objet = it },
                        label = { Text("Objet") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        natures.forEach { nature ->
                            val paymentColor = paymentModeColor(nature)
                            FilterChip(
                                selected = selectedNature == nature,
                                onClick = { selectedNature = nature },
                                label = { Text(nature, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = paymentColor.copy(alpha = 0.08f),
                                    labelColor = paymentColor,
                                    selectedContainerColor = paymentColor.copy(alpha = 0.24f),
                                    selectedLabelColor = paymentColor
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onShowFormDialog(false)
                                resetForm()
                            }
                        ) {
                            Text("Annuler")
                        }
                        Button(
                            onClick = { validate() },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = greenColor,
                                contentColor = Color.White
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = "Valider")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = it
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Annuler")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteConfirm && depenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirmer") },
            text = { Text("Supprimer ?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDepense(depenseToDelete!!)
                        showDeleteConfirm = false
                        depenseToDelete = null
                        selectedDepenseId = 0L
                    }
                ) {
                    Text("Oui")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Non")
                }
            }
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

private fun String.normalizedForSearch(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .lowercase(Locale.ROOT)
