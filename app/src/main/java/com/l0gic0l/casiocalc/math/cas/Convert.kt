package com.l0gic0l.casiocalc.math.cas

import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.mathError
import com.l0gic0l.casiocalc.math.num.BigRational
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.parse.Expr
import java.math.BigInteger

/** Thrown for input the symbolic solvers cannot work with, such as matrices or i. */
class UnsupportedInput(what: String) : RuntimeException("Can't use $what here")

private val functionNames = setOf(
    "sin", "cos", "tan", "asin", "acos", "atan",
    "sinh", "cosh", "tanh", "asinh", "acosh", "atanh", "ln", "log",
)

/** Converts a parsed calculator expression into a symbolic one. Letters stay as variables. */
fun Expr.toSym(): Sym = when (this) {
    is Expr.Num -> value.toSym()
    is Expr.Var -> Sym.Var(name)
    is Expr.Const -> when (id) {
        "pi" -> PI
        "e" -> E
        "ten" -> num(10)
        else -> throw UnsupportedInput(if (id == "i") "complex numbers" else id)
    }
    is Expr.Unary -> when (op) {
        "neg" -> neg(e.toSym())
        else -> throw UnsupportedInput(op)
    }
    is Expr.Binary -> {
        val a = l.toSym()
        val b = r.toSym()
        when (op) {
            "+" -> add(a, b)
            "-" -> sub(a, b)
            "*", "imul" -> mul(a, b)
            "/" -> divide(a, b)
            "pow" -> pow(a, b)
            else -> throw UnsupportedInput(op)
        }
    }
    is Expr.Postfix -> when (op) {
        "deg" -> mul(e.toSym(), PI, rat(1, 180))
        "!" -> {
            val n = e.toSym().int ?: throw UnsupportedInput("x!")
            if (n !in 0..60) throw UnsupportedInput("x!")
            num(BigRational.of((1..n).fold(BigInteger.ONE) { acc, k -> acc * BigInteger.valueOf(k.toLong()) }))
        }
        else -> throw UnsupportedInput(op)
    }
    is Expr.Call -> when {
        name in functionNames && args.size == 1 -> fn(name, args[0].toSym())
        name == "log" && args.size == 2 -> div(fn("ln", args[1].toSym()), fn("ln", args[0].toSym()))
        else -> throw UnsupportedInput("$name(")
    }
    is Expr.Tpl -> {
        val s = slots.map { it?.toSym() }
        when (kind) {
            TemplateKind.FRACTION -> divide(s[0]!!, s[1]!!)
            TemplateKind.MIXED -> {
                val w = s[0]!!
                val f = divide(s[1]!!, s[2]!!)
                // 2 1/2 is 2 + 1/2; −2 1/2 is −(2 + 1/2).
                if (splitCoefficient(w).first.signum < 0) sub(w, f) else add(w, f)
            }
            TemplateKind.SQRT -> sqrt(s[0]!!)
            TemplateKind.ROOT -> pow(s[1]!!, div(ONE, s[0]!!))
            TemplateKind.LOG_BASE -> div(fn("ln", s[1]!!), fn("ln", s[0]!!))
            TemplateKind.ABS -> fn("abs", s[0]!!)
            else -> throw UnsupportedInput(kind.name.lowercase())
        }
    }
    is Expr.ListLit -> throw UnsupportedInput("lists")
    is Expr.MatLit -> throw UnsupportedInput("matrices")
    is Expr.Ref -> throw UnsupportedInput(id)
    is Expr.Store -> throw UnsupportedInput("→")
    is Expr.Format -> e.toSym()
}

/** An exact calculator number as a symbolic one: (a + b√r)·πᵏ. */
fun Real.toSym(): Sym = when (this) {
    is Real.Exact -> {
        val surd = if (b.isZero) ZERO else mul(Sym.Num(b), pow(num(r), HALF))
        mul(add(Sym.Num(a), surd), pow(PI, num(pi.toLong())))
    }
    is Real.Approx -> Sym.Num(BigRational.approximate(v, 1_000_000, 1e-12) ?: BigRational.of(java.math.BigDecimal(v)))
}

/** Converts back to a calculator expression, for exact evaluation with the normal evaluator. */
fun Sym.toExpr(): Expr = when (this) {
    is Sym.Num -> Expr.Num(Real.of(q))
    is Sym.Const -> Expr.Const(name)
    is Sym.Var -> Expr.Var(name)
    is Sym.Add -> terms.map { it.toExpr() }.reduce { a, b -> Expr.Binary("+", a, b) }
    is Sym.Mul -> factors.map { it.toExpr() }.reduce { a, b -> Expr.Binary("*", a, b) }
    is Sym.Pow -> when {
        exp == HALF -> Expr.Tpl(TemplateKind.SQRT, listOf(base.toExpr()))
        else -> Expr.Binary("pow", base.toExpr(), exp.toExpr())
    }
    is Sym.Fn -> if (name == "abs") Expr.Tpl(TemplateKind.ABS, listOf(arg.toExpr())) else Expr.Call(name, listOf(arg.toExpr()))
}

/** a ÷ b as typed: a denominator that is zero, such as in 0/0 or 1/(X − X), is an error rather than folding to 0. */
private fun divide(a: Sym, b: Sym): Sym {
    if (b.isZero) mathError("division by zero")
    return div(a, b)
}
