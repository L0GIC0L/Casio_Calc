package com.l0gic0l.casiocalc.math.cas

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.num.BigRational

/** Shared work limit for one solve, so a hopeless integral gives up quickly. */
class Budget(var calls: Int = 3000, val cancelled: () -> Boolean = { false }) {
    /** Integrals being worked on, with the variable renamed, so substitutions cannot go round in circles. */
    internal val active = HashSet<Sym>()
}

/**
 * Antiderivatives with respect to [x], found the way they are taught: linearity, a table of
 * standard integrals, u-substitution, partial fractions, integration by parts and trigonometric
 * identities. Each method records the [Step] it took.
 */
class Integrator(private val x: String = "X", private val budget: Budget = Budget()) {
    private val r = Render(x)
    private val xv = Sym.Var(x)
    private val diff = Differentiator(x)

    class Found(val value: Sym, val step: Step)

    fun integrate(f: Sym): Found? = integ(f, 0, true)

    private fun integ(f: Sym, depth: Int, allowParts: Boolean): Found? {
        if (depth > MAX_DEPTH || --budget.calls < 0 || budget.cancelled()) return null
        val key = f.subst(xv, ANY)
        if (!budget.active.add(key)) return null
        try {
            return methods(f, depth, allowParts)
        } finally {
            budget.active.remove(key)
        }
    }

    private fun methods(f: Sym, depth: Int, allowParts: Boolean): Found? {
        constant(f)?.let { return it }
        if (f is Sym.Add) sum(f, depth, allowParts)?.let { return it }
        constantMultiple(f, depth, allowParts)?.let { return it }
        table(f)?.let { return it }
        squareUnderRoot(f, depth)?.let { return it }
        productToSum(f, depth)?.let { return it }
        substitution(f, depth, late = false)?.let { return it }
        rational(f, depth)?.let { return it }
        splitSquare(f, depth, allowParts)?.let { return it }
        if (allowParts) parts(f, depth)?.let { return it }
        substitution(f, depth, late = true)?.let { return it }
        trig(f, depth, allowParts)?.let { return it }
        expanded(f, depth, allowParts)?.let { return it }
        return null
    }

    private fun I(f: Sym): MathRow = r.integral(f, x)

    private fun found(f: Sym, raw: Sym, title: String, text: String?, children: List<Step> = emptyList(), middle: List<MathRow> = emptyList()): Found {
        val value = tidy(raw)
        val lines = middle.map { r.equation(I(f), it) } + r.equation(I(f), r.row(value))
        return Found(value, Step(title, text, lines, children))
    }

    // ---- linearity ------------------------------------------------------------------------

    private fun constant(f: Sym): Found? {
        if (f.dependsOn(x)) return null
        return found(f, mul(f, xv), "Constant rule", "The integral of a constant c is c·${v()}.")
    }

    private fun sum(f: Sym.Add, depth: Int, allowParts: Boolean): Found? {
        val parts = f.terms.map { integ(it, depth + 1, allowParts) ?: return null }
        return found(f, add(parts.map { it.value }), "Sum rule", "Integrate each term separately.",
            parts.map { it.step }, listOf(joinRows(f.terms.map { I(it) })))
    }

    private fun constantMultiple(f: Sym, depth: Int, allowParts: Boolean): Found? {
        if (f !is Sym.Mul) return null
        val c = mul(f.factors.filter { !it.dependsOn(x) })
        if (c.isOne) return null
        val g = mul(f.factors.filter { it.dependsOn(x) })
        val inner = integ(g, depth + 1, allowParts) ?: return null
        return found(f, mul(c, inner.value), "Constant multiple rule", "Move the constant ${plain(c)} in front of the integral.",
            listOf(inner.step), listOf(r.scaled(c, I(g))))
    }

    // ---- standard integrals -----------------------------------------------------------------

