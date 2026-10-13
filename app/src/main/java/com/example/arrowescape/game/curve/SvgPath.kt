package com.example.arrowescape.game.curve

/**
 * Minimal SVG path parser (M, L, H, V, C, S, Q, T, Z + relative forms).
 *
 * Shapes used to be hundreds of lines of hand-written PathCommand
 * constructors, which made them impossible to eyeball and - as the "Owl that
 * looked like a blob" proved - impossible to get right. Compact path strings
 * can be lifted straight from vector art and read at a glance.
 */
object SvgPath {

    fun parse(d: String, width: Float = 1f, height: Float = 1f): List<PathCommand> {
        val tokens = tokenize(d)
        val commands = mutableListOf<PathCommand>()

        var i = 0
        var cursor = Point2D.ZERO
        var start = Point2D.ZERO
        var lastControl: Point2D? = null
        var command = ' '

        fun pt(x: Float, y: Float) = Point2D(x * width, y * height)

        while (i < tokens.size) {
            val token = tokens[i]
            if (token.isLetter()) {
                command = token.char
                i++
                // Z takes no arguments.
                if (command == 'Z' || command == 'z') {
                    commands.add(PathCommand.Close)
                    cursor = start
                    lastControl = null
                    continue
                }
            }

            val relative = command.isLowerCase()
            fun num(): Float = tokens[i++].value
            fun coord(): Point2D {
                val x = num()
                val y = num()
                return if (relative) Point2D(cursor.x + x * width, cursor.y + y * height)
                else pt(x, y)
            }

            when (command.uppercaseChar()) {
                'M' -> {
                    val p = coord()
                    commands.add(PathCommand.MoveTo(p))
                    cursor = p
                    start = p
                    lastControl = null
                    // Implicit subsequent pairs are line-tos.
                    command = if (relative) 'l' else 'L'
                }

                'L' -> {
                    val p = coord()
                    commands.add(PathCommand.LineTo(p))
                    cursor = p
                    lastControl = null
                }

                'H' -> {
                    val x = num()
                    val p = if (relative) Point2D(cursor.x + x * width, cursor.y)
                    else Point2D(x * width, cursor.y)
                    commands.add(PathCommand.LineTo(p))
                    cursor = p
                    lastControl = null
                }

                'V' -> {
                    val y = num()
                    val p = if (relative) Point2D(cursor.x, cursor.y + y * height)
                    else Point2D(cursor.x, y * height)
                    commands.add(PathCommand.LineTo(p))
                    cursor = p
                    lastControl = null
                }

                'C' -> {
                    val c1 = coord()
                    val c2 = coord()
                    val end = coord()
                    commands.add(PathCommand.CurveTo(c1, c2, end))
                    cursor = end
                    lastControl = c2
                }

                'S' -> {
                    // Smooth cubic: reflect the previous control point.
                    val c1 = lastControl?.let { cursor * 2f - it } ?: cursor
                    val c2 = coord()
                    val end = coord()
                    commands.add(PathCommand.CurveTo(c1, c2, end))
                    cursor = end
                    lastControl = c2
                }

                'Q' -> {
                    val c = coord()
                    val end = coord()
                    commands.add(PathCommand.QuadTo(c, end))
                    cursor = end
                    lastControl = c
                }

                'T' -> {
                    val c = lastControl?.let { cursor * 2f - it } ?: cursor
                    val end = coord()
                    commands.add(PathCommand.QuadTo(c, end))
                    cursor = end
                    lastControl = c
                }

                else -> i++ // Unknown command - skip defensively.
            }
        }

        return commands
    }

    // ── tokenizer ────────────────────────────────────────────────────────

    private class Token(val char: Char, val value: Float, private val letter: Boolean) {
        fun isLetter() = letter
    }

    private fun tokenize(d: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0

        while (i < d.length) {
            val c = d[i]
            when {
                c.isWhitespace() || c == ',' -> i++

                c.isLetter() -> {
                    tokens.add(Token(c, 0f, true))
                    i++
                }

                else -> {
                    val sb = StringBuilder()
                    if (d[i] == '-' || d[i] == '+') sb.append(d[i++])
                    while (i < d.length && (d[i].isDigit() || d[i] == '.')) sb.append(d[i++])
                    // Exponent form, e.g. 1e-3
                    if (i < d.length && (d[i] == 'e' || d[i] == 'E')) {
                        sb.append(d[i++])
                        if (i < d.length && (d[i] == '-' || d[i] == '+')) sb.append(d[i++])
                        while (i < d.length && d[i].isDigit()) sb.append(d[i++])
                    }
                    val parsed = sb.toString().toFloatOrNull()
                    if (parsed == null) i++ else tokens.add(Token(' ', parsed, false))
                }
            }
        }

        return tokens
    }
}
