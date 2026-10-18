package com.l0gic0l.casiocalc.math.numeric

import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.ErrorKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** Numerical calculus and root finding on real functions. */
object Numeric {

    // Gauss–Kronrod 7/15 nodes and weights.
    private val xgk = doubleArrayOf(
        0.991455371120812639206854697526329, 0.949107912342758524526189684047851,
        0.864864423359769072789712788640926, 0.741531185599394439863864773280788,
        0.586087235467691130294144845693013, 0.405845151377397166906606412076961,
        0.207784955007898467600689403773245, 0.000000000000000000000000000000000,
    )
    private val wgk = doubleArrayOf(
        0.022935322010529224963732008058970, 0.063092092629978553290700663189204,
        0.104790010322250183839876322541518, 0.140653259715525918745189590510238,
        0.169004726639267902826583426598550, 0.190350578064785409913256402421014,
        0.204432940075298892414161999234649, 0.209482141084727828012999174891714,
    )
    private val wg = doubleArrayOf(
        0.129484966168869693270611432679082, 0.279705391489276667901467771423780,
        0.381830050505118944950369775488975, 0.417959183673469387755102040816327,
    )

    private class Segment(val a: Double, val b: Double, val value: Double, val error: Double)

    private fun gk15(f: (Double) -> Double, a: Double, b: Double): Segment {
        val c = (a + b) / 2
        val h = (b - a) / 2
        val fc = f(c)
        var kronrod = fc * wgk[7]
        var gauss = fc * wg[3]
        for (j in 0 until 7) {
            val dx = h * xgk[j]
            val f1 = f(c - dx)
            val f2 = f(c + dx)
            kronrod += wgk[j] * (f1 + f2)
            if (j % 2 == 1) gauss += wg[j / 2] * (f1 + f2)
        }
        return Segment(a, b, kronrod * h, abs((kronrod - gauss) * h))
    }

    /** Adaptive Gauss–Kronrod integration of f over [a, b]. */
    fun integrate(f: (Double) -> Double, a: Double, b: Double, tol: Double = 1e-12, check: () -> Unit = {}): Double {
        if (a == b) return 0.0
        if (a > b) return -integrate(f, b, a, tol, check)
        val segments = java.util.PriorityQueue<Segment>(compareByDescending { it.error })
        val first = gk15(f, a, b)
        segments += first
        var total = first.value
        var error = first.error
        var iterations = 0
        while (error > max(tol, tol * abs(total)) && iterations < 2000) {
            check()
            val worst = segments.poll() ?: break
            val mid = (worst.a + worst.b) / 2
            val left = gk15(f, worst.a, mid)
            val right = gk15(f, mid, worst.b)
            total += left.value + right.value - worst.value
            error += left.error + right.error - worst.error
            segments += left
            segments += right
            iterations++
        }
        if (total.isNaN() || total.isInfinite()) throw CalcException(ErrorKind.MATH, "integral")
        return segments.sumOf { it.value }
    }

    /** First derivative by Ridders' extrapolation of central differences. */
    fun derivative(f: (Double) -> Double, x: Double): Double {
        val ntab = 10
        val con = 1.4
        val con2 = con * con
        var h = max(abs(x), 1.0) * 0.1
        val a = Array(ntab) { DoubleArray(ntab) }
        a[0][0] = (f(x + h) - f(x - h)) / (2 * h)
        var best = a[0][0]
        var err = Double.MAX_VALUE
        for (i in 1 until ntab) {
            h /= con
            a[0][i] = (f(x + h) - f(x - h)) / (2 * h)
            var fac = con2
            for (j in 1..i) {
                a[j][i] = (a[j - 1][i] * fac - a[j - 1][i - 1]) / (fac - 1)
                fac *= con2
                val errt = max(abs(a[j][i] - a[j - 1][i]), abs(a[j][i] - a[j - 1][i - 1]))
                if (errt <= err) {
                    err = errt
                    best = a[j][i]
                }
            }
            if (abs(a[i][i] - a[i - 1][i - 1]) >= 2 * err) break
        }
        if (best.isNaN() || best.isInfinite()) throw CalcException(ErrorKind.MATH, "derivative")
        return best
    }

    fun secondDerivative(f: (Double) -> Double, x: Double): Double = derivative({ t -> derivative(f, t) }, x)

