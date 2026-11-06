package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "travel_track_settings")

data class AppSettings(
    val notifyDayBefore: Boolean = true,
    val notifyTwoHoursBefore: Boolean = true,
    val darkTheme: Boolean = true,
    val lastWidgetRefreshEpochMillis: Long = 0L,
    val autoCheckPnr: Boolean = false,
    val checkInFinal24Hours: Boolean = true,
    val showCheckForConfirmed: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val NOTIFY_DAY_BEFORE = booleanPreferencesKey("notify_day_before")
        val NOTIFY_TWO_HOURS_BEFORE = booleanPreferencesKey("notify_two_hours_before")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LAST_WIDGET_REFRESH = longPreferencesKey("last_widget_refresh")
        val AUTO_CHECK_PNR = booleanPreferencesKey("auto_check_pnr")
        val CHECK_IN_FINAL_24H = booleanPreferencesKey("check_in_final_24h")
        val SHOW_CHECK_CONFIRMED = booleanPreferencesKey("show_check_confirmed")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            notifyDayBefore = prefs[Keys.NOTIFY_DAY_BEFORE] ?: true,
            notifyTwoHoursBefore = prefs[Keys.NOTIFY_TWO_HOURS_BEFORE] ?: true,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            lastWidgetRefreshEpochMillis = prefs[Keys.LAST_WIDGET_REFRESH] ?: 0L,
            autoCheckPnr = prefs[Keys.AUTO_CHECK_PNR] ?: false,
            checkInFinal24Hours = prefs[Keys.CHECK_IN_FINAL_24H] ?: true,
            showCheckForConfirmed = prefs[Keys.SHOW_CHECK_CONFIRMED] ?: false
        )
    }

    suspend fun getSettingsSnapshot(): AppSettings = settingsFlow.first()

    suspend fun setNotifyDayBefore(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_DAY_BEFORE] = enabled }
    }

    suspend fun setNotifyTwoHoursBefore(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_TWO_HOURS_BEFORE] = enabled }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun recordWidgetRefresh(timestampMillis: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.LAST_WIDGET_REFRESH] = timestampMillis }
    }

    suspend fun setAutoCheckPnr(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_PNR] = enabled }
    }

    suspend fun setCheckInFinal24Hours(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHECK_IN_FINAL_24H] = enabled }
    }

    suspend fun setShowCheckForConfirmed(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_CHECK_CONFIRMED] = enabled }
    }
}
