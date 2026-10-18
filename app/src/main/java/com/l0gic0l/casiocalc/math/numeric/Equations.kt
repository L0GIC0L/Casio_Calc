package com.l0gic0l.casiocalc.math.numeric

import com.l0gic0l.casiocalc.math.argError
import com.l0gic0l.casiocalc.math.eval.MatrixOps
import com.l0gic0l.casiocalc.math.num.BigRational
import com.l0gic0l.casiocalc.math.num.C
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.R
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Solvers behind the Equation mode. */
object Equations {

    sealed class LinearResult {
        data class Unique(val values: List<Complex>) : LinearResult()
        data object Infinite : LinearResult()
        data object NoSolution : LinearResult()
    }

    /** Solves the n×(n+1) augmented system exactly when the coefficients are exact. */
    fun solveLinear(augmented: Value.Mat): LinearResult {
        val n = augmented.rows
        if (augmented.cols != n + 1) argError("augmented matrix")
        val reduced = MatrixOps.rref(augmented)
        fun zero(z: Complex) = z.re.isZero && z.im.isZero
        for (r in 0 until n) {
            val coeffZero = (0 until n).all { zero(reduced[r, it]) }
            if (coeffZero) {
                return if (zero(reduced[r, n])) LinearResult.Infinite else LinearResult.NoSolution
            }
        }
        return LinearResult.Unique((0 until n).map { reduced[it, n] })
    }

    // ---- polynomials -----------------------------------------------------------------------

