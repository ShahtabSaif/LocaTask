package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SageGreenPrimary,
    onPrimary = NaturalBg,
    secondary = DarkSageGreenSecondary,
    onSecondary = WarmBoneTextPrimary,
    tertiary = MutedSageGreenTertiary,
    background = NaturalBg,
    onBackground = WarmBoneTextPrimary,
    surface = NaturalSurface,
    onSurface = WarmBoneTextPrimary,
    surfaceVariant = OrganicBorder,
    onSurfaceVariant = GrayGreenTextSecondary,
    error = SoftAlertRed
)

@Composable
fun MyApplicationTheme(
    // We enforce Dark Mode as requested by the user, with fallback to system dark preference if needed
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve our pristine custom neon branding
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
