package dev.harlow.core.network

import io.mockk.every
import io.mockk.mockk
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The token goes to the signed-in server in the Authorization header, and nowhere else. */
class JellyfinAuthInterceptorTest {

    private val server = MockWebServer()
    private val otherHost = MockWebServer()
    private var session: ApiClient? = null
    private val client = OkHttpClient.Builder()
        .addInterceptor(JellyfinAuthInterceptor { session })
        .build()

    @Before
    fun setUp() {
        server.start()
        otherHost.start()
        session = mockk {
            every { baseUrl } returns server.url("/").toString().trimEnd('/')
            every { accessToken } returns TOKEN
            every { clientInfo } returns ClientInfo("Harlow", "1.0")
            every { deviceInfo } returns DeviceInfo(id = "device-1", name = "Harlow")
        }
    }

    @After
    fun tearDown() {
        server.close()
        otherHost.close()
    }

    @Test
    fun `requests to the server carry the token in the header`() {
        server.enqueue(MockResponse.Builder().code(200).build())

        get(server.url("/Audio/abc/stream?static=true").toString())

        val authorization = server.takeRequest().headers["Authorization"].orEmpty()
        assertTrue(authorization.startsWith("MediaBrowser "))
        assertTrue(authorization.contains("Token=\"$TOKEN\""))
        assertTrue(authorization.contains("DeviceId=\"device-1\""))
    }

    @Test
    fun `requests to other hosts carry no token`() {
        otherHost.enqueue(MockResponse.Builder().code(200).build())

        get(otherHost.url("/image.webp").toString())

        assertNull(otherHost.takeRequest().headers["Authorization"])
    }

    @Test
    fun `a redirect to another host drops the token`() {
        server.enqueue(
            MockResponse.Builder().code(302).addHeader("Location", otherHost.url("/elsewhere").toString()).build(),
        )
        otherHost.enqueue(MockResponse.Builder().code(200).build())

        get(server.url("/Items/abc/Images/Primary").toString())

        assertTrue(server.takeRequest().headers["Authorization"].orEmpty().contains(TOKEN))
        assertNull(otherHost.takeRequest().headers["Authorization"])
    }

    @Test
    fun `signed out, requests carry no token`() {
        session = null
        server.enqueue(MockResponse.Builder().code(200).build())

        get(server.url("/System/Info/Public").toString())

        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `a header the SDK set itself is kept`() {
        server.enqueue(MockResponse.Builder().code(200).build())

        client.newCall(
            Request.Builder().url(server.url("/Users/AuthenticateByName")).header("Authorization", "sdk").build(),
        ).execute().close()

        assertEquals("sdk", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `the URL never contains the token`() {
        server.enqueue(MockResponse.Builder().code(200).build())

        get(server.url("/Audio/abc/stream?static=true").toString())

        assertFalse(server.takeRequest().url.toString().contains(TOKEN))
    }

    private fun get(url: String) {
        client.newCall(Request.Builder().url(url).build()).execute().close()
    }

    private companion object {
        const val TOKEN = "secret-token"
    }
}
