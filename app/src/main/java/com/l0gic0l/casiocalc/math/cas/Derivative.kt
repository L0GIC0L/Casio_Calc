package com.l0gic0l.casiocalc.math.cas

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens

/**
 * One step of a worked solution: the rule used, a sentence about it, the maths in natural
 * display (one row per line) and the smaller steps it needed.
 */
class Step(
    val title: String,
    val text: String? = null,
    val math: List<MathRow> = emptyList(),
    val children: List<Step> = emptyList(),
)

/** Joins rows with an operator between them: a + b + c. */
internal fun joinRows(rows: List<MathRow>, op: Node = TokenNode(Tokens.ADD)): MathRow {
    val out = ArrayList<Node>()
    rows.forEachIndexed { i, r ->
        if (i > 0) out += op.copy()
        out += r.copy().nodes.map { it.copy() }
    }
    return MathRow(out)
}

/** Prefixes a row with a constant factor: c·row, −row, or (a+b)·row. */
internal fun Render.scaled(c: Sym, inner: MathRow): MathRow {
    if (c.isOne) return inner
    if (c == MINUS_ONE) return MathRow(listOf(TokenNode(Tokens.SUB)) + inner.copy().nodes.map { it.copy() })
    val prefix = if (c is Sym.Add) listOf(TokenNode(Tokens.LPAREN)) + nodes(c) + TokenNode(Tokens.RPAREN) else nodes(c)
    return MathRow(prefix + inner.copy().nodes.map { it.copy() })
}

/** Derivatives with respect to [x], with or without the worked steps. */
class Differentiator(private val x: String = "X") {
    private val r = Render(x)
    private val xv = Sym.Var(x)

    /** The derivative alone, in canonical form. */
    fun d(f: Sym): Sym = rule(f, false).value

    /** The derivative and the steps that produce it. */
    fun withSteps(f: Sym): Pair<Sym, Step> {
        val res = rule(f, true)
        return res.value to res.step!!
    }

    private class Res(val value: Sym, val step: Step?)

    private fun res(value: Sym, steps: Boolean, title: String, text: String?, f: Sym, children: List<Step> = emptyList(), middle: MathRow? = null): Res {
        if (!steps) return Res(value, null)
        val lines = listOfNotNull(middle?.let { r.equation(r.derivative(f), it) })
        val final = r.equation(r.derivative(f), r.row(value))
        return Res(value, Step(title, text, if (middle != null) lines + final else listOf(final), children))
    }

    private fun rule(f: Sym, steps: Boolean): Res {
        if (!f.dependsOn(x)) return res(ZERO, steps, "Constant rule", "The derivative of a constant is 0.", f)
        if (f == xv) return res(ONE, steps, "Variable rule", "The derivative of ${x.lowercase()} is 1.", f)
        return when (f) {
            is Sym.Add -> {
                val parts = f.terms.map { rule(it, steps) }
                val value = add(parts.map { it.value })
                res(value, steps, "Sum rule", "Differentiate each term separately.", f, parts.mapNotNull { it.step },
                    if (steps) joinRows(f.terms.map { r.derivative(it) }) else null)
            }
            is Sym.Mul -> product(f, steps)
            is Sym.Pow -> power(f, steps)
            is Sym.Fn -> function(f, steps)
            else -> res(ZERO, steps, "Constant rule", null, f)
        }
    }

