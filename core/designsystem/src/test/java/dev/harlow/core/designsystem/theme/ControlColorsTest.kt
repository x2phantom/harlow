package dev.harlow.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Radio buttons must stand out from the dialog in both themes (WCAG 2.1, 1.4.11: at least 3:1 for controls). */
class ControlColorsTest {

    @Test
    fun selectedOptionIsVisibleInTheLightTheme() = assertVisible(harlowLightColorScheme)

    @Test
    fun selectedOptionIsVisibleInTheDarkTheme() = assertVisible(harlowDarkColorScheme)

    private fun assertVisible(scheme: HarlowColorScheme) {
        // Dialogs sit on the surface, the screens on the background
        for (behind in listOf(scheme.background, scheme.surface, scheme.surfaceElevated)) {
            val ratio = contrast(scheme.controlSelected, behind)
            assertTrue("selected option has contrast $ratio:1 against $behind", ratio >= 3f)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }
}
