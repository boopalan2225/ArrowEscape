package com.arrowescape.game.curve

/**
 * The colour vocabulary shapes are painted with.
 *
 * Colours are chosen from what the thing *is* - a trunk is brown, foliage is
 * green, a stop-sign octagon is red - rather than rotated through a level
 * palette. Named here once so every shape agrees on what "green" means.
 */
object Hues {
    const val RED = 0xFFE53935L
    const val CRIMSON = 0xFFC62828L
    const val PINK = 0xFFEC407AL
    const val ORANGE = 0xFFFB8C00L
    const val GOLD = 0xFFF9A825L
    const val GREEN = 0xFF43A047L
    const val FOREST = 0xFF2E7D32L
    const val TEAL = 0xFF26A69AL
    const val CYAN = 0xFF26C6DAL
    const val DEEP_CYAN = 0xFF00ACC1L
    const val SKY = 0xFF64B5F6L
    const val BLUE = 0xFF1E88E5L
    const val PURPLE = 0xFF8E44ADL
    const val BROWN = 0xFF795548L
    const val LIGHT_BROWN = 0xFFA1744AL
    const val TAN = 0xFFD7A86EL
    const val STEEL = 0xFF90A4AEL
}

/**
 * A few tones of one colour.
 *
 * A big region in a single flat colour reads as a blob; the reference art gets
 * its texture from slightly varied strokes. Lighter and darker tones of the
 * *same* hue give that texture without ever turning a green tree rainbow.
 */
object ArrowTones {

    /** How far each tone drifts from the base, as a fraction. */
    private const val SHIFT = 0.14f

    /** Light, base, dark - arrows cycle through them by id. */
    fun of(base: Long): List<Long> = listOf(shift(base, SHIFT), base, shift(base, -SHIFT))

    /** Positive [amount] blends toward white, negative toward black. */
    private fun shift(argb: Long, amount: Float): Long {
        fun channel(shiftBits: Int): Long {
            val c = ((argb shr shiftBits) and 0xFF).toFloat()
            val moved = if (amount >= 0f) c + (255f - c) * amount else c * (1f + amount)
            return moved.toInt().coerceIn(0, 255).toLong()
        }
        return (0xFFL shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
