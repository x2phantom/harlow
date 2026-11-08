package dev.harlow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.harlow.core.designsystem.theme.HarlowTheme

/**
 * Root of every own screen (settings, sign-in). The activities draw edge-to-edge — enforced from Android 15 with
 * targetSdk 35+, without opt-out on Android 16 — so the window reaches under the car's system bars, which on some
 * head units sit on any side (e.g. a wide bar on the left). The background fills the whole window; [content] is
 * inset by [insets]: system bars, display cutouts and the on-screen keyboard.
 *
 * Screenshot tests pass fixed [insets] to simulate a head unit's bars.
 */
@Composable
fun HarlowWindow(
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HarlowTheme.colors.background)
            .windowInsetsPadding(insets),
    ) {
        content()
    }
}
