package dev.harlow.core.appcommon

import dev.harlow.core.common.normalizeServerUrl
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/** Untrusted certificates can't be accepted anymore, so sign-in explains what to do instead. */
class SignInErrorMessageTest {

    @Test
    fun `an untrusted certificate, however wrapped, gets an explanation`() {
        val wrapped = IOException("request failed", SSLHandshakeException("Trust anchor for certification path"))

        assertEquals(R.string.sign_in_error_untrusted_certificate, signInErrorMessage(wrapped))
        assertEquals(R.string.sign_in_error_untrusted_certificate, signInErrorMessage(CertificateException("expired")))
    }

    @Test
    fun `addresses that can't be used get their own message`() {
        assertEquals(R.string.sign_in_error_insecure_address, signInErrorMessage(addressError("http://jelly.example")))
        assertEquals(R.string.sign_in_error_invalid_address, signInErrorMessage(addressError("ftp://jelly.example")))
        assertEquals(R.string.sign_in_error_invalid_address, signInErrorMessage(addressError("   ")))
    }

    @Test
    fun `other errors never show the exception text`() {
        assertEquals(R.string.sign_in_error_generic, signInErrorMessage(IOException("Invalid HTTP status: 401")))
    }

    private fun addressError(address: String): Throwable =
        runCatching { normalizeServerUrl(address) }.exceptionOrNull()!!
}
