package com.l0gic0l.casiocalc.math.eval

import com.l0gic0l.casiocalc.math.dimError
import com.l0gic0l.casiocalc.math.mathError
import com.l0gic0l.casiocalc.math.num.C
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.Value

/** Matrix arithmetic that stays exact for exact entries (inverse of an integer matrix gives fractions). */
object MatrixOps {
    fun add(a: Value.Mat, b: Value.Mat, subtract: Boolean = false): Value.Mat {
        if (a.rows != b.rows || a.cols != b.cols) dimError("matrix size")
        return Value.Mat(a.rows, a.cols, a.cells.indices.map {
            if (subtract) C.sub(a.cells[it], b.cells[it]) else C.add(a.cells[it], b.cells[it])
        })
    }

    fun mul(a: Value.Mat, b: Value.Mat): Value.Mat {
        if (a.cols != b.rows) dimError("matrix size")
        return Value.Mat.build(a.rows, b.cols) { r, c ->
            var s = Complex.ZERO
            for (k in 0 until a.cols) s = C.add(s, C.mul(a[r, k], b[k, c]))
            s
        }
    }

    fun scale(a: Value.Mat, k: Complex) = Value.Mat(a.rows, a.cols, a.cells.map { C.mul(it, k) })

    fun map(a: Value.Mat, f: (Complex) -> Complex) = Value.Mat(a.rows, a.cols, a.cells.map(f))

    fun transpose(a: Value.Mat) = Value.Mat.build(a.cols, a.rows) { r, c -> a[c, r] }

    fun identity(n: Int): Value.Mat {
        if (n < 1 || n > 99) dimError("identity size")
        return Value.Mat.build(n, n) { r, c -> if (r == c) Complex.ONE else Complex.ZERO }
    }

    private fun magnitude(z: Complex) = kotlin.math.hypot(z.re.toDouble(), z.im.toDouble())
    private fun isZero(z: Complex) = z.re.isZero && z.im.isZero

    /** Row reduction on a mutable grid; returns the determinant sign/scale bookkeeping. */
    private class Grid(m: Value.Mat) {
        val rows = m.rows
        val cols = m.cols
        val a: Array<Array<Complex>> = Array(rows) { r -> Array(cols) { c -> m[r, c] } }
        fun toMat() = Value.Mat.build(rows, cols) { r, c -> a[r][c] }
    }

    /** Index of the best pivot row at or below [start] in column [col], or -1. */
    private fun pivotRow(g: Grid, start: Int, col: Int): Int {
        var best = -1
        var bestMag = 0.0
        for (r in start until g.rows) {
            val z = g.a[r][col]
            if (isZero(z)) continue
            val m = magnitude(z)
            // Prefer exact pivots (keeps fractions small); otherwise partial pivoting.
            if (best == -1 || m > bestMag) {
                best = r; bestMag = m
            }
        }
        if (best >= 0 && bestMag < 1e-14 && !(g.a[best][col].isExact)) return -1
        return best
    }

    fun det(m: Value.Mat): Complex {
        if (m.rows != m.cols) dimError("square matrix required")
        val g = Grid(m)
        var det = Complex.ONE
        for (c in 0 until g.cols) {
            val p = pivotRow(g, c, c)
            if (p < 0) return Complex.ZERO
            if (p != c) {
                val t = g.a[p]; g.a[p] = g.a[c]; g.a[c] = t
                det = C.neg(det)
            }
            val pivot = g.a[c][c]
            det = C.mul(det, pivot)
            for (r in c + 1 until g.rows) {
                if (isZero(g.a[r][c])) continue
                val f = C.div(g.a[r][c], pivot)
                for (k in c until g.cols) g.a[r][k] = C.sub(g.a[r][k], C.mul(f, g.a[c][k]))
            }
        }
        return det
    }

    fun inverse(m: Value.Mat): Value.Mat {
        if (m.rows != m.cols) dimError("square matrix required")
        val n = m.rows
        val aug = Value.Mat.build(n, 2 * n) { r, c ->
            if (c < n) m[r, c] else if (c - n == r) Complex.ONE else Complex.ZERO
        }
        val reduced = rref(aug)
        for (i in 0 until n) {
            if (reduced[i, i] != Complex.ONE && !(isOne(reduced[i, i]))) mathError("singular matrix")
        }
        return Value.Mat.build(n, n) { r, c -> reduced[r, c + n] }
    }

    private fun isOne(z: Complex) = z.im.isZero && kotlin.math.abs(z.re.toDouble() - 1) < 1e-12 && !isZero(z)

    /** Row echelon form with leading ones (the CG50's Ref). */
    fun ref(m: Value.Mat): Value.Mat = reduce(m, full = false)

    /** Reduced row echelon form. */
    fun rref(m: Value.Mat): Value.Mat = reduce(m, full = true)

    private fun reduce(m: Value.Mat, full: Boolean): Value.Mat {
        val g = Grid(m)
        var lead = 0
        for (c in 0 until g.cols) {
            if (lead >= g.rows) break
            val p = pivotRow(g, lead, c)
            if (p < 0) continue
            if (p != lead) {
                val t = g.a[p]; g.a[p] = g.a[lead]; g.a[lead] = t
            }
            val pivot = g.a[lead][c]
            for (k in 0 until g.cols) g.a[lead][k] = C.div(g.a[lead][k], pivot)
            val start = if (full) 0 else lead + 1
            for (r in start until g.rows) {
                if (r == lead || isZero(g.a[r][c])) continue
                val f = g.a[r][c]
                for (k in 0 until g.cols) g.a[r][k] = C.sub(g.a[r][k], C.mul(f, g.a[lead][k]))
            }
            lead++
        }
        // Clean round-off for approximate entries.
        for (r in 0 until g.rows) for (c in 0 until g.cols) {
            val z = g.a[r][c]
            if (!z.isExact && magnitude(z) < 1e-13) g.a[r][c] = Complex.ZERO
        }
        return g.toMat()
    }

    fun pow(m: Value.Mat, n: Long): Value.Mat {
        if (m.rows != m.cols) dimError("square matrix required")
        if (n < 0) return pow(inverse(m), -n)
        var result = identity(m.rows)
        var base = m
        var e = n
        while (e > 0) {
            if (e and 1L == 1L) result = mul(result, base)
            e = e shr 1
            if (e > 0) base = mul(base, base)
        }
        return result
    }

    fun augment(a: Value.Mat, b: Value.Mat): Value.Mat {
        if (a.rows != b.rows) dimError("row count")
        return Value.Mat.build(a.rows, a.cols + b.cols) { r, c -> if (c < a.cols) a[r, c] else b[r, c - a.cols] }
    }
}
