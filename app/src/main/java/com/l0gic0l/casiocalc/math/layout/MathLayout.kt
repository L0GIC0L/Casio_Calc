package com.l0gic0l.casiocalc.math.layout

import com.l0gic0l.casiocalc.math.editor.MathRow

/** Colour role of a drawn element; the renderer maps roles to theme colours. */
enum class Role { NORMAL, FUNCTION, OPERATOR, PLACEHOLDER }

/** Measures text width in pixels; the only thing layout needs from the platform. */
fun interface TextWidth {
    fun width(text: String, size: Float, role: Role): Float
}

/** Drawing commands in layout coordinates (y grows downward, origin at the top-left). */
sealed class DrawOp {
    abstract fun moved(dx: Float, dy: Float): DrawOp

    /** Text whose baseline sits at [baseline]. */
    data class Text(val text: String, val x: Float, val baseline: Float, val size: Float, val role: Role) : DrawOp() {
        override fun moved(dx: Float, dy: Float) = copy(x = x + dx, baseline = baseline + dy)
    }

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val stroke: Float, val role: Role = Role.NORMAL) : DrawOp() {
        override fun moved(dx: Float, dy: Float) = copy(x1 = x1 + dx, y1 = y1 + dy, x2 = x2 + dx, y2 = y2 + dy)
    }

    /** Open polyline given as x0, y0, x1, y1 … */
    data class Polyline(val points: FloatArray, val stroke: Float, val role: Role = Role.NORMAL) : DrawOp() {
        override fun moved(dx: Float, dy: Float) =
            copy(points = FloatArray(points.size) { if (it % 2 == 0) points[it] + dx else points[it] + dy })

        override fun equals(other: Any?) = other is Polyline && points.contentEquals(other.points) && stroke == other.stroke
        override fun hashCode() = points.contentHashCode()
    }

    /** A bracket stretched from [top] to [bottom]; [kind] is one of ( ) [ ] | { }. */
    data class Delimiter(val kind: Char, val x: Float, val top: Float, val bottom: Float, val width: Float, val stroke: Float) : DrawOp() {
        override fun moved(dx: Float, dy: Float) = copy(x = x + dx, top = top + dy, bottom = bottom + dy)
    }

    data class IntegralSign(val x: Float, val top: Float, val bottom: Float, val width: Float, val stroke: Float) : DrawOp() {
        override fun moved(dx: Float, dy: Float) = copy(x = x + dx, top = top + dy, bottom = bottom + dy)
    }

    /** Dotted box marking an empty slot. */
    data class Placeholder(val x: Float, val top: Float, val width: Float, val height: Float) : DrawOp() {
        override fun moved(dx: Float, dy: Float) = copy(x = x + dx, top = top + dy)
    }
}

/** A place the cursor can sit: before node [index] of [row]. */
data class CursorStop(val row: MathRow, val index: Int, val x: Float, val baseline: Float, val size: Float) {
    fun moved(dx: Float, dy: Float) = copy(x = x + dx, baseline = baseline + dy)
    val top get() = baseline - size * 0.82f
    val bottom get() = baseline + size * 0.2f
}

class MathLayout(
    val width: Float,
    val height: Float,
    /** Distance from the top to the main baseline. */
    val baseline: Float,
    val ops: List<DrawOp>,
    val stops: List<CursorStop>,
) {
    fun stopFor(row: MathRow, index: Int): CursorStop? = stops.firstOrNull { it.row === row && it.index == index }

    /** Nearest cursor position to a tap at ([px], [py]). */
    fun hitTest(px: Float, py: Float): CursorStop? = stops.minByOrNull { s ->
        val dy = when {
            py < s.top -> s.top - py
            py > s.bottom -> py - s.bottom
            else -> 0f
        }
        kotlin.math.abs(px - s.x) + dy * 3f + s.size * 0.01f
    }
}
