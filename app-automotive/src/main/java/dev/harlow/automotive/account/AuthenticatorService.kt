package dev.harlow.automotive.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.core.os.bundleOf
import dagger.hilt.android.AndroidEntryPoint
import dev.harlow.automotive.LoginActivity
import dev.harlow.core.appcommon.HarlowAccounts
import javax.inject.Inject

/** Binds the app's [Authenticator] for the system's account settings. */
@AndroidEntryPoint
class AuthenticatorService : Service() {

    @Inject lateinit var accounts: HarlowAccounts

    private val authenticator by lazy { Authenticator(this, accounts) }

    override fun onBind(intent: Intent?): IBinder? = authenticator.iBinder
}

/**
 * The Jellyfin account in the system's account settings: "Add account" opens [LoginActivity], removing the account
 * signs out. The app keeps its session (server and token) in Room, so there are no auth tokens to hand out here.
 */
private class Authenticator(
    private val context: Context,
    private val accounts: HarlowAccounts,
) : AbstractAccountAuthenticator(context) {

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ): Bundle {
        val intent = Intent(context, LoginActivity::class.java)
            .putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
        return bundleOf(AccountManager.KEY_INTENT to intent)
    }

    /** Called on a binder thread before the system removes the account; signs out in the background. */
    override fun getAccountRemovalAllowed(response: AccountAuthenticatorResponse, account: Account): Bundle {
        accounts.onAccountRemoved()
        return bundleOf(AccountManager.KEY_BOOLEAN_RESULT to true)
    }

    override fun editProperties(response: AccountAuthenticatorResponse, accountType: String): Bundle? = null

    override fun confirmCredentials(response: AccountAuthenticatorResponse, account: Account, options: Bundle?) =
        null

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle?,
    ): Bundle = bundleOf(AccountManager.KEY_ERROR_CODE to AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)

    override fun getAuthTokenLabel(authTokenType: String): String? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String?,
        options: Bundle?,
    ): Bundle? = null

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>,
    ): Bundle = bundleOf(AccountManager.KEY_BOOLEAN_RESULT to false)
}
