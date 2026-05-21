package me.fulltxt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import me.fulltxt.app.data.cloud.googledrive.GoogleAuthManager
import me.fulltxt.app.data.cloud.onedrive.MsalAuthManager
import me.fulltxt.app.ui.search.SearchScreen
import me.fulltxt.app.ui.settings.SettingsScreen
import me.fulltxt.app.ui.theme.FulltxtTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var msalAuthManager: MsalAuthManager
    @Inject lateinit var googleAuthManager: GoogleAuthManager

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        googleAuthManager.handleSignInResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleAuthManager.setSignInLauncher(googleSignInLauncher)
        enableEdgeToEdge()
        setContent {
            FulltxtTheme {
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
