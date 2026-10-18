package com.l0gic0l.casiocalc.math.cas

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.TokenKind
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.num.BigRational
import java.math.BigInteger

/**
 * Draws symbolic expressions in natural display: quotients as fractions, x^(1/2) as √x, sums
 * ordered by falling power of [x] with minus signs instead of negative coefficients.
 */
class Render(private val x: String = "X") {

    fun row(s: Sym): MathRow = MathRow(nodes(s))

    /** ∫ f dv, without limits. */
    fun integral(f: Sym, v: String = x): MathRow =
        MathRow(listOf(Template(TemplateKind.INDEF_INTEGRAL, listOf(integrand(f), MathRow(listOf(differential(v)))))))

    /** ∫ from a to b of f dx. */
    fun definite(f: Sym, a: MathRow, b: MathRow): MathRow =
        MathRow(listOf(Template(TemplateKind.INTEGRAL, listOf(a.copy(), b.copy(), integrand(f)))))

    /** A sum between ∫ and dx is bracketed: ∫(x + 1)dx, not ∫x + 1 dx. */
    private fun integrand(f: Sym): MathRow = if (f is Sym.Add) MathRow(paren(sum(f))) else row(f)

    /** d/dv of f, or dⁿ/dvⁿ when [order] > 1. */
    fun derivative(f: Sym, v: String = x, order: Int = 1): MathRow =
        MathRow(listOf(Template(TemplateKind.DIFF, listOf(row(f), MathRow(listOf(differential(v))), MathRow(if (order > 1) digits(BigInteger.valueOf(order.toLong())) else emptyList())))))

    /** Joins rows with "=": a = b = c. */
    fun equation(vararg parts: MathRow): MathRow {
        val out = ArrayList<Node>()
        parts.forEachIndexed { i, p ->
            if (i > 0) out += TokenNode(Tokens.EQ)
            out += p.copy().nodes.map { it.copy() }
        }
        return MathRow(out)
    }

    /** a + C, the constant of integration. */
    fun plusC(r: MathRow): MathRow = MathRow(r.copy().nodes.map { it.copy() } + listOf(TokenNode(Tokens.ADD), TokenNode(Tokens.letter('C'))))

    /** Plain label such as "u" or "dv" followed by "=" and an expression. */
    fun define(name: String, s: Sym): MathRow = MathRow(listOf(TokenNode(label(name)), TokenNode(Tokens.EQ)) + nodes(s))

    fun definePair(n1: String, s1: Sym, n2: String, s2: Sym): MathRow =
        MathRow(define(n1, s1).nodes + listOf(TokenNode(Tokens.COMMA)) + define(n2, s2).nodes)

    // -------------------------------------------------------------------------------------

    fun nodes(s: Sym): List<Node> = when (s) {
        is Sym.Add -> sum(s)
        else -> {
            val (c, _) = splitCoefficient(s)
            if (c.signum < 0) listOf(TokenNode(Tokens.SUB)) + term(neg(s)) else term(s)
        }
    }

    private fun sum(s: Sym.Add): List<Node> {
        val out = ArrayList<Node>()
        orderedTerms(s, x).forEachIndexed { i, t ->
            val (c, _) = splitCoefficient(t)
            val negative = c.signum < 0
            if (i == 0) {
                if (negative) out += TokenNode(Tokens.SUB)
            } else {
                out += TokenNode(if (negative) Tokens.SUB else Tokens.ADD)
            }
            out += term(if (negative) neg(t) else t)
        }
        return out
    }

    /** A positive product: coefficient, then factors, with a fraction bar for negative powers. */
    private fun term(s: Sym): List<Node> {
        val (c, rest) = splitCoefficient(s)
        val factors = if (rest.isOne) emptyList() else if (rest is Sym.Mul) rest.factors else listOf(rest)
        val top = ArrayList<Sym>()
        val bottom = ArrayList<Sym>()
        for (f in factors) {
            val e = (f as? Sym.Pow)?.exp
            val (ec, _) = if (e != null) splitCoefficient(e) else (BigRational.ONE to ONE)
            if (f is Sym.Pow && ec.signum < 0) bottom += pow(f.base, neg(f.exp)) else top += f
        }
        val fraction = bottom.isNotEmpty() || c.den != BigInteger.ONE
        val numerator = product(c.num.abs(), top, bare = fraction)
        if (!fraction) return numerator
        val denominator = product(c.den, bottom, bare = true)
        return listOf(Template(TemplateKind.FRACTION, listOf(MathRow(numerator), MathRow(denominator))))
    }

    /** [bare]: a lone sum needs no brackets, as when it is the whole numerator or denominator of a fraction. */
    private fun product(coefficient: BigInteger, factors: List<Sym>, bare: Boolean = false): List<Node> {
        if (bare && coefficient == BigInteger.ONE && factors.size == 1) return nodes(factors[0])
        val out = ArrayList<Node>()
        if (coefficient != BigInteger.ONE || factors.isEmpty()) out += digits(coefficient)
        for (f in factors) {
            val part = factor(f)
            // Two numbers side by side need a × between them: 2×3ˣ, 2×√3 reads as 2√3 and is fine.
            val first = part.firstOrNull()
            if (out.isNotEmpty() && first is TokenNode && first.tok.kind == TokenKind.DIGIT) out += TokenNode(Tokens.MUL)
            out += part
        }
        return out
    }

