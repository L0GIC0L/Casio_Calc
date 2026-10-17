package com.l0gic0l.casiocalc.math.graph

import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.eval.Evaluator
import com.l0gic0l.casiocalc.math.eval.Trig
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.parse.Expr
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.round
import kotlin.math.sqrt

/** A real function of one variable; NaN where it is undefined or not real. */
fun interface RealFn {
    fun eval(x: Double): Double
}

/**
 * Compiles an expression into plain double arithmetic so graphs can be sampled thousands of times
 * per frame. Anything unusual (Σ, ∫, lists, complex numbers) falls back to the exact [Evaluator]
 * for that sub-expression, so results always match Run-Matrix.
 */
class FastCompiler(private val evaluator: Evaluator, private val variable: String) {
    private val unit = evaluator.settings.angle

    fun compile(e: Expr): RealFn = node(e)

    private fun const(v: Double) = RealFn { v }

    private fun fallback(e: Expr) = RealFn { x ->
        try {
            evaluator.evalReal(e, variable, x)
        } catch (ex: Exception) {
            Double.NaN
        }
    }

    private fun constantValue(e: Expr): Double? = when (e) {
        is Expr.Num -> e.value.toDouble()
        is Expr.Unary -> if (e.op == "neg") constantValue(e.e)?.let { -it } else null
        is Expr.Tpl -> if (e.kind == TemplateKind.FRACTION) {
            val n = constantValue(e.slots[0]!!)
            val d = constantValue(e.slots[1]!!)
            if (n != null && d != null) n / d else null
        } else null
        else -> null
    }

    /** True when [e] is a constant rational whose denominator is odd, e.g. 1/3, so x^e is real for x < 0. */
    private fun oddRoot(e: Expr): Pair<Double, Boolean>? {
        val v = constantValue(e) ?: return null
        val q = com.l0gic0l.casiocalc.math.num.BigRational.approximate(v, 99, 1e-12) ?: return null
        if (!q.den.testBit(0)) return null
        return v to q.num.testBit(0)
    }

    private fun node(e: Expr): RealFn = when (e) {
        is Expr.Num -> const(e.value.toDouble())
        is Expr.Var -> if (e.name == variable) RealFn { it } else {
            val v = evaluator.memory.getVar(e.name)
            val z = (v as? Value.Num)?.z
            const(if (z != null && z.isReal) z.re.toDouble() else Double.NaN)
        }
        is Expr.Const -> when (e.id) {
            "pi" -> const(Math.PI)
            "e" -> const(Math.E)
            "Ran#" -> RealFn { Math.random() }
            else -> fallback(e)
        }
        is Expr.Unary -> if (e.op == "neg") {
            val a = node(e.e)
            RealFn { -a.eval(it) }
        } else fallback(e)
        is Expr.Binary -> binary(e)
        is Expr.Postfix -> postfix(e)
        is Expr.Call -> call(e)
        is Expr.Tpl -> template(e)
        else -> fallback(e)
    }

    private fun binary(e: Expr.Binary): RealFn {
        val a = node(e.l)
        val b = node(e.r)
        return when (e.op) {
            "+" -> RealFn { a.eval(it) + b.eval(it) }
            "-" -> RealFn { a.eval(it) - b.eval(it) }
            "*", "imul" -> RealFn { a.eval(it) * b.eval(it) }
            "/" -> RealFn { val d = b.eval(it); if (d == 0.0) Double.NaN else a.eval(it) / d }
            "pow" -> {
                val odd = oddRoot(e.r)
                if (odd != null) {
                    val (p, oddNumerator) = odd
                    RealFn { x ->
                        val base = a.eval(x)
                        if (base >= 0) Math.pow(base, p) else {
                            val m = Math.pow(-base, p)
                            if (oddNumerator) -m else m
                        }
                    }
                } else RealFn { Math.pow(a.eval(it), b.eval(it)) }
            }
            "=" -> RealFn { if (a.eval(it) == b.eval(it)) 1.0 else 0.0 }
            "!=" -> RealFn { if (a.eval(it) != b.eval(it)) 1.0 else 0.0 }
            ">" -> RealFn { if (a.eval(it) > b.eval(it)) 1.0 else 0.0 }
            "<" -> RealFn { if (a.eval(it) < b.eval(it)) 1.0 else 0.0 }
            ">=" -> RealFn { if (a.eval(it) >= b.eval(it)) 1.0 else 0.0 }
            "<=" -> RealFn { if (a.eval(it) <= b.eval(it)) 1.0 else 0.0 }
            else -> fallback(e)
        }
    }

