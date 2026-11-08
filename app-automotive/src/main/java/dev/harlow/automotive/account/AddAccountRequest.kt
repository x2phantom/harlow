package dev.harlow.automotive.account

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentCompat

/**
 * The "Add account" request of the system's account settings that sign-in answers. At most one is pending: sign-in is
 * `singleTask`, so a second request reaches the same activity, replaces the first and tells it it was canceled.
 */
class AddAccountRequest {

    private var response: AccountAuthenticatorResponse? = null

    /**
     * Takes over the response [intent] carries. Only the system's account settings hand one over; a deep link (from
     * any app) never does and leaves a pending request alone.
     */
    fun take(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) return
        val newResponse = IntentCompat.getParcelableExtra(
            intent,
            AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
            AccountAuthenticatorResponse::class.java,
        ) ?: return
        if (newResponse === response) return
        cancel()
        response = newResponse
        newResponse.onRequestContinued()
    }

    /** Signed in: the account settings stop waiting and show the new account. */
    fun complete(result: Bundle) {
        response?.onResult(result)
        response = null
    }

    /** Closed without signing in: the account settings otherwise keep waiting. */
    fun cancel() {
        response?.onError(AccountManager.ERROR_CODE_CANCELED, "Canceled")
        response = null
    }
}
