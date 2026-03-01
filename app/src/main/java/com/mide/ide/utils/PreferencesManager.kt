package com.mide.ide.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mide_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val TAB_SIZE = intPreferencesKey("tab_size")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val BUILD_TYPE = stringPreferencesKey("build_type")
        val LAST_PROJECT_PATH = stringPreferencesKey("last_project_path")
        val ALLOW_UNVERIFIED_PLUGINS = booleanPreferencesKey("allow_unverified_plugins")
    }

    val fontSize: Flow<Float> = context.dataStore.data.map { it[FONT_SIZE] ?: 14f }
    val tabSize: Flow<Int> = context.dataStore.data.map { it[TAB_SIZE] ?: 4 }
    val wordWrap: Flow<Boolean> = context.dataStore.data.map { it[WORD_WRAP] ?: false }
    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[DARK_THEME] ?: true }
    val showLineNumbers: Flow<Boolean> = context.dataStore.data.map { it[SHOW_LINE_NUMBERS] ?: true }
    val autoSave: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SAVE] ?: true }
    val buildType: Flow<String> = context.dataStore.data.map { it[BUILD_TYPE] ?: "debug" }
    val lastProjectPath: Flow<String?> = context.dataStore.data.map { it[LAST_PROJECT_PATH] }
    val allowUnverifiedPlugins: Flow<Boolean> = context.dataStore.data.map { it[ALLOW_UNVERIFIED_PLUGINS] ?: false }

    suspend fun setFontSize(value: Float) = context.dataStore.edit { it[FONT_SIZE] = value }
    suspend fun setTabSize(value: Int) = context.dataStore.edit { it[TAB_SIZE] = value }
    suspend fun setWordWrap(value: Boolean) = context.dataStore.edit { it[WORD_WRAP] = value }
    suspend fun setDarkTheme(value: Boolean) = context.dataStore.edit { it[DARK_THEME] = value }
    suspend fun setShowLineNumbers(value: Boolean) = context.dataStore.edit { it[SHOW_LINE_NUMBERS] = value }
    suspend fun setAutoSave(value: Boolean) = context.dataStore.edit { it[AUTO_SAVE] = value }
    suspend fun setBuildType(value: String) = context.dataStore.edit { it[BUILD_TYPE] = value }
    suspend fun setLastProjectPath(value: String) = context.dataStore.edit { it[LAST_PROJECT_PATH] = value }
    suspend fun setAllowUnverifiedPlugins(value: Boolean) = context.dataStore.edit { it[ALLOW_UNVERIFIED_PLUGINS] = value }
}
