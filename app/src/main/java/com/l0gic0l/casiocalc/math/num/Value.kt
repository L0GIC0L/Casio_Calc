package com.l0gic0l.casiocalc.math.num

import com.l0gic0l.casiocalc.math.dimError

/** Anything an expression can evaluate to: a number, a matrix or a list. */
sealed class Value {
    data class Num(val z: Complex) : Value()

    class Mat(val rows: Int, val cols: Int, val cells: List<Complex>) : Value() {
        init {
            require(cells.size == rows * cols)
        }

        operator fun get(r: Int, c: Int) = cells[r * cols + c]

        override fun equals(other: Any?) =
            other is Mat && rows == other.rows && cols == other.cols && cells == other.cells

        override fun hashCode() = (rows * 31 + cols) * 31 + cells.hashCode()

        companion object {
            fun build(rows: Int, cols: Int, f: (Int, Int) -> Complex) =
                Mat(rows, cols, List(rows * cols) { f(it / cols, it % cols) })
        }
    }

    data class Lst(val items: List<Complex>) : Value()

    companion object {
        fun of(r: Real): Value = Num(Complex(r))
        fun of(n: Long): Value = Num(Complex.of(n))
        fun of(z: Complex): Value = Num(z)
    }
}

fun Value.asNum(): Complex = (this as? Value.Num)?.z ?: dimError("number expected")
