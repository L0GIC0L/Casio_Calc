package com.l0gic0l.casiocalc.math.cas

import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.Calculator
import com.l0gic0l.casiocalc.math.ErrorKind
import com.l0gic0l.casiocalc.math.mathError
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.numeric.Numeric
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.parse.Parser

/** A worked derivative: the function, its derivative, the steps and, optionally, a value at a point. */
class DerivativeSolution(
    val function: Sym,
    val derivative: Sym,
    val order: Int,
    val steps: List<Step>,
    val at: Sym?,
    /** The derivative at [at], simplified but not evaluated, such as −π²; null where it is not defined. */
    val symbolicAt: Sym?,
    /** Its value; null where it is not defined or when letters other than X leave it symbolic. */
    val valueAt: Value?,
    /** Defined, but 10¹⁰⁰ or more, out of the calculator's range. */
    val tooLarge: Boolean = false,
)

/** A worked integral. [antiderivative] is null when no method applied; [definite] is set when limits were given. */
class IntegralSolution(
    val integrand: Sym,
    val antiderivative: Sym?,
    val steps: List<Step>,
    val verified: Boolean,
    val definite: Definite?,
)

/** A definite integral: F(b) − F(a) where it applies, otherwise a numerical value or the reason there is none. */
class Definite(
    val lower: Sym,
    val upper: Sym,
    /** F(b) − F(a), when an antiderivative was found. */
    val symbolic: Sym?,
    /** The exact value, for [Status.EXACT]. */
    val value: Value?,
    /** The numerical value, for [Status.NUMERIC]. */
    val numeric: Double?,
    val status: Status,
    /** For [Status.EXACT]: numerical integration gave the same value. */
    val checked: Boolean = false,
) {
    enum class Status {
        /** F(b) − F(a) applies: F is continuous on [a, b]. */
        EXACT,
        /** F(b) − F(a) does not apply (F jumps), or no antiderivative was found; numerical integration converged. */
        NUMERIC,
        /** Letters other than X, or X in a limit, leave only the symbolic F(b) − F(a). */
        PARAMETERS,
        /** F blows up inside or at the end of [a, b] and numerical integration does not converge. */
        DIVERGES,
        /** F(b) − F(a) is 10¹⁰⁰ or more, out of the calculator's range. */
        TOO_LARGE,
        /** No antiderivative and numerical integration did not converge. */
        UNKNOWN,
    }
}

/** Entry point for the Integral and Derivative modes. Calculus is always done in radians. */
object Calculus {
    const val X = "X"

    fun parse(row: MathRow): Sym = Parser.parse(row).toSym().also { s ->
        // Canonical forms keep 1/0 as 0⁻¹; it is an error here, as everywhere else in the calculator.
        if (s.subexpressions().any { it is Sym.Pow && it.base.isZero && (it.exp.rational?.signum ?: 0) < 0 }) mathError("division by zero")
    }

