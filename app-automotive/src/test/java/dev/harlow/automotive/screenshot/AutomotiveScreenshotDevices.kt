package dev.harlow.automotive.screenshot

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Head units the screenshots are taken on: the SDK's AAOS emulator, a wide landscape and a portrait display, and
 * further head-unit sizes at 160 dpi. Those give the app area beside the system bars, plus two that draw edge-to-edge
 * under the bars (Android 15+), with the bars as [ScreenshotCapture.systemBars].
 */

/** The Android SDK's AAOS emulator: 1408×792 px at 160 dpi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1408dp-h792dp-land-mdpi")
class Automotive_SdkEmulator : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-emulator-1408x792"
}

/** Wide landscape head unit: 1920×720 px at 240 dpi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h480dp-land-hdpi")
class Automotive_Landscape1920x720 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-landscape-1920x720"
}

/** Portrait head unit: 1152×1536 px at 240 dpi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w768dp-h1024dp-port-hdpi")
class Automotive_Portrait1152x1536 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-portrait-1152x1536"
}

/** 2220×816 px panel: app area 1800×816 dp beside a 420 dp bar on the left. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1800dp-h816dp-land-mdpi")
class Automotive_1800x816 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-1800x816"
}

/** The same panel edge-to-edge: the whole 2220×816, the 420 dp bar on the left drawn over the app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w2220dp-h816dp-land-mdpi")
class Automotive_EdgeToEdge2220x816 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-edge-to-edge-2220x816"
    override val systemBars = WindowInsets(left = 420.dp)
}

/** Edge-to-edge 1920×720 px, bars of 159 dp left and right drawn over the app. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1920dp-h720dp-land-mdpi")
class Automotive_EdgeToEdge1920x720 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-edge-to-edge-1920x720"
    override val systemBars = WindowInsets(left = 159.dp, right = 159.dp)
}

/** App area 1632×720 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1632dp-h720dp-land-mdpi")
class Automotive_1632x720 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-1632x720"
}

/** App area 1800×640 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1800dp-h640dp-land-mdpi")
class Automotive_1800x640 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-1800x640"
}

/** App area 1412×655 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1412dp-h655dp-land-mdpi")
class Automotive_1412x655 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-1412x655"
}

/** App area 1920×620 dp, the lowest one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1920dp-h620dp-land-mdpi")
class Automotive_1920x620 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-1920x620"
}

/** App area 1688×1472 dp, nearly square. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1688dp-h1472dp-land-mdpi")
class Automotive_1688x1472 : AutomotiveScreenshotTests() {
    override val deviceFolder = "aaos-1688x1472"
}
