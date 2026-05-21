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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
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
                    onDisconnect = { viewModel.disconnectAccount(state.account.accountId) }
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

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AccountCard(
    state: AccountUiState,
    onStartIndexing: () -> Unit,
    onDisconnect: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Cloud,
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
            } else {
                val statusText = when {
                    state.isFullyIndexed -> "${state.fileCount} Dateien indexiert"
                    state.fileCount > 0 -> "${state.fileCount} Dateien (Indexierung unvollständig)"
                    else -> "Noch nicht indexiert"
                }
                Text(statusText, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

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

private val CloudProvider.displayName get() = when (this) {
    CloudProvider.GOOGLE_DRIVE -> "Google Drive"
    CloudProvider.ONE_DRIVE -> "Microsoft OneDrive"
}
