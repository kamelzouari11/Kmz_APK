package com.example.simpleradio.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.core.content.edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.simpleradio.data.RadioRepository
import com.example.simpleradio.utils.BackupUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class DataManagementActions(val exportFavorites: () -> Unit, val importFavorites: () -> Unit)

@Composable
fun rememberDataManagement(
        radioRepository: RadioRepository,
        prefs: SharedPreferences,
        onImportApplied: () -> Unit = {},
        scope: CoroutineScope = rememberCoroutineScope(),
        context: Context = LocalContext.current
): DataManagementActions {

    return remember(radioRepository, prefs, onImportApplied, scope, context) {
        DataManagementActions(
                exportFavorites = {
                    scope.launch {
                        val json =
                                radioRepository.exportFavoritesToJson(
                                        readConfirmedLogos(prefs)
                                )
                        BackupUtils.saveToCloud(context, json)
                    }
                },
                importFavorites = {
                    scope.launch {
                        val json = BackupUtils.fetchFromCloud(context)
                        if (json != null) {
                            try {
                                val confirmedLogos =
                                        radioRepository.importFavoritesFromJson(json)
                                applyConfirmedLogosFromGitHub(prefs, confirmedLogos)
                                onImportApplied()
                                Toast.makeText(
                                                context,
                                                "Favoris et logos GitHub appliqués",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            } catch (_: Exception) {
                                Toast.makeText(
                                                context,
                                                "Backup GitHub invalide : données locales conservées",
                                                Toast.LENGTH_LONG
                                        )
                                        .show()
                            }
                        }
                    }
                }
        )
    }
}

private const val CONFIRMED_LOGO_PREFIX = "confirmed_station_logo_"

private fun readConfirmedLogos(prefs: SharedPreferences): Map<String, String> =
        prefs.all.mapNotNull { (key, value) ->
            val stationUuid = key.removePrefix(CONFIRMED_LOGO_PREFIX)
            val logoUrl = value as? String
            if (key.startsWith(CONFIRMED_LOGO_PREFIX) &&
                            stationUuid.isNotBlank() &&
                            !logoUrl.isNullOrBlank()
            ) {
                stationUuid to logoUrl
            } else {
                null
            }
        }.toMap()

/** GitHub wins per station; confirmations missing from GitHub remain untouched locally. */
private fun applyConfirmedLogosFromGitHub(
        prefs: SharedPreferences,
        confirmedLogos: Map<String, String>
) {
    prefs.edit(commit = true) {
        confirmedLogos.forEach { (stationUuid, logoUrl) ->
            putString("$CONFIRMED_LOGO_PREFIX$stationUuid", logoUrl)
        }
    }
}
