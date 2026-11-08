package dev.harlow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val userId: String,
    /** Encrypted with [dev.harlow.core.database.TokenCipher]; read the token through `UserRepositoryImpl`. */
    @ColumnInfo(name = "accessToken") val encryptedAccessToken: String,
    val isActive: Boolean,
    val lastConnected: Long,
)
