package me.fulltxt.app.ui.theme

import androidx.compose.ui.graphics.Color

// FullTXT Markenfarben — abgeleitet aus den Design Guidelines (OKLCH → sRGB).
// Ein einziger Akzentton (Neon-Grün) auf fast-schwarzem, technisch-präzisem Grund;
// kein Verlauf, keine Zweitfarbe.

// Akzent (Neon-Grün) — oklch(0.85 0.19 150)
val NeonGreen = Color(0xFF5DEF88)
// Akzent auf Hellgrund — oklch(0.4 0.14 150), für Kontrast auf weißen Flächen
val GreenOnLight = Color(0xFF005B16)
val GreenContainerDark = Color(0xFF0E3D20)
val GreenContainerLight = Color(0xFF7BF8A0)

// Grün getönte Container (ausgewählte Chips/Segmente etc.) statt der
// lila Material-Baseline.
val GreenSecondaryContainerDark = Color(0xFF16351F)
val OnGreenSecondaryContainerDark = Color(0xFFA8F0BF)
val GreenSecondaryContainerLight = Color(0xFFC6F0CE)
val OnGreenSecondaryContainerLight = Color(0xFF002109)

// Dunkle Flächen — oklch(0.15 / 0.19 / 0.14 …, 250)
val BgDark = Color(0xFF0A0E14)        // Seiten-/App-Hintergrund
val SurfaceDark = Color(0xFF0A0E14)
val SurfaceVariantDark = Color(0xFF141A22) // Cards, Container
val OutlineDark = Color(0xFF212A33)   // Rahmen/Trenner — oklch(0.28 0.02 250)
val OutlineVariantDark = Color(0xFF1A2029)

// Text — oklch(0.96 / 0.62, 250)
val TextPrimaryDark = Color(0xFFEFF2F5)
val TextSecondaryDark = Color(0xFF828790)

// Helle Flächen (Light-Variante)
val BgLight = Color(0xFFF7F9F8)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE1E6E3)
val OutlineLight = Color(0xFF727872)
val TextPrimaryLight = Color(0xFF0A0E14)
val TextSecondaryLight = Color(0xFF424846)
