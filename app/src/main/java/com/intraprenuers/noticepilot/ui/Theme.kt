package com.intraprenuers.noticepilot.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F4E8C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF565F71),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFD),
    onBackground = Color(0xFF1A1C20),
    surface = Color(0xFFFAFAFD),
    onSurface = Color(0xFF1A1C20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF1F4E8C),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun NoticePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

/** Background + text colour pair for a status badge. */
class StatusColor(val container: Color, val content: Color)

object StatusColors {
    @Composable
    fun verified(): StatusColor =
        if (isSystemInDarkTheme()) StatusColor(Color(0xFF1B4D2B), Color(0xFFB7F0C3))
        else StatusColor(Color(0xFFD4F1DB), Color(0xFF0C3B1B))

    @Composable
    fun approximate(): StatusColor =
        if (isSystemInDarkTheme()) StatusColor(Color(0xFF5C4300), Color(0xFFFFE08A))
        else StatusColor(Color(0xFFFFEFC2), Color(0xFF4A3500))

    @Composable
    fun needsCheck(): StatusColor =
        if (isSystemInDarkTheme()) StatusColor(Color(0xFF6B2A12), Color(0xFFFFD2C2))
        else StatusColor(Color(0xFFFFDDD0), Color(0xFF5A1F0A))

    @Composable
    fun confirmed(): StatusColor =
        if (isSystemInDarkTheme()) StatusColor(Color(0xFF1B3F73), Color(0xFFCFE0FF))
        else StatusColor(Color(0xFFD6E3FF), Color(0xFF0A2A5C))
}
