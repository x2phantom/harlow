package dev.harlow.automotive.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.withTranslation
import dev.harlow.core.designsystem.component.HarlowWindow
import dev.harlow.core.designsystem.theme.HarlowTheme
import org.junit.Rule
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a composable with Robolectric and saves it as `.marathon-snapshots/current/android/<deviceFolder>/<id>.png`.
 * Subclasses pick the head unit through `@Config(qualifiers = …)` and name its folder in [deviceFolder].
 *
 * Every screen sits in [HarlowWindow] like in the activities. Robolectric reports no system bars, so a head unit whose
 * app draws under its bars (edge-to-edge, Android 15+) sets them in [systemBars].
 */
abstract class ScreenshotCapture {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    abstract val deviceFolder: String

    /** The head unit's system bars the app draws under; none by default (the window is the app area). */
    open val systemBars: WindowInsets = WindowInsets(0)

    private val snapshotDir: File by lazy {
        var dir = File(System.getProperty("user.dir")!!)
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: break
        }
        File(dir, ".marathon-snapshots/current/android/$deviceFolder").also { it.mkdirs() }
    }

    protected fun capture(targetId: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            HarlowTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    HarlowWindow(insets = systemBars) {
                        content()
                    }
                    SystemBars(systemBars)
                }
            }
        }

        composeTestRule.waitForIdle()

        val rootView = composeTestRule.activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)
        drawTopmostDialog(canvas, rootView.width, rootView.height)

        FileOutputStream(File(snapshotDir, "$targetId.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /** Stand-in for the head unit's system bars, drawn over the app like the real ones. */
    @Composable
    private fun SystemBars(insets: WindowInsets) {
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        Canvas(Modifier.fillMaxSize()) {
            val left = insets.getLeft(density, direction).toFloat()
            val top = insets.getTop(density).toFloat()
            val right = insets.getRight(density, direction).toFloat()
            val bottom = insets.getBottom(density).toFloat()
            if (left > 0) drawRect(SYSTEM_BAR_COLOR, size = Size(left, size.height))
            if (top > 0) drawRect(SYSTEM_BAR_COLOR, size = Size(size.width, top))
            if (right > 0) drawRect(SYSTEM_BAR_COLOR, Offset(size.width - right, 0f), Size(right, size.height))
            if (bottom > 0) drawRect(SYSTEM_BAR_COLOR, Offset(0f, size.height - bottom), Size(size.width, bottom))
        }
    }

    /**
     * Compose `Dialog`s live in their own window, which [android.view.View.draw] on the activity's decor view never
     * reaches. Composite the newest showing dialog on top, centred like the system would place it.
     */
    private fun drawTopmostDialog(canvas: Canvas, screenWidth: Int, screenHeight: Int) {
        val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } ?: return
        val decor = dialog.window?.decorView ?: return
        if (decor.width == 0 || decor.height == 0) {
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST),
            )
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        }
        val left = (screenWidth - decor.width) / 2f
        val top = (screenHeight - decor.height) / 2f
        canvas.withTranslation(left, top) { decor.draw(this) }
    }
}

private val SYSTEM_BAR_COLOR = Color(0xFF3A3A3A)
