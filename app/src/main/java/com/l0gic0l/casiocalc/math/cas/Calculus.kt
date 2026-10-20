package com.l0gic0l.casiocalc.math.cas

import com.l0gic0l.casiocalc.math.Calculator
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
        /** Letters other than X leave only the symbolic F(b) − F(a). */
        PARAMETERS,
        /** F blows up inside or at the end of [a, b] and numerical integration does not converge. */
        DIVERGES,
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
        val symbolicAt = point?.let { p -> tidy(current.subst(Sym.Var(X), p)) }?.takeIf { s ->
            // Simplifying can hide a division by zero (x/|x| at 0 folds 0·0⁻¹ to 0), so check the value too.
            val free = point.otherVariables(X).isEmpty() && current.otherVariables(X).isEmpty()
            !free || current.eval(X, point.eval(X, 0.0)).isFinite() && s.eval(X, 0.0).isFinite()
        }
        // Letters other than X stay symbolic, as in the steps, rather than taking stored values.
        val valueAt = symbolicAt?.takeIf { it.otherVariables(X).isEmpty() }?.let { exact(it, memory, settings) }
        return DerivativeSolution(f, current, order, steps, point, symbolicAt, valueAt)
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
        val hasParams = f.otherVariables(X).isNotEmpty() || a.otherVariables(X).isNotEmpty() || b.otherVariables(X).isNotEmpty()
        if (hasParams) return Definite(a, b, symbolic, null, null, Definite.Status.PARAMETERS)
        val lo = a.eval(X, 0.0)
        val hi = b.eval(X, 0.0)
        if (!lo.isFinite() || !hi.isFinite()) return Definite(a, b, symbolic, null, null, Definite.Status.UNKNOWN)
        val (numeric, converged) = Numeric.integrateEstimate({ t -> f.eval(X, t) }, lo, hi)
        val found = converged && numeric.isFinite()
        val value = symbolic?.let { exact(it, memory, settings) }
        val exactDouble = (value as? Value.Num)?.z?.takeIf { it.isReal }?.re?.toDouble()
        val agrees = found && exactDouble != null && Math.abs(numeric - exactDouble) <= 1e-6 * maxOf(1.0, Math.abs(numeric))
        return when {
            agrees -> Definite(a, b, symbolic, value, null, Definite.Status.EXACT, checked = true)
            // FTC: with F continuous on [a, b], F(b) − F(a) is right even where quadrature struggles.
            anti != null && exactDouble != null && continuous(anti, lo, hi) -> Definite(a, b, symbolic, value, null, Definite.Status.EXACT)
            found -> Definite(a, b, symbolic, null, numeric, Definite.Status.NUMERIC)
            anti != null -> Definite(a, b, symbolic, null, null, Definite.Status.DIVERGES)
            else -> Definite(a, b, null, null, null, Definite.Status.UNKNOWN)
        }
    }

    /**
     * Whether [F] is continuous on the open interval (lo, hi), judged from samples: between
     * neighbouring points a continuous F changes less and less as they get closer, while across a
     * pole, a logarithmic singularity or a jump it does not. Two pairs of grids allow for F that
     * oscillates quickly.
     */
    private fun continuous(F: Sym, lo: Double, hi: Double): Boolean {
        fun largestStep(n: Int): Double? {
            var previous = 0.0
            var largest = 0.0
            for (k in 0 until n) {
                val v = F.eval(X, lo + (k + 0.5) * (hi - lo) / n)
                if (!v.isFinite()) return null
                if (k > 0) largest = maxOf(largest, Math.abs(v - previous))
                previous = v
            }
            return largest
        }
        for ((coarse, fine) in listOf(2048 to 16384, 16384 to 131072)) {
            val c = largestStep(coarse) ?: return false
            val d = largestStep(fine) ?: return false
            if (c < 1e-12 || d < c * 0.6) return true
        }
        return false
    }

    /** Exact value of a constant expression with the normal evaluator, in radians. */
    private fun exact(s: Sym, memory: Memory, settings: Settings): Value? = runCatching {
        val expr = s.toExpr()
        Calculator.evaluator(expr, memory, settings.copy(angle = AngleUnit.RAD)).eval(expr)
    }.getOrNull()

    private fun ordinal(k: Int) = when (k) {
        1 -> "First"
        2 -> "Second"
        3 -> "Third"
        else -> "${k}th"
    }
}