    private fun product(f: Sym.Mul, steps: Boolean): Res {
        val constant = mul(f.factors.filter { !it.dependsOn(x) })
        val variable = mul(f.factors.filter { it.dependsOn(x) })
        if (!constant.isOne) {
            val inner = rule(variable, steps)
            return res(mul(constant, inner.value), steps, "Constant multiple rule", "A constant factor stays in front of the derivative.", f,
                listOfNotNull(inner.step), if (steps) r.scaled(constant, r.derivative(variable)) else null)
        }
        val factors = (variable as Sym.Mul).factors
        val isDenominator = { s: Sym -> s is Sym.Pow && (s.exp.rational?.signum ?: 0) < 0 }
        if (factors.any(isDenominator)) {
            // Quotient rule: (u/v)′ = (u′v − uv′)/v².
            val u = mul(factors.filterNot(isDenominator))
            val v = mul(factors.filter(isDenominator).map { pow((it as Sym.Pow).base, neg(it.exp)) })
            val du = rule(u, steps)
            val dv = rule(v, steps)
            val value = div(sub(mul(du.value, v), mul(u, dv.value)), pow(v, TWO))
            return res(value, steps, "Quotient rule",
                "For a quotient u/v the derivative is (u′v − uv′)/v², here with u = ${plain(u)} and v = ${plain(v)}.", f,
                listOfNotNull(du.step, dv.step))
        }
        // Product rule: (uv)′ = u′v + uv′.
        val u = factors.first()
        val v = mul(factors.drop(1))
        val du = rule(u, steps)
        val dv = rule(v, steps)
        val value = add(mul(du.value, v), mul(u, dv.value))
        return res(value, steps, "Product rule",
            "For a product uv the derivative is u′v + uv′, here with u = ${plain(u)} and v = ${plain(v)}.", f,
            listOfNotNull(du.step, dv.step))
    }

    private fun power(f: Sym.Pow, steps: Boolean): Res {
        val b = f.base
        val e = f.exp
        return when {
            !e.dependsOn(x) -> {
                // Power rule, with the chain rule when the base is not plain x.
                val outer = mul(e, pow(b, sub(e, ONE)))
                if (b == xv) {
                    res(outer, steps, "Power rule", "d/dx xⁿ = n·xⁿ⁻¹ with n = ${plain(e)}.", f)
                } else {
                    val inner = rule(b, steps)
                    res(mul(outer, inner.value), steps, "Power rule with the chain rule",
                        "Treat ${plain(b)} as u: d/dx uⁿ = n·uⁿ⁻¹·du/dx with n = ${plain(e)}.", f, listOfNotNull(inner.step))
                }
            }
            !b.dependsOn(x) -> {
                // Exponential: d/dx aᵘ = aᵘ·ln(a)·u′, and eᵘ′ = eᵘ·u′.
                val outer = if (b == E) f else mul(f, fn("ln", b))
                val text = if (b == E) "The derivative of eᵘ is eᵘ·du/dx." else "The derivative of aᵘ is aᵘ·ln(a)·du/dx."
                if (e == xv) {
                    res(outer, steps, "Exponential rule", text, f)
                } else {
                    val inner = rule(e, steps)
                    res(mul(outer, inner.value), steps, "Exponential rule with the chain rule", text, f, listOfNotNull(inner.step))
                }
            }
            else -> {
                // uᵛ = e^(v·ln u): (uᵛ)′ = uᵛ·(v′·ln u + v·u′/u).
                val du = rule(b, steps)
                val dv = rule(e, steps)
                val value = mul(f, add(mul(dv.value, fn("ln", b)), mul(e, du.value, pow(b, MINUS_ONE))))
                res(value, steps, "Logarithmic differentiation",
                    "Write uᵛ as e^(v·ln u); then (uᵛ)′ = uᵛ·(v′·ln u + v·u′/u).", f, listOfNotNull(du.step, dv.step))
            }
        }
    }

    private fun function(f: Sym.Fn, steps: Boolean): Res {
        val u = f.arg
        val outer = outerDerivative(f.name, u) ?: return res(ZERO, steps, "Unknown function", null, f)
        val name = functionLabel(f.name)
        if (u == xv) {
            return res(outer, steps, "Derivative of $name", "d/dx $name(x) = ${plain(outerDerivative(f.name, Sym.Var("x"))!!)}.", f)
        }
        val inner = rule(u, steps)
        return res(mul(outer, inner.value), steps, "Chain rule",
            "With u = ${plain(u)}: d/dx $name(u) = ${plain(outerDerivative(f.name, Sym.Var("u"))!!)}·du/dx.", f, listOfNotNull(inner.step))
    }