    private fun postfix(e: Expr.Postfix): RealFn {
        val a = node(e.e)
        return when (e.op) {
            "deg" -> { val k = Trig.convert(Real.ONE, com.l0gic0l.casiocalc.math.eval.AngleUnit.DEG, unit).toDouble(); RealFn { a.eval(it) * k } }
            "rad" -> { val k = Trig.convert(Real.ONE, com.l0gic0l.casiocalc.math.eval.AngleUnit.RAD, unit).toDouble(); RealFn { a.eval(it) * k } }
            "gra" -> { val k = Trig.convert(Real.ONE, com.l0gic0l.casiocalc.math.eval.AngleUnit.GRA, unit).toDouble(); RealFn { a.eval(it) * k } }
            "%" -> RealFn { a.eval(it) / 100 }
            "dmsDeg" -> a
            "!" -> RealFn { x ->
                val v = a.eval(x)
                if (v < 0 || v != floor(v) || v > 170) Double.NaN else {
                    var f = 1.0
                    var k = 2.0
                    while (k <= v) { f *= k; k++ }
                    f
                }
            }
            else -> fallback(e)
        }
    }

    private fun toRad(v: Double) = Trig.toRadians(v, unit)
    private fun fromRad(v: Double) = Trig.fromRadians(v, unit)

    private fun call(e: Expr.Call): RealFn {
        if (e.args.size != 1) {
            if (e.name == "log" && e.args.size == 2) {
                val b = node(e.args[0]); val x = node(e.args[1])
                return RealFn { ln(x.eval(it)) / ln(b.eval(it)) }
            }
            return fallback(e)
        }
        val a = node(e.args[0])
        return when (e.name) {
            "sin" -> RealFn { kotlin.math.sin(toRad(a.eval(it))) }
            "cos" -> RealFn { kotlin.math.cos(toRad(a.eval(it))) }
            "tan" -> RealFn { kotlin.math.tan(toRad(a.eval(it))) }
            "asin" -> RealFn { fromRad(kotlin.math.asin(a.eval(it))) }
            "acos" -> RealFn { fromRad(kotlin.math.acos(a.eval(it))) }
            "atan" -> RealFn { fromRad(kotlin.math.atan(a.eval(it))) }
            "sinh" -> RealFn { kotlin.math.sinh(a.eval(it)) }
            "cosh" -> RealFn { kotlin.math.cosh(a.eval(it)) }
            "tanh" -> RealFn { kotlin.math.tanh(a.eval(it)) }
            "asinh" -> RealFn { kotlin.math.asinh(a.eval(it)) }
            "acosh" -> RealFn { kotlin.math.acosh(a.eval(it)) }
            "atanh" -> RealFn { kotlin.math.atanh(a.eval(it)) }
            "log" -> RealFn { kotlin.math.log10(a.eval(it)) }
            "ln" -> RealFn { ln(a.eval(it)) }
            "Int" -> RealFn { val v = a.eval(it); if (v < 0) kotlin.math.ceil(v) else floor(v) }
            "Intg" -> RealFn { floor(a.eval(it)) }
            "Frac" -> RealFn { val v = a.eval(it); v - (if (v < 0) kotlin.math.ceil(v) else floor(v)) }
            else -> fallback(e)
        }
    }

    private fun template(e: Expr.Tpl): RealFn {
        val s = e.slots
        return when (e.kind) {
            TemplateKind.FRACTION -> {
                val n = node(s[0]!!); val d = node(s[1]!!)
                RealFn { val dv = d.eval(it); if (dv == 0.0) Double.NaN else n.eval(it) / dv }
            }
            TemplateKind.SQRT -> {
                val a = node(s[0]!!)
                RealFn { sqrt(a.eval(it)) }
            }
            TemplateKind.ROOT -> {
                val idx = constantValue(s[0]!!)
                val a = node(s[1]!!)
                if (idx != null && idx == round(idx) && abs(idx) % 2 == 1.0) {
                    RealFn { val v = a.eval(it); if (v < 0) -Math.pow(-v, 1 / idx) else Math.pow(v, 1 / idx) }
                } else {
                    val n = node(s[0]!!)
                    RealFn { Math.pow(a.eval(it), 1 / n.eval(it)) }
                }
            }
            TemplateKind.ABS -> {
                val a = node(s[0]!!)
                RealFn { abs(a.eval(it)) }
            }
            TemplateKind.LOG_BASE -> {
                val b = node(s[0]!!); val x = node(s[1]!!)
                RealFn { ln(x.eval(it)) / ln(b.eval(it)) }
            }
            else -> fallback(e)
        }
    }
}
