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

    companion object {
        private const val KEY_ALLOW_METERED = "allow_metered_indexing"
        private fun dailyDeltaKey(accountId: String) = "daily_delta_$accountId"
    }
}
