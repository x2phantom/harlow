package dev.harlow.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device: the Android Keystore isn't available in Robolectric. */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {

    private val cipher = KeystoreTokenCipher()

    @After
    fun tearDown() = cipher.deleteKey()

    @Test
    fun encryptedTokenDecryptsAndHidesThePlaintext() {
        val stored = cipher.encrypt(TOKEN)

        assertFalse(stored.contains(TOKEN))
        assertFalse(cipher.isLegacyPlaintext(stored))
        assertEquals(TOKEN, cipher.decrypt(stored))
    }

    @Test
    fun legacyPlaintextIsReturnedAsIs() {
        assertTrue(cipher.isLegacyPlaintext(TOKEN))
        assertEquals(TOKEN, cipher.decrypt(TOKEN))
    }

    @Test
    fun afterDeletingTheKeyTheTokenIsUnreadable() {
        val stored = cipher.encrypt(TOKEN)

        cipher.deleteKey()

        assertNull(cipher.decrypt(stored))
    }

    @Test
    fun damagedCiphertextIsUnreadable() {
        val stored = cipher.encrypt(TOKEN)

        assertNull(cipher.decrypt(stored.dropLast(4) + "AAAA"))
        assertNull(cipher.decrypt("v1:not-base64"))
    }

    private companion object {
        const val TOKEN = "0123456789abcdef0123456789abcdef"
    }
}