    fun derivative(function: MathRow, order: Int, at: MathRow?, memory: Memory, settings: Settings, cancelled: () -> Boolean = { false }): DerivativeSolution {
        val f = parse(function)
        val d = Differentiator(X)
        val steps = ArrayList<Step>()
        var current = f
        for (k in 1..order) {
            if (cancelled()) throw InterruptedException()
            val (value, step) = d.withSteps(current)
            steps += if (order == 1) step else Step(ordinal(k) + " derivative", null, step.math, listOf(step))
            current = value
        }
        val point = at?.takeIf { !it.isEmpty }?.let { parse(it) }
        var symbolicAt = point?.let { p -> tidy(current.subst(Sym.Var(X), p)) }
        var valueAt: Value? = null
        var tooLarge = false
        // Only a constant gets a value: letters other than X, or X in the point (f′ at X + 1), leave
        // a formula as in the steps, rather than taking stored values.
        if (point != null && symbolicAt != null && !symbolicAt.dependsOn(X) && symbolicAt.otherVariables(X).isEmpty()) {
            val value = exactResult(symbolicAt, memory, settings)
            val failure = value.exceptionOrNull()
            // Checking that the derivative exists there needs numbers throughout.
            val checkable = !point.dependsOn(X) && point.otherVariables(X).isEmpty() && f.otherVariables(X).isEmpty()
            when {
                // An overflow on the way, as in sinh(240)/cosh(240), or a value of 10¹⁰⁰ or more, as
                // for e^(X²) at 30: the double value tells which.
                failure.isOverflow() -> {
                    val approx = symbolicAt.eval(X, 0.0)
                    if (approx.isFinite() && Math.abs(approx) < 1e100) valueAt = Value.of(Real.Approx(approx)) else tooLarge = true
                }
                // tan(π/2): the double cos(π/2) is not quite 0, so only the exact value notices.
                failure != null -> symbolicAt = null
                checkable && !definedAt(f, current, point.eval(X, 0.0), symbolicAt.eval(X, 0.0)) -> symbolicAt = null
                else -> valueAt = value.getOrNull()
            }
        }
        return DerivativeSolution(f, current, order, steps, point, symbolicAt, valueAt, tooLarge)
    }

    /**
     * Whether the derivative [d] of [f] has the value [s] at [x]. Simplifying can hide a division by
     * zero (x/|x| at 0 folds 0·0⁻¹ to 0), so where d itself is not finite at x, the value counts only
     * if f is defined there and d tends to s from both sides: its distance from s shrinks with the
     * step, as for the rule-made 2x²/|x| of x·|x| at 0, instead of staying put, as at a kink.
     */
    private fun definedAt(f: Sym, d: Sym, x: Double, s: Double): Boolean {
        if (!s.isFinite()) return false
        if (d.eval(X, x).isFinite()) return true
        if (!f.eval(X, x).isFinite()) return false
        val scale = maxOf(1.0, Math.abs(x))
        fun gap(h: Double) = maxOf(Math.abs(d.eval(X, x - h * scale) - s), Math.abs(d.eval(X, x + h * scale) - s))
        val far = gap(1e-6)
        val near = gap(1e-9)
        return far.isFinite() && near.isFinite() && (near <= 1e-12 * maxOf(1.0, Math.abs(s)) || near <= 0.1 * far)
    }

    fun integral(integrand: MathRow, lower: MathRow?, upper: MathRow?, memory: Memory, settings: Settings, cancelled: () -> Boolean = { false }): IntegralSolution {
        val f = parse(integrand)
        val found = Integrator(X, Budget(cancelled = cancelled)).integrate(f)
        val anti = found?.value?.let(::tidy)
        val verified = anti != null && Integrator.verify(f, anti, X)
        val definite = if (lower != null && upper != null && !lower.isEmpty && !upper.isEmpty) {
            definite(f, anti, parse(lower), parse(upper), memory, settings)
        } else null
        return IntegralSolution(f, anti, listOfNotNull(found?.step), verified, definite)
    }

