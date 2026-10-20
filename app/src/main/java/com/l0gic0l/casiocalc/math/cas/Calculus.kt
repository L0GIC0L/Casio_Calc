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
        // Letters other than X, and X itself in the point (f′ at X + 1), stay symbolic as in the
        // steps rather than taking stored values.
        val numeric = point != null && !point.dependsOn(X) && point.otherVariables(X).isEmpty() && current.otherVariables(X).isEmpty()
        if (numeric && symbolicAt != null) {
            val value = exactResult(symbolicAt, memory, settings)
            val failure = value.exceptionOrNull()
            val defined = when {
                // Too large to show, as e^(X²) at 30, but defined.
                failure.isOverflow() -> true
                // tan(π/2): the double cos(π/2) is not quite 0, so only the exact value notices.
                failure != null -> false
                else -> definedAt(current, point.eval(X, 0.0), symbolicAt.eval(X, 0.0))
            }
            if (defined) valueAt = value.getOrNull() else symbolicAt = null
        }
        return DerivativeSolution(f, current, order, steps, point, symbolicAt, valueAt)
    }

    /**
     * Whether the derivative [d] has the value [s] at [x]. Simplifying can hide a division by zero
     * (x/|x| at 0 folds 0·0⁻¹ to 0), so where d itself is not finite at x, the value counts only if
     * d tends to it from both sides, as the rule-made 2x²/|x| does for x·|x| at 0.
     */
    private fun definedAt(d: Sym, x: Double, s: Double): Boolean {
        if (!s.isFinite()) return false
        if (d.eval(X, x).isFinite()) return true
        val h = 1e-7 * maxOf(1.0, Math.abs(x))
        val tolerance = 1e-4 * maxOf(1.0, Math.abs(s))
        return listOf(d.eval(X, x - h), d.eval(X, x + h)).all { it.isFinite() && Math.abs(it - s) <= tolerance }
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
            result?.exceptionOrNull().isOverflow() -> Definite(a, b, symbolic, null, null, Definite.Status.TOO_LARGE)
            // FTC: with F continuous on [a, b], F(b) − F(a) is right even where quadrature struggles.
            // F(b) − F(a) worked out in doubles must match the exact one: simplifying F(b) can
            // cancel a ln 0 against another, as for ln(4 − x²) at 2.
            exactDouble != null && continuous && matches(anti!!, lo, hi, exactDouble) ->
                Definite(a, b, symbolic, value, null, Definite.Status.EXACT)
            found -> Definite(a, b, symbolic, null, numeric, Definite.Status.NUMERIC)
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
     * Whether [F] is continuous on [lo, hi], judged from samples. F must be finite at the ends and
     * at 4096 points between them. Then the biggest steps between neighbouring samples, and the
     * samples largest in size, are looked at more closely: a window round each is cut down, again
     * and again, to the three twelfths with the biggest step. For a continuous F the step shrinks
     * with the window. Across a pole, a logarithmic singularity or a jump it does not, however the
     * samples happen to fall, and F that oscillates faster than the grid is followed down until it
     * is smooth.
     */
    private fun continuous(F: Sym, from: Double, to: Double): Boolean {
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val n = 4096
        val xs = DoubleArray(n + 2) { k -> if (k == 0) lo else if (k == n + 1) hi else lo + (k - 0.5) * (hi - lo) / n }
        val values = DoubleArray(n + 2) { F.eval(X, xs[it]) }
        if (values.any { !it.isFinite() }) return false
        val size = values.maxOf { Math.abs(it) }
        val steps = (0 until n + 1).sortedByDescending { Math.abs(values[it + 1] - values[it]) }.take(16)
        val largest = values.indices.sortedByDescending { Math.abs(values[it]) }.take(4).map { minOf(it, n) }
        val tiny = 1e-13 * maxOf(1.0, Math.abs(lo), Math.abs(hi))
        for (spot in (steps + largest).distinct()) {
            var a = xs[maxOf(spot - 1, 0)]
            var b = xs[minOf(spot + 2, n + 1)]
            var first = -1.0
            var last = 0.0
            while (b - a > tiny) {
                val px = DoubleArray(12) { a + (b - a) * it / 11 }
                val pv = DoubleArray(12) { F.eval(X, px[it]) }
                if (pv.any { !it.isFinite() }) return false
                val j = (0 until 11).maxByOrNull { Math.abs(pv[it + 1] - pv[it]) }!!
                last = Math.abs(pv[j + 1] - pv[j])
                if (first < 0) first = last
                a = px[maxOf(j - 1, 0)]
                b = px[minOf(j + 2, 11)]
            }
            if (last > 0.01 * first && last > 1e-12 * maxOf(1.0, size)) return false
        }
        return true
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
