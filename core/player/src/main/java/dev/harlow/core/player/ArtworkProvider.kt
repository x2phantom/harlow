package dev.harlow.core.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.harlow.core.common.artworkAuthority
import dev.harlow.core.data.ArtworkPreCacher
import kotlinx.coroutines.runBlocking
import java.io.File

class ArtworkProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ArtworkEntryPoint {
        fun artworkPreCacher(): ArtworkPreCacher
    }

    override fun onCreate(): Boolean {
        context?.let { artworkAuthority = "${it.packageName}.artwork" }
        return true
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val itemId = ArtworkUris.itemId(uri)

        // Exported for the media center, so every app can ask: only valid IDs of the signed-in library are fetched.
        val file = itemId?.let {
            val entryPoint = EntryPointAccessors.fromApplication(
                ctx.applicationContext,
                ArtworkEntryPoint::class.java,
            )
            runBlocking { entryPoint.artworkPreCacher().resolveArtwork(it) }
        } ?: placeholder(ArtworkUris.placeholderOf(uri)) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /**
     * A neutral cover for items without artwork, or whose artwork can't be fetched right now (offline, signed out):
     * the car's media center shows every item with a thumbnail (MC-1). The icon of the item's kind on a grey tile,
     * rendered once per kind.
     */
    @Synchronized
    private fun placeholder(kind: ArtworkPlaceholder): File? {
        val ctx = context ?: return null
        val file = File(ctx.cacheDir, "artwork/placeholder-${kind.pathName}.png")
        if (file.exists() && file.length() > 0) return file
        val icon = ctx.getDrawable(kind.icon) ?: return null
        val bitmap = Bitmap.createBitmap(PLACEHOLDER_SIZE, PLACEHOLDER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PLACEHOLDER_BACKGROUND)
        val inset = PLACEHOLDER_SIZE / 4
        icon.setBounds(inset, inset, PLACEHOLDER_SIZE - inset, PLACEHOLDER_SIZE - inset)
        icon.draw(canvas)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    override fun getType(uri: Uri): String = "image/*"
    override fun query(uri: Uri, p: Array<String>?, s: String?, sa: Array<String>?, so: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, sa: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<String>?): Int = 0

    private companion object {
        const val PLACEHOLDER_SIZE = 256
        val PLACEHOLDER_BACKGROUND = Color.rgb(0x44, 0x40, 0x3C)
    }
}