    private fun definite(f: Sym, anti: Sym?, a: Sym, b: Sym, memory: Memory, settings: Settings): Definite {
        val symbolic = anti?.let { tidy(sub(it.subst(Sym.Var(X), b), it.subst(Sym.Var(X), a))) }
        // Other letters, or X in a limit (∫ from 1 to X), leave the answer as a formula.
        val hasParams = f.otherVariables(X).isNotEmpty() || listOf(a, b).any { it.dependsOn(X) || it.otherVariables(X).isNotEmpty() }
        if (hasParams) return Definite(a, b, symbolic, null, null, Definite.Status.PARAMETERS)
        val lo = a.eval(X, 0.0)
        val hi = b.eval(X, 0.0)
        if (!lo.isFinite() || !hi.isFinite()) return Definite(a, b, symbolic, null, null, Definite.Status.UNKNOWN)
        val (numeric, converged) = Numeric.integrateEstimate({ t -> f.eval(X, t) }, lo, hi)
        val found = converged && numeric.isFinite()
        val result = symbolic?.let { exactResult(it, memory, settings) }
        val value = result?.getOrNull()
        val exactDouble = (value as? Value.Num)?.z?.takeIf { it.isReal }?.re?.toDouble()
        val agrees = found && exactDouble != null && Math.abs(numeric - exactDouble) <= 1e-6 * maxOf(1.0, Math.abs(numeric))
        val continuous by lazy { anti != null && continuous(anti, lo, hi) }
        return when {
            agrees -> Definite(a, b, symbolic, value, null, Definite.Status.EXACT, checked = true)
            // FTC: with F continuous on [a, b], F(b) − F(a) is right even where quadrature struggles.
            // F(b) − F(a) worked out in doubles must match the exact one: simplifying F(b) can
            // cancel a ln 0 against another, as for ln(4 − x²) at 2.
            exactDouble != null && continuous && matches(anti!!, lo, hi, exactDouble) ->
                Definite(a, b, symbolic, value, null, Definite.Status.EXACT)
            // Also where F(b) − F(a) only overflows on the way, as ln(cosh 240) does.
            found && Math.abs(numeric) < 1e100 -> Definite(a, b, symbolic, null, numeric, Definite.Status.NUMERIC)
            found || result?.exceptionOrNull().isOverflow() -> Definite(a, b, symbolic, null, null, Definite.Status.TOO_LARGE)
            anti != null && !continuous -> Definite(a, b, symbolic, null, null, Definite.Status.DIVERGES)
            else -> Definite(a, b, null, null, null, Definite.Status.UNKNOWN)
        }
    }

    private fun matches(F: Sym, lo: Double, hi: Double, exact: Double): Boolean {
        val upper = F.eval(X, hi)
        val lower = F.eval(X, lo)
        return Math.abs(upper - lower - exact) <= 1e-6 * maxOf(1.0, Math.abs(upper), Math.abs(lower))
    }

    /**
     * Whether [F] is continuous on [from, to]. F must be finite at the ends and at 4096 points
     * between them. Inside, F can only blow up or jump where a denominator, the argument of ln or
     * the cosine under tan is zero, so those zeros are found (sign changes, and minima of |g| that
     * reach 0) and F is looked at on each side of them. Unlike looking for big steps between
     * samples, this does not depend on where the samples fall, is not misled by a steep part
     * elsewhere, and leaves fast but smooth oscillation alone.
     */
    private fun continuous(F: Sym, from: Double, to: Double): Boolean {
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val n = 4096
        val xs = DoubleArray(n + 2) { k -> if (k == 0) lo else if (k == n + 1) hi else lo + (k - 0.5) * (hi - lo) / n }
        if (xs.any { !F.eval(X, it).isFinite() }) return false
        for (g in mustNotVanish(F)) {
            for (x0 in zeros(g, xs)) if (brokenAt(F, x0, lo, hi)) return false
        }
        return true
    }

    /** The quantities F divides by or takes ln of, as functions of X: F can only blow up or jump where one is 0. */
    private fun mustNotVanish(F: Sym): List<(Double) -> Double> = F.subexpressions().flatMap { s ->
        when {
            s is Sym.Pow && s.base.dependsOn(X) && !s.exp.dependsOn(X) && s.exp.eval(X, 0.0) < 0 -> listOf { t: Double -> s.base.eval(X, t) }
            s is Sym.Fn && (s.name == "ln" || s.name == "log") && s.arg.dependsOn(X) -> listOf { t: Double -> s.arg.eval(X, t) }
            s is Sym.Fn && s.name == "tan" && s.arg.dependsOn(X) -> listOf { t: Double -> Math.cos(s.arg.eval(X, t)) }
            s is Sym.Fn && s.name == "atanh" && s.arg.dependsOn(X) -> listOf({ t: Double -> 1 - s.arg.eval(X, t) }, { t: Double -> 1 + s.arg.eval(X, t) })
            else -> emptyList()
        }
    }