    private fun factor(f: Sym): List<Node> = when (f) {
        is Sym.Add -> paren(sum(f))
        is Sym.Mul -> paren(nodes(f))
        else -> atom(f)
    }

    private fun atom(s: Sym): List<Node> = when (s) {
        is Sym.Num -> number(s.q)
        is Sym.Const -> listOf(TokenNode(if (s.name == "pi") Tokens.PI else Tokens.E))
        is Sym.Var -> listOf(varNode(s.name))
        is Sym.Pow -> power(s)
        is Sym.Fn -> function(s)
        is Sym.Add -> paren(sum(s))
        is Sym.Mul -> paren(nodes(s))
    }

    private fun number(q: BigRational): List<Node> {
        val sign = if (q.signum < 0) listOf<Node>(TokenNode(Tokens.SUB)) else emptyList()
        val a = q.abs()
        return sign + if (a.isInteger) digits(a.num) else listOf(Template(TemplateKind.FRACTION, listOf(MathRow(digits(a.num)), MathRow(digits(a.den)))))
    }

    private fun power(p: Sym.Pow): List<Node> {
        val e = p.exp.rational
        if (e != null && e.signum > 0 && e.num == BigInteger.ONE) {
            if (e.den == BigInteger.valueOf(2)) return listOf(Template(TemplateKind.SQRT, listOf(row(p.base))))
            if (e.den.bitLength() < 16) return listOf(Template(TemplateKind.ROOT, listOf(MathRow(digits(e.den)), row(p.base))))
        }
        if (e != null && e.signum < 0) {
            // A lone negative power reads best as a fraction: x⁻² → 1/x².
            return listOf(Template(TemplateKind.FRACTION, listOf(MathRow(digits(BigInteger.ONE)), MathRow(nodes(pow(p.base, neg(p.exp)))))))
        }
        val needsParens = when (val b = p.base) {
            is Sym.Add, is Sym.Mul, is Sym.Pow -> true
            is Sym.Num -> b.q.signum < 0 || !b.q.isInteger
            else -> false
        }
        val base = if (needsParens) paren(nodes(p.base)) else atom(p.base)
        return base + Template(TemplateKind.POWER, listOf(row(p.exp)))
    }

    private fun function(f: Sym.Fn): List<Node> {
        if (f.name == "abs") return listOf(Template(TemplateKind.ABS, listOf(row(f.arg))))
        return listOf(TokenNode(Tokens.fn(f.name))) + nodes(f.arg) + TokenNode(Tokens.RPAREN)
    }

    private fun paren(inner: List<Node>): List<Node> = listOf(TokenNode(Tokens.LPAREN)) + inner + TokenNode(Tokens.RPAREN)

    private fun digits(n: BigInteger): List<Node> = n.abs().toString().map { TokenNode(Tokens.digit(it - '0')) }

    private fun varNode(name: String): Node = TokenNode(
        when {
            name.length == 1 && name[0] in 'A'..'Z' -> Tokens.letter(name[0])
            else -> label(name)
        },
    )

    /** The variable after d in dx and d/dx, lower case as the calculator writes it. */
    private fun differential(name: String): Node = TokenNode(label(name.lowercase()))

    companion object {
        private val labels = HashMap<String, Tok>()

        /** A display-only name such as u, v, du or dv. */
        fun label(name: String): Tok = labels.getOrPut(name) { Tok("cas_$name", name, TokenKind.VARIABLE) }
    }
}

/**
 * Power of [x] in a term c·xᵏ·g(x), for ordering sums: higher powers first, a non-polynomial
 * factor counts a half (x²eˣ before x², x² before x·eˣ), and constants come last.
 */
private fun degreeIn(t: Sym, x: String): Double {
    if (!t.dependsOn(x)) return -1e9
    val (_, rest) = splitCoefficient(t)
    val factors = if (rest is Sym.Mul) rest.factors else listOf(rest)
    var d = 0.0
    var other = false
    for (f in factors) {
        when {
            f is Sym.Var && f.name == x -> d += 1
            f is Sym.Pow && f.base is Sym.Var && f.base.name == x && f.exp is Sym.Num -> d += f.exp.q.toDouble()
            f.dependsOn(x) -> other = true
        }
    }
    return if (other) d + 0.5 else d
}

/** The terms of a sum in reading order: falling power of [x], constants last. */
internal fun orderedTerms(s: Sym.Add, x: String): List<Sym> =
    s.terms.sortedWith(compareByDescending<Sym> { degreeIn(it, x) }.thenBy { if (it.dependsOn(x)) 0 else 1 })
