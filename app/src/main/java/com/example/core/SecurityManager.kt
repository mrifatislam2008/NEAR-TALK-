package com.example.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.util.Logger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cryptographic SecurityManager implementing:
 * - Ephemeral ECDH (secp256r1 / P-256) key agreement
 * - HKDF-SHA256 per-session AES-256 key derivation
 * - AES-256-GCM authenticated encryption with per-packet nonces and AAD
 * - HMAC-SHA256 mutual challenge proof
 * - Sliding-window replay protection on sequence numbers
 * - Android KeyStore master key protection for local identity/secrets
 */
@Singleton
class SecurityManager @Inject constructor() {

    private val secureRandom = SecureRandom()

    @Volatile
    private var sessionAesKey: SecretKey? = null

    @Volatile
    private var sessionAuthMacKey: ByteArray? = null

    @Volatile
    private var highestInboundSeq: Int = -1
    private var replayWindowMask: Long = 0L

    private val outboundIvCounter = AtomicInteger(1)
    private val localIvSalt = ByteArray(4)

    init {
        ensureKeystoreMasterKey()
        secureRandom.nextBytes(localIvSalt)
    }

    private fun ensureKeystoreMasterKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
        }.onFailure {
            // In unit tests / non-TEE Robolectric JVM environments KeyStore may be unavailable;
            // session ECDH + AES-GCM remains fully operational in memory.
            Logger.w(TAG, "AndroidKeyStore unavailable in current environment: ${it.message}")
        }
    }

    fun generateRandomBytes(length: Int): ByteArray {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    fun generateEphemeralKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return kpg.generateKeyPair()
    }

    /**
     * Derives per-session AES-256 encryption key and HMAC authentication key via ECDH + HKDF-SHA256.
     */
    @Synchronized
    fun deriveSessionKeys(
        localKeyPair: KeyPair,
        remotePublicKeyEncoded: ByteArray,
        localNonce: ByteArray,
        remoteNonce: ByteArray,
        isInitiator: Boolean
    ): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val remotePubKey = keyFactory.generatePublic(X509EncodedKeySpec(remotePublicKeyEncoded))
            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(localKeyPair.private)
            keyAgreement.doPhase(remotePubKey, true)
            val sharedSecret = keyAgreement.generateSecret()

            // Deterministic transcript salt ordering regardless of initiator/responder
            val salt = if (isInitiator) {
                localNonce + remoteNonce
            } else {
                remoteNonce + localNonce
            }

            val prk = hmacSha256(salt, sharedSecret)
            val aesKeyBytes = hkdfExpand(prk, "NEARTALK-AES-GCM-V1".toByteArray(), 32)
            val macKeyBytes = hkdfExpand(prk, "NEARTALK-AUTH-MAC-V1".toByteArray(), 32)

            sessionAesKey = SecretKeySpec(aesKeyBytes, "AES")
            sessionAuthMacKey = macKeyBytes
            highestInboundSeq = -1
            replayWindowMask = 0L
            outboundIvCounter.set(1)
            secureRandom.nextBytes(localIvSalt)

            sharedSecret.fill(0)
            prk.fill(0)
            aesKeyBytes.fill(0)
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to derive session keys", e)
            false
        }
    }

    fun computeAuthProof(localNonce: ByteArray, remoteNonce: ByteArray, signerDeviceId: String): ByteArray {
        val macKey = sessionAuthMacKey ?: throw IllegalStateException("Session key not derived")
        val input = localNonce + remoteNonce + signerDeviceId.toByteArray(Charsets.UTF_8)
        return hmacSha256(macKey, input)
    }

    fun verifyAuthProof(
        proof: ByteArray,
        peerNonce: ByteArray,
        ourNonce: ByteArray,
        peerDeviceId: String
    ): Boolean {
        val macKey = sessionAuthMacKey ?: return false
        val expected = hmacSha256(macKey, peerNonce + ourNonce + peerDeviceId.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(proof, expected)
    }

    fun hasActiveSession(): Boolean = sessionAesKey != null

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Output format: [12-byte IV][ciphertext + 16-byte GCM tag]
     */
    fun encryptPayload(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val key = sessionAesKey ?: throw IllegalStateException("No active AES-GCM session key")
        val iv = ByteArray(GCM_IV_LENGTH).apply {
            val counter = outboundIvCounter.getAndIncrement()
            System.arraycopy(localIvSalt, 0, this, 0, 4)
            ByteBuffer.wrap(this, 4, 8).putLong(counter.toLong() shl 32 or (secureRandom.nextInt().toLong() and 0xFFFFFFFFL))
        }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /**
     * Decrypts AES-256-GCM payload and verifies authentication tag + AAD.
     */
    fun decryptPayload(encrypted: ByteArray, aad: ByteArray): ByteArray {
        val key = sessionAesKey ?: throw IllegalStateException("No active AES-GCM session key")
        require(encrypted.size > GCM_IV_LENGTH + (GCM_TAG_BITS / 8)) {
            "Encrypted payload too short"
        }
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Sliding-window replay protection (64-packet tolerance for out-of-order audio frames,
     * rejecting duplicates and overly stale packets).
     */
    @Synchronized
    fun checkAndAcceptSequence(sequenceNumber: Int): Boolean {
        if (sequenceNumber < 0) return false
        if (highestInboundSeq == -1) {
            highestInboundSeq = sequenceNumber
            replayWindowMask = 1L
            return true
        }
        if (sequenceNumber > highestInboundSeq) {
            val shift = sequenceNumber - highestInboundSeq
            replayWindowMask = if (shift >= 64) 1L else (replayWindowMask shl shift) or 1L
            highestInboundSeq = sequenceNumber
            return true
        }
        val diff = highestInboundSeq - sequenceNumber
        if (diff >= 64) {
            return false // Too old / potential replay
        }
        val bit = 1L shl diff
        if ((replayWindowMask and bit) != 0L) {
            return false // Duplicate / replay
        }
        replayWindowMask = replayWindowMask or bit
        return true
    }

    @Synchronized
    fun clearSession() {
        sessionAesKey = null
        sessionAuthMacKey?.fill(0)
        sessionAuthMacKey = null
        highestInboundSeq = -1
        replayWindowMask = 0L
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val result = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        return result
    }

    companion object {
        private const val TAG = "SecurityManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "neartalk_local_master_key"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
    }
}
