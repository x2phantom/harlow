package dev.harlow.core.designsystem.theme

import androidx.compose.ui.graphics.Color

object HarlowPalette {
    val Stone50 = Color(0xFFFAFAF9)
    val Stone100 = Color(0xFFF5F5F4)
    val Stone200 = Color(0xFFE7E5E4)
    val Stone300 = Color(0xFFD6D3D1)
    val Stone400 = Color(0xFFA8A29E)
    val Stone500 = Color(0xFF78716C)
    val Stone600 = Color(0xFF57534E)
    val Stone700 = Color(0xFF44403C)
    val Stone800 = Color(0xFF292524)
    val Stone900 = Color(0xFF1C1917)
    val Stone950 = Color(0xFF0C0A09)

    val Amber500 = Color(0xFFD4A054)
    val Red500 = Color(0xFFC97070)
    val Green500 = Color(0xFF6EBB8A)
}

data class HarlowColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val foreground: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentStrong: Color,
    /** Ring and dot of a chosen option (radio button). */
    val controlSelected: Color,
    /** Ring of an option that isn't chosen. */
    val controlUnselected: Color,
    val favorite: Color,
    val online: Color,
    val warning: Color,
    val error: Color,
    val success: Color,
)

val harlowDarkColorScheme = HarlowColorScheme(
    background = HarlowPalette.Stone950,
    surface = HarlowPalette.Stone900,
    surfaceElevated = Color(0xFF231F1D),
    foreground = HarlowPalette.Stone100,
    muted = HarlowPalette.Stone400,
    border = HarlowPalette.Stone800,
    accent = HarlowPalette.Stone400,
    accentStrong = HarlowPalette.Stone300,
    controlSelected = HarlowPalette.Stone300,
    controlUnselected = HarlowPalette.Stone600,
    favorite = HarlowPalette.Red500,
    online = HarlowPalette.Green500,
    warning = HarlowPalette.Amber500,
    error = HarlowPalette.Red500,
    success = HarlowPalette.Green500,
)

val harlowLightColorScheme = HarlowColorScheme(
    background = HarlowPalette.Stone50,
    surface = HarlowPalette.Stone100,
    surfaceElevated = HarlowPalette.Stone200,
    foreground = HarlowPalette.Stone900,
    muted = HarlowPalette.Stone500,
    border = HarlowPalette.Stone300,
    accent = HarlowPalette.Stone500,
    accentStrong = HarlowPalette.Stone700,
    controlSelected = HarlowPalette.Stone700,
    controlUnselected = HarlowPalette.Stone400,
    favorite = HarlowPalette.Red500,
    online = HarlowPalette.Green500,
    warning = HarlowPalette.Amber500,
    error = HarlowPalette.Red500,
    success = HarlowPalette.Green500,
)
