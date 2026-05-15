package fr.kmz.projects.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.kmz.projects.data.model.Beneficiaire
import fr.kmz.projects.data.model.Chapitre

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapitreDropdown(
    chapitres: List<Chapitre>,
    selectedId: Long,
    onSelected: (Long) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newChaptreName by remember { mutableStateOf("") }

    val selectedChapitre = chapitres.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedChapitre?.nom ?: "",
            onValueChange = {},
            label = { Text("Chapitre") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("+ Nouveau chapitre") },
                onClick = {
                    expanded = false
                    showCreateDialog = true
                },
                leadingIcon = { Icon(Icons.Filled.Add, null) }
            )

            chapitres.forEach { chapitre ->
                DropdownMenuItem(
                    text = { Text(chapitre.nom) },
                    onClick = {
                        onSelected(chapitre.id)
                        expanded = false
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nouveau chapitre") },
            text = {
                OutlinedTextField(
                    value = newChaptreName,
                    onValueChange = { newChaptreName = it },
                    label = { Text("Nom") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newChaptreName.isNotBlank()) {
                            onCreateNew(newChaptreName)
                            newChaptreName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeneficiaireDropdown(
    beneficiaires: List<Beneficiaire>,
    selectedId: Long,
    onSelected: (Long) -> Unit,
    onCreateNew: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newBeneficiaireName by remember { mutableStateOf("") }

    val selectedBeneficiaire = beneficiaires.find { it.id == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedBeneficiaire?.nom ?: "",
            onValueChange = {},
            label = { Text("Bénéficiaire") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("+ Nouveau bénéficiaire") },
                onClick = {
                    expanded = false
                    showCreateDialog = true
                },
                leadingIcon = { Icon(Icons.Filled.Add, null) }
            )

            beneficiaires.forEach { beneficiaire ->
                DropdownMenuItem(
                    text = { Text(beneficiaire.nom) },
                    onClick = {
                        onSelected(beneficiaire.id)
                        expanded = false
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nouveau bénéficiaire") },
            text = {
                OutlinedTextField(
                    value = newBeneficiaireName,
                    onValueChange = { newBeneficiaireName = it },
                    label = { Text("Nom") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newBeneficiaireName.isNotBlank()) {
                            onCreateNew(newBeneficiaireName)
                            newBeneficiaireName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Créer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
