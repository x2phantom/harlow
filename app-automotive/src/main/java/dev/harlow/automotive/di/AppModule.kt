package dev.harlow.automotive.di

import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.harlow.automotive.BuildConfig
import dev.harlow.automotive.LoginActivity
import dev.harlow.core.player.SignInIntentProvider
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Named("appVersion")
    fun provideAppVersion(): String = BuildConfig.VERSION_NAME

    /** The media center's "Sign in" button opens the sign-in activity in a new task, from outside the app. */
    @Provides
    fun provideSignInIntentProvider(): SignInIntentProvider = SignInIntentProvider { context ->
        Intent(context, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
