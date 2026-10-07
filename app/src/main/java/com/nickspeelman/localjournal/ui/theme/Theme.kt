package com.nickspeelman.localjournal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SeaGlass300,
    onPrimary = SeaGlass900,
    primaryContainer = SeaGlass700,
    onPrimaryContainer = SeaGlass50,
    secondary = SeaGlass200,
    onSecondary = SeaGlass900,
    secondaryContainer = SeaGlass950,
    onSecondaryContainer = SeaGlass100,
    tertiary = SeaGlass500,
    onTertiary = SeaGlass990,
    tertiaryContainer = SeaGlass700,
    onTertiaryContainer = SeaGlass50,
    background = SeaGlass990,
    onBackground = SeaGlass100,
    surface = SeaGlass990,
    onSurface = SeaGlass100,
    surfaceVariant = SeaGlass950,
    onSurfaceVariant = SeaGlassDarkMuted,
    outline = SeaGlass500,
    outlineVariant = SeaGlass700,
    inverseSurface = SeaGlass100,
    inverseOnSurface = SeaGlass900,
    inversePrimary = SeaGlass700,
    surfaceTint = SeaGlass300
)

private val LightColorScheme = lightColorScheme(
    primary = SeaGlass700,
    onPrimary = SeaGlass50,
    primaryContainer = SeaGlass100,
    onPrimaryContainer = SeaGlass900,
    secondary = SeaGlass500,
    onSecondary = SeaGlass900,
    secondaryContainer = SeaGlass200,
    onSecondaryContainer = SeaGlass900,
    tertiary = SeaGlass900,
    onTertiary = SeaGlass50,
    tertiaryContainer = SeaGlass200,
    onTertiaryContainer = SeaGlass900,
    background = SeaGlass50,
    onBackground = SeaGlass900,
    surface = SeaGlass50,
    onSurface = SeaGlass900,
    surfaceVariant = SeaGlass100,
    onSurfaceVariant = SeaGlassMuted,
    outline = SeaGlass700,
    outlineVariant = SeaGlass200,
    inverseSurface = SeaGlass900,
    inverseOnSurface = SeaGlass50,
    inversePrimary = SeaGlass300,
    surfaceTint = SeaGlass700
)

@Composable
fun LocalJournalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic color is intentionally not used: Aside should remain visually quiet
    // and recognizable instead of inheriting a potentially saturated system palette.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
