package fr.kmz.projects.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun LotInputForm(
    nom: String,
    onNomChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    
) {
    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = nom,
            onValueChange = onNomChange,
            label = { Text("Nom du Lot") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true
        )
    }
}

@Composable
fun SousLotInputForm(
    nom: String,
    onNomChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = nom,
            onValueChange = onNomChange,
            label = { Text("Nom du Sous-Lot") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true
        )
    }
}

@Composable
fun ArticleInputForm(
    nom: String,
    onNomChange: (String) -> Unit,
    quantite: String,
    onQuantiteChange: (String) -> Unit,
    unite: String,
    onUniteChange: (String) -> Unit,
    prixUnitaire: String,
    onPrixChange: (String) -> Unit,
    total: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = nom,
            onValueChange = onNomChange,
            label = { Text("Nom de l'article") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = quantite,
            onValueChange = onQuantiteChange,
            label = { Text("Quantité") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = unite,
            onValueChange = onUniteChange,
            label = { Text("Unité (m², m, kg, etc.)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = prixUnitaire,
            onValueChange = onPrixChange,
            label = { Text("Prix unitaire (dt)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        Text(
            text = "Total: $total dt",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