    private fun table(f: Sym): Found? {
        val v = xv
        val n = v()
        fun std(value: Sym, rule: String) = found(f, value, "Standard integral", rule)
        return when {
            f == v -> found(f, mul(HALF, pow(v, TWO)), "Power rule", "∫ $n dx = $n²/2.")
            f is Sym.Pow && f.base == v && !f.exp.dependsOn(x) ->
                if (f.exp == MINUS_ONE) {
                    std(fn("ln", fn("abs", v)), "∫ 1/$n dx = ln|$n|.")
                } else {
                    val k = add(f.exp, ONE)
                    found(f, div(pow(v, k), k), "Power rule", "∫ $n^k dx = $n^(k+1)/(k+1), here with k = ${plain(f.exp)}.")
                }
            f is Sym.Pow && f.base == E && f.exp == v -> std(f, "∫ e^$n dx = e^$n.")
            f is Sym.Pow && f.exp == v && !f.base.dependsOn(x) -> std(div(f, fn("ln", f.base)), "∫ a^$n dx = a^$n / ln(a).")
            f is Sym.Fn && f.arg == v -> when (f.name) {
                "sin" -> std(neg(fn("cos", v)), "∫ sin($n) dx = −cos($n).")
                "cos" -> std(fn("sin", v), "∫ cos($n) dx = sin($n).")
                "tan" -> std(neg(fn("ln", fn("abs", fn("cos", v)))), "∫ tan($n) dx = −ln|cos($n)|.")
                "sinh" -> std(fn("cosh", v), "∫ sinh($n) dx = cosh($n).")
                "cosh" -> std(fn("sinh", v), "∫ cosh($n) dx = sinh($n).")
                "tanh" -> std(fn("ln", fn("cosh", v)), "∫ tanh($n) dx = ln(cosh($n)).")
                "abs" -> std(mul(HALF, v, fn("abs", v)), "∫ |$n| dx = $n|$n|/2.")
                else -> null
            }
            f is Sym.Pow -> {
                val b = f.base
                val e = f.exp
                when {
                    e == num(-2) && b == fn("cos", v) -> std(fn("tan", v), "∫ 1/cos²($n) dx = tan($n).")
                    e == num(-2) && b == fn("sin", v) -> std(neg(div(fn("cos", v), fn("sin", v))), "∫ 1/sin²($n) dx = −cos($n)/sin($n).")
                    e == num(-2) && b == fn("cosh", v) -> std(fn("tanh", v), "∫ 1/cosh²($n) dx = tanh($n).")
                    e == MINUS_ONE && b == fn("cos", v) ->
                        std(fn("ln", fn("abs", add(fn("tan", v), pow(fn("cos", v), MINUS_ONE)))), "∫ 1/cos($n) dx = ln|tan($n) + 1/cos($n)|.")
                    e == MINUS_ONE && b == fn("sin", v) ->
                        std(fn("ln", fn("abs", fn("tan", mul(HALF, v)))), "∫ 1/sin($n) dx = ln|tan($n/2)|.")
                    e == MINUS_ONE && b == add(ONE, pow(v, TWO)) -> std(fn("atan", v), "∫ 1/(1 + $n²) dx = tan⁻¹($n).")
                    e == rat(-1, 2) && b == sub(ONE, pow(v, TWO)) -> std(fn("asin", v), "∫ 1/√(1 − $n²) dx = sin⁻¹($n).")
                    e == rat(-1, 2) && b == add(pow(v, TWO), ONE) -> std(fn("asinh", v), "∫ 1/√($n² + 1) dx = sinh⁻¹($n).")
                    e == rat(-1, 2) && b == sub(pow(v, TWO), ONE) ->
                        std(fn("ln", fn("abs", add(v, sqrt(sub(pow(v, TWO), ONE))))), "∫ 1/√($n² − 1) dx = ln|$n + √($n² − 1)|.")
                    else -> pureQuadratic(b)?.let { (qb, qc) -> quadraticTable(f, qb, qc) }
                }
            }
            else -> null
        }
    }

    /** b and c when [s] is b·x² + c, with b and c free of x. */
    private fun pureQuadratic(s: Sym): Pair<Sym, Sym>? {
        if (s !is Sym.Add) return null
        val (xs, cs) = s.terms.partition { it.dependsOn(x) }
        if (xs.size != 1 || cs.isEmpty()) return null
        val b = div(xs[0], pow(xv, TWO))
        return if (b.dependsOn(x)) null else b to add(cs)
    }

    /** √s for a constant, taking letters as positive as a table of integrals does, so √(A²) = A. */
    private fun root(s: Sym): Sym = when {
        s is Sym.Pow && s.exp.rational?.isInteger == true -> pow(s.base, mul(s.exp, HALF))
        s is Sym.Mul -> mul(s.factors.map(::root))
        else -> sqrt(s)
    }

