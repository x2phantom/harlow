package dev.harlow.core.database

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the Jellyfin access token before it goes into Room ([dev.harlow.core.database.entity.ServerEntity]).
 * Blocking (Keystore): call off the main thread.
 */
interface TokenCipher {
    fun encrypt(token: String): String

    /** The token, or `null` if [stored] can't be decrypted (key gone, data damaged). */
    fun decrypt(stored: String): String?

    /** Whether [stored] is still plaintext from a version before encryption. */
    fun isLegacyPlaintext(stored: String): Boolean = !stored.startsWith(PREFIX)

    /** Deletes the key on signing out: whatever copy of the old ciphertext survived can't be decrypted anymore. */
    fun deleteKey()

    companion object {
        const val PREFIX = "v1:"
    }
}

/** AES-256-GCM with a key in the Android Keystore; the key never leaves it and needs no user authentication. */
class KeystoreTokenCipher : TokenCipher {

    override fun encrypt(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getEncoder()
        return TokenCipher.PREFIX + encoder.encodeToString(cipher.iv) + SEPARATOR + encoder.encodeToString(encrypted)
    }

    override fun decrypt(stored: String): String? {
        if (isLegacyPlaintext(stored)) return stored
        return try {
            val (iv, encrypted) = stored.removePrefix(TokenCipher.PREFIX).split(SEPARATOR, limit = 2)
                .map { Base64.getDecoder().decode(it) }
                .takeIf { it.size == 2 } ?: return null
            val key = existingKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun deleteKey() {
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun key(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
        }
        .generateKey()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "harlow_access_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
