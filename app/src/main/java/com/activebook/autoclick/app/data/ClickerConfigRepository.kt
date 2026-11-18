package com.activebook.autoclick.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.activebook.autoclick.app.domain.model.ClickerConfig
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.clickerDataStore: DataStore<Preferences> by preferencesDataStore(name = "clicker_config")

/** Persisted bubble position, in screen pixels. */
data class OverlayPosition(val x: Int, val y: Int)

/**
 * Persists the user's target cell, interval and preferences, plus the floating bubble position.
 *
 * Every read goes through [ClickerConfig.of], so a malformed or partially written store can
 * never produce an invalid interval or an out-of-range cell.
 */
class ClickerConfigRepository(context: Context) {

    private val dataStore = context.applicationContext.clickerDataStore

    val config: Flow<ClickerConfig> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> preferences.toConfig() }

    val bubblePosition: Flow<OverlayPosition?> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences ->
            val x = preferences[Keys.BUBBLE_X]
            val y = preferences[Keys.BUBBLE_Y]
            if (x != null && y != null && x >= 0 && y >= 0) OverlayPosition(x, y) else null
        }

    suspend fun updateConfig(config: ClickerConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.CELL_INDEX] = config.cellIndex
            preferences[Keys.INTERVAL_MS] = config.intervalMs
            preferences[Keys.STOP_AFTER_TAPS] = config.stopAfterTaps
            preferences[Keys.HAPTIC] = config.hapticOnTap
            preferences[Keys.FLASH] = config.flashOnTap
        }
    }

    suspend fun saveBubblePosition(x: Int, y: Int) {
        dataStore.edit { preferences ->
            preferences[Keys.BUBBLE_X] = x
            preferences[Keys.BUBBLE_Y] = y
        }
    }

    private fun Preferences.toConfig(): ClickerConfig = ClickerConfig.of(
        cellIndex = this[Keys.CELL_INDEX] ?: ClickerConfig.DEFAULT_CELL_INDEX,
        intervalMs = this[Keys.INTERVAL_MS] ?: ClickerConfig.DEFAULT_INTERVAL_MS,
        stopAfterTaps = this[Keys.STOP_AFTER_TAPS] ?: ClickerConfig.DEFAULT_STOP_AFTER_TAPS,
        hapticOnTap = this[Keys.HAPTIC] ?: true,
        flashOnTap = this[Keys.FLASH] ?: true,
    )

    private object Keys {
        val CELL_INDEX = intPreferencesKey("cell_index")
        val INTERVAL_MS = longPreferencesKey("interval_ms")
        val STOP_AFTER_TAPS = intPreferencesKey("stop_after_taps")
        val HAPTIC = booleanPreferencesKey("haptic_on_tap")
        val FLASH = booleanPreferencesKey("flash_on_tap")
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
    }
}
