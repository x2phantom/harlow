package dev.harlow.core.player

import android.content.Context
import android.content.Intent

/**
 * Opens the app's sign-in screen. The app module owns that activity and binds this; the media service puts it behind
 * the "Sign in" button the car's media center shows while nobody is signed in.
 */
fun interface SignInIntentProvider {
    fun signInIntent(context: Context): Intent
}
