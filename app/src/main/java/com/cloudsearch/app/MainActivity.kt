package me.fulltxt.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import me.fulltxt.app.data.cloud.googledrive.GoogleAuthManager
import me.fulltxt.app.data.cloud.onedrive.MsalAuthManager
import me.fulltxt.app.data.preferences.AppPreferences
import me.fulltxt.app.ui.search.SearchScreen
import me.fulltxt.app.ui.settings.SettingsScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleAuthManager.setSignInLauncher(googleSignInLauncher)
        enableEdgeToEdge()

        val powerManager = getSystemService(PowerManager::class.java)
        val isIgnoring = powerManager.isIgnoringBatteryOptimizations(packageName)
        val initialShowDialog = !isIgnoring && !appPreferences.batteryOptimizationPromptShown

        setContent {
            FulltxtTheme {
                var showBatteryDialog by remember { mutableStateOf(initialShowDialog) }

                if (showBatteryDialog) {
                    BatteryOptimizationDialog(
                        onConfirm = {
                            appPreferences.batteryOptimizationPromptShown = true
                            showBatteryDialog = false
                            startActivity(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                            )
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
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
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
