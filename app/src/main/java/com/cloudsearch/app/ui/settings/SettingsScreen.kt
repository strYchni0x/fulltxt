package me.fulltxt.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import me.fulltxt.app.domain.model.CloudAccount
import me.fulltxt.app.domain.model.CloudProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val connectingProvider by viewModel.connectingProvider.collectAsState()
    val allowMeteredIndexing by viewModel.allowMeteredIndexing.collectAsState()
    val dbSizeBytes by viewModel.dbSizeBytes.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.connectLocalFolder(it) } }
    val snackbarHostState = remember { SnackbarHostState() }
    var showNextcloudDialog   by rememberSaveable { mutableStateOf(false) }
    var showOwnCloudDialog    by rememberSaveable { mutableStateOf(false) }
    var showMagentaDialog     by rememberSaveable { mutableStateOf(false) }
    var showStratoDialog      by rememberSaveable { mutableStateOf(false) }
    var showMeteredWarning    by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (showNextcloudDialog) {
        NextcloudConnectDialog(
            isConnecting = connectingProvider == CloudProvider.NEXTCLOUD,
            onDismiss    = { showNextcloudDialog = false },
            onConfirm    = { url, user, pass ->
                showNextcloudDialog = false
                viewModel.connectNextcloud(url, user, pass)
            }
        )
    }

    if (showOwnCloudDialog) {
        OwnCloudConnectDialog(
            isConnecting = connectingProvider == CloudProvider.OWNCLOUD,
            onDismiss    = { showOwnCloudDialog = false },
            onConfirm    = { url, user, pass ->
                showOwnCloudDialog = false
                viewModel.connectOwnCloud(url, user, pass)
            }
        )
    }

    if (showMagentaDialog) {
        WebDavConnectDialog(
            title          = "MagentaCloud verbinden",
            hint           = "Verwende ein App-Passwort (MagentaCloud → Einstellungen → Sicherheit).",
            prefillUrl     = "https://magentacloud.de",
            isConnecting   = connectingProvider == CloudProvider.MAGENTA_CLOUD,
            onDismiss      = { showMagentaDialog = false },
            onConfirm      = { url, user, pass ->
                showMagentaDialog = false
                viewModel.connectMagentaCloud(url, user, pass)
            }
        )
    }

    if (showStratoDialog) {
        StratoConnectDialog(
            isConnecting = connectingProvider == CloudProvider.STRATO_HIDRIVE,
            onDismiss    = { showStratoDialog = false },
            onConfirm    = { user, pass ->
                showStratoDialog = false
                viewModel.connectStrato(user, pass)
            }
        )
    }

    if (showMeteredWarning) {
        AlertDialog(
            onDismissRequest = { showMeteredWarning = false },
            title = { Text("Mobilfunk-Indexierung aktivieren?") },
            text = {
                Text(
                    "Die Indexierung großer Cloud-Accounts kann mehrere Gigabyte Datenvolumen " +
                    "verbrauchen. Beim nächsten Indexieren wird auch das Mobilfunknetz (5G/LTE) " +
                    "genutzt.\n\nBitte achte auf dein Datenkontingent."
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setAllowMeteredIndexing(true)
                    showMeteredWarning = false
                }) { Text("Aktivieren") }
            },
            dismissButton = {
                TextButton(onClick = { showMeteredWarning = false }) { Text("Abbrechen") }
            }
        )
    }

    val connectedProviders = accounts.map { it.account.provider }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Cloud-Accounts", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }

            if (accounts.isEmpty()) {
                item {
                    Text(
                        "Noch kein Account verbunden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(accounts, key = { it.account.accountId }) { state ->
                AccountCard(
                    state = state,
                    onStartIndexing = { viewModel.startIndexing(state.account) },
                    onDisconnect = { viewModel.disconnectAccount(state.account.accountId) },
                    onToggleDailyDelta = { enabled -> viewModel.toggleDailyDelta(state.account, enabled) }
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            if (CloudProvider.GOOGLE_DRIVE !in connectedProviders) {
                item {
                    ConnectButton(
                        label = "Google Drive verbinden",
                        isConnecting = connectingProvider == CloudProvider.GOOGLE_DRIVE,
                        onClick = { viewModel.connectAccount(CloudProvider.GOOGLE_DRIVE) }
                    )
                }
            }

            if (CloudProvider.ONE_DRIVE !in connectedProviders) {
                item {
                    ConnectButton(
                        label = "OneDrive verbinden",
                        isConnecting = connectingProvider == CloudProvider.ONE_DRIVE,
                        onClick = { viewModel.connectAccount(CloudProvider.ONE_DRIVE) }
                    )
                }
            }

            if (CloudProvider.NEXTCLOUD !in connectedProviders) {
                item {
                    ConnectButton(
                        label = "Nextcloud verbinden",
                        isConnecting = connectingProvider == CloudProvider.NEXTCLOUD,
                        onClick = { showNextcloudDialog = true }
                    )
                }
            }

            if (CloudProvider.OWNCLOUD !in connectedProviders) {
                item {
                    ConnectButton(
                        label = "ownCloud verbinden",
                        isConnecting = connectingProvider == CloudProvider.OWNCLOUD,
                        onClick = { showOwnCloudDialog = true }
                    )
                }
            }

            if (CloudProvider.MAGENTA_CLOUD !in connectedProviders) {
                item {
                    ConnectButton(
                        label = "MagentaCloud verbinden",
                        isConnecting = connectingProvider == CloudProvider.MAGENTA_CLOUD,
                        onClick = { showMagentaDialog = true }
                    )
                }
            }

            if (CloudProvider.STRATO_HIDRIVE !in connectedProviders) {
                item {
                    ConnectButton(
                        label = "Strato HiDrive verbinden",
                        isConnecting = connectingProvider == CloudProvider.STRATO_HIDRIVE,
                        onClick = { showStratoDialog = true }
                    )
                }
            }

            if (CloudProvider.DROPBOX !in connectedProviders) {
                item {
                    ConnectButton(
                        label = "Dropbox verbinden",
                        isConnecting = connectingProvider == CloudProvider.DROPBOX,
                        onClick = { viewModel.connectAccount(CloudProvider.DROPBOX) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Lokale Ordner",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    enabled = connectingProvider == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ordner auswählen…")
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        enabled = connectingProvider == null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Downloads") }
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        enabled = connectingProvider == null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Dokumente") }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Indexierung",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Mobilfunk erlauben") },
                    supportingContent = {
                        Text(
                            if (allowMeteredIndexing)
                                "Indexierung läuft auch über mobile Daten (5G/LTE)."
                            else
                                "Indexierung nur über WLAN (Standard)."
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = allowMeteredIndexing,
                            onCheckedChange = { enabled ->
                                if (enabled) showMeteredWarning = true
                                else viewModel.setAllowMeteredIndexing(false)
                            }
                        )
                    }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Speicher",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                val totalFiles = accounts.sumOf { it.fileCount }
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Storage, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    headlineContent = { Text("Index-Datenbank") },
                    supportingContent = {
                        Text(
                            buildString {
                                append(formatDbSize(dbSizeBytes))
                                if (totalFiles > 0) append(" · $totalFiles Dateien")
                            }
                        )
                    }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AccountCard(
    state: AccountUiState,
    onStartIndexing: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleDailyDelta: (Boolean) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.account.provider == CloudProvider.LOCAL) Icons.Default.Folder
                    else Icons.Default.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        state.account.provider.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        state.account.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (state.isRunning) {
                val progress = state.progressFraction
                if (state.progressTotal > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${state.progressCurrent} / ${state.progressTotal} Dateien" +
                            if (state.progressErrors > 0) " (${state.progressErrors} Fehler)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Wird vorbereitet…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Trennen")
                }
            } else {
                val statusText = when {
                    state.isFullyIndexed -> "${state.fileCount} Dateien indexiert"
                    state.fileCount > 0 -> "${state.fileCount} Dateien (Indexierung unvollständig)"
                    else -> "Noch nicht indexiert"
                }
                Text(statusText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Daily delta toggle — only available after first full index
                if (state.isFullyIndexed) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tägl. Delta-Sync",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (state.dailyDeltaEnabled) "Läuft automatisch einmal täglich"
                                else "Automatische Aktualisierung deaktiviert",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.dailyDeltaEnabled,
                            onCheckedChange = onToggleDailyDelta
                        )
                    }
                    HorizontalDivider()
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartIndexing, modifier = Modifier.weight(1f)) {
                        Text(if (state.isFullyIndexed) "Neu indexieren" else "Indexieren")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Trennen")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectButton(
    label: String,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isConnecting,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isConnecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(8.dp))
            Text("Verbinde…")
        } else {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

/**
 * Generischer WebDAV-Verbindungsdialog (Server-URL + Benutzername + App-Passwort).
 * Wird für MagentaCloud und ähnliche Nextcloud-basierte Dienste mit bekannter Standard-URL genutzt.
 */
@Composable
private fun WebDavConnectDialog(
    title: String,
    hint: String,
    prefillUrl: String = "https://",
    isConnecting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (serverUrl: String, username: String, appPassword: String) -> Unit
) {
    var serverUrl       by rememberSaveable(prefillUrl) { mutableStateOf(prefillUrl) }
    var username        by rememberSaveable { mutableStateOf("") }
    var appPassword     by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val canConfirm = serverUrl.length > 8 && username.isNotBlank() && appPassword.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = serverUrl, onValueChange = { serverUrl = it },
                    label = { Text("Server-URL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Benutzername") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appPassword, onValueChange = { appPassword = it },
                    label = { Text("App-Passwort") }, singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(serverUrl.trim(), username.trim(), appPassword) },
                   enabled = canConfirm && !isConnecting) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isConnecting) "Verbinde…" else "Verbinden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConnecting) { Text("Abbrechen") }
        }
    )
}

/**
 * Strato-spezifischer Dialog: Server-URL ist fix (webdav.hidrive.strato.com),
 * daher werden nur Benutzername und Passwort abgefragt.
 */
@Composable
private fun StratoConnectDialog(
    isConnecting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (username: String, password: String) -> Unit
) {
    var username        by rememberSaveable { mutableStateOf("") }
    var password        by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val canConfirm = username.isNotBlank() && password.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text("Strato HiDrive verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Server: webdav.hidrive.strato.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("Strato-Benutzername") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Passwort") }, singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(username.trim(), password) },
                   enabled = canConfirm && !isConnecting) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isConnecting) "Verbinde…" else "Verbinden")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConnecting) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun OwnCloudConnectDialog(
    isConnecting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (serverUrl: String, username: String, appPassword: String) -> Unit
) {
    var serverUrl       by rememberSaveable { mutableStateOf("https://") }
    var username        by rememberSaveable { mutableStateOf("") }
    var appPassword     by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val canConfirm = serverUrl.length > 8 && username.isNotBlank() && appPassword.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text("ownCloud verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Verwende ein App-Passwort (ownCloud → Einstellungen → Sicherheit).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server-URL") },
                    placeholder = { Text("https://cloud.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Benutzername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appPassword,
                    onValueChange = { appPassword = it },
                    label = { Text("App-Passwort") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Passwort verbergen"
                                                     else "Passwort anzeigen"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(serverUrl.trim(), username.trim(), appPassword) },
                enabled = canConfirm && !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Verbinde…")
                } else {
                    Text("Verbinden")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConnecting) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun NextcloudConnectDialog(
    isConnecting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (serverUrl: String, username: String, appPassword: String) -> Unit
) {
    var serverUrl   by rememberSaveable { mutableStateOf("https://") }
    var username    by rememberSaveable { mutableStateOf("") }
    var appPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val canConfirm = serverUrl.length > 8 && username.isNotBlank() && appPassword.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = { Text("Nextcloud verbinden") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bitte verwende ein App-Passwort (Nextcloud → Einstellungen → Sicherheit).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server-URL") },
                    placeholder = { Text("https://cloud.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Benutzername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = appPassword,
                    onValueChange = { appPassword = it },
                    label = { Text("App-Passwort") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Passwort verbergen"
                                                     else "Passwort anzeigen"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(serverUrl.trim(), username.trim(), appPassword) },
                enabled = canConfirm && !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Verbinde…")
                } else {
                    Text("Verbinden")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isConnecting) {
                Text("Abbrechen")
            }
        }
    )
}

private fun formatDbSize(bytes: Long): String = when {
    bytes <= 0L          -> "0 B"
    bytes < 1024L        -> "$bytes B"
    bytes < 1024L * 1024 -> "${bytes / 1024} KB"
    else                 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

private val CloudProvider.displayName get() = when (this) {
    CloudProvider.GOOGLE_DRIVE   -> "Google Drive"
    CloudProvider.ONE_DRIVE      -> "Microsoft OneDrive"
    CloudProvider.NEXTCLOUD      -> "Nextcloud"
    CloudProvider.OWNCLOUD       -> "ownCloud"
    CloudProvider.DROPBOX        -> "Dropbox"
    CloudProvider.MAGENTA_CLOUD  -> "MagentaCloud"
    CloudProvider.STRATO_HIDRIVE -> "Strato HiDrive"
    CloudProvider.LOCAL          -> "Lokaler Ordner"
}
