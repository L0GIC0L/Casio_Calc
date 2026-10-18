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
    /** The derivative at [at], simplified but not evaluated, such as −π². */
    val symbolicAt: Sym?,
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

/** F(b) − F(a) symbolically, its exact value when there are no parameters, and a numeric cross-check. */
class Definite(
    val lower: Sym,
    val upper: Sym,
    val symbolic: Sym?,
    val value: Value?,
    val numeric: Double?,
    /** The antiderivative does not apply across the interval (a singularity in between), so only [numeric] is right. */
    val numericOnly: Boolean,
)

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
        val symbolicAt = point?.let { p -> tidy(current.subst(Sym.Var(X), p)) }
        val valueAt = symbolicAt?.let { exact(it, memory, settings) }
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
        val hasParams = f.otherVariables(X).isNotEmpty() || a.otherVariables(X).isNotEmpty() || b.otherVariables(X).isNotEmpty()
        val numeric = if (hasParams) null else runCatching {
            val lo = a.eval(X, 0.0)
            val hi = b.eval(X, 0.0)
            Numeric.integrate({ t -> f.eval(X, t) }, lo, hi)
        }.getOrNull()?.takeIf { it.isFinite() }
        val symbolic = anti?.let { tidy(sub(it.subst(Sym.Var(X), b), it.subst(Sym.Var(X), a))) }
        val value = if (symbolic != null && !hasParams) exact(symbolic, memory, settings) else null
        val exactDouble = (value as? Value.Num)?.z?.re?.toDouble()
        // A jump or pole inside [a, b] makes F(b) − F(a) wrong; trust the numeric result then.
        val numericOnly = numeric != null && exactDouble != null &&
            Math.abs(numeric - exactDouble) > 1e-6 * maxOf(1.0, Math.abs(numeric))
        return Definite(a, b, symbolic, value, numeric, numericOnly || (symbolic == null))
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
