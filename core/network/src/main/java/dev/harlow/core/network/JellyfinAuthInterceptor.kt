package dev.harlow.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder

/**
 * Signs requests to the signed-in server with Jellyfin's `Authorization` header, so streams, downloads and artwork
 * don't need the token in the URL (where it would end up in server and proxy logs). Requests to other hosts stay
 * unsigned; OkHttp drops the header itself when a redirect leaves the host. Requests that already carry the header
 * (the SDK's own) are left alone.
 */
internal class JellyfinAuthInterceptor(private val session: () -> ApiClient?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val api = session()
        val token = api?.accessToken
        if (api == null || token == null || request.header(AUTHORIZATION) != null) return chain.proceed(request)
        val server = api.baseUrl?.toHttpUrlOrNull()
        if (server == null || !request.url.isSameOrigin(server)) return chain.proceed(request)

        val header = AuthorizationHeaderBuilder.buildHeader(
            api.clientInfo.name,
            api.clientInfo.version,
            api.deviceInfo.id,
            api.deviceInfo.name,
            token,
        )
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, header).build())
    }

    private fun HttpUrl.isSameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host.equals(other.host, ignoreCase = true) && port == other.port

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
