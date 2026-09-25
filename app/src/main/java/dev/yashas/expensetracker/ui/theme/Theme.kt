package dev.yashas.expensetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = TextPrimary,
    primaryContainer = VioletContainer,
    onPrimaryContainer = OnVioletContainer,
    secondary = CyanSecondary,
    onSecondary = InkBackground,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    background = InkBackground,
    onBackground = TextPrimary,
    surface = InkSurface,
    onSurface = TextPrimary,
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = InkOutline,
    error = SemanticCoral,
)

/**
 * Dark-mode-first theme (00-MASTER §8). Light theme is v2 scope; the app renders
 * the dark scheme regardless of system setting until v2 lands.
 */
@Composable
fun ExpenseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content,
    )
}
