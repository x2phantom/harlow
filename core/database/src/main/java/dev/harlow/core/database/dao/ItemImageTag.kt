package dev.harlow.core.database.dao

/** An item's id and the tag of its primary image: Jellyfin gives the image a new tag whenever it changes. */
data class ItemImageTag(
    val id: String,
    val imageTag: String,
)
