package dev.harlow.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `an address without scheme gets https`() {
        assertEquals("https://jelly.example.com", normalizeServerUrl("  jelly.example.com/ "))
        assertEquals("https://192.168.1.10:8920/jellyfin", normalizeServerUrl("192.168.1.10:8920/jellyfin"))
    }

    @Test
    fun `an https address is kept`() {
        assertEquals("https://jelly.example.com", normalizeServerUrl("HTTPS://jelly.example.com//"))
    }

    @Test(expected = InsecureServerUrlException::class)
    fun `http is rejected`() {
        normalizeServerUrl("http://jelly.example.com")
    }

    @Test(expected = InsecureServerUrlException::class)
    fun `http is rejected regardless of case`() {
        normalizeServerUrl("HTTP://jelly.example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `other schemes are rejected`() {
        normalizeServerUrl("ftp://jelly.example.com")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an empty address is rejected`() {
        normalizeServerUrl("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a bare scheme is rejected`() {
        normalizeServerUrl("https://")
    }

    @Test
    fun `only https urls are secure`() {
        assertTrue(isSecureServerUrl("https://jelly.example.com"))
        assertFalse(isSecureServerUrl("http://jelly.example.com"))
    }
}
