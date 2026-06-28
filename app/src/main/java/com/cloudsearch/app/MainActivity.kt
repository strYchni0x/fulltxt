package me.fulltxt.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import me.fulltxt.app.data.cloud.googledrive.GoogleAuthManager
import me.fulltxt.app.data.cloud.onedrive.MsalAuthManager
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.data.preferences.ThemeMode
import me.fulltxt.app.ui.search.SearchScreen
import me.fulltxt.app.ui.settings.CloudAccountsScreen
import me.fulltxt.app.ui.settings.SettingsScreen
import me.fulltxt.app.ui.settings.SettingsViewModel
import me.fulltxt.app.ui.theme.FulltxtTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var msalAuthManager: MsalAuthManager
    @Inject lateinit var googleAuthManager: GoogleAuthManager
    @Inject lateinit var appPreferences: AppPreferences

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        googleAuthManager.handleSignInResult(result.resultCode, result.data)
    }

    // Result is intentionally ignored: the indexing foreground service runs either way;
    // if the user denies, the progress notification is simply not shown.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleAuthManager.setSignInLauncher(googleSignInLauncher)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()

        val powerManager = getSystemService(PowerManager::class.java)
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
        val initialShowDialog = !isIgnoring && !appPreferences.batteryOptimizationPromptShown

        setContent {
            val themeMode by appPreferences.themeModeFlow.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
            }
            FulltxtTheme(darkTheme = darkTheme) {
                var showBatteryDialog by remember { mutableStateOf(initialShowDialog) }

                if (showBatteryDialog) {
                    BatteryOptimizationDialog(
                        onConfirm = {
                            appPreferences.batteryOptimizationPromptShown = true
                            showBatteryDialog = false
                            // Open the system battery-optimization settings list (no special
                            // permission required). The direct ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                            // intent needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which Google Play
                            // restricts to a narrow set of app categories, so we avoid it.
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                        onDismiss = {
                            appPreferences.batteryOptimizationPromptShown = true
                            showBatteryDialog = false
                        }
                    )
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "search") {
                        composable("search") {
                            SearchScreen(
                                onSettingsClick = { navController.navigate("settings") }
                            )
                        }
                        navigation(startDestination = "settings_home", route = "settings") {
                            composable("settings_home") { entry ->
                                val parentEntry = remember(entry) {
                                    navController.getBackStackEntry("settings")
                                }
                                val vm: SettingsViewModel = hiltViewModel(parentEntry)
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onCloudAccountsClick = { navController.navigate("cloud_accounts") },
                                    viewModel = vm
                                )
                            }
                            composable("cloud_accounts") { entry ->
                                val parentEntry = remember(entry) {
                                    navController.getBackStackEntry("settings")
                                }
                                val vm: SettingsViewModel = hiltViewModel(parentEntry)
                                CloudAccountsScreen(
                                    onBack = { navController.popBackStack() },
                                    viewModel = vm
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * On Android 13+ the foreground-service progress notification is only visible if the
     * POST_NOTIFICATIONS runtime permission is granted. Request it once on startup; on older
     * versions the permission is implicit, so nothing happens.
     */
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        msalAuthManager.setActivity(this)
    }

    override fun onStop() {
        super.onStop()
        msalAuthManager.setActivity(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        googleAuthManager.setSignInLauncher(null)
    }
}

@Composable
private fun BatteryOptimizationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Akku-Optimierung deaktivieren") },
        text = {
            Text(
                "Damit die Indexierung im Hintergrund zuverlässig abläuft, " +
                "empfehlen wir, FullTXT von der Akku-Optimierung auszunehmen."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Jetzt einstellen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Nicht jetzt") }
        }
    )
}
