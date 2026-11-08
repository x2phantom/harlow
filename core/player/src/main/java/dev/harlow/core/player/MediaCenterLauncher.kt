package dev.harlow.core.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * Carries out a [DeepLink]: starts playback through the media service if asked to, then opens the car's media
 * center on Harlow, at the linked item or search where the media center supports it (`MEDIA_TEMPLATE_V2`).
 */
object MediaCenterLauncher {

    fun open(context: Context, link: DeepLink) {
        val appContext = context.applicationContext
        if (link is DeepLink.Play) play(appContext, link.mediaId)
        context.startActivity(mediaCenterIntent(appContext, link))
    }

    /** The intent that opens the media center for [link] (see `CarMediaIntents` and `MediaIntentExtras`). */
    fun mediaCenterIntent(context: Context, link: DeepLink): Intent {
        val v2 = supportsMediaTemplateV2(context)
        val intent = Intent(if (v2) ACTION_MEDIA_TEMPLATE_V2 else ACTION_MEDIA_TEMPLATE)
            .putExtra(EXTRA_KEY_MEDIA_COMPONENT, ComponentName(context, HarlowMediaService::class.java).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when (link) {
            is DeepLink.Browse -> if (v2) intent.putExtra(EXTRA_KEY_MEDIA_ID, link.mediaId)
            is DeepLink.Search -> {
                intent.putExtra(EXTRA_KEY_SEARCH_QUERY, link.query)
                if (v2 && link.playFirst) {
                    intent.putExtra(EXTRA_KEY_SEARCH_ACTION, EXTRA_VALUE_PLAY_FIRST_ITEM_FROM_SEARCH)
                }
            }
            is DeepLink.Play, DeepLink.Open -> Unit
        }
        return intent
    }

    /** Only preinstalled media centers count; an app claiming the action isn't one (see the AAOS media guide). */
    private fun supportsMediaTemplateV2(context: Context): Boolean =
        context.packageManager.queryIntentActivities(
            Intent(ACTION_MEDIA_TEMPLATE_V2),
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_SYSTEM_ONLY,
        ).isNotEmpty()

    /**
     * Plays [mediaId] through a controller of its own, which goes away once playback started (or failed). The
     * service resolves the id as for the media center (`onSetMediaItems`), so signed out it reports that instead.
     */
    private fun play(context: Context, mediaId: String) {
        val token = SessionToken(context, ComponentName(context, HarlowMediaService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val controller = try {
                    future.get()
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't connect to the media service", e)
                    return@addListener
                }
                val handler = Handler(Looper.getMainLooper())
                val release = Runnable { controller.release() }
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) handler.post(release)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        handler.post(release)
                    }
                })
                controller.setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
                controller.prepare()
                controller.play()
                handler.postDelayed(release, CONTROLLER_TIMEOUT_MS)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private const val TAG = "MediaCenterLauncher"
    private const val CONTROLLER_TIMEOUT_MS = 15_000L

    private const val ACTION_MEDIA_TEMPLATE = "android.car.intent.action.MEDIA_TEMPLATE"
    private const val ACTION_MEDIA_TEMPLATE_V2 = "androidx.car.app.mediaextensions.action.MEDIA_TEMPLATE_V2"
    private const val EXTRA_KEY_MEDIA_COMPONENT = "android.car.intent.extra.MEDIA_COMPONENT"
    private const val EXTRA_KEY_MEDIA_ID = "androidx.car.app.mediaextensions.extra.KEY_MEDIA_ID"
    private const val EXTRA_KEY_SEARCH_QUERY = "android.car.media.extra.SEARCH_QUERY"
    private const val EXTRA_KEY_SEARCH_ACTION = "androidx.car.app.mediaextensions.extra.KEY_SEARCH_ACTION"
    private const val EXTRA_VALUE_PLAY_FIRST_ITEM_FROM_SEARCH = 1
}
