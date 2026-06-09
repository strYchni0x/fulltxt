package me.fulltxt.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.fulltxt.app.data.cloud.googledrive.GoogleAuthManager
import me.fulltxt.app.data.cloud.googledrive.GoogleDriveConnector
import me.fulltxt.app.data.backup.BackupManager
import me.fulltxt.app.data.cloud.local.LocalFolderAuthManager
import me.fulltxt.app.data.cloud.local.LocalFolderConnector
import me.fulltxt.app.data.cloud.dropbox.DropboxAuthManager
import me.fulltxt.app.data.cloud.dropbox.DropboxConnector
import me.fulltxt.app.data.cloud.magenta.MagentaCloudAuthManager
import me.fulltxt.app.data.cloud.magenta.MagentaCloudConnector
import me.fulltxt.app.data.cloud.magenta.MagentaCloudCredentials
import me.fulltxt.app.data.cloud.nextcloud.NextcloudAuthManager
import me.fulltxt.app.data.cloud.nextcloud.NextcloudConnector
import me.fulltxt.app.data.cloud.nextcloud.NextcloudCredentials
import me.fulltxt.app.data.cloud.onedrive.MsalAuthManager
import me.fulltxt.app.data.cloud.onedrive.OneDriveConnector
import me.fulltxt.app.data.cloud.owncloud.OwnCloudAuthManager
import me.fulltxt.app.data.cloud.owncloud.OwnCloudConnector
import me.fulltxt.app.data.cloud.owncloud.OwnCloudCredentials
import me.fulltxt.app.data.cloud.strato.StratoAuthManager
import me.fulltxt.app.data.cloud.strato.StratoConnector
import me.fulltxt.app.data.cloud.strato.StratoCredentials
import me.fulltxt.app.data.cloud.yandex.YandexAuthManager
import me.fulltxt.app.data.cloud.yandex.YandexConnector
import me.fulltxt.app.data.cloud.yandex.YandexCredentials
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.data.preferences.ThemeMode
import me.fulltxt.app.data.repository.IndexRepository
import me.fulltxt.app.domain.model.CloudAccount
import me.fulltxt.app.domain.model.CloudProvider
import me.fulltxt.app.domain.usecase.IndexFilesUseCase
import me.fulltxt.app.worker.IndexingWorker
import java.net.URI
import javax.inject.Inject

