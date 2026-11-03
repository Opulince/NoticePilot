package com.intraprenuers.noticepilot.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * NoticePilot design system: paper, ink and one forest-green accent. Flat colour, thin borders,
 * moderate radii. Serif (system Noto Serif) for titles gives editorial presence; sans for reading.
 */

/** Raw palette. Use through MaterialTheme.colorScheme where possible. */
object Palette {
    val Paper = Color(0xFFF7F6F2)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF202321)
    // Darker than the brief's #747873 so small metadata text meets WCAG AA (~5:1) on paper.
    val Graphite = Color(0xFF6B6F6A)
    val Border = Color(0xFFE6E5DF)
    val Forest = Color(0xFF245B49)
    val Sage = Color(0xFFA9BBAA)
    val PaleSage = Color(0xFFEAF0E9)
}

private val LightColors = lightColorScheme(
    primary = Palette.Forest,
    onPrimary = Color.White,
    primaryContainer = Palette.PaleSage,
    onPrimaryContainer = Color(0xFF173D30),
    secondary = Color(0xFF55675B),
    onSecondary = Color.White,
    secondaryContainer = Palette.PaleSage,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Sage,
    onTertiary = Palette.Ink,
    background = Palette.Paper,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFEFEEE9),
    onSurfaceVariant = Palette.Graphite,
    outline = Color(0xFFC9C8C1),
    outlineVariant = Palette.Border,
    error = Color(0xFF9E3A2C),
    onError = Color.White,
    errorContainer = Color(0xFFF6E1DC),
    onErrorContainer = Color(0xFF5C1F15),
    surfaceTint = Palette.Forest,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF3F2EE),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE8E7E2)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC3AE),
    onPrimary = Color(0xFF0D2A20),
    primaryContainer = Color(0xFF1F3A2F),
    onPrimaryContainer = Color(0xFFCFE3D6),
    secondary = Color(0xFFB4C7B8),
    onSecondary = Color(0xFF1E2B22),
    secondaryContainer = Color(0xFF26342B),
    onSecondaryContainer = Color(0xFFDCE8DF),
    tertiary = Palette.Sage,
    onTertiary = Palette.Ink,
    background = Color(0xFF141615),
    onBackground = Color(0xFFE9E8E3),
    surface = Color(0xFF1C1F1D),
    onSurface = Color(0xFFE9E8E3),
    surfaceVariant = Color(0xFF262927),
    onSurfaceVariant = Color(0xFFA3A7A1),
    outline = Color(0xFF4A4F4B),
    outlineVariant = Color(0xFF2E322F),
    error = Color(0xFFF0A899),
    onError = Color(0xFF4A150C),
    errorContainer = Color(0xFF42231E),
    onErrorContainer = Color(0xFFF6D5CE),
    surfaceTint = Color(0xFF8FC3AE),
    surfaceContainerLowest = Color(0xFF111312),
    surfaceContainerLow = Color(0xFF191B1A),
    surfaceContainer = Color(0xFF1F2220),
    surfaceContainerHigh = Color(0xFF222523),
    surfaceContainerHighest = Color(0xFF2F3230)
)

/** Corner radii: small controls 8, buttons 10, cards/panels 12. Nothing is a pill. */
object NpShape {
    val Tile = RoundedCornerShape(8.dp)
    val Control = RoundedCornerShape(10.dp)
    val Card = RoundedCornerShape(12.dp)
    val Badge = RoundedCornerShape(6.dp)
}

private val NpShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

private val NpTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
        headlineSmall = t.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = t.bodyMedium.copy(lineHeight = 22.sp),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
    )
}

@Composable
fun NoticePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = NpShapes,
        typography = NpTypography,
        content = content
    )
}

/** Background + text colour pair for a status badge. */
class StatusColor(val container: Color, val content: Color)

/** Status colours: deep green / muted amber / restrained brick, each on a pale wash. */
object StatusColors {
    @Composable
    fun verified(): StatusColor =
        if (isSystemInDarkTheme()) StatusColor(Color(0xFF1E3A2D), Color(0xFFA8D5BC))
        else StatusColor(Color(0xFFE4EFE7), Color(0xFF1F5A3F))

    @Composable
    fun approximate(): StatusColor =
        if (isSystemInDarkTheme()) StatusColor(Color(0xFF3D3218), Color(0xFFE7C77A))
        else StatusColor(Color(0xFFF6EBD3), Color(0xFF7A5A12))

    @Composable
    fun needsCheck(): StatusColor =
        if (isSystemInDarkTheme()) StatusColor(Color(0xFF42231E), Color(0xFFF0A899))
        else StatusColor(Color(0xFFF6E1DC), Color(0xFF8E3426))

    /** "Confirmed by you": neutral ink, so it never reads as an automatic verification. */
    @Composable
    fun confirmed(): StatusColor =
        if (isSystemInDarkTheme()) StatusColor(Color(0xFF2A2D2B), Color(0xFFE9E8E3))
        else StatusColor(Color(0xFFECEBE6), Color(0xFF202321))
}
