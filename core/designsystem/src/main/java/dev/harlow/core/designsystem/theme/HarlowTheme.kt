package dev.harlow.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHarlowColors = staticCompositionLocalOf { harlowDarkColorScheme }

private val HarlowMaterial3Dark = darkColorScheme(
    background = HarlowPalette.Stone950,
    surface = HarlowPalette.Stone900,
    surfaceVariant = HarlowPalette.Stone800,
    surfaceContainerLowest = HarlowPalette.Stone950,
    surfaceContainerLow = HarlowPalette.Stone900,
    surfaceContainer = HarlowPalette.Stone800,
    surfaceContainerHigh = HarlowPalette.Stone700,
    surfaceContainerHighest = HarlowPalette.Stone600,
    onBackground = HarlowPalette.Stone100,
    onSurface = HarlowPalette.Stone100,
    onSurfaceVariant = HarlowPalette.Stone400,
    primary = HarlowPalette.Stone200,
    onPrimary = HarlowPalette.Stone950,
    primaryContainer = HarlowPalette.Stone800,
    onPrimaryContainer = HarlowPalette.Stone200,
    secondary = HarlowPalette.Stone400,
    onSecondary = HarlowPalette.Stone950,
    outline = HarlowPalette.Stone700,
    outlineVariant = HarlowPalette.Stone800,
    inverseSurface = HarlowPalette.Stone100,
    inverseOnSurface = HarlowPalette.Stone900,
)

private val HarlowMaterial3Light = lightColorScheme(
    background = HarlowPalette.Stone50,
    surface = HarlowPalette.Stone100,
    surfaceVariant = HarlowPalette.Stone200,
    onBackground = HarlowPalette.Stone900,
    onSurface = HarlowPalette.Stone900,
    onSurfaceVariant = HarlowPalette.Stone500,
    primary = HarlowPalette.Stone800,
    onPrimary = HarlowPalette.Stone50,
    primaryContainer = HarlowPalette.Stone200,
    onPrimaryContainer = HarlowPalette.Stone800,
    secondary = HarlowPalette.Stone600,
    onSecondary = HarlowPalette.Stone50,
    outline = HarlowPalette.Stone400,
    outlineVariant = HarlowPalette.Stone300,
    inverseSurface = HarlowPalette.Stone900,
    inverseOnSurface = HarlowPalette.Stone100,
)

@Composable
fun HarlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val harlowColors = if (darkTheme) harlowDarkColorScheme else harlowLightColorScheme
    val materialScheme = if (darkTheme) HarlowMaterial3Dark else HarlowMaterial3Light

    CompositionLocalProvider(LocalHarlowColors provides harlowColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = harlowTypography,
            content = content,
        )
    }
}

object HarlowTheme {
    val colors: HarlowColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalHarlowColors.current

    val spacing: HarlowSpacing
        get() = HarlowSpacing

    val shapes: HarlowShapes
        get() = HarlowShapes
}
