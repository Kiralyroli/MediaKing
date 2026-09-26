package com.kiroland.mediacenter.data.transfer

import java.security.SecureRandom

/**
 * Pairing code for the upload page: shown on the TV and in its QR code, sent by the browser with each
 * API call. Eight characters from an alphabet without look-alikes (no 0/O, 1/I/L) so it can be typed
 * from the screen; a lockout after repeated wrong codes stops anyone on the LAN from guessing it.
 */
class PairingGuard(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxFailures: Int = 5,
    private val lockoutMs: Long = 60_000,
) {
    private var failures = 0
    private var lockedUntil = 0L

    sealed interface Check {
        data object Ok : Check
        data object Wrong : Check
        data class Locked(val retryAfterMs: Long) : Check
    }

    @Synchronized
    fun check(expected: String, given: String?): Check {
        val now = clock()
        if (now < lockedUntil) return Check.Locked(lockedUntil - now)
        if (given != null && normalize(given) == normalize(expected)) {
            failures = 0
            return Check.Ok
        }
        if (++failures >= maxFailures) {
            failures = 0
            lockedUntil = now + lockoutMs
        }
        return Check.Wrong
    }

    companion object {
        private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun newCode(): String = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

        /** "k7m2-qp9x", "K7M2QP9X" and "K7M2 QP9X" are the same code. */
        fun normalize(code: String): String = code.uppercase().filter { it.isLetterOrDigit() }

        fun display(code: String): String = normalize(code).chunked(4).joinToString("-")
    }
}
