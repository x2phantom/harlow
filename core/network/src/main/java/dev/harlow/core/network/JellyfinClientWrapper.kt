package dev.harlow.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class JellyfinClientWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceIdentity: DeviceIdentity,
    @Named("appVersion") private val appVersion: String,
) {
    @Volatile
    private var _api: ApiClient? = null
    val api: ApiClient get() = requireNotNull(_api) { "API client not initialized. Call connect() first." }

    val isConnected: Boolean get() = _api != null

    /**
     * The one HTTP client for the server: SDK calls, streams, downloads, artwork and the reachability check. Only
     * certificates the system trusts are accepted; requests to the signed-in server get the Authorization header.
     */
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(JellyfinAuthInterceptor { _api })
            .build()
    }

    private val jellyfin: Jellyfin by lazy {
        val factory = OkHttpFactory(httpClient)
        createJellyfin {
            clientInfo = ClientInfo("Harlow", appVersion)
            this.context = this@JellyfinClientWrapper.context
            apiClientFactory = factory
            socketConnectionFactory = factory
        }
    }

    /** A client for [serverUrl] that isn't the session's, e.g. to sign in without touching a running session. */
    suspend fun createApi(serverUrl: String): ApiClient =
        jellyfin.createApi(baseUrl = serverUrl, deviceInfo = deviceIdentity.deviceInfo())

    suspend fun connect(serverUrl: String) {
        _api = createApi(serverUrl)
    }

    fun authenticate(accessToken: String) {
        _api?.update(accessToken = accessToken)
    }

    suspend fun restoreSession(serverUrl: String, accessToken: String) {
        if (_api?.baseUrl != serverUrl) connect(serverUrl)
        authenticate(accessToken)
    }

    /** Signed out: no more requests carry the token. */
    fun disconnect() {
        _api = null
    }
}
