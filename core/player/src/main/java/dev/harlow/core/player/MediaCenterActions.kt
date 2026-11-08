package dev.harlow.core.player

import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand

/**
 * The custom actions next to the media center's play controls: favorite the current track, shuffle, repeat, favorite
 * the current track's album and artist. The media center (a legacy controller) only shows the session's custom
 * actions, not shuffle or repeat modes, so all of them are custom session commands. They go into the overflow slot so
 * previous/next keep their places; album and artist come last, so the media center fills its free control-bar places
 * with the same actions as before and puts them into its "⋮" menu.
 */
internal object MediaCenterActions {
    const val ACTION_TOGGLE_FAVORITE = "dev.harlow.action.TOGGLE_FAVORITE"
    const val ACTION_TOGGLE_SHUFFLE = "dev.harlow.action.TOGGLE_SHUFFLE"
    const val ACTION_CYCLE_REPEAT = "dev.harlow.action.CYCLE_REPEAT"
    const val ACTION_TOGGLE_ALBUM_FAVORITE = "dev.harlow.action.TOGGLE_ALBUM_FAVORITE"
    const val ACTION_TOGGLE_ARTIST_FAVORITE = "dev.harlow.action.TOGGLE_ARTIST_FAVORITE"

    val toggleFavorite = SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
    val toggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val cycleRepeat = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
    val toggleAlbumFavorite = SessionCommand(ACTION_TOGGLE_ALBUM_FAVORITE, Bundle.EMPTY)
    val toggleArtistFavorite = SessionCommand(ACTION_TOGGLE_ARTIST_FAVORITE, Bundle.EMPTY)
    val commands = listOf(toggleFavorite, toggleShuffle, cycleRepeat, toggleAlbumFavorite, toggleArtistFavorite)

    /** What a favorite button favorites: the current track, or its album or artist. */
    enum class FavoriteTarget { TRACK, ALBUM, ARTIST }

    /**
     * Favorite state of the current track, its album and its artist; `null` where it isn't known (yet) or the track
     * has no album or artist in Room.
     */
    data class Favorites(val track: Boolean?, val album: Boolean?, val artist: Boolean?) {
        companion object {
            val UNKNOWN = Favorites(track = null, album = null, artist = null)
        }
    }

    /** Display names of the buttons, from the service's resources. */
    class Labels(
        val addFavorite: String,
        val removeFavorite: String,
        val addAlbumFavorite: String,
        val removeAlbumFavorite: String,
        val addArtistFavorite: String,
        val removeArtistFavorite: String,
        val shuffleOn: String,
        val shuffleOff: String,
        val repeatOff: String,
        val repeatAll: String,
        val repeatOne: String,
    )

    /**
     * The buttons for the current state. Without a current item there is nothing to favorite, and no favorite
     * buttons; a favorite button is also left out while its state in [favorites] isn't known.
     */
    fun buttons(
        labels: Labels,
        hasCurrentItem: Boolean,
        favorites: Favorites,
        shuffleEnabled: Boolean,
        @Player.RepeatMode repeatMode: Int,
    ): List<CommandButton> = buildList {
        val isFavorite = favorites.track
        if (hasCurrentItem && isFavorite != null) {
            add(
                button(
                    icon = if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
                    name = if (isFavorite) labels.removeFavorite else labels.addFavorite,
                    command = toggleFavorite,
                ),
            )
        }
        add(
            button(
                icon = if (shuffleEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
                name = if (shuffleEnabled) labels.shuffleOn else labels.shuffleOff,
                command = toggleShuffle,
            ),
        )
        add(
            when (repeatMode) {
                Player.REPEAT_MODE_ALL -> button(CommandButton.ICON_REPEAT_ALL, labels.repeatAll, cycleRepeat)
                Player.REPEAT_MODE_ONE -> button(CommandButton.ICON_REPEAT_ONE, labels.repeatOne, cycleRepeat)
                else -> button(CommandButton.ICON_REPEAT_OFF, labels.repeatOff, cycleRepeat)
            },
        )
        val isAlbumFavorite = favorites.album
        if (hasCurrentItem && isAlbumFavorite != null) {
            add(
                button(
                    icon = CommandButton.ICON_UNDEFINED,
                    name = if (isAlbumFavorite) labels.removeAlbumFavorite else labels.addAlbumFavorite,
                    command = toggleAlbumFavorite,
                    customIcon = if (isAlbumFavorite) {
                        R.drawable.ic_action_album_favorite
                    } else {
                        R.drawable.ic_action_album_not_favorite
                    },
                ),
            )
        }
        val isArtistFavorite = favorites.artist
        if (hasCurrentItem && isArtistFavorite != null) {
            add(
                button(
                    icon = CommandButton.ICON_UNDEFINED,
                    name = if (isArtistFavorite) labels.removeArtistFavorite else labels.addArtistFavorite,
                    command = toggleArtistFavorite,
                    customIcon = if (isArtistFavorite) {
                        R.drawable.ic_action_artist_favorite
                    } else {
                        R.drawable.ic_action_artist_not_favorite
                    },
                ),
            )
        }
    }

    /** Repeat cycles off → all → one → off, as in most music players. */
    @Player.RepeatMode
    fun nextRepeatMode(@Player.RepeatMode mode: Int): Int = when (mode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

    /**
     * Media3 has no filled/unfilled album or artist icon, so those buttons have [customIcon] (album/artist with a
     * heart) and no predefined icon: the media center draws a predefined icon instead of the resource when there is
     * one (seen on the AOSP media center of Android 13).
     */
    private fun button(
        icon: Int,
        name: String,
        command: SessionCommand,
        @DrawableRes customIcon: Int? = null,
    ): CommandButton =
        CommandButton.Builder(icon)
            .apply { if (customIcon != null) setCustomIconResId(customIcon) }
            .setDisplayName(name)
            .setSessionCommand(command)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
}