    /** 1/(bx² + c) with letters, and 1/√(bx² + c) and √(bx² + c), from the table. */
    private fun quadraticTable(f: Sym.Pow, b: Sym, c: Sym): Found? {
        val n = v()
        val q = f.base
        val br = b.rational
        val cr = c.rational
        // Signs come from the number in front, so −A² counts as negative: √ is only taken of positive terms.
        val sb = splitCoefficient(b).first.signum
        val sc = splitCoefficient(c).first.signum
        // With b < 0 the root is a circle-type √(c − |b|x²), which needs c > 0 and gives sin⁻¹.
        val circle = sb < 0
        val m = if (circle) neg(b) else b
        val asin = fn("asin", mul(xv, root(div(m, c))))
        val log = fn("ln", fn("abs", add(mul(root(m), xv), sqrt(q))))
        val title = "Standard integral"
        return when (f.exp) {
            // Rational coefficients are done by partial fractions or completing the square instead.
            MINUS_ONE -> when {
                br != null && cr != null -> null
                sb > 0 && sc > 0 ->
                    found(f, div(fn("atan", mul(xv, root(div(b, c)))), root(mul(b, c))), title, "∫ 1/(b$n² + c) dx = tan⁻¹($n√(b/c))/√(bc).")
                sb != sc -> {
                    // 1/(B$n² − K) with B, K > 0; when b is the negative one, 1/(b$n² + c) = −1/(B$n² − K).
                    val big = if (sb > 0) b else neg(b)
                    val k = if (sb > 0) neg(c) else c
                    val rb = root(big)
                    val rk = root(k)
                    val ratio = fn("ln", fn("abs", div(sub(mul(rb, xv), rk), add(mul(rb, xv), rk))))
                    val value = div(ratio, mul(TWO, root(mul(big, k))))
                    found(f, if (sb > 0) value else neg(value), title, "∫ 1/(B$n² − K) dx = ln|(√B·$n − √K)/(√B·$n + √K)|/(2√(BK)).")
                }
                else -> null
            }
            rat(-1, 2) -> when {
                circle && sc > 0 -> found(f, div(asin, root(m)), title, "∫ 1/√(c − b$n²) dx = sin⁻¹($n√(b/c))/√b.")
                circle -> null
                else -> found(f, div(log, root(m)), title, "∫ 1/√(b$n² + c) dx = ln|√b·$n + √(b$n² + c)|/√b.")
            }
            HALF -> {
                val half = mul(HALF, xv, sqrt(q))
                when {
                    circle && sc > 0 -> found(f, add(half, mul(div(c, mul(TWO, root(m))), asin)), title, "∫ √(c − b$n²) dx = $n√(c − b$n²)/2 + c·sin⁻¹($n√(b/c))/(2√b).")
                    circle -> null
                    else -> found(f, add(half, mul(div(c, mul(TWO, root(m))), log)), title, "∫ √(b$n² + c) dx = $n√(b$n² + c)/2 + c·ln|√b·$n + √(b$n² + c)|/(2√b).")
                }
            }
            else -> null
        }
    }

    /** √(ax² + bx + c) and its reciprocal: complete the square, then a shift gives a standard integral. */
    private fun squareUnderRoot(f: Sym, depth: Int): Found? {
        if (f !is Sym.Pow || (f.exp != HALF && f.exp != rat(-1, 2))) return null
        val p = Poly.of(f.base, x) ?: return null
        if (p.degree != 2 || p[1].isZero) return null
        val a = p[2]
        val h = p[1] / (BigRational.TWO * a)
        val k = p[0] - p[1] * p[1] / (BigRational.of(4) * a)
        if (k.isZero) return null
        val square = add(mul(Sym.Num(a), pow(add(xv, Sym.Num(h)), TWO)), Sym.Num(k))
        val g = pow(square, f.exp)
        val inner = integ(g, depth + 1, false) ?: return null
        return found(f, inner.value, "Complete the square", "${plain(f.base)} = ${plain(square)}.", listOf(inner.step), listOf(I(g)))
    }

    /** x²·(bx² + c)^(k/2): write x² = ((bx² + c) − c)/b, which leaves integrals of powers of the root. */
    private fun splitSquare(f: Sym, depth: Int, allowParts: Boolean): Found? {
        if (f !is Sym.Mul) return null
        val square = f.factors.firstOrNull { it is Sym.Pow && it.base == xv && (it.exp.int ?: 0) >= 2 } ?: return null
        val root = f.factors.firstOrNull { it is Sym.Pow && it.exp.rational?.isInteger == false && pureQuadratic(it.base) != null } as? Sym.Pow
            ?: return null
        val (b, c) = pureQuadratic(root.base)!!
        val rest = div(f, pow(xv, TWO))
        val g = add(div(mul(rest, root.base), b), neg(div(mul(rest, c), b)))
        if (square == ONE || g == f) return null
        val inner = integ(g, depth + 1, allowParts) ?: return null
        return found(f, inner.value, "Rewrite the integrand", "Write ${v()}² = ((${plain(root.base)}) − ${plain(c)})/${plain(b)}.", listOf(inner.step), listOf(I(g)))
    }

    // ---- u-substitution -------------------------------------------------------------------

