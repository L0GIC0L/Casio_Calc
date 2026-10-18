package com.l0gic0l.casiocalc.math.graph

import com.l0gic0l.casiocalc.math.numeric.Numeric
import kotlin.math.abs
import kotlin.math.max

/** G-Solv: roots, extrema and intersections of graphs inside the visible x range. */
object GraphSolve {
    private const val SAMPLES = 800

    private fun sample(f: RealFn, a: Double, b: Double): Pair<DoubleArray, DoubleArray> {
        val xs = DoubleArray(SAMPLES + 1) { a + (b - a) * it / SAMPLES }
        val ys = DoubleArray(SAMPLES + 1) { f.eval(xs[it]) }
        return xs to ys
    }

    /** Every root of f in [a, b], ascending. */
    fun roots(f: RealFn, a: Double, b: Double): List<Double> {
        val (xs, ys) = sample(f, a, b)
        val span = ys.filter { it.isFinite() }.let { if (it.isEmpty()) 1.0 else max(1e-300, it.maxOf { v -> abs(v) }) }
        val out = ArrayList<Double>()
        for (i in 0 until SAMPLES) {
            val y0 = ys[i]; val y1 = ys[i + 1]
            if (!y0.isFinite() || !y1.isFinite()) continue
            if (y0 == 0.0) {
                out += xs[i]
                continue
            }
            if (y0 * y1 < 0) {
                val r = runCatching { Numeric.brent({ f.eval(it) }, xs[i], xs[i + 1], y0, y1) }.getOrNull() ?: continue
                // Reject sign changes across poles (tan, 1/x): the function must be small at the root.
                val fr = f.eval(r)
                if (fr.isFinite() && abs(fr) <= 1e-6 * span + 1e-9) out += r
            } else if (i > 0) {
                // Touching roots such as x²: a local minimum of |f| that reaches zero.
                val yp = ys[i - 1]
                if (yp.isFinite() && abs(y0) < abs(yp) && abs(y0) < abs(y1) && yp * y0 > 0 && y0 * y1 > 0) {
                    val sign = if (y0 > 0) 1.0 else -1.0
                    val m = Numeric.minimize({ sign * f.eval(it) }, xs[i - 1], xs[i + 1])
                    if (abs(f.eval(m)) < 1e-10 * max(1.0, span)) out += m
                }
            }
        }
        if (ys[SAMPLES] == 0.0) out += xs[SAMPLES]
        return dedupe(out, (b - a) * 1e-9)
    }

    private fun dedupe(v: List<Double>, tol: Double): List<Double> {
        val sorted = v.sorted()
        val out = ArrayList<Double>()
        for (x in sorted) if (out.isEmpty() || abs(x - out.last()) > tol) out += x
        return out
    }

    /** Local maxima (or minima) of f inside (a, b) as (x, y) pairs. */
    fun extrema(f: RealFn, a: Double, b: Double, maximum: Boolean): List<Pair<Double, Double>> {
        val sign = if (maximum) -1.0 else 1.0
        val (xs, ys) = sample(f, a, b)
        val out = ArrayList<Pair<Double, Double>>()
        for (i in 1 until SAMPLES) {
            val y0 = ys[i - 1]; val y1 = ys[i]; val y2 = ys[i + 1]
            if (!y0.isFinite() || !y1.isFinite() || !y2.isFinite()) continue
            val s0 = sign * y0; val s1 = sign * y1; val s2 = sign * y2
            if (s1 <= s0 && s1 <= s2 && (s1 < s0 || s1 < s2)) {
                val x = Numeric.minimize({ sign * f.eval(it) }, xs[i - 1], xs[i + 1])
                out += x to f.eval(x)
            }
        }
        val tol = (b - a) * 1e-7
        return out.sortedBy { it.first }.fold(ArrayList()) { acc, p ->
            if (acc.isEmpty() || abs(p.first - acc.last().first) > tol) acc += p
            acc
        }
    }

    fun intersections(f: RealFn, g: RealFn, a: Double, b: Double): List<Double> =
        roots({ x -> f.eval(x) - g.eval(x) }, a, b)

    /** Every x in [a, b] with f(x) = y. */
    fun xForY(f: RealFn, y: Double, a: Double, b: Double): List<Double> = roots({ x -> f.eval(x) - y }, a, b)
}
