package dev.harlow.core.appcommon

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.harlow.core.common.HarlowResult
import dev.harlow.core.common.InsecureServerUrlException
import dev.harlow.core.common.InvalidServerUrlException
import dev.harlow.core.data.repository.UserRepository
import dev.harlow.core.model.Server
import dev.harlow.core.network.NetworkStateObserver
import dev.harlow.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.cert.CertificateException
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

data class LoginUiState(
    val isLoading: Boolean = false,
    @StringRes val error: Int? = null,
    val server: Server? = null,
)

/**
 * Signs in to a Jellyfin server. On success the session is in Room (which the media service watches, so the car's
 * media center refreshes), the user is added as an Android account and the library sync starts.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val networkStateObserver: NetworkStateObserver,
    private val syncScheduler: SyncScheduler,
    private val accounts: HarlowAccounts,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signIn(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = userRepository.authenticate(serverUrl, username, password)) {
                is HarlowResult.Success -> {
                    val server = result.data
                    accounts.onSignedIn(server, username)
                    networkStateObserver.markConnected()
                    syncScheduler.schedulePeriodicSync(server.id)
                    syncScheduler.syncNow(server.id)
                    _uiState.value = _uiState.value.copy(isLoading = false, server = server)
                }
                is HarlowResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = signInErrorMessage(result.exception),
                    )
                }
                else -> {}
            }
        }
    }
}

/**
 * The sign-in error to show. Only certificates the system trusts are accepted (there is no way to turn the check
 * off), so a self-signed or otherwise untrusted certificate gets an explanation instead of the TLS exception. Exception
 * texts are never shown: they are English and technical.
 */
@StringRes
internal fun signInErrorMessage(error: Throwable): Int {
    val causes = generateSequence(error) { it.cause }.take(MAX_CAUSES).toList()
    return when {
        causes.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException || it is CertificateException } ->
            R.string.sign_in_error_untrusted_certificate
        error is InsecureServerUrlException -> R.string.sign_in_error_insecure_address
        error is InvalidServerUrlException -> R.string.sign_in_error_invalid_address
        else -> R.string.sign_in_error_generic
    }
}

private const val MAX_CAUSES = 10
