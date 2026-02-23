package com.jassun16.flow.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Fallback color schemes (used only if dynamic color unavailable) ────────

private val FallbackDarkColors = darkColorScheme(
    primary          = Color(0xFF90CAF9),   // soft blue
    onPrimary        = Color(0xFF003258),
    primaryContainer = Color(0xFF004880),
    background       = Color(0xFF000000),   // pure black for OLED
    surface          = Color(0xFF000000),   // pure black surfaces
    surfaceVariant   = Color(0xFF1C1C1E),   // slightly elevated surface
    onBackground     = Color(0xFFE3E3E3),
    onSurface        = Color(0xFFE3E3E3)
)

private val FallbackLightColors = lightColorScheme(
    primary          = Color(0xFF1565C0),   // deep blue
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    background       = Color(0xFFFAFAFA),
    surface          = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFFF3F3F3),
    onBackground     = Color(0xFF1A1A1A),
    onSurface        = Color(0xFF1A1A1A)
)

@Composable
fun FlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),  // follows system setting
    content:   @Composable () -> Unit
) {
    val context = LocalContext.current

    // Dynamic Color — extracts palette from your wallpaper automatically
    // This is the Material You magic on Pixel 8 Pro
    val colorScheme = when {
        darkTheme -> dynamicDarkColorScheme(context).copy(
            // Override background to pure black regardless of wallpaper color
            // This ensures true OLED black in dark mode — saves battery + looks great
            background     = Color(0xFF000000),
            surface        = Color(0xFF000000),
            surfaceVariant = Color(0xFF1C1C1E)
        )
        else -> dynamicLightColorScheme(context)
    }

    // Make status bar and navigation bar match our theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent bars — content draws behind them (edge-to-edge)
            window.statusBarColor  = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            // Light/dark icons on status bar based on theme
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = FlowTypography,
        content     = content
    )
}