    /**
     * u-substitution. Writing x = eᵘ or x = ln u is a last resort ([late]), tried after parts, which
     * is how x³·ln x and the like are taught.
     */
    private fun substitution(f: Sym, depth: Int, late: Boolean): Found? {
        // A fresh name: never the variable we integrate in, nor a letter already in the integrand.
        val used = f.otherVariables(x) + x
        val u = listOf("u", "w", "t", "s", "z", "p", "q").first { it !in used }
        val uv = Sym.Var(u)
        // Besides the integrand's own parts: u = √x for roots of x, and u = xᵐ⁺¹ when xᵐ multiplies
        // the integrand, as in x/(x⁴ + 1) with u = x².
        val extra = ArrayList<Sym>()
        for (s in f.subexpressions()) {
            val e = (s as? Sym.Pow)?.takeIf { it.base == xv }?.exp?.rational
            if (e != null && !e.isInteger) extra += pow(xv, Sym.Num(BigRational.of(java.math.BigInteger.ONE, e.den)))
        }
        for (k in if (f is Sym.Mul) f.factors else listOf(f)) {
            val m = if (k == xv) 1 else (k as? Sym.Pow)?.takeIf { it.base == xv }?.exp?.int ?: 0
            if (m >= 1) extra += pow(xv, num(m + 1L))
        }
        // u = √(x² − 1) rather than u = 1/√(x² − 1): substituting a reciprocal is rarely what is meant.
        val own = f.subexpressions()
            .filter { it.dependsOn(x) && it != xv && it != f && it !is Sym.Num }
            .map { g -> if (g is Sym.Pow && (g.exp.rational?.signum ?: 0) < 0) pow(g.base, neg(g.exp)) else g }
            .sortedByDescending { it.size }
            .take(14)
        val candidates = (extra + own).distinct().filter { it != xv }
        for (g in candidates) {
            val dg = diff.d(g)
            if (dg.isZero) continue
            var q = div(f, dg).subst(g, uv)
            var xInU: Sym? = null
            if (q.dependsOn(x)) {
                // The x left over is written in terms of u when g is easy to invert.
                val inverse = inverse(g, uv) ?: continue
                if (inverse.late != late) continue
                // Shifting a rational function only moves it around; partial fractions do it directly.
                if (inverse.linear && rationalParts(f) != null) continue
                q = writeIn(q, inverse.power) ?: continue
                if (q.dependsOn(x)) continue
                xInU = inverse.power(BigRational.ONE)
            } else if (late) {
                continue
            }
            val inner = Integrator(u, budget).integ(q, depth + 1, true) ?: continue
            val value = inner.value.subst(uv, g)
            val ru = Render(u)
            val du = MathRow(listOf(TokenNode(Render.label("d$u")), TokenNode(com.l0gic0l.casiocalc.math.editor.Tokens.EQ)) +
                (if (dg.isOne) emptyList() else r.nodes(dg)) + TokenNode(Render.label("d${v()}")))
            val back = Step("Substitute back", "Replace $u with ${plain(g)}.", listOf(r.equation(ru.row(inner.value), r.row(value))))
            val step = Step(
                "Substitution",
                "Let $u = ${plain(g)}. Then d$u = ${if (dg.isOne) "" else plain(dg) + " "}d${v()}" + (xInU?.let { ", and ${v()} = ${plain(it)}" } ?: "") +
                    ", which turns the integral into one in $u.",
                listOf(r.define(u, g), du, r.equation(I(f), ru.integral(q, u))),
                listOf(inner.step, back),
            )
            return Found(value, step)
        }
        return null
    }

    /** x^r in terms of u for a u = g(x) that is easy to invert. [linear] marks u = ax + b. */
    private class Inverse(val linear: Boolean, val power: (BigRational) -> Sym?, val late: Boolean = false)

    /** The inverse of g = a·xⁿ + b, ln x or e^(cx), or null for anything else. */
    private fun inverse(g: Sym, u: Sym): Inverse? {
        if (g is Sym.Fn && g.name == "ln" && g.arg == xv) return Inverse(false, { r -> exp(mul(Sym.Num(r), u)) }, late = true)
        if (g is Sym.Pow && g.base == E) {
            val c = div(g.exp, xv)
            if (c.dependsOn(x)) return null
            return Inverse(false, { r -> if (r.isInteger) pow(div(fn("ln", u), c), Sym.Num(r)) else null }, late = true)
        }
        val (xs, cs) = (if (g is Sym.Add) g.terms else listOf(g)).partition { it.dependsOn(x) }
        if (xs.size != 1) return null
        val (xf, af) = (if (xs[0] is Sym.Mul) (xs[0] as Sym.Mul).factors else xs).partition { it.dependsOn(x) }
        if (xf.size != 1) return null
        val p = xf[0]
        val n = when {
            p == xv -> BigRational.ONE
            p is Sym.Pow && p.base == xv -> p.exp.rational ?: return null
            else -> return null
        }
        val base = div(sub(u, add(cs)), mul(af))
        return Inverse(n == BigRational.ONE, { r -> (r / n).takeIf { it.isInteger }?.let { pow(base, Sym.Num(it)) } })
    }

