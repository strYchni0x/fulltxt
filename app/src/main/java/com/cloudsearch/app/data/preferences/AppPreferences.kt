package me.fulltxt.app.data.preferences

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** App-weite Theme-Auswahl, unabhängig pro Konto. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Thin wrapper around SharedPreferences for app-wide settings that are
 * not tied to a specific cloud account.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("fulltxt_prefs", Context.MODE_PRIVATE)

    private fun readThemeMode(): ThemeMode =
        prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM

    private val _themeMode = MutableStateFlow(readThemeMode())

    /** Reactive theme selection so the Compose root can re-theme on change. */
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    var themeMode: ThemeMode
        get() = _themeMode.value
        set(value) {
            prefs.edit { putString(KEY_THEME_MODE, value.name) }
            _themeMode.value = value
        }

    private val _fileTypeIcons = MutableStateFlow(prefs.getBoolean(KEY_FILE_TYPE_ICONS, true))

    /** Reactive flag so the search screen updates live when toggled in settings. */
    val fileTypeIconsFlow: StateFlow<Boolean> = _fileTypeIcons.asStateFlow()

    /** When true, search results show a colored icon per file type instead of a generic one. */
    var fileTypeIcons: Boolean
        get() = _fileTypeIcons.value
        set(value) {
            prefs.edit { putBoolean(KEY_FILE_TYPE_ICONS, value) }
            _fileTypeIcons.value = value
        }

    private val _searchResultLimit = MutableStateFlow(
        prefs.getInt(KEY_SEARCH_LIMIT, DEFAULT_SEARCH_LIMIT).let {
            if (it in SEARCH_LIMIT_OPTIONS) it else DEFAULT_SEARCH_LIMIT
        }
    )

    /** Reactive max number of search results; search re-runs when this changes. */
    val searchResultLimitFlow: StateFlow<Int> = _searchResultLimit.asStateFlow()

    var searchResultLimit: Int
        get() = _searchResultLimit.value
        set(value) {
            val applied = if (value in SEARCH_LIMIT_OPTIONS) value else DEFAULT_SEARCH_LIMIT
            prefs.edit { putInt(KEY_SEARCH_LIMIT, applied) }
            _searchResultLimit.value = applied
        }

    /**
     * When true, the indexing WorkManager job runs on any network (including mobile data).
     * When false (default), the job is restricted to unmetered networks (WiFi / Ethernet).
     */
    var allowMeteredIndexing: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_METERED, false)
        set(value) = prefs.edit { putBoolean(KEY_ALLOW_METERED, value) }

    private val _ocrEnabled = MutableStateFlow(prefs.getBoolean(KEY_OCR_ENABLED, false))

    /** Reactive flag so settings reflects the OCR toggle live. */
    val ocrEnabledFlow: StateFlow<Boolean> = _ocrEnabled.asStateFlow()

    /**
     * When true, scanned/image-only PDFs are run through on-device OCR during indexing.
     * Off by default: OCR is significantly slower and more battery-intensive.
     */
    var ocrEnabled: Boolean
        get() = _ocrEnabled.value
        set(value) {
            prefs.edit { putBoolean(KEY_OCR_ENABLED, value) }
            _ocrEnabled.value = value
        }

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
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FILE_TYPE_ICONS = "file_type_icons"
        private const val KEY_SEARCH_LIMIT = "search_result_limit"
        private const val KEY_OCR_ENABLED = "ocr_enabled"
        const val DEFAULT_SEARCH_LIMIT = 100
        val SEARCH_LIMIT_OPTIONS = listOf(50, 100, 200, 500)
        const val DEFAULT_RECENT_LIMIT = 5
        const val MIN_RECENT_LIMIT = 0
        const val MAX_RECENT_LIMIT = 10
        private fun dailyDeltaKey(accountId: String) = "daily_delta_$accountId"
    }
}
