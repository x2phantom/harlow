package dev.harlow.automotive.account

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddAccountRequestTest {

    private val request = AddAccountRequest()

    private fun accountSettingsIntent(response: AccountAuthenticatorResponse) =
        Intent().putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)

    @Test
    fun `a request from the account settings is continued and answered on sign-in`() {
        val response = mockk<AccountAuthenticatorResponse>(relaxed = true)
        val result = Bundle()

        request.take(accountSettingsIntent(response))
        request.complete(result)
        request.cancel()

        verify(exactly = 1) { response.onRequestContinued() }
        verify(exactly = 1) { response.onResult(result) }
        verify(exactly = 0) { response.onError(any(), any()) }
    }

    @Test
    fun `closing without signing in cancels the request`() {
        val response = mockk<AccountAuthenticatorResponse>(relaxed = true)

        request.take(accountSettingsIntent(response))
        request.cancel()

        verify(exactly = 1) { response.onError(AccountManager.ERROR_CODE_CANCELED, any()) }
    }

    @Test
    fun `a second request (singleTask, onNewIntent) cancels the first and is answered instead`() {
        val first = mockk<AccountAuthenticatorResponse>(relaxed = true)
        val second = mockk<AccountAuthenticatorResponse>(relaxed = true)
        val result = Bundle()

        request.take(accountSettingsIntent(first))
        request.take(accountSettingsIntent(second))
        request.complete(result)

        verify(exactly = 1) { first.onError(AccountManager.ERROR_CODE_CANCELED, any()) }
        verify(exactly = 0) { first.onResult(any()) }
        verify(exactly = 1) { second.onRequestContinued() }
        verify(exactly = 1) { second.onResult(result) }
    }

    @Test
    fun `a deep link neither hands over a response nor drops a pending one`() {
        val pending = mockk<AccountAuthenticatorResponse>(relaxed = true)
        val forged = mockk<AccountAuthenticatorResponse>(relaxed = true)
        val result = Bundle()
        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("harlow://signin"))
            .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, forged)

        request.take(accountSettingsIntent(pending))
        request.take(deepLink)
        request.complete(result)

        verify(exactly = 0) { forged.onRequestContinued() }
        verify(exactly = 0) { forged.onResult(any()) }
        verify(exactly = 0) { pending.onError(any(), any()) }
        verify(exactly = 1) { pending.onResult(result) }
    }

    @Test
    fun `an intent without a response leaves the pending request alone`() {
        val pending = mockk<AccountAuthenticatorResponse>(relaxed = true)

        request.take(accountSettingsIntent(pending))
        request.take(Intent())

        verify(exactly = 0) { pending.onError(any(), any()) }
    }
}