    /** [s] with every power of x replaced by [power]; null if some power has no replacement. */
    private fun writeIn(s: Sym, power: (BigRational) -> Sym?): Sym? {
        if (!s.dependsOn(x)) return s
        if (s == xv) return power(BigRational.ONE)
        return when (s) {
            is Sym.Pow -> if (s.base == xv && s.exp is Sym.Num) power(s.exp.q) else
                pow(writeIn(s.base, power) ?: return null, writeIn(s.exp, power) ?: return null)
            is Sym.Add -> add(s.terms.map { writeIn(it, power) ?: return null })
            is Sym.Mul -> mul(s.factors.map { writeIn(it, power) ?: return null })
            is Sym.Fn -> fn(s.name, writeIn(s.arg, power) ?: return null)
            else -> null
        }
    }

    // ---- rational functions -----------------------------------------------------------------

    /** Numerator and denominator polynomials when [f] is a quotient of polynomials with rational coefficients. */
    private fun rationalParts(f: Sym): Pair<Poly, Poly>? {
        val factors = if (f is Sym.Mul) f.factors else listOf(f)
        val top = ArrayList<Sym>()
        val bottom = ArrayList<Sym>()
        for (k in factors) {
            val e = (k as? Sym.Pow)?.exp?.int
            if (k is Sym.Pow && e != null && e < 0 && k.base.dependsOn(x)) bottom += pow(k.base, num(-e.toLong())) else top += k
        }
        if (bottom.isEmpty()) return null
        val n = Poly.of(mul(top), x) ?: return null
        val d = Poly.of(mul(bottom), x) ?: return null
        return if (d.degree < 1) null else n to d
    }

    private fun rational(f: Sym, depth: Int): Found? {
        val (n, d) = rationalParts(f) ?: return null
        val children = ArrayList<Step>()
        val pieces = ArrayList<Sym>()
        var value: Sym = ZERO
        var rem = n
        if (n.degree >= d.degree) {
            val (q, rr) = n.divRem(d)
            val qs = q.toSym(x)
            val inner = integ(qs, depth + 1, false) ?: return null
            value = add(value, inner.value)
            pieces += qs
            rem = rr
            children += Step("Polynomial long division", "The numerator's degree is at least the denominator's, so divide first.",
                listOf(r.equation(r.row(f), r.row(add(qs, div(rr.toSym(x), d.toSym(x)))))), listOf(inner.step))
        }
        if (!rem.isZero) {
            val pf = partialFractions(rem, d) ?: return null
            if (pf.size > 1) {
                children += Step("Partial fractions", "Split the fraction into simpler fractions whose denominators are the factors of ${plain(d.toSym(x))}.",
                    listOf(r.equation(r.row(div(rem.toSym(x), d.toSym(x))), r.row(add(pf.map { it.first })))))
            }
            for ((piece, quadratic) in pf) {
                val inner = if (quadratic != null) quadraticPiece(piece, quadratic) else integ(piece, depth + 1, false)
                inner ?: return null
                value = add(value, inner.value)
                children += inner.step
            }
        }
        return found(f, value, "Rational function", "Integrate the pieces one at a time.", children)
    }

    /** Pieces A/(x − r)ʲ and (Bx + C)/q, the second with q, of the partial fraction decomposition of n/d. */
    private fun partialFractions(n: Poly, d: Poly): List<Pair<Sym, Poly?>>? {
        val lead = d.lead
        val dm = d.scale(lead.reciprocal())
        val nm = n.scale(lead.reciprocal())
        val (roots, rest) = dm.factorRational()
        if (rest.degree > 2) return null
        if (rest.degree == 2) {
            val disc = rest[1] * rest[1] - BigRational.of(4) * rest[0] * rest[2]
            if (disc.signum >= 0) return null
        }
        val basis = ArrayList<Poly>()
        val shapes = ArrayList<(BigRational, BigRational?) -> Pair<Sym, Poly?>>()
        for ((root, m) in roots) {
            val lin = Poly(listOf(-root, BigRational.ONE))
            for (j in 1..m) {
                var div = dm
                repeat(j) { div = div.divRem(lin).first }
                basis += div
                shapes += { a, _ -> mul(Sym.Num(a), pow(lin.toSym(x), num(-j.toLong()))) to null }
            }
        }
        if (rest.degree == 2) {
            val other = dm.divRem(rest).first
            basis += other.shift(1)
            basis += other
        }
        val size = dm.degree
        if (basis.size != size) return null
        val a = List(size) { k -> basis.map { it[k] } }
        val b = List(size) { k -> nm[k] }
        val sol = solveLinear(a, b) ?: return null
        val out = ArrayList<Pair<Sym, Poly?>>()
        for (i in shapes.indices) if (!sol[i].isZero) out += shapes[i](sol[i], null)
        if (rest.degree == 2) {
            val bq = sol[size - 2]
            val cq = sol[size - 1]
            val monic = rest.scale(rest.lead.reciprocal())
            val scale = rest.lead.reciprocal()
            if (!bq.isZero || !cq.isZero) {
                out += mul(add(mul(Sym.Num(bq * scale), xv), Sym.Num(cq * scale)), pow(monic.toSym(x), MINUS_ONE)) to monic
            }
        }
        return out
    }

