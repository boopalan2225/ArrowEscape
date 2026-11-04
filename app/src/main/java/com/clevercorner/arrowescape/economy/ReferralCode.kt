package com.clevercorner.arrowescape.economy

import kotlin.random.Random

/**
 * Invite codes: short enough to read out loud, strict enough to reject a typo
 * before it reaches anyone's wallet.
 *
 * Pure arithmetic over a reduced alphabet - no I/O, no Android, no server.
 * The last character is a checksum of the others, which means roughly 1 in 24
 * random strings is accepted; that is not security, and is not pretending to
 * be. It exists so "is that an O or a zero" never costs a player coins, and
 * so a mistyped code fails instantly instead of silently crediting nothing.
 */
object ReferralCode {

    /**
     * No I/1/O/0/S/5/B/8/Z/2: every pair a human mishears or mistypes when
     * reading a code off a screen is simply not in the alphabet.
     */
    private const val ALPHABET = "ACDEFGHJKLMNPQRTUVWXY6794"

    /** Characters a player types; the checksum is the one after these. */
    private const val BODY_LENGTH = 5

    val LENGTH: Int = BODY_LENGTH + 1

    /** A fresh code. Minted once per install and then persisted forever. */
    fun generate(random: Random = Random.Default): String {
        val body = (1..BODY_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return body + checksumOf(body)
    }

    /** Upper-cases and strips the spaces and dashes people add while reading. */
    fun normalise(raw: String): String =
        raw.uppercase().filter { it in ALPHABET }

    /** True when [code] is the right length and its checksum agrees. */
    fun isValid(code: String): Boolean {
        val clean = normalise(code)
        if (clean.length != LENGTH) return false
        return checksumOf(clean.take(BODY_LENGTH)) == clean.last()
    }

    private fun checksumOf(body: String): Char =
        ALPHABET[body.sumOf { ALPHABET.indexOf(it) + 1 } % ALPHABET.length]
}

/** Why a redeem did or did not pay out. The UI says one sentence per case. */
enum class RedeemResult {
    CREDITED,

    /** Not a real code - wrong length, or the checksum disagrees. */
    MALFORMED,

    /** Players cannot invite themselves, however entrepreneurial. */
    OWN_CODE,

    /** One invite bonus per player, ever. */
    ALREADY_REDEEMED
}
