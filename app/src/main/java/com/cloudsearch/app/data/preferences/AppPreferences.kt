package me.fulltxt.app.data.preferences

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around SharedPreferences for app-wide settings that are
 * not tied to a specific cloud account.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("fulltxt_prefs", Context.MODE_PRIVATE)

    /**
     * When true, the indexing WorkManager job runs on any network (including mobile data).
     * When false (default), the job is restricted to unmetered networks (WiFi / Ethernet).
     */
    var allowMeteredIndexing: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_METERED, false)
        set(value) = prefs.edit { putBoolean(KEY_ALLOW_METERED, value) }

    /** Returns true if the daily automatic delta sync is enabled for the given account. */
    fun isDailyDeltaEnabled(accountId: String): Boolean =
        prefs.getBoolean(dailyDeltaKey(accountId), false)

    /** Persists the daily delta sync preference for the given account. */
    fun setDailyDeltaEnabled(accountId: String, enabled: Boolean) =
        prefs.edit { putBoolean(dailyDeltaKey(accountId), enabled) }

    var batteryOptimizationPromptShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_OPT_PROMPT, false)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_OPT_PROMPT, value) }

    var recentSearches: List<String>
        get() = prefs.getString(KEY_RECENT_SEARCHES, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = prefs.edit { putString(KEY_RECENT_SEARCHES, value.joinToString("\n")) }

    /** How many recent searches to remember and show. Clamped to [MIN_RECENT, MAX_RECENT]. */
    var recentSearchLimit: Int
        get() = prefs.getInt(KEY_RECENT_LIMIT, DEFAULT_RECENT_LIMIT)
            .coerceIn(MIN_RECENT_LIMIT, MAX_RECENT_LIMIT)
        set(value) = prefs.edit {
            putInt(KEY_RECENT_LIMIT, value.coerceIn(MIN_RECENT_LIMIT, MAX_RECENT_LIMIT))
        }

    companion object {
        private const val KEY_ALLOW_METERED = "allow_metered_indexing"
        private const val KEY_BATTERY_OPT_PROMPT = "battery_opt_prompt_shown"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        private const val KEY_RECENT_LIMIT = "recent_search_limit"
        const val DEFAULT_RECENT_LIMIT = 5
        const val MIN_RECENT_LIMIT = 0
        const val MAX_RECENT_LIMIT = 10
        private fun dailyDeltaKey(accountId: String) = "daily_delta_$accountId"
    }
}
