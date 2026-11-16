package com.retrobit.app.data

import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Offline supporter keys. The Play build unlocks through Google Play instead; this path exists
 * for the sideload build and for internal testing without a store listing.
 *
 * Format: RB7-XXXX-XXXX-XXXX-CCCCCC, where the first twelve characters are the body and the
 * last six are a PBKDF2-derived check. Only the check is embedded, never a key list. The
 * 100k-iteration KDF makes blind guessing and offline enumeration expensive, but a key derived
 * from the embedded secret is still extractable from a modified APK: this is a convenience
 * lock, not a cryptographic paywall. The Play build hides this entry entirely.
 */
object SupporterKeys {

    const val TEST_KEY = "RB7-GAME-RKEY-ABCD-8V3TB9"

    private const val PREFIX = "RB7"
    private const val BODY_LENGTH = 12
    private const val CHECK_LENGTH = 6
    private const val ITERATIONS = 100_000
    private const val KEY_BITS = 256
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secret = "retrobit-supporters-2026".toCharArray()

    fun isValid(rawInput: String): Boolean {
        val raw = normalize(rawInput)
        if (raw.length != PREFIX.length + BODY_LENGTH + CHECK_LENGTH) return false
        if (!raw.startsWith(PREFIX)) return false
        val bodyStart = PREFIX.length
        val body = raw.substring(bodyStart, bodyStart + BODY_LENGTH)
        val check = raw.substring(bodyStart + BODY_LENGTH)
        if (body.any { it !in ALPHABET } || check.any { it !in ALPHABET }) return false
        return MessageDigest.isEqual(
            checksum(body).toByteArray(Charsets.US_ASCII),
            check.toByteArray(Charsets.US_ASCII)
        )
    }

    private fun normalize(input: String): String = buildString {
        input.uppercase().forEach { char ->
            if (char in 'A'..'Z' || char in '0'..'9') append(char)
        }
    }

    private fun checksum(body: String): String {
        val spec = PBEKeySpec(secret, body.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_BITS)
        return try {
            val digest = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            buildString {
                for (i in 0 until CHECK_LENGTH) {
                    append(ALPHABET[(digest[i].toInt() and 0xFF) and 31])
                }
            }
        } finally {
            spec.clearPassword()
        }
    }
}
