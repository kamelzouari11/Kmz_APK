package com.example.simpleradio.ui.components

import android.content.Context
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
        scope: CoroutineScope = rememberCoroutineScope(),
        context: Context = LocalContext.current
): DataManagementActions {

    return remember(radioRepository, scope, context) {
        DataManagementActions(
                exportFavorites = {
                    scope.launch {
                        val json = radioRepository.exportFavoritesToJson()
                        BackupUtils.saveToCloud(context, json)
                    }
                },
                importFavorites = {
                    scope.launch {
                        val json = BackupUtils.fetchFromCloud(context)
                        if (json != null) {
                            radioRepository.importFavoritesFromJson(json)
                        }
                    }
                }
        )
    }
}