    /** ∫ (Bx + C)/(x² + px + q) dx for an irreducible monic quadratic, by completing the square. */
    private fun quadraticPiece(piece: Sym, q: Poly): Found? {
        val p = q[1]
        val c0 = q[0]
        val num = Poly.of(mul(piece, q.toSym(x)), x) ?: return null
        val b = num[1]
        val c = num[0]
        val k = BigRational.of(4) * c0 - p * p
        val root = sqrt(Sym.Num(k))
        val logPart = mul(Sym.Num(b / BigRational.TWO), fn("ln", q.toSym(x)))
        val atanCoef = mul(Sym.Num((c - b * p / BigRational.TWO) * BigRational.TWO), pow(root, MINUS_ONE))
        val atanPart = mul(atanCoef, fn("atan", div(add(mul(TWO, xv), Sym.Num(p)), root)))
        val value = add(logPart, atanPart)
        return found(piece, value, "Complete the square",
            "Write the numerator as a multiple of the denominator's derivative plus a constant; the first part gives a logarithm and the rest gives tan⁻¹ after completing the square.")
    }

    // ---- integration by parts ---------------------------------------------------------------

    private class Lin(val value: Sym, val self: BigRational, val step: Step)

    private fun parts(f: Sym, depth: Int): Found? {
        val lin = partsLin(f, depth, f, 0) ?: return null
        if (lin.self.isZero) return Found(lin.value, lin.step)
        val k = BigRational.ONE - lin.self
        if (k.isZero) return null
        val value = mul(lin.value, Sym.Num(k.reciprocal()))
        val step = Step("Solve for the integral",
            "The original integral appeared again on the right-hand side. Moving it to the left gives ${k} times the integral, so divide by ${k}.",
            listOf(r.equation(I(f), r.row(value))), listOf(lin.step))
        return Found(value, step)
    }

    /** LIATE: logarithms, inverse trigonometric, algebraic, trigonometric, exponential. */
    private fun liate(s: Sym): Int = when {
        // A whole power of a logarithm or inverse function, such as (ln x)², ranks with the function.
        s is Sym.Pow && s.base is Sym.Fn && (s.exp.int ?: 0) > 0 && liate(s.base) <= 1 -> liate(s.base)
        s is Sym.Fn && (s.name == "ln" || s.name == "log") -> 0
        s is Sym.Fn && s.name in setOf("asin", "acos", "atan", "asinh", "acosh", "atanh") -> 1
        Poly.of(s, x) != null -> 2
        s is Sym.Fn && s.name in setOf("sin", "cos", "tan", "sinh", "cosh") -> 3
        s is Sym.Pow && !s.base.dependsOn(x) -> 4
        else -> 5
    }

    private fun partsLin(f: Sym, depth: Int, target: Sym, level: Int): Lin? {
        if (level > 2 || depth > MAX_DEPTH || --budget.calls < 0) return null
        val (c, g) = run {
            val cs = if (f is Sym.Mul) mul(f.factors.filter { !it.dependsOn(x) }) else ONE
            cs to div(f, cs)
        }
        if (!c.isOne) {
            val k = c.rational ?: return null
            val inner = partsLin(g, depth, target, level) ?: return null
            return Lin(mul(c, inner.value), inner.self * k, inner.step)
        }
        val factors = if (g is Sym.Mul) g.factors else listOf(g)
        val options = ArrayList<Pair<Sym, Sym>>()
        if (factors.size == 1 && liate(g) <= 1) options += g to ONE
        for (u in factors.sortedBy { liate(it) }) {
            if (factors.size > 1 && liate(u) < 5) options += u to div(g, u)
        }
        for ((u, dv) in options.take(2)) {
            val vFound = integ(dv, depth + 1, false) ?: continue
            val v = vFound.value
            val du = diff.d(u)
            val uv = mul(u, v)
            val rem = mul(v, du)
            val setup = listOf(r.definePair("u", u, "dv", dv), r.definePair("du", du, "v", v),
                r.equation(I(g), MathRow(r.row(uv).nodes + TokenNode(com.l0gic0l.casiocalc.math.editor.Tokens.SUB) + I(rem).nodes)))
            val title = "Integration by parts"
            val text = "∫ u dv = uv − ∫ v du with u = ${plain(u)} and dv = ${plain(dv)} d${v()}."
            val ratio = div(rem, target).rational
            if (ratio != null) {
                return Lin(uv, -ratio, Step(title, "$text The integral on the right is a multiple of the original one.", setup, listOf(vFound.step)))
            }
            val cyclic = isExpTrig(rem)
            if (!cyclic) {
                val inner = integ(rem, depth + 1, true)
                if (inner != null) {
                    val value = tidy(sub(uv, inner.value))
                    return Lin(value, BigRational.ZERO, Step(title, text, setup + r.equation(I(g), r.row(value)), listOf(vFound.step, inner.step)))
                }
            }
            val again = partsLin(rem, depth + 1, target, level + 1)
            if (again != null) {
                return Lin(sub(uv, again.value), -again.self, Step(title, text, setup, listOf(vFound.step, again.step)))
            }
        }
        return null
    }