    companion object {
        /** f′(u) for the named function. */
        fun outerDerivative(name: String, u: Sym): Sym? = when (name) {
            "sin" -> fn("cos", u)
            "cos" -> neg(fn("sin", u))
            "tan" -> pow(fn("cos", u), num(-2))
            "asin" -> pow(sub(ONE, pow(u, TWO)), rat(-1, 2))
            "acos" -> neg(pow(sub(ONE, pow(u, TWO)), rat(-1, 2)))
            "atan" -> pow(add(ONE, pow(u, TWO)), MINUS_ONE)
            "sinh" -> fn("cosh", u)
            "cosh" -> fn("sinh", u)
            "tanh" -> pow(fn("cosh", u), num(-2))
            "asinh" -> pow(add(pow(u, TWO), ONE), rat(-1, 2))
            "acosh" -> pow(sub(pow(u, TWO), ONE), rat(-1, 2))
            "atanh" -> pow(sub(ONE, pow(u, TWO)), MINUS_ONE)
            "ln" -> pow(u, MINUS_ONE)
            "log" -> pow(mul(u, fn("ln", num(10))), MINUS_ONE)
            "abs" -> mul(u, pow(fn("abs", u), MINUS_ONE))
            else -> null
        }

        fun functionLabel(name: String) = when (name) {
            "asin" -> "sin⁻¹"
            "acos" -> "cos⁻¹"
            "atan" -> "tan⁻¹"
            "asinh" -> "sinh⁻¹"
            "acosh" -> "cosh⁻¹"
            "atanh" -> "tanh⁻¹"
            "abs" -> "|·|"
            else -> name
        }
    }
}

/** A short linear form for explanations, ordered as the math is drawn: X²·sin(X), 1/(X + 1). */
fun plain(s: Sym): String = when (s) {
    is Sym.Num -> s.q.toString().replace('-', '−')
    is Sym.Const -> if (s.name == "pi") "π" else "e"
    is Sym.Var -> s.name
    is Sym.Add -> orderedTerms(s, Calculus.X).mapIndexed { i, t ->
        val negative = splitCoefficient(t).first.signum < 0
        when {
            i == 0 -> plain(t)
            negative -> " − " + plain(neg(t))
            else -> " + " + plain(t)
        }
    }.joinToString("")
    is Sym.Mul, is Sym.Pow -> plainProduct(s)
    is Sym.Fn -> if (s.name == "abs") "|${plain(s.arg)}|" else "${Differentiator.functionLabel(s.name)}(${plain(s.arg)})"
}

private fun plainProduct(s: Sym): String {
    val (c, rest) = splitCoefficient(s)
    if (c.signum < 0) return "−" + plainProduct(neg(s))
    val factors = if (rest.isOne) emptyList() else if (rest is Sym.Mul) rest.factors else listOf(rest)
    val top = ArrayList<String>()
    val bottom = ArrayList<String>()
    if (c.num != java.math.BigInteger.ONE || factors.isEmpty()) top += c.num.toString()
    if (c.den != java.math.BigInteger.ONE) bottom += c.den.toString()
    for (f in factors) {
        val e = (f as? Sym.Pow)?.exp?.rational
        if (f is Sym.Pow && e != null && e.signum < 0) bottom += plainPower(pow(f.base, neg(f.exp))) else top += plainPower(f)
    }
    val num = if (top.isEmpty()) "1" else top.joinToString("·")
    if (bottom.isEmpty()) return num
    return (if (top.size > 1) "($num)" else num) + "/" + bottom.joinToString("·").let { if (bottom.size > 1) "($it)" else it }
}

private fun plainPower(s: Sym): String {
    if (s !is Sym.Pow) return plainAtom(s)
    val e = s.exp.rational
    return when {
        e == HALF.rational -> "√" + plainAtom(s.base)
        e != null && e.isInteger -> plainAtom(s.base) + e.toString().map { if (it == '-') '⁻' else "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
        else -> plainAtom(s.base) + "^" + plainAtom(s.exp)
    }
}

private fun plainAtom(s: Sym): String = when (s) {
    is Sym.Add, is Sym.Mul, is Sym.Pow -> "(${plain(s)})"
    is Sym.Num -> if (s.q.isInteger && s.q.signum >= 0) plain(s) else "(${plain(s)})"
    else -> plain(s)
}
