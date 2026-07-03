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
 * Dünner Wrapper um SharedPreferences für app-weite Einstellungen, die nicht
 * an ein bestimmtes Cloud-Konto gebunden sind.
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

    /** Reaktive Theme-Auswahl, damit der Compose-Root bei Änderung neu themen kann. */
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    var themeMode: ThemeMode
        get() = _themeMode.value
        set(value) {
            prefs.edit { putString(KEY_THEME_MODE, value.name) }
            _themeMode.value = value
        }

    private val _fileTypeIcons = MutableStateFlow(prefs.getBoolean(KEY_FILE_TYPE_ICONS, true))

    /** Reaktives Flag, damit der Suchbildschirm live aktualisiert, wenn es in den Einstellungen umgeschaltet wird. */
    val fileTypeIconsFlow: StateFlow<Boolean> = _fileTypeIcons.asStateFlow()

    /** Wenn true, zeigen Suchergebnisse ein farbiges Symbol pro Dateityp statt eines generischen. */
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

    /** Reaktive maximale Anzahl an Suchergebnissen; die Suche läuft bei Änderung erneut. */
    val searchResultLimitFlow: StateFlow<Int> = _searchResultLimit.asStateFlow()

    var searchResultLimit: Int
        get() = _searchResultLimit.value
        set(value) {
            val applied = if (value in SEARCH_LIMIT_OPTIONS) value else DEFAULT_SEARCH_LIMIT
            prefs.edit { putInt(KEY_SEARCH_LIMIT, applied) }
            _searchResultLimit.value = applied
        }

    /**
     * Wenn true, läuft der Indexierungs-WorkManager-Job in jedem Netzwerk (auch mobile Daten).
     * Wenn false (Standard), ist der Job auf ungetaktete Netzwerke beschränkt (WLAN / Ethernet).
     */
    var allowMeteredIndexing: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_METERED, false)
        set(value) = prefs.edit { putBoolean(KEY_ALLOW_METERED, value) }

    private val _ocrEnabled = MutableStateFlow(prefs.getBoolean(KEY_OCR_ENABLED, false))

    /** Reaktives Flag, damit die Einstellungen den OCR-Schalter live widerspiegeln. */
    val ocrEnabledFlow: StateFlow<Boolean> = _ocrEnabled.asStateFlow()

    /**
     * Wenn true, werden gescannte/reine Bild-PDFs während der Indexierung durch On-Device-OCR
     * verarbeitet. Standardmäßig aus: OCR ist deutlich langsamer und akkuintensiver.
     */
    var ocrEnabled: Boolean
        get() = _ocrEnabled.value
        set(value) {
            prefs.edit { putBoolean(KEY_OCR_ENABLED, value) }
            _ocrEnabled.value = value
        }

    private val _maxFileSizeMb = MutableStateFlow(
        prefs.getInt(KEY_MAX_FILE_SIZE_MB, DEFAULT_MAX_FILE_SIZE_MB).let {
            if (it in MAX_FILE_SIZE_OPTIONS) it else DEFAULT_MAX_FILE_SIZE_MB
        }
    )

    /** Reaktive maximale Dateigröße (in MB) für die Indexierung; größere Dateien werden übersprungen. */
    val maxFileSizeMbFlow: StateFlow<Int> = _maxFileSizeMb.asStateFlow()

    /**
     * Dateien größer als dies (in MB) werden bei der Indexierung übersprungen, um zu vermeiden, dass
     * riesige Dateien in den Speicher geladen werden. Konfigurierbar; höhere Werte brauchen mehr RAM
     * während der Indexierung.
     */
    var maxFileSizeMb: Int
        get() = _maxFileSizeMb.value
        set(value) {
            val applied = if (value in MAX_FILE_SIZE_OPTIONS) value else DEFAULT_MAX_FILE_SIZE_MB
            prefs.edit { putInt(KEY_MAX_FILE_SIZE_MB, applied) }
            _maxFileSizeMb.value = applied
        }

    /** Gibt true zurück, wenn der tägliche automatische Delta-Sync für das angegebene Konto aktiviert ist. */
    fun isDailyDeltaEnabled(accountId: String): Boolean =
        prefs.getBoolean(dailyDeltaKey(accountId), false)

    /** Persistiert die Einstellung für den täglichen Delta-Sync des angegebenen Kontos. */
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

    /** Wie viele letzte Suchanfragen gemerkt und angezeigt werden. Begrenzt auf [MIN_RECENT, MAX_RECENT]. */
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
        private const val KEY_MAX_FILE_SIZE_MB = "max_file_size_mb"
        const val DEFAULT_SEARCH_LIMIT = 100
        val SEARCH_LIMIT_OPTIONS = listOf(50, 100, 200, 500)
        const val DEFAULT_MAX_FILE_SIZE_MB = 50
        val MAX_FILE_SIZE_OPTIONS = listOf(25, 50, 100, 250, 500)
        const val DEFAULT_RECENT_LIMIT = 5
        const val MIN_RECENT_LIMIT = 0
        const val MAX_RECENT_LIMIT = 10
        private fun dailyDeltaKey(accountId: String) = "daily_delta_$accountId"
    }
}
