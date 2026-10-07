package com.nickspeelman.localjournal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Brand typography is centralized here. Forum is reserved for true display moments;
 * Lato carries functional UI. The project copy supplied for this pass does not include
 * redistributable Forum/Lato font binaries, so these families deliberately use Android's
 * serif/sans-serif fallbacks for now rather than adding a network font dependency.
 *
 * When the licensed local font files are added under res/font, only these two declarations
 * need to change to FontFamily(Font(...)). No screen-level typography changes are required.
 */
val AsideDisplayFontFamily = FontFamily.Serif
val AsideUiFontFamily = FontFamily.SansSerif

private val displayBase = TextStyle(
    fontFamily = AsideDisplayFontFamily,
    fontWeight = FontWeight.Normal
)

private val uiBase = TextStyle(
    fontFamily = AsideUiFontFamily,
    fontWeight = FontWeight.Normal
)

val Typography = Typography(
    displayLarge = displayBase.copy(fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = displayBase.copy(fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = displayBase.copy(fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = displayBase.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = displayBase.copy(fontSize = 28.sp, lineHeight = 36.sp),
    // Smaller headings are functional rather than branding moments.
    headlineSmall = uiBase.copy(fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = uiBase.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = uiBase.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = uiBase.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = uiBase.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.25.sp),
    bodyMedium = uiBase.copy(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = uiBase.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = uiBase.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = uiBase.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = uiBase.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)
