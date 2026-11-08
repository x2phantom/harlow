package dev.harlow.automotive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import dev.harlow.core.player.DeepLink
import dev.harlow.core.player.MediaCenterLauncher

/**
 * Deep links for browsing and playback (`harlow://browse|play|search|open`, see [DeepLink]). Has no UI: browsing and
 * playback belong to the car's media center, so this hands over to it ([MediaCenterLauncher]) and finishes at once.
 * Not distraction-optimized, like every activity of a media app.
 */
class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val link = intent.data?.let(DeepLink::parse) ?: DeepLink.Open.also { Log.w(TAG, "Unknown deep link") }
        MediaCenterLauncher.open(this, link)
        finish()
    }

    private companion object {
        const val TAG = "DeepLinkActivity"
    }
}
