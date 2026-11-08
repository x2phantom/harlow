package dev.harlow.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.DeviceInfo
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.deviceIdentityDataStore: DataStore<Preferences> by preferencesDataStore(name = "device_identity")

/**
 * The device Harlow reports to Jellyfin: a random ID, made once per installation and again after signing out, so
 * the server lists one "Harlow" device per sign-in. No hardware identifiers.
 */
@Singleton
class DeviceIdentity(private val dataStore: DataStore<Preferences>) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.deviceIdentityDataStore)

    private val mutex = Mutex()

    suspend fun deviceInfo(): DeviceInfo = DeviceInfo(id = deviceId(), name = DEVICE_NAME)

    private suspend fun deviceId(): String = mutex.withLock {
        dataStore.data.first()[DEVICE_ID] ?: UUID.randomUUID().toString().also { id ->
            dataStore.edit { it[DEVICE_ID] = id }
        }
    }

    /** After signing out: the next sign-in is a new device. */
    suspend fun reset() {
        mutex.withLock { dataStore.edit { it.remove(DEVICE_ID) } }
    }

    private companion object {
        const val DEVICE_NAME = "Harlow"
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