    /** Brent's method on a bracketing interval with f(a)·f(b) ≤ 0. */
    fun brent(f: (Double) -> Double, a0: Double, b0: Double, fa0: Double = f(a0), fb0: Double = f(b0)): Double {
        var a = a0; var b = b0; var fa = fa0; var fb = fb0
        if (fa == 0.0) return a
        if (fb == 0.0) return b
        if (fa * fb > 0) throw CalcException(ErrorKind.CANT_SOLVE)
        var c = a; var fc = fa
        var d = b - a; var e = d
        for (iter in 0 until 200) {
            if (fb * fc > 0) {
                c = a; fc = fa; d = b - a; e = d
            }
            if (abs(fc) < abs(fb)) {
                a = b; b = c; c = a
                fa = fb; fb = fc; fc = fa
            }
            val tol = 2 * Math.ulp(b) + 1e-15 * abs(b)
            val m = (c - b) / 2
            if (abs(m) <= tol || fb == 0.0) return b
            if (abs(e) >= tol && abs(fa) > abs(fb)) {
                val s = fb / fa
                var p: Double
                var q: Double
                if (a == c) {
                    p = 2 * m * s
                    q = 1 - s
                } else {
                    val qq = fa / fc
                    val r = fb / fc
                    p = s * (2 * m * qq * (qq - r) - (b - a) * (r - 1))
                    q = (qq - 1) * (r - 1) * (s - 1)
                }
                if (p > 0) q = -q else p = -p
                if (2 * p < minOf(3 * m * q - abs(tol * q), abs(e * q))) {
                    e = d; d = p / q
                } else {
                    d = m; e = m
                }
            } else {
                d = m; e = m
            }
            a = b; fa = fb
            b += if (abs(d) > tol) d else if (m > 0) tol else -tol
            fb = f(b)
        }
        return b
    }

    /**
     * Finds a root of f near [guess]: Newton/secant steps first, then an outward scan for a sign
     * change refined with Brent's method, optionally limited to [lo, hi].
     */
    fun solve(
        f: (Double) -> Double,
        guess: Double,
        lo: Double = Double.NEGATIVE_INFINITY,
        hi: Double = Double.POSITIVE_INFINITY,
        check: () -> Unit = {},
    ): Double {
        fun safe(x: Double) = try {
            f(x)
        } catch (e: CalcException) {
            if (e.kind == ErrorKind.CANCELLED) throw e
            Double.NaN
        }
        // Newton with numeric derivative.
        var x = guess.coerceIn(lo, hi)
        for (i in 0 until 60) {
            check()
            val fx = safe(x)
            if (fx.isNaN()) break
            if (fx == 0.0) return x
            val h = max(abs(x), 1.0) * 1e-7
            val df = (safe(x + h) - safe(x - h)) / (2 * h)
            if (df == 0.0 || df.isNaN()) break
            val nx = (x - fx / df).coerceIn(lo, hi)
            if (abs(nx - x) <= 1e-14 * max(1.0, abs(x))) {
                val fn = safe(nx)
                if (!fn.isNaN() && abs(fn) <= 1e-9 * max(1.0, abs(fx))) return nx
                break
            }
            x = nx
        }
        // Scan outward for a sign change.
        var step = max(abs(guess), 1.0) * 0.01
        var left = guess.coerceIn(lo, hi)
        var right = left
        var fl = safe(left)
        var fr = fl
        if (fl == 0.0) return left
        for (i in 0 until 400) {
            check()
            val nl = (left - step).coerceIn(lo, hi)
            val nr = (right + step).coerceIn(lo, hi)
            val fnl = safe(nl)
            val fnr = safe(nr)
            if (!fnr.isNaN() && !fr.isNaN() && fnr * fr <= 0) return brent(::safe, right, nr, fr, fnr)
            if (!fnl.isNaN() && !fl.isNaN() && fnl * fl <= 0) return brent(::safe, nl, left, fnl, fl)
            left = nl; right = nr; fl = fnl; fr = fnr
            step *= 1.25
            if (left == lo && right == hi) break
        }
        throw CalcException(ErrorKind.CANT_SOLVE)
    }

    /** Golden-section search for the minimum of f on [a, b]; returns x. */
    fun minimize(f: (Double) -> Double, a0: Double, b0: Double): Double {
        // Sample first so we start from the best basin.
        val n = 64
        var bestX = a0
        var bestF = Double.POSITIVE_INFINITY
        for (i in 0..n) {
            val x = a0 + (b0 - a0) * i / n
            val v = f(x)
            if (!v.isNaN() && v < bestF) {
                bestF = v; bestX = x
            }
        }
        val step = (b0 - a0) / n
        var a = max(a0, bestX - step)
        var b = minOf(b0, bestX + step)
        val gr = (sqrt(5.0) - 1) / 2
        var c = b - gr * (b - a)
        var d = a + gr * (b - a)
        var fc = f(c)
        var fd = f(d)
        repeat(200) {
            if (abs(b - a) <= 1e-13 * max(1.0, abs(c))) return (a + b) / 2
            if (fc < fd) {
                b = d; d = c; fd = fc
                c = b - gr * (b - a); fc = f(c)
            } else {
                a = c; c = d; fc = fd
                d = a + gr * (b - a); fd = f(d)
            }
        }
        return (a + b) / 2
    }
}