    private fun isExpTrig(s: Sym): Boolean {
        val all = s.subexpressions()
        return all.any { it is Sym.Pow && !it.base.dependsOn(x) && it.exp.dependsOn(x) } &&
            all.any { it is Sym.Fn && it.name in setOf("sin", "cos") }
    }

    // ---- trigonometric identities ---------------------------------------------------------

    private fun trig(f: Sym, depth: Int, allowParts: Boolean): Found? {
        var why = ""
        var title = "Trigonometric identity"
        fun rewrite(s: Sym): Sym = when (s) {
            is Sym.Add -> add(s.terms.map(::rewrite))
            is Sym.Mul -> mul(s.factors.map(::rewrite))
            is Sym.Pow -> {
                val b = s.base
                val n = s.exp.int
                if (b is Sym.Fn && n != null && n >= 2 && b.dependsOn(x)) {
                    val u = b.arg
                    when (b.name) {
                        "sin" -> if (n % 2 == 0) {
                            why = "Use sin²u = (1 − cos 2u)/2."
                            pow(mul(HALF, sub(ONE, fn("cos", mul(TWO, u)))), num((n / 2).toLong()))
                        } else {
                            why = "Use sin²u = 1 − cos²u to leave one sin u for a substitution."
                            mul(b, pow(sub(ONE, pow(fn("cos", u), TWO)), num(((n - 1) / 2).toLong())))
                        }
                        "cos" -> if (n % 2 == 0) {
                            why = "Use cos²u = (1 + cos 2u)/2."
                            pow(mul(HALF, add(ONE, fn("cos", mul(TWO, u)))), num((n / 2).toLong()))
                        } else {
                            why = "Use cos²u = 1 − sin²u to leave one cos u for a substitution."
                            mul(b, pow(sub(ONE, pow(fn("sin", u), TWO)), num(((n - 1) / 2).toLong())))
                        }
                        "tan" -> {
                            why = "Use tan²u = 1/cos²u − 1."
                            mul(pow(b, num((n - 2).toLong())), sub(pow(fn("cos", u), num(-2)), ONE))
                        }
                        "sinh" -> {
                            title = "Hyperbolic identity"
                            if (n % 2 == 0) {
                                why = "Use sinh²u = (cosh 2u − 1)/2."
                                pow(mul(HALF, sub(fn("cosh", mul(TWO, u)), ONE)), num((n / 2).toLong()))
                            } else {
                                why = "Use sinh²u = cosh²u − 1 to leave one sinh u for a substitution."
                                mul(b, pow(sub(pow(fn("cosh", u), TWO), ONE), num(((n - 1) / 2).toLong())))
                            }
                        }
                        "cosh" -> {
                            title = "Hyperbolic identity"
                            if (n % 2 == 0) {
                                why = "Use cosh²u = (cosh 2u + 1)/2."
                                pow(mul(HALF, add(fn("cosh", mul(TWO, u)), ONE)), num((n / 2).toLong()))
                            } else {
                                why = "Use cosh²u = 1 + sinh²u to leave one cosh u for a substitution."
                                mul(b, pow(add(ONE, pow(fn("sinh", u), TWO)), num(((n - 1) / 2).toLong())))
                            }
                        }
                        else -> s
                    }
                } else s
            }
            else -> s
        }
        // With an odd power, only that factor is rewritten; the others stay powers of the function
        // the substitution will use, as in sin³x·cos²x = sin x·(1 − cos²x)·cos²x.
        fun oddPower(s: Sym, names: Set<String>) = s is Sym.Pow && s.base is Sym.Fn && (s.base as Sym.Fn).name in names &&
            s.base.dependsOn(x) && (s.exp.int ?: 0).let { it >= 3 && it % 2 == 1 }
        val factors = if (f is Sym.Mul) f.factors else listOf(f)
        val odd = factors.firstOrNull { oddPower(it, setOf("sin", "sinh")) } ?: factors.firstOrNull { oddPower(it, setOf("cos", "cosh")) }
        val g = if (odd != null) mul(factors.map { if (it === odd) rewrite(it) else it }) else rewrite(f)
        if (g == f) return null
        val e = expand(g)
        val inner = integ(e, depth + 1, allowParts) ?: return null
        return found(f, inner.value, title, why, listOf(inner.step), listOf(I(e)))
    }

