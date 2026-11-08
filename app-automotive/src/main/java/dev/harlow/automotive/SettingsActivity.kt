package dev.harlow.automotive

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.harlow.automotive.navigation.AutoLicensesRoute
import dev.harlow.automotive.navigation.AutoSettingsRoute
import dev.harlow.core.appcommon.AuthState
import dev.harlow.core.appcommon.MainViewModel
import dev.harlow.core.designsystem.component.HarlowWindow
import dev.harlow.core.designsystem.theme.HarlowTheme
import javax.inject.Inject
import javax.inject.Named

/**
 * The app's settings, opened from the car's media center (the `APPLICATION_PREFERENCES` intent filter) or the system's
 * app info. Browsing and playback have no activity: the media center draws them from HarlowMediaService.
 *
 * Only usable while parked (not distraction-optimized). Opened while nobody is signed in, it hands over to
 * [LoginActivity].
 *
 * `singleTask` (multi-display head units): moving between driver and passenger display moves this one instance instead of
 * starting a second.
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject @Named("appVersion") lateinit var appVersion: String

    /** Signed out from these settings: close them; the media center then offers to sign in again. */
    private var wasSignedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HarlowTheme {
                HarlowWindow {
                    val authState by mainViewModel.authState.collectAsStateWithLifecycle()
                    when (authState) {
                        AuthState.CHECKING -> Loading()
                        AuthState.LOGGED_OUT -> {
                            Loading()
                            LaunchedEffect(Unit) {
                                if (!wasSignedIn) {
                                    startActivity(Intent(this@SettingsActivity, LoginActivity::class.java))
                                }
                                finish()
                            }
                        }
                        AuthState.LOGGED_IN -> {
                            wasSignedIn = true
                            SettingsNavHost(onClose = ::finish)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsNavHost(onClose: () -> Unit) {
        val navController = rememberNavController()
        val serverUrl by mainViewModel.serverUrl.collectAsStateWithLifecycle()
        NavHost(navController = navController, startDestination = ROUTE_SETTINGS) {
            composable(ROUTE_SETTINGS) {
                AutoSettingsRoute(
                    mainViewModel = mainViewModel,
                    appVersion = appVersion,
                    serverUrl = serverUrl,
                    onBack = onClose,
                    onLicensesClick = { navController.navigate(ROUTE_LICENSES) },
                )
            }
            composable(ROUTE_LICENSES) {
                AutoLicensesRoute(onBack = { navController.popBackStack() })
            }
        }
    }

    @Composable
    private fun Loading() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HarlowTheme.colors.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }

    private companion object {
        const val ROUTE_SETTINGS = "settings"
        const val ROUTE_LICENSES = "licenses"
    }
}
