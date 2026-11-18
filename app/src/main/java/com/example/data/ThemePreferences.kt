package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "amar_hisab_prefs")

class ThemePreferences(private val context: Context) {
    private val darkModeKey = booleanPreferencesKey("dark_mode")

    val darkModeFlow: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[darkModeKey] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[darkModeKey] = enabled
        }
    }
}
