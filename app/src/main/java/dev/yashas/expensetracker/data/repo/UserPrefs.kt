package dev.yashas.expensetracker.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsStore by preferencesDataStore(name = "user_prefs")

/**
 * Tiny per-device settings (S17 profile section, minimal v0): greeting name.
 * DataStore-backed; survives reinstalls-with-backup, no accounts anywhere.
 */
class UserPrefs(private val context: Context) {

    val nameFlow: Flow<String> = context.userPrefsStore.data.map { it[NAME] ?: DEFAULT_NAME }

    suspend fun setName(value: String) {
        context.userPrefsStore.edit { it[NAME] = value.trim().take(24).ifEmpty { DEFAULT_NAME } }
    }

    companion object {
        const val DEFAULT_NAME = "Yashas"
        private val NAME = stringPreferencesKey("display_name")
    }
}
