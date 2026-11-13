package de.davscha.raster.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Gleiche Farbwelt wie Lasur und Kontur, damit die Apps zusammenpassen. */
object RasterColors {
    val StudioBackground = Color(0xFF1C1D21)
    val StudioPanel = Color(0xFF26272C)
    val StudioLine = Color(0xFF34353B)
    val Accent = Color(0xFF3B7CF0)
    val Prototype = Color(0xFFFF8A4C)
    val Text = Color(0xFFE6E6E9)
    val Muted = Color(0xFFA4A5AD)
}

@Composable
fun RasterTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = RasterColors.Accent,
        onPrimary = Color.White,
        secondary = RasterColors.Prototype,
        onSecondary = Color(0xFF1A1A1A),
        background = RasterColors.StudioBackground,
        surface = RasterColors.StudioPanel,
        surfaceVariant = Color(0xFF2E2F35),
        surfaceContainerHigh = Color(0xFF2A2B31),
        onSurface = RasterColors.Text,
        onSurfaceVariant = RasterColors.Muted,
        outline = Color(0xFF4A4C54),
        outlineVariant = RasterColors.StudioLine,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
