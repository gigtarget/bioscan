package com.example.bioscan.core.common

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

object CryptoUtils {
    private const val KEYSTORE_ALIAS = "BioScanKioskMasterKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    init {
        getOrCreateMasterKey()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptEmbedding(embedding: FloatArray): String {
        val masterKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv

        // Convert FloatArray to ByteArray
        val byteBuffer = java.nio.ByteBuffer.allocate(embedding.size * 4)
        embedding.forEach { byteBuffer.putFloat(it) }
        val plainBytes = byteBuffer.array()

        val encryptedBytes = cipher.doFinal(plainBytes)

        // Combine IV + EncryptedBytes
        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptEmbedding(encryptedBase64: String): FloatArray? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return null

            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            val masterKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

            val plainBytes = cipher.doFinal(encryptedBytes)
            val floatBuffer = java.nio.ByteBuffer.wrap(plainBytes).asFloatBuffer()
            val floats = FloatArray(floatBuffer.remaining())
            floatBuffer.get(floats)
            floats
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun sha256(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
