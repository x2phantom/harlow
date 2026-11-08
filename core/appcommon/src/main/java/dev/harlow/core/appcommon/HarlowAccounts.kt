package dev.harlow.core.appcommon

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.harlow.core.common.isSecureServerUrl
import dev.harlow.core.data.repository.UserRepositoryImpl
import dev.harlow.core.model.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in Jellyfin user as an Android account, so AAOS lists it in the system's account settings and the user
 * can sign out there too (removal goes through the app's authenticator, which calls [onAccountRemoved]).
 *
 * Room's active server stays the source of truth for the session (its token is what the app uses); the account
 * mirrors it. [reconcile] repairs the mirror on start, e.g. after an update from a version without accounts.
 *
 * Guest profiles may have `DISALLOW_MODIFY_ACCOUNTS`: the system's settings then can't add or remove accounts, but
 * the app's own authenticator still can, so signing in and out inside the app keeps working.
 */
@Singleton
class HarlowAccounts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepositoryImpl,
) {
    private val accountManager: AccountManager = AccountManager.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accountType: String = context.getString(R.string.account_type)

    fun accounts(): List<Account> = accountManager.getAccountsByType(accountType).toList()

    /** Adds the account for a fresh sign-in, replacing any previous one. Returns it, or `null` if that failed. */
    suspend fun onSignedIn(server: Server, username: String): Account? = withContext(Dispatchers.IO) {
        removeAll()
        val account = Account(accountName(username, server.url), accountType)
        try {
            val userData = Bundle().apply {
                putString(KEY_SERVER_ID, server.id)
                putString(KEY_SERVER_URL, server.url)
            }
            if (accountManager.addAccountExplicitly(account, null, userData)) account else null
        } catch (e: SecurityException) {
            // The session works without the account; it just doesn't show in the system's settings.
            Log.w(TAG, "Couldn't add the account", e)
            null
        }
    }

    /** Signs out in the app: ends the session and removes the account. */
    suspend fun signOut() {
        userRepository.logout()
        withContext(Dispatchers.IO) { removeAll() }
    }

    /**
     * The account was removed in the system's settings: sign out too. Called on a binder thread of the system's
     * account service, which must not wait for revoking the token and deleting the data: that runs in the background.
     */
    fun onAccountRemoved() {
        scope.launch { userRepository.logout() }
    }

    /** Brings the account in line with the session: one account while signed in, none while signed out. */
    suspend fun reconcile() {
        // Sessions from versions that allowed http:// can't connect anymore (cleartext is blocked): sign them out, so
        // the media center offers "Sign in" instead of staying offline.
        if (userRepository.getActiveServer()?.let { !isSecureServerUrl(it.url) } == true) {
            Log.i(TAG, "Signing out of a server without HTTPS")
            signOut()
            return
        }
        val server = userRepository.getActiveServer()
        val accounts = accounts()
        when {
            server == null && accounts.isNotEmpty() -> withContext(Dispatchers.IO) { removeAll() }
            // The username isn't stored; the server's name stands in for it.
            server != null && accounts.isEmpty() -> onSignedIn(server, username = server.name.ifBlank { "Jellyfin" })
        }
    }

    private fun removeAll() {
        accounts().forEach { account ->
            try {
                accountManager.removeAccountExplicitly(account)
            } catch (e: SecurityException) {
                Log.w(TAG, "Couldn't remove the account", e)
            }
        }
    }

    private fun accountName(username: String, serverUrl: String): String {
        val host = Uri.parse(serverUrl).host ?: serverUrl
        return "$username@$host"
    }

    private companion object {
        const val TAG = "HarlowAccounts"
        const val KEY_SERVER_ID = "server_id"
        const val KEY_SERVER_URL = "server_url"
    }
}
