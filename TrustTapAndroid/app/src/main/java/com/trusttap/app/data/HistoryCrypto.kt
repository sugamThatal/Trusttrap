package com.trusttap.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** Encrypts detailed reports in the local Room database using Android Keystore. */
class HistoryCrypto private constructor() {
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKey()
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return PREFIX + iv + ":" + body
    }

    fun decrypt(value: String): String {
        if (!value.startsWith(PREFIX)) return value
        return try {
            val parts = value.removePrefix(PREFIX).split(":", limit = 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // A deleted Keystore key should not crash the History screen.
            "{}"
        }
    }

    fun status(): String = try {
        val secret = key()
        val keyInfo = SecretKeyFactory.getInstance(secret.algorithm, ANDROID_KEYSTORE)
            .getKeySpec(secret, KeyInfo::class.java) as KeyInfo
        if (keyInfo.isInsideSecureHardware) {
            "Detailed history is encrypted with a hardware-backed Android Keystore key."
        } else {
            "Detailed history is encrypted with Android Keystore software protection."
        }
    } catch (_: Exception) {
        "Local history encryption is unavailable on this device."
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "trusttap_history_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX = "v1:"

        @Volatile private var instance: HistoryCrypto? = null

        fun get(context: Context): HistoryCrypto =
            instance ?: synchronized(this) {
                instance ?: HistoryCrypto().also { instance = it }
            }

        fun status(context: Context): String = get(context).status()
    }
}