    /** Where [g] is 0 on the grid [xs]: each sign change, bisected, and each minimum of |g| that comes down to 0. */
    private fun zeros(g: (Double) -> Double, xs: DoubleArray): List<Double> {
        val v = DoubleArray(xs.size) { g(xs[it]) }
        val size = v.maxOf { if (it.isFinite()) Math.abs(it) else 0.0 }
        val out = ArrayList<Double>()
        for (k in xs.indices) {
            if (out.size >= 64) break
            if (v[k] == 0.0) out += xs[k]
            if (k + 1 < xs.size && v[k] * v[k + 1] < 0) {
                var a = xs[k]
                var b = xs[k + 1]
                val sa = Math.signum(v[k])
                repeat(80) {
                    val m = (a + b) / 2
                    if (m <= a || m >= b) return@repeat
                    if (Math.signum(g(m)) == sa) a = m else b = m
                }
                out += (a + b) / 2
            }
            // A zero that touches without crossing, as |x| or x² does.
            if (k in 1 until xs.size - 1 && Math.abs(v[k]) <= Math.abs(v[k - 1]) && Math.abs(v[k]) <= Math.abs(v[k + 1]) && Math.abs(v[k]) < 1e-2 * size) {
                var a = xs[k - 1]
                var b = xs[k + 1]
                repeat(100) {
                    val m1 = a + (b - a) * 0.382
                    val m2 = a + (b - a) * 0.618
                    if (Math.abs(g(m1)) < Math.abs(g(m2))) b = m2 else a = m1
                }
                val m = (a + b) / 2
                if (Math.abs(g(m)) <= 1e-9 * maxOf(1.0, size)) out += m
            }
        }
        return out
    }

    /**
     * Whether F blows up or jumps at [x0]. On each side inside [lo, hi], F is taken at 10⁻³, 10⁻⁶,
     * 10⁻⁹ and 10⁻¹² of the interval's width away: a continuous F settles, so the change from one
     * distance to the next shrinks, while across a pole or a log singularity it keeps going. The
     * two sides must also meet.
     */
    private fun brokenAt(F: Sym, x0: Double, lo: Double, hi: Double): Boolean {
        val width = hi - lo
        val finest = 1e3 * Math.ulp(maxOf(Math.abs(x0), java.lang.Double.MIN_NORMAL))
        val nearest = DoubleArray(2) { Double.NaN }
        for ((i, side) in listOf(-1.0, 1.0).withIndex()) {
            val values = listOf(1e-3, 1e-6, 1e-9, 1e-12).map { it * width }
                .filter { it >= finest && x0 + side * it in lo..hi }
                .map { F.eval(X, x0 + side * it) }
            if (values.any { !it.isFinite() }) return true
            for (j in 2 until values.size) {
                val before = Math.abs(values[j - 1] - values[j - 2])
                val after = Math.abs(values[j] - values[j - 1])
                if (after > 0.5 * before && after > 1e-9 * maxOf(1.0, Math.abs(values[j]))) return true
            }
            nearest[i] = values.lastOrNull() ?: Double.NaN
        }
        val (left, right) = nearest
        return left.isFinite() && right.isFinite() && Math.abs(left - right) > 1e-6 * maxOf(1.0, Math.abs(left), Math.abs(right))
    }

    /** Exact value of a constant expression with the normal evaluator, in radians. */
    private fun exactResult(s: Sym, memory: Memory, settings: Settings): Result<Value> = runCatching {
        val expr = s.toExpr()
        Calculator.evaluator(expr, memory, settings.copy(angle = AngleUnit.RAD)).eval(expr)
    }

    private fun Throwable?.isOverflow() = this is CalcException && kind == ErrorKind.MATH && detail == "overflow"

    private fun ordinal(k: Int) = when (k) {
        1 -> "First"
        2 -> "Second"
        3 -> "Third"
        else -> "${k}th"
    }
}
