package com.example.kanjipractice.domain.stroke

/**
 * A small SVG path-data parser, enough for KanjiVG and for hand-written paths.
 *
 * Every command in the SVG 1.1 grammar is understood except elliptical arcs
 * (`A`/`a`), which are approximated by a straight line to the arc's end point.
 * KanjiVG only ever emits M, m, C, c, S and s (verified against the whole
 * bundled corpus by StrokeDataServiceTest), so this is not a practical gap --
 * and it is recorded here rather than silently ignored.
 */
object SvgPathParser {

    /**
     * @throws IllegalArgumentException when the data contains an unknown command
     *   or runs out of numbers.
     */
    fun parse(data: String): List<SvgPathCommand> {
        val scanner = Scanner(data)
        val out = ArrayList<SvgPathCommand>()

        var current = Point(0f, 0f)      // current point
        var subpathStart = Point(0f, 0f) // start of the current subpath, for Z
        var lastCubicControl: Point? = null
        var lastQuadControl: Point? = null
        var command = ' '
        var relative = false

        while (true) {
            if (scanner.hasCommand()) {
                command = scanner.nextCommand()
                relative = command.isLowerCase()
                if (command.lowercaseChar() == 'z') {
                    out += SvgPathCommand.Close
                    current = subpathStart
                    scanner.consumeSeparators()
                    continue
                }
            } else if (scanner.hasNumber()) {
                // Implicit repeat: "M 1 2 3 4" means M then L; other commands repeat.
                command = when (command) {
                    'M' -> 'L'
                    'm' -> 'l'
                    else -> command
                }
                if (command == ' ') break
            } else {
                break
            }

            val originX = if (relative) current.x else 0f
            val originY = if (relative) current.y else 0f

            when (command.lowercaseChar()) {
                'm' -> {
                    val x = scanner.nextNumber() + originX
                    val y = scanner.nextNumber() + originY
                    out += SvgPathCommand.MoveTo(x, y)
                    current = Point(x, y)
                    subpathStart = current
                    lastCubicControl = null
                    lastQuadControl = null
                }

                'l' -> {
                    val x = scanner.nextNumber() + originX
                    val y = scanner.nextNumber() + originY
                    out += SvgPathCommand.LineTo(x, y)
                    current = Point(x, y)
                    lastCubicControl = null
                    lastQuadControl = null
                }

                'h' -> {
                    val x = scanner.nextNumber() + originX
                    out += SvgPathCommand.LineTo(x, current.y)
                    current = Point(x, current.y)
                    lastCubicControl = null
                    lastQuadControl = null
                }

                'v' -> {
                    val y = scanner.nextNumber() + originY
                    out += SvgPathCommand.LineTo(current.x, y)
                    current = Point(current.x, y)
                    lastCubicControl = null
                    lastQuadControl = null
                }

                'c' -> {
                    val x1 = scanner.nextNumber() + originX
                    val y1 = scanner.nextNumber() + originY
                    val x2 = scanner.nextNumber() + originX
                    val y2 = scanner.nextNumber() + originY
                    val x = scanner.nextNumber() + originX
                    val y = scanner.nextNumber() + originY
                    out += SvgPathCommand.CubicTo(x1, y1, x2, y2, x, y)
                    current = Point(x, y)
                    lastCubicControl = Point(x2, y2)
                    lastQuadControl = null
                }

                's' -> {
                    // First control point is the reflection of the previous one.
                    val reflected = lastCubicControl?.let {
                        Point(2f * current.x - it.x, 2f * current.y - it.y)
                    } ?: current
                    val x2 = scanner.nextNumber() + originX
                    val y2 = scanner.nextNumber() + originY
                    val x = scanner.nextNumber() + originX
                    val y = scanner.nextNumber() + originY
                    out += SvgPathCommand.CubicTo(reflected.x, reflected.y, x2, y2, x, y)
                    current = Point(x, y)
                    lastCubicControl = Point(x2, y2)
                    lastQuadControl = null
                }

                'q' -> {
                    val x1 = scanner.nextNumber() + originX
                    val y1 = scanner.nextNumber() + originY
                    val x = scanner.nextNumber() + originX
                    val y = scanner.nextNumber() + originY
                    out += SvgPathCommand.QuadTo(x1, y1, x, y)
                    current = Point(x, y)
                    lastQuadControl = Point(x1, y1)
                    lastCubicControl = null
                }

                't' -> {
                    val reflected = lastQuadControl?.let {
                        Point(2f * current.x - it.x, 2f * current.y - it.y)
                    } ?: current
                    val x = scanner.nextNumber() + originX
                    val y = scanner.nextNumber() + originY
                    out += SvgPathCommand.QuadTo(reflected.x, reflected.y, x, y)
                    current = Point(x, y)
                    lastQuadControl = reflected
                    lastCubicControl = null
                }

                'a' -> {
                    // rx ry x-axis-rotation large-arc-flag sweep-flag x y
                    scanner.nextNumber(); scanner.nextNumber(); scanner.nextNumber()
                    scanner.nextNumber(); scanner.nextNumber()
                    val x = scanner.nextNumber() + originX
                    val y = scanner.nextNumber() + originY
                    out += SvgPathCommand.LineTo(x, y)
                    current = Point(x, y)
                    lastCubicControl = null
                    lastQuadControl = null
                }

                else -> throw IllegalArgumentException("unsupported path command '$command'")
            }
        }
        return out
    }

    private data class Point(val x: Float, val y: Float)

    /**
     * Reads SVG path data: commands are single letters, everything else is a
     * number, and separators (whitespace or commas) are optional between a
     * number and a following sign or dot ("10-5" is two numbers).
     */
    private class Scanner(private val data: String) {
        private var index = 0

        private fun peek(): Char? = if (index < data.length) data[index] else null

        fun consumeSeparators() {
            while (true) {
                val c = peek() ?: return
                if (c == ' ' || c == ',' || c == '\n' || c == '\r' || c == '\t') index++ else return
            }
        }

        fun hasCommand(): Boolean {
            consumeSeparators()
            val c = peek() ?: return false
            return c.isLetter() && c != 'e' && c != 'E'
        }

        fun nextCommand(): Char {
            consumeSeparators()
            return data[index++]
        }

        fun hasNumber(): Boolean {
            consumeSeparators()
            val c = peek() ?: return false
            return c.isDigit() || c == '-' || c == '+' || c == '.'
        }

        fun nextNumber(): Float {
            consumeSeparators()
            val start = index
            val c = peek() ?: throw IllegalArgumentException("path data ended early")
            if (c == '-' || c == '+') index++
            while (peek()?.isDigit() == true) index++
            if (peek() == '.') {
                index++
                while (peek()?.isDigit() == true) index++
            }
            if (peek() == 'e' || peek() == 'E') {
                val mark = index
                index++
                if (peek() == '-' || peek() == '+') index++
                if (peek()?.isDigit() == true) {
                    while (peek()?.isDigit() == true) index++
                } else {
                    index = mark
                }
            }
            if (index == start) throw IllegalArgumentException("expected a number at $index in '$data'")
            return data.substring(start, index).toFloat()
        }
    }
}
