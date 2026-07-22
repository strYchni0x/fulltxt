package me.fulltxt.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Festes Marken-Farbschema (Material You / dynamicColor bewusst deaktiviert),
// damit der Neon-Grün-auf-Dunkel-Look auf allen Geräten identisch bleibt.

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = BgDark,                 // dunkler Text auf Akzentgrün-Flächen
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = NeonGreen,
    secondary = NeonGreen,
    onSecondary = BgDark,
    secondaryContainer = GreenSecondaryContainerDark,
    onSecondaryContainer = OnGreenSecondaryContainerDark,
    tertiary = NeonGreen,
    onTertiary = BgDark,
    tertiaryContainer = GreenSecondaryContainerDark,
    onTertiaryContainer = OnGreenSecondaryContainerDark,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)

private val LightColorScheme = lightColorScheme(
    primary = GreenOnLight,
    onPrimary = Color.White,
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = Color(0xFF002109),
    secondary = GreenOnLight,
    onSecondary = Color.White,
    secondaryContainer = GreenSecondaryContainerLight,
    onSecondaryContainer = OnGreenSecondaryContainerLight,
    tertiary = GreenOnLight,
    onTertiary = Color.White,
    tertiaryContainer = GreenSecondaryContainerLight,
    onTertiaryContainer = OnGreenSecondaryContainerLight,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
)

@Composable
fun FulltxtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Feste Markenfarbe: kein Material You mehr.
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparente System-Bars (Edge-to-Edge); nur die Icon-Helligkeit
            // wird an das Theme angepasst, damit die Status-Icons lesbar bleiben.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
