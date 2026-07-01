package com.example.crypto

import java.security.SecureRandom
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Pure Kotlin implementation of Curve25519 (RFC 7748) key agreement (X25519).
 * Fully self-contained and compatible with any Android API level (24+).
 * This class provides key pair generation and scalar multiplication.
 */
object Curve25519 {

    private val random = SecureRandom()

    // GF(2^255 - 19) prime: 2^255 - 19
    private val P = java.math.BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    private val A24 = java.math.BigInteger("121665") // (A - 2) / 4 where A = 486662

    /**
     * Generates a secure random 32-byte private key.
     * Clans (clamps) it as per RFC 7748.
     */
    fun generatePrivateKey(): ByteArray {
        val privateKey = ByteArray(32)
        random.nextBytes(privateKey)
        // Clamp private key
        privateKey[0] = privateKey[0] and 248.toByte()
        privateKey[31] = privateKey[31] and 127.toByte()
        privateKey[31] = privateKey[31] or 64.toByte()
        return privateKey
    }

    /**
     * Computes the 32-byte public key from a clamped private key.
     * Uses 9 as the base point u-coordinate.
     */
    fun generatePublicKey(privateKey: ByteArray): ByteArray {
        val basePoint = ByteArray(32)
        basePoint[0] = 9
        return scalarMult(privateKey, basePoint)
    }

    /**
     * Computes the E2E shared secret: DH(privateKey, peerPublicKey)
     * Returns 32-byte shared secret.
     */
    fun computeSharedSecret(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        return scalarMult(privateKey, peerPublicKey)
    }

    /**
     * X25519 Montgomery Ladder scalar multiplication.
     * Computes scalar * point. Returns 32 bytes representing the u-coordinate.
     */
    private fun scalarMult(scalar: ByteArray, point: ByteArray): ByteArray {
        // Clamp the scalar again to be absolutely sure
        val s = scalar.clone()
        s[0] = s[0] and 248.toByte()
        s[31] = s[31] and 127.toByte()
        s[31] = s[31] or 64.toByte()

        val u = decodeU(point)

        // Montgomery ladder variables
        var x1 = u
        var x2 = java.math.BigInteger.ONE
        var z2 = java.math.BigInteger.ZERO
        var x3 = u
        var z3 = java.math.BigInteger.ONE

        // Perform the Montgomery ladder bits (from bit 254 down to 0)
        for (t in 254 downTo 0) {
            val bit = (s[t ushr 3].toInt() ushr (t and 7)) and 1
            if (bit == 1) {
                // Swap (x2, x3) and (z2, z3)
                var temp = x2; x2 = x3; x3 = temp
                temp = z2; z2 = z3; z3 = temp
            }

            // Montgomery formulas
            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            x3 = da.add(cb).mod(P)
            x3 = x3.multiply(x3).mod(P)
            z3 = da.subtract(cb).mod(P)
            z3 = z3.multiply(z3).mod(P).multiply(x1).mod(P)

            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e))).mod(P)

            if (bit == 1) {
                // Swap back
                var temp = x2; x2 = x3; x3 = temp
                temp = z2; z2 = z3; z3 = temp
            }
        }

        val result = x2.multiply(z2.modInverse(P)).mod(P)
        return encodeU(result)
    }

    private fun decodeU(point: ByteArray): java.math.BigInteger {
        val reversed = ByteArray(32)
        for (i in 0..31) {
            reversed[i] = point[31 - i]
        }
        // Force positive signum to treat as unsigned big-endian
        return java.math.BigInteger(1, reversed)
    }

    private fun encodeU(u: java.math.BigInteger): ByteArray {
        val bytes = u.toByteArray()
        val result = ByteArray(32)
        // Copy big integer bytes to output in little-endian format
        val start = Math.max(0, bytes.size - 32)
        for (i in 0 until (bytes.size - start)) {
            result[i] = bytes[bytes.size - 1 - i]
        }
        return result
    }

    // Helper functions for Hex conversion
    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun fromHex(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
