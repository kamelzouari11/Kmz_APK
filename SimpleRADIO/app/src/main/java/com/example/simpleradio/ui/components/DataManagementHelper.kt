package com.example.simpleradio.ui.components

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.simpleradio.data.RadioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class DataManagementActions(val exportFavorites: () -> Unit, val importFavorites: () -> Unit)

@Composable
fun rememberDataManagement(
        radioRepository: RadioRepository,
        scope: CoroutineScope = rememberCoroutineScope(),
        context: Context = LocalContext.current
): DataManagementActions {

    val exportLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                uri?.let {
                    scope.launch {
                        try {
                            val json = radioRepository.exportFavoritesToJson()
                            context.contentResolver.openOutputStream(it)?.use { out ->
                                out.write(json.toByteArray())
                            }
                            Toast.makeText(
                                            context,
                                            "Favoris exportés avec succès !",
                                            Toast.LENGTH_SHORT
                                    )
                                    .show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                            context,
                                            "Erreur export : ${e.message}",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    }
                }
            }

    val importLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) {
                    uri ->
                uri?.let {
                    scope.launch {
                        try {
                            val json =
                                    context.contentResolver
                                            .openInputStream(it)
                                            ?.bufferedReader()
                                            ?.use { reader -> reader.readText() }
                            if (json != null) {
                                try {
                                    radioRepository.importFavoritesFromJson(json)
                                    Toast.makeText(
                                                    context,
                                                    "Favoris importés ! Redémarrage conseillé.",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                } catch (e: Exception) {
                                    // Try importing as station list if favorites import fails or if
                                    // it's the wrong format
                                    // Note: original code had a commented out block for stations
                                    // import.
                                    // We will assume the user wanted the robust logic.
                                    // However, the active code I saw only had
                                    // importFavoritesFromJson.
                                    Toast.makeText(
                                                    context,
                                                    "Erreur format favoris : ${e.message}",
                                                    Toast.LENGTH_LONG
                                            )
                                            .show()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(
                                            context,
                                            "Erreur import : ${e.message}",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    }
                }
            }

    return remember(exportLauncher, importLauncher) {
        DataManagementActions(
                exportFavorites = { exportLauncher.launch("radio_favorites_backup.json") },
                importFavorites = { importLauncher.launch(arrayOf("application/json")) }
        )
    }
}
