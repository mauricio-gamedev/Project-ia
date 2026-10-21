package io.github.astromg01.nucleo.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts provider secrets with an AES key held by Android Keystore.
 * Only ciphertext and the GCM nonce are stored in app-private preferences.
 */
class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("encrypted_secrets", Context.MODE_PRIVATE)

    @Synchronized
    fun saveApiKey(value: String) {
        if (value.isBlank()) {
            removeApiKey()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_NONCE, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun readApiKey(): String? {
        val nonceText = preferences.getString(KEY_NONCE, null) ?: return null
        val ciphertextText = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(nonceText, Base64.NO_WRAP)),
            )
            String(Base64.decode(ciphertextText, Base64.NO_WRAP).let(cipher::doFinal), Charsets.UTF_8)
        } catch (_: Exception) {
            // Do not expose the secret or cryptographic details to the UI/logs.
            null
        }
    }

    @Synchronized
    fun removeApiKey() {
        preferences.edit().remove(KEY_NONCE).remove(KEY_CIPHERTEXT).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nucleo_provider_secret_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_NONCE = "provider_api_key_nonce"
        const val KEY_CIPHERTEXT = "provider_api_key_ciphertext"
    }
}
