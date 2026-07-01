package com.example.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Standard Cryptography engine implementing AES-256-GCM symmetric encryption
 * and HKDF-SHA256 Key Derivation as required by the E2E protocol.
 */
object CryptoEngine {

    private val random = SecureRandom()
    private const val AES_KEY_SIZE = 32 // 256 bits
    private const val GCM_IV_SIZE = 12 // 96 bits nonce
    private const val GCM_TAG_SIZE = 128 // 16 bytes tag size (in bits)

    /**
     * Derives a 256-bit AES key from a 32-byte X25519 shared secret using HKDF-SHA256.
     * @param sharedSecret 32-byte shared DH secret
     * @param info Optional context/info info to bind the key (default is "E2E-Session-Key")
     */
    fun deriveKey(sharedSecret: ByteArray, info: String = "E2E-Session-Key"): ByteArray {
        val salt = ByteArray(32) { 0 } // Standard constant salt for extraction
        return hkdfSha256(sharedSecret, salt, info.toByteArray(), AES_KEY_SIZE)
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with the derived symmetric key.
     * @return Pair containing: [Ciphertext (Hex), Nonce/IV (Hex)]
     */
    fun encrypt(plaintext: ByteArray, symmetricKey: ByteArray): Pair<String, String> {
        val iv = ByteArray(GCM_IV_SIZE)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(symmetricKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext)

        return Pair(
            Curve25519.toHex(ciphertext),
            Curve25519.toHex(iv)
        )
    }

    /**
     * Decrypts AES-256-GCM ciphertext bytes using the symmetric key.
     * @return Decrypted plaintext bytes
     */
    fun decrypt(ciphertextHex: String, nonceHex: String, symmetricKey: ByteArray): ByteArray {
        val ciphertext = Curve25519.fromHex(ciphertextHex)
        val iv = Curve25519.fromHex(nonceHex)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(symmetricKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Generates a secure random 256-bit symmetric key for group chats.
     */
    fun generateGroupKey(): ByteArray {
        val key = ByteArray(AES_KEY_SIZE)
        random.nextBytes(key)
        return key
    }

    /**
     * HKDF RFC 5869 implementation using HMAC-SHA256.
     */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray {
        // Step 1: Extract (PRK = HMAC-Hash(Salt, IKM))
        val macExtract = Mac.getInstance("HmacSHA256")
        macExtract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = macExtract.doFinal(ikm)

        // Step 2: Expand (OKM = HKDF-Expand(PRK, info, L))
        val macExpand = Mac.getInstance("HmacSHA256")
        macExpand.init(SecretKeySpec(prk, "HmacSHA256"))

        val okm = ByteArray(size)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1

        while (offset < size) {
            macExpand.reset()
            macExpand.update(t)
            macExpand.update(info)
            macExpand.update(counter.toByte())
            t = macExpand.doFinal()

            val bytesToCopy = Math.min(t.size, size - offset)
            System.arraycopy(t, 0, okm, offset, bytesToCopy)
            offset += bytesToCopy
            counter++
        }

        return okm
    }
}