data class AccountUiState(
    val account: CloudAccount,
    val fileCount: Int = 0,
    val isFullyIndexed: Boolean = false,
    val dailyDeltaEnabled: Boolean = false,
    val workState: WorkInfo.State? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val progressErrors: Int = 0
) {
    val isRunning get() = workState == WorkInfo.State.RUNNING || workState == WorkInfo.State.ENQUEUED
    val progressFraction get() = if (progressTotal > 0) progressCurrent.toFloat() / progressTotal else 0f
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val indexRepository: IndexRepository,
    private val googleDriveConnector: GoogleDriveConnector,
    private val oneDriveConnector: OneDriveConnector,
    private val nextcloudConnector: NextcloudConnector,
    private val ownCloudConnector: OwnCloudConnector,
    private val dropboxConnector: DropboxConnector,
    private val magentaCloudConnector: MagentaCloudConnector,
    private val stratoConnector: StratoConnector,
    private val yandexConnector: YandexConnector,
    private val googleAuthManager: GoogleAuthManager,
    private val msalAuthManager: MsalAuthManager,
    private val nextcloudAuthManager: NextcloudAuthManager,
    private val ownCloudAuthManager: OwnCloudAuthManager,
    private val dropboxAuthManager: DropboxAuthManager,
    private val magentaCloudAuthManager: MagentaCloudAuthManager,
    private val stratoAuthManager: StratoAuthManager,
    private val yandexAuthManager: YandexAuthManager,
    private val localFolderConnector: LocalFolderConnector,
    private val localFolderAuthManager: LocalFolderAuthManager,
    private val backupManager: BackupManager,
    private val indexFilesUseCase: IndexFilesUseCase
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<AccountUiState>>(emptyList())
    val accounts: StateFlow<List<AccountUiState>> = _accounts.asStateFlow()

    private val _connectingProvider = MutableStateFlow<CloudProvider?>(null)
    val connectingProvider: StateFlow<CloudProvider?> = _connectingProvider.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    private val _allowMeteredIndexing = MutableStateFlow(appPreferences.allowMeteredIndexing)
    val allowMeteredIndexing: StateFlow<Boolean> = _allowMeteredIndexing.asStateFlow()

    private val _recentSearchLimit = MutableStateFlow(appPreferences.recentSearchLimit)
    val recentSearchLimit: StateFlow<Int> = _recentSearchLimit.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = appPreferences.themeModeFlow

    fun setThemeMode(mode: ThemeMode) {
        appPreferences.themeMode = mode
    }

    val fileTypeIcons: StateFlow<Boolean> = appPreferences.fileTypeIconsFlow

    fun setFileTypeIcons(enabled: Boolean) {
        appPreferences.fileTypeIcons = enabled
    }

    val searchResultLimit: StateFlow<Int> = appPreferences.searchResultLimitFlow
    val searchResultLimitOptions = AppPreferences.SEARCH_LIMIT_OPTIONS

    fun setSearchResultLimit(limit: Int) {
        appPreferences.searchResultLimit = limit
    }

    private val _exportSuccess = MutableSharedFlow<Unit>()
    val exportSuccess = _exportSuccess.asSharedFlow()

    fun exportIndex(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { _exportSuccess.emit(Unit) }
                .onFailure { _errorMessage.emit("Export fehlgeschlagen: ${it.localizedMessage ?: it.javaClass.simpleName}") }
        }
    }

    fun importIndex(uri: Uri) {
        viewModelScope.launch {
            runCatching { backupManager.importFrom(uri) }
                .onSuccess { backupManager.restartApp() }
                .onFailure { _errorMessage.emit("Import fehlgeschlagen: ${it.localizedMessage ?: it.javaClass.simpleName}") }
        }
    }

    private val _dbSizeBytes = MutableStateFlow(context.getDatabasePath("fulltxt.db").length())
    val dbSizeBytes: StateFlow<Long> = _dbSizeBytes.asStateFlow()

    fun setAllowMeteredIndexing(allow: Boolean) {
        appPreferences.allowMeteredIndexing = allow
        _allowMeteredIndexing.value = allow
    }

    fun setRecentSearchLimit(limit: Int) {
        appPreferences.recentSearchLimit = limit
        val applied = appPreferences.recentSearchLimit
        _recentSearchLimit.value = applied
        // Trim already-stored history so the search screen reflects the new limit immediately.
        appPreferences.recentSearches = appPreferences.recentSearches.take(applied)
    }

    val recentSearchLimitRange = AppPreferences.MIN_RECENT_LIMIT..AppPreferences.MAX_RECENT_LIMIT

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            // Restore MSAL's in-memory auth state from its encrypted token cache so
            // isAuthenticated() returns correct results without requiring a new sign-in.
            msalAuthManager.loadCachedAccounts()

            val accounts = indexRepository.getConnectedAccounts()
            _accounts.value = accounts.map { account ->
                AccountUiState(
                    account = account,
                    fileCount = indexRepository.getIndexedFileCount(account.accountId),
                    isFullyIndexed = indexRepository.isFullyIndexed(account.accountId),
                    dailyDeltaEnabled = appPreferences.isDailyDeltaEnabled(account.accountId)
                )
            }
            accounts.forEach { observeWork(it.accountId) }
            _dbSizeBytes.value = context.getDatabasePath("fulltxt.db").length()
        }
    }

    private fun observeWork(accountId: String) {
        WorkManager.getInstance(context)
            .getWorkInfosByTagFlow(IndexFilesUseCase.workTag(accountId))
            .onEach { workInfos ->
                val active = workInfos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
                val fileCount = if (active == null) indexRepository.getIndexedFileCount(accountId)
                                else _accounts.value.find { it.account.accountId == accountId }?.fileCount ?: 0
                val isFullyIndexed = if (active == null) indexRepository.isFullyIndexed(accountId)
                                     else false

                _accounts.update { list ->
                    list.map { state ->
                        if (state.account.accountId != accountId) state
                        else state.copy(
                            fileCount = fileCount,
                            isFullyIndexed = isFullyIndexed,
                            workState = active?.state,
                            progressCurrent = active?.progress?.getInt(IndexingWorker.PROGRESS_CURRENT, 0) ?: 0,
                            progressTotal = active?.progress?.getInt(IndexingWorker.PROGRESS_TOTAL, 0) ?: 0,
                            progressErrors = active?.progress?.getInt(IndexingWorker.PROGRESS_ERRORS, 0) ?: 0
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun connectAccount(provider: CloudProvider) {
        viewModelScope.launch {
            _connectingProvider.value = provider
            try {
                val account: CloudAccount? = when (provider) {
                    CloudProvider.GOOGLE_DRIVE -> {
                        googleDriveConnector.authenticate("")
                        googleAuthManager.lastSignedInAccount?.let { g ->
                            CloudAccount(
                                accountId = g.email ?: return@launch,
                                provider = CloudProvider.GOOGLE_DRIVE,
                                displayName = g.displayName ?: g.email ?: "",
                                email = g.email ?: ""
                            )
                        }
                    }
                    CloudProvider.ONE_DRIVE -> {
                        oneDriveConnector.authenticate("")
                        msalAuthManager.lastSignedInAccount?.let { m ->
                            CloudAccount(
                                accountId = m.id,
                                provider = CloudProvider.ONE_DRIVE,
                                displayName = m.username ?: m.id,
                                email = m.username ?: ""
                            )
                        }
                    }
                    // Nextcloud/ownCloud use dedicated connect functions with credential dialogs.
                    CloudProvider.NEXTCLOUD -> null
                    CloudProvider.OWNCLOUD       -> null
                    CloudProvider.MAGENTA_CLOUD  -> null
                    CloudProvider.STRATO_HIDRIVE -> null
                    // Yandex Disk uses a dedicated connect function with a credential dialog.
                    CloudProvider.YANDEX_DISK    -> null
                    // Local folders are connected via connectLocalFolder() with a URI.
                    CloudProvider.LOCAL -> null
                    // Dropbox uses a browser OAuth flow triggered via connectDropbox().
                    CloudProvider.DROPBOX -> {
                        dropboxConnector.authenticate("")
                        dropboxAuthManager.lastSignedInAccount?.let { d ->
                            CloudAccount(
                                accountId   = d.accountId,
                                provider    = CloudProvider.DROPBOX,
                                displayName = d.displayName,
                                email       = d.email
                            )
                        }
                    }
                }

                if (account != null) {
                    indexRepository.saveAccount(account)
                    // Guard against duplicate: account may already be in the list if the user
                    // re-authenticated (e.g. expired token) while the account was loaded from DB.
                    val alreadyPresent = _accounts.value.any { it.account.accountId == account.accountId }
                    if (!alreadyPresent) {
                        _accounts.update { it + AccountUiState(account = account) }
                        observeWork(account.accountId)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.emit("Verbindung fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _connectingProvider.value = null
            }
        }
    }

    /** Validates ownCloud credentials and persists the account. */
    fun connectOwnCloud(serverUrl: String, username: String, appPassword: String) {
        viewModelScope.launch {
            _connectingProvider.value = CloudProvider.OWNCLOUD
            try {
                val normalizedUrl = serverUrl.trim().trimEnd('/')
                val host = runCatching { URI(normalizedUrl).host }.getOrElse { normalizedUrl }
                val accountId = "$username@$host"

                ownCloudAuthManager.saveCredentials(
                    accountId,
                    OwnCloudCredentials(normalizedUrl, username, appPassword)
                )
                try {
                    ownCloudConnector.authenticate(accountId)
                } catch (e: Exception) {
                    ownCloudAuthManager.removeCredentials(accountId)
                    throw e
                }

                val account = CloudAccount(
                    accountId   = accountId,
                    provider    = CloudProvider.OWNCLOUD,
                    displayName = username,
                    email       = "$username@$host"
                )
                indexRepository.saveAccount(account)
                if (_accounts.value.none { it.account.accountId == accountId }) {
                    _accounts.update { it + AccountUiState(account = account) }
                    observeWork(accountId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.emit("ownCloud-Verbindung fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _connectingProvider.value = null
            }
        }
    }

    /** Validates MagentaCloud credentials and persists the account. */
    fun connectMagentaCloud(serverUrl: String, username: String, appPassword: String) {
        viewModelScope.launch {
            _connectingProvider.value = CloudProvider.MAGENTA_CLOUD
            try {
                val normalizedUrl = serverUrl.trim().trimEnd('/')
                val host = runCatching { URI(normalizedUrl).host }.getOrElse { normalizedUrl }
                val accountId = "$username@$host"

                magentaCloudAuthManager.saveCredentials(
                    accountId,
                    MagentaCloudCredentials(normalizedUrl, username, appPassword)
                )
                try {
                    magentaCloudConnector.authenticate(accountId)
                } catch (e: Exception) {
                    magentaCloudAuthManager.removeCredentials(accountId)
                    throw e
                }

                val account = CloudAccount(
                    accountId   = accountId,
                    provider    = CloudProvider.MAGENTA_CLOUD,
                    displayName = username,
                    email       = "$username@$host"
                )
                indexRepository.saveAccount(account)
                if (_accounts.value.none { it.account.accountId == accountId }) {
                    _accounts.update { it + AccountUiState(account = account) }
                    observeWork(accountId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.emit("MagentaCloud-Verbindung fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _connectingProvider.value = null
            }
        }
    }

    /** Validates Strato HiDrive credentials and persists the account. */
    fun connectStrato(username: String, password: String) {
        viewModelScope.launch {
            _connectingProvider.value = CloudProvider.STRATO_HIDRIVE
            try {
                val accountId = "$username@hidrive.strato.com"

                stratoAuthManager.saveCredentials(accountId, StratoCredentials(username, password))
                try {
                    stratoConnector.authenticate(accountId)
                } catch (e: Exception) {
                    stratoAuthManager.removeCredentials(accountId)
                    throw e
                }

                val account = CloudAccount(
                    accountId   = accountId,
                    provider    = CloudProvider.STRATO_HIDRIVE,
                    displayName = username,
                    email       = "$username@hidrive.strato.com"
                )
                indexRepository.saveAccount(account)
                if (_accounts.value.none { it.account.accountId == accountId }) {
                    _accounts.update { it + AccountUiState(account = account) }
                    observeWork(accountId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.emit("Strato HiDrive-Verbindung fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _connectingProvider.value = null
            }
        }
    }

    /** Validates Yandex Disk credentials via a test PROPFIND, then persists the account. */
    fun connectYandex(username: String, password: String) {
        viewModelScope.launch {
            _connectingProvider.value = CloudProvider.YANDEX_DISK
            try {
                val accountId = "$username@yandex"

                yandexAuthManager.saveCredentials(accountId, YandexCredentials(username, password))
                try {
                    yandexConnector.authenticate(accountId)
                } catch (e: Exception) {
                    yandexAuthManager.removeCredentials(accountId)
                    throw e
                }

                val account = CloudAccount(
                    accountId   = accountId,
                    provider    = CloudProvider.YANDEX_DISK,
                    displayName = username,
                    email       = username
                )
                indexRepository.saveAccount(account)
                if (_accounts.value.none { it.account.accountId == accountId }) {
                    _accounts.update { it + AccountUiState(account = account) }
                    observeWork(accountId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.emit("Yandex Disk-Verbindung fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _connectingProvider.value = null
            }
        }
    }

    /**
     * Validates Nextcloud credentials via a test PROPFIND, then persists the account.
     * Called from the settings dialog after the user enters server URL, username and app password.
     */
    fun connectNextcloud(serverUrl: String, username: String, appPassword: String) {
        viewModelScope.launch {
            _connectingProvider.value = CloudProvider.NEXTCLOUD
            try {
                val normalizedUrl = serverUrl.trim().trimEnd('/')
                // Derive a stable, human-readable account ID
                val host = runCatching { URI(normalizedUrl).host }.getOrElse { normalizedUrl }
                val accountId = "$username@$host"

                val credentials = NextcloudCredentials(normalizedUrl, username, appPassword)
                nextcloudAuthManager.saveCredentials(accountId, credentials)

                // Validate by doing a test PROPFIND (throws on auth failure / unreachable)
                try {
                    nextcloudConnector.authenticate(accountId)
                } catch (e: Exception) {
                    nextcloudAuthManager.removeCredentials(accountId)
                    throw e
                }

                val account = CloudAccount(
                    accountId   = accountId,
                    provider    = CloudProvider.NEXTCLOUD,
                    displayName = username,
                    email       = "$username@$host"
                )
                indexRepository.saveAccount(account)
                if (_accounts.value.none { it.account.accountId == accountId }) {
                    _accounts.update { it + AccountUiState(account = account) }
                    observeWork(accountId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.emit("Nextcloud-Verbindung fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _connectingProvider.value = null
            }
        }
    }

    fun startIndexing(account: CloudAccount) {
        indexFilesUseCase.scheduleInitialIndexing(account.accountId, account.provider)
    }

    fun toggleDailyDelta(account: CloudAccount, enabled: Boolean) {
        appPreferences.setDailyDeltaEnabled(account.accountId, enabled)
        if (enabled) {
            indexFilesUseCase.scheduleDailyDelta(account.accountId, account.provider)
        } else {
            indexFilesUseCase.cancelDailyDelta(account.accountId)
        }
        _accounts.update { list ->
            list.map { state ->
                if (state.account.accountId == account.accountId) state.copy(dailyDeltaEnabled = enabled)
                else state
            }
        }
    }

    fun connectLocalFolder(treeUri: Uri) {
        viewModelScope.launch {
            _connectingProvider.value = CloudProvider.LOCAL
            try {
                localFolderAuthManager.addFolder(treeUri)
                val accountId = treeUri.toString()
                val account = CloudAccount(
                    accountId   = accountId,
                    provider    = CloudProvider.LOCAL,
                    displayName = localFolderAuthManager.getFolderName(treeUri),
                    email       = ""
                )
                indexRepository.saveAccount(account)
                if (_accounts.value.none { it.account.accountId == accountId }) {
                    _accounts.update { it + AccountUiState(account = account) }
                    observeWork(accountId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.emit("Ordner konnte nicht hinzugefügt werden: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _connectingProvider.value = null
            }
        }
    }

    fun disconnectAccount(accountId: String) {
        viewModelScope.launch {
            val state = _accounts.value.find { it.account.accountId == accountId } ?: return@launch
            // Cancel any running or enqueued indexing jobs (one-time + periodic daily delta)
            WorkManager.getInstance(context).cancelAllWorkByTag(IndexFilesUseCase.workTag(accountId))
            indexFilesUseCase.cancelDailyDelta(accountId)
            appPreferences.setDailyDeltaEnabled(accountId, false)
            when (state.account.provider) {
                CloudProvider.GOOGLE_DRIVE -> googleDriveConnector.signOut(accountId)
                CloudProvider.ONE_DRIVE    -> oneDriveConnector.signOut(accountId)
                CloudProvider.NEXTCLOUD    -> nextcloudConnector.signOut(accountId)
                CloudProvider.OWNCLOUD       -> ownCloudConnector.signOut(accountId)
                CloudProvider.DROPBOX        -> dropboxConnector.signOut(accountId)
                CloudProvider.MAGENTA_CLOUD  -> magentaCloudConnector.signOut(accountId)
                CloudProvider.STRATO_HIDRIVE -> stratoConnector.signOut(accountId)
                CloudProvider.YANDEX_DISK    -> yandexConnector.signOut(accountId)
                CloudProvider.LOCAL          -> localFolderConnector.signOut(accountId)
            }
            indexRepository.deleteAllByAccount(accountId)
            indexRepository.removeAccount(accountId)
            _accounts.update { list -> list.filter { it.account.accountId != accountId } }
        }
    }
}
