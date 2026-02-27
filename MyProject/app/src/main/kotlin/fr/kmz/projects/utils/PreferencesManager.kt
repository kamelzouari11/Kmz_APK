package fr.kmz.projects.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "renovation_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
        private val LAST_SELECTED_LOT = stringPreferencesKey("last_selected_lot")
    }

    fun getLastSelectedLotId(): Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SELECTED_LOT]?.toLongOrNull() ?: -1L
    }

    suspend fun saveLastSelectedLotId(lotId: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SELECTED_LOT] = lotId.toString()
        }
    }
}
