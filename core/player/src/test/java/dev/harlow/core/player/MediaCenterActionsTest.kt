package dev.harlow.core.player

import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCenterActionsTest {

    private val labels = MediaCenterActions.Labels(
        addFavorite = "add",
        removeFavorite = "remove",
        addAlbumFavorite = "add album",
        removeAlbumFavorite = "remove album",
        addArtistFavorite = "add artist",
        removeArtistFavorite = "remove artist",
        shuffleOn = "shuffle on",
        shuffleOff = "shuffle off",
        repeatOff = "repeat off",
        repeatAll = "repeat all",
        repeatOne = "repeat one",
    )

    private val noFavorites = MediaCenterActions.Favorites(track = false, album = false, artist = false)

    private fun buttons(
        hasCurrentItem: Boolean = true,
        favorites: MediaCenterActions.Favorites = noFavorites,
        shuffleEnabled: Boolean = false,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
    ) = MediaCenterActions.buttons(labels, hasCurrentItem, favorites, shuffleEnabled, repeatMode)

    @Test
    fun `offers favorite, shuffle and repeat for the current track, then favorite album and artist`() {
        val buttons = buttons()
        assertEquals(
            listOf(
                MediaCenterActions.ACTION_TOGGLE_FAVORITE,
                MediaCenterActions.ACTION_TOGGLE_SHUFFLE,
                MediaCenterActions.ACTION_CYCLE_REPEAT,
                MediaCenterActions.ACTION_TOGGLE_ALBUM_FAVORITE,
                MediaCenterActions.ACTION_TOGGLE_ARTIST_FAVORITE,
            ),
            buttons.map { it.sessionCommand?.customAction },
        )
        assertEquals(
            listOf(
                CommandButton.ICON_HEART_UNFILLED,
                CommandButton.ICON_SHUFFLE_OFF,
                CommandButton.ICON_REPEAT_OFF,
                CommandButton.ICON_UNDEFINED,
                CommandButton.ICON_UNDEFINED,
            ),
            buttons.map { it.icon },
        )
        assertEquals(
            listOf(R.drawable.ic_action_album_not_favorite, R.drawable.ic_action_artist_not_favorite),
            buttons.takeLast(2).map { it.iconResId },
        )
    }

    @Test
    fun `keeps previous and next in place by going into the overflow slot`() {
        buttons().forEach { assertEquals(CommandButton.SLOT_OVERFLOW, it.slots[0]) }
    }

    @Test
    fun `shows the current state`() {
        val buttons = buttons(
            favorites = MediaCenterActions.Favorites(track = true, album = true, artist = true),
            shuffleEnabled = true,
            repeatMode = Player.REPEAT_MODE_ONE,
        )
        assertEquals(
            listOf(CommandButton.ICON_HEART_FILLED, CommandButton.ICON_SHUFFLE_ON, CommandButton.ICON_REPEAT_ONE),
            buttons.take(3).map { it.icon },
        )
        assertEquals(
            listOf(R.drawable.ic_action_album_favorite, R.drawable.ic_action_artist_favorite),
            buttons.takeLast(2).map { it.iconResId },
        )
        assertEquals(
            listOf("remove", "shuffle on", "repeat one", "remove album", "remove artist"),
            buttons.map { it.displayName.toString() },
        )
        assertEquals(
            CommandButton.ICON_REPEAT_ALL,
            buttons(repeatMode = Player.REPEAT_MODE_ALL)[2].icon,
        )
    }

    @Test
    fun `has no favorite buttons without a track`() {
        assertEquals(
            listOf(MediaCenterActions.ACTION_TOGGLE_SHUFFLE, MediaCenterActions.ACTION_CYCLE_REPEAT),
            buttons(hasCurrentItem = false).map { it.sessionCommand?.customAction },
        )
    }

    @Test
    fun `leaves out a favorite button while its state is unknown`() {
        assertEquals(2, buttons(favorites = MediaCenterActions.Favorites.UNKNOWN).size)
        assertEquals(
            listOf(
                MediaCenterActions.ACTION_TOGGLE_SHUFFLE,
                MediaCenterActions.ACTION_CYCLE_REPEAT,
                MediaCenterActions.ACTION_TOGGLE_ARTIST_FAVORITE,
            ),
            buttons(favorites = noFavorites.copy(track = null, album = null))
                .map { it.sessionCommand?.customAction },
        )
        assertEquals(
            listOf(
                MediaCenterActions.ACTION_TOGGLE_FAVORITE,
                MediaCenterActions.ACTION_TOGGLE_SHUFFLE,
                MediaCenterActions.ACTION_CYCLE_REPEAT,
                MediaCenterActions.ACTION_TOGGLE_ALBUM_FAVORITE,
            ),
            buttons(favorites = noFavorites.copy(artist = null)).map { it.sessionCommand?.customAction },
        )
    }

    @Test
    fun `cycles repeat off, all, one`() {
        assertEquals(Player.REPEAT_MODE_ALL, MediaCenterActions.nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, MediaCenterActions.nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, MediaCenterActions.nextRepeatMode(Player.REPEAT_MODE_ONE))
    }
}