    /** Lightweight complex double for root finding. */
    private data class Z(val re: Double, val im: Double) {
        operator fun plus(o: Z) = Z(re + o.re, im + o.im)
        operator fun minus(o: Z) = Z(re - o.re, im - o.im)
        operator fun times(o: Z) = Z(re * o.re - im * o.im, re * o.im + im * o.re)
        operator fun div(o: Z): Z {
            val d = o.re * o.re + o.im * o.im
            return Z((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
        }
        val abs get() = hypot(re, im)
    }

    private fun horner(coeffs: List<Z>, x: Z): Z {
        var acc = Z(0.0, 0.0)
        for (c in coeffs) acc = acc * x + c
        return acc
    }

    /** All complex roots of a polynomial with highest-degree-first double coefficients (Durand–Kerner). */
    private fun numericRoots(coeffs: List<Double>): List<Z> {
        val n = coeffs.size - 1
        if (n < 1) return emptyList()
        val lead = coeffs[0]
        val monic = coeffs.map { Z(it / lead, 0.0) }
        // Cauchy bound for the starting circle.
        val radius = 1 + coeffs.drop(1).maxOf { abs(it / lead) }
        var roots = List(n) { k ->
            val angle = 2 * Math.PI * k / n + 0.4
            Z(radius * cos(angle), radius * sin(angle))
        }
        repeat(1000) {
            var maxStep = 0.0
            val next = roots.mapIndexed { i, zi ->
                var den = Z(1.0, 0.0)
                roots.forEachIndexed { j, zj -> if (i != j) den *= (zi - zj) }
                val step = horner(monic, zi) / den
                maxStep = max(maxStep, step.abs)
                zi - step
            }
            roots = next
            if (maxStep < 1e-15 * radius) return polish(roots, coeffs)
        }
        return polish(roots, coeffs)
    }

    private fun polish(roots: List<Z>, coeffs: List<Double>): List<Z> {
        val p = coeffs.map { Z(it, 0.0) }
        val dp = coeffs.dropLast(1).mapIndexed { i, c -> Z(c * (coeffs.size - 1 - i), 0.0) }
        return roots.map { r0 ->
            var r = r0
            repeat(4) {
                val d = horner(dp, r)
                if (d.abs == 0.0) return@repeat
                val step = horner(p, r) / d
                if (step.abs.isNaN()) return@repeat
                r -= step
            }
            // Snap tiny imaginary parts from round-off.
            if (abs(r.im) < 1e-12 * max(1.0, r.abs)) Z(r.re, 0.0) else r
        }
    }

    private fun evalExact(coeffs: List<BigRational>, x: BigRational): BigRational {
        var acc = BigRational.ZERO
        for (c in coeffs) acc = acc * x + c
        return acc
    }

    /** Synthetic division by (x − root); returns the quotient. */
    private fun deflate(coeffs: List<BigRational>, root: BigRational): List<BigRational> {
        val out = ArrayList<BigRational>()
        var acc = BigRational.ZERO
        for (i in 0 until coeffs.size - 1) {
            acc = acc * root + coeffs[i]
            out += acc
        }
        return out
    }

    /** Divides by x² − s·x + p when that leaves no remainder. */
    private fun divideQuadratic(coeffs: List<BigRational>, s: BigRational, p: BigRational): List<BigRational>? {
        val rem = coeffs.toMutableList()
        val out = ArrayList<BigRational>()
        for (i in 0..coeffs.size - 3) {
            val q = rem[i]
            out += q
            rem[i + 1] = rem[i + 1] + q * s
            rem[i + 2] = rem[i + 2] - q * p
        }
        val r1 = rem[rem.size - 2]
        val r0 = rem[rem.size - 1]
        return if (r1.isZero && r0.isZero) out else null
    }

    /** Exact roots of a·x² + b·x + c. */
    fun quadraticExact(a: BigRational, b: BigRational, c: BigRational): List<Complex> {
        val disc = b * b - BigRational.of(4) * a * c
        val twoA = BigRational.TWO * a
        val base = Real.of(-b / twoA)
        return if (disc.signum >= 0) {
            val s = R.div(R.sqrt(Real.of(disc)), Real.of(twoA))
            listOf(Complex(R.sub(base, s)), Complex(R.add(base, s))).sortedWith { x, y -> R.cmp(x.re, y.re) }
        } else {
            val s = R.div(R.sqrt(Real.of(-disc)), Real.of(twoA.abs()))
            listOf(Complex(base, s), Complex(base, R.neg(s)))
        }
    }

    /**
     * Roots of a polynomial given highest degree first. Exact rational coefficients give exact roots
     * wherever they exist (rational roots and quadratic factors); the rest are numeric.
     */
    fun polynomialRoots(coefficients: List<Real>): List<Complex> {
        var coeffs = coefficients.dropWhile { it.isZero }
        if (coeffs.size < 2) argError("polynomial degree")
        val exact = coeffs.all { it is Real.Exact && it.isRational }
        if (!exact) {
            return sortRoots(numericRoots(coeffs.map { it.toDouble() }).map { Complex.approx(it.re, it.im) })
        }
        var q = coeffs.map { (it as Real.Exact).a }
        val roots = ArrayList<Complex>()
        // Zero roots.
        while (q.size > 1 && q.last().isZero) {
            roots += Complex.ZERO
            q = q.dropLast(1)
        }
        // Rational roots found numerically and confirmed exactly.
        var changed = true
        while (q.size > 3 && changed) {
            changed = false
            val numeric = numericRoots(q.map { it.toDouble() })
            for (z in numeric.sortedBy { abs(it.im) }) {
                if (abs(z.im) > 1e-7 * max(1.0, z.abs)) continue
                val cand = BigRational.approximate(z.re, 100_000, 1e-9) ?: continue
                if (evalExact(q, cand).isZero) {
                    roots += Complex(Real.of(cand))
                    q = deflate(q, cand)
                    changed = true
                    break
                }
            }
        }
        if (q.size == 4 || q.size == 5) {
            // Try a quadratic factor with rational coefficients from pairs of numeric roots.
            val numeric = numericRoots(q.map { it.toDouble() })
            search@ for (i in numeric.indices) for (j in i + 1 until numeric.size) {
                val s = numeric[i] + numeric[j]
                val p = numeric[i] * numeric[j]
                if (abs(s.im) > 1e-7 || abs(p.im) > 1e-7) continue
                val sq = BigRational.approximate(s.re, 10_000, 1e-9) ?: continue
                val pq = BigRational.approximate(p.re, 10_000, 1e-9) ?: continue
                val rest = divideQuadratic(q, sq, pq) ?: continue
                roots += quadraticExact(BigRational.ONE, -sq, pq)
                q = rest
                break@search
            }
        }
        when (q.size) {
            1 -> {}
            2 -> roots += Complex(Real.of(-q[1] / q[0]))
            3 -> roots += quadraticExact(q[0], q[1], q[2])
            else -> roots += numericRoots(q.map { it.toDouble() }).map { Complex.approx(it.re, it.im) }
        }
        return sortRoots(roots)
    }

    /** Real roots ascending, then complex roots by real part with the positive imaginary part first. */
    private fun sortRoots(roots: List<Complex>): List<Complex> {
        val (real, complex) = roots.partition { it.isReal }
        return real.sortedBy { it.re.toDouble() } +
            complex.sortedWith(compareBy<Complex>({ it.re.toDouble() }, { -it.im.toDouble() }))
    }

    /** Evaluates the polynomial at a complex point; used to check results. */
    fun evaluate(coefficients: List<Real>, x: Complex): Complex {
        var acc = Complex.ZERO
        for (c in coefficients) acc = C.add(C.mul(acc, x), Complex(c))
        return acc
    }
}
