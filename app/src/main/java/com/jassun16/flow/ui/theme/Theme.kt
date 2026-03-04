package com.jassun16.flow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark: pure black OLED + muted blue accent ──────────────────────────────
private val FlowDarkColors = darkColorScheme(
    primary          = Color(0xFF4A90D9),   // muted blue — badges, FAB, selected states
    onPrimary        = Color(0xFF000000),
    primaryContainer = Color(0xFF1A3A5C),   // deep blue container
    onPrimaryContainer = Color(0xFFCCE4FF),
    background       = Color(0xFF000000),   // pure black OLED
    surface          = Color(0xFF000000),   // pure black surfaces
    surfaceVariant   = Color(0xFF1C1C1E),   // slightly elevated cards/drawer
    onBackground     = Color(0xFFE3E3E3),   // primary text
    onSurface        = Color(0xFFE3E3E3),   // primary text on surfaces
    onSurfaceVariant = Color(0xFF8E8E93),   // secondary/muted text
    outline          = Color(0xFF3A3A3C),   // dividers
    outlineVariant   = Color(0xFF2C2C2E)
)

// ── Light: clean white + muted blue accent ─────────────────────────────────
private val FlowLightColors = lightColorScheme(
    primary          = Color(0xFF1A6BB5),   // slightly deeper blue for light mode
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF00325A),
    background       = Color(0xFFFAFAFA),
    surface          = Color(0xFFFFFFFF),
    surfaceVariant   = Color(0xFFF3F3F3),
    onBackground     = Color(0xFF1A1A1A),
    onSurface        = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline          = Color(0xFFD1D1D6),
    outlineVariant   = Color(0xFFE5E5EA)
)

@Composable
fun FlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    manageStatusBar: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) FlowDarkColors else FlowLightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = FlowTypography,
        content     = content
    )
}
