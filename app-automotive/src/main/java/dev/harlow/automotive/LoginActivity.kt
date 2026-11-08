package dev.harlow.automotive

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.harlow.automotive.account.AddAccountRequest
import dev.harlow.core.appcommon.LoginViewModel
import dev.harlow.core.designsystem.component.HarlowWindow
import dev.harlow.core.designsystem.login.LoginScreen
import dev.harlow.core.designsystem.theme.HarlowTheme

/**
 * Signs in to a Jellyfin server. Opened by the "Sign in" button the car's media center shows while nobody is signed
 * in, by "Add account" in the system's account settings (with an [AccountAuthenticatorResponse]), from settings
 * and by the `harlow://signin` deep link.
 *
 * Only usable while parked: like all non-media activities it isn't distraction-optimized, so AAOS blocks it while
 * driving. Finishes once signed in; the media service notices the new session and the media center refreshes.
 *
 * `singleTask` (multi-display head units): moving between driver and passenger display moves this one instance instead of
 * starting a second; a new request reaches it through [onNewIntent].
 */
@AndroidEntryPoint
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private val addAccountRequest = AddAccountRequest()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        addAccountRequest.take(intent)

        setContent {
            HarlowTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(state.server) {
                    if (state.server != null) onSignedIn()
                }

                HarlowWindow {
                    LoginScreen(
                        logo = painterResource(R.drawable.ic_launcher_foreground),
                        onSignIn = viewModel::signIn,
                        isLoading = state.isLoading,
                        error = state.error?.let { stringResource(it) },
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        addAccountRequest.take(intent)
    }

    private fun onSignedIn() {
        addAccountRequest.complete(
            bundleOf(AccountManager.KEY_ACCOUNT_TYPE to getString(dev.harlow.core.appcommon.R.string.account_type)),
        )
        setResult(RESULT_OK)
        finish()
    }

    override fun finish() {
        // Closed without signing in: tell the system's account settings, which otherwise keep waiting.
        addAccountRequest.cancel()
        super.finish()
    }
}