    /** sin a·cos b, sin a·sin b and cos a·cos b with different angles, by the product-to-sum formulas. */
    private fun productToSum(f: Sym, depth: Int): Found? {
        if (f !is Sym.Mul) return null
        val (trigs, rest) = f.factors.partition { it is Sym.Fn && it.name in setOf("sin", "cos") && it.dependsOn(x) }
        if (trigs.size != 2 || rest.any { it.dependsOn(x) }) return null
        val a = trigs[0] as Sym.Fn
        val b = trigs[1] as Sym.Fn
        if (a.arg == b.arg) return null
        val p = add(a.arg, b.arg)
        val m = sub(a.arg, b.arg)
        val (g, why) = when {
            a.name == "sin" && b.name == "cos" -> mul(HALF, add(fn("sin", p), fn("sin", m))) to "Use sin a·cos b = (sin(a + b) + sin(a − b))/2."
            a.name == "cos" && b.name == "sin" -> mul(HALF, sub(fn("sin", p), fn("sin", m))) to "Use cos a·sin b = (sin(a + b) − sin(a − b))/2."
            a.name == "sin" -> mul(HALF, sub(fn("cos", m), fn("cos", p))) to "Use sin a·sin b = (cos(a − b) − cos(a + b))/2."
            else -> mul(HALF, add(fn("cos", m), fn("cos", p))) to "Use cos a·cos b = (cos(a − b) + cos(a + b))/2."
        }
        val e = expand(mul(mul(rest), g))
        val inner = integ(e, depth + 1, false) ?: return null
        return found(f, inner.value, "Trigonometric identity", why, listOf(inner.step), listOf(I(e)))
    }

    private fun expanded(f: Sym, depth: Int, allowParts: Boolean): Found? {
        val e = expand(f)
        if (e == f || e !is Sym.Add) return null
        val inner = integ(e, depth + 1, allowParts) ?: return null
        return found(f, inner.value, "Expand", "Multiply out the brackets, then integrate term by term.", listOf(inner.step), listOf(I(e)))
    }

    private fun v() = x.lowercase()

    companion object {
        private const val MAX_DEPTH = 9

        /** Stands for whichever variable an integral is in, when comparing integrals. */
        private val ANY = Sym.Var("§")

        /**
         * Checks F′ = f numerically at several points (with any parameters set to sample values).
         * Returns false if they disagree anywhere both are defined, or if too few points could be checked.
         */
        fun verify(f: Sym, antiderivative: Sym, x: String): Boolean {
            val params = (f.otherVariables(x) + antiderivative.otherVariables(x)).toList()
            var ff = f
            var fa = antiderivative
            params.forEachIndexed { i, p ->
                val value = rat(17 + 6L * i, 10)
                ff = ff.subst(Sym.Var(p), value)
                fa = fa.subst(Sym.Var(p), value)
            }
            val points = listOf(0.37, 0.81, 1.23, 1.94, 2.61, -0.43, -1.37, 0.12, 3.3, -2.2)
            // Further out, for answers such as √(x − 5) that are only defined there.
            val further = listOf(4.7, 6.3, 8.9, 12.4, 25.1, 51.7, -4.9, -8.3, -13.1)
            var checked = 0
            for (t in points + further) {
                if (checked >= 3 && t in further) break
                val expected = ff.eval(x, t)
                if (!expected.isFinite()) continue
                val h = 1e-5 * maxOf(1.0, Math.abs(t))
                val f1 = fa.eval(x, t + h)
                val f0 = fa.eval(x, t - h)
                val f2 = fa.eval(x, t + 2 * h)
                val fm = fa.eval(x, t - 2 * h)
                if (!f1.isFinite() || !f0.isFinite() || !f2.isFinite() || !fm.isFinite()) continue
                val slope = (8 * (f1 - f0) - (f2 - fm)) / (12 * h)
                if (Math.abs(slope - expected) > 1e-5 * maxOf(1.0, Math.abs(expected))) return false
                checked++
            }
            return checked >= 3
        }
    }
}
