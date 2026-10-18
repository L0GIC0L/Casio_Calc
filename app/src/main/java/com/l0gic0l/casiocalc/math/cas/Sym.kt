package com.l0gic0l.casiocalc.math.cas

import com.l0gic0l.casiocalc.math.num.BigRational
import java.math.BigInteger

/**
 * A symbolic expression for the calculus solvers. Instances are always built through the
 * functions in this file ([add], [mul], [pow], [fn]), which keep them in one canonical form:
 * sums and products are flat and sorted, numbers are folded, like terms and like factors are
 * collected. Two equal expressions therefore compare equal with ==.
 *
 * Subtraction is a sum with a −1 coefficient, division is a power with a negative exponent,
 * √u is u^(1/2) and eᵘ is a power of [E].
 */
sealed class Sym {
    data class Num(val q: BigRational) : Sym() {
        override fun toString() = q.toString()
    }

    /** π or e. */
    data class Const(val name: String) : Sym() {
        override fun toString() = name
    }

    data class Var(val name: String) : Sym() {
        override fun toString() = name
    }

    data class Add(val terms: List<Sym>) : Sym() {
        override fun toString() = terms.joinToString(" + ", "(", ")")
    }

    data class Mul(val factors: List<Sym>) : Sym() {
        override fun toString() = factors.joinToString("·", "(", ")")
    }

    data class Pow(val base: Sym, val exp: Sym) : Sym() {
        override fun toString() = "$base^$exp"
    }

    /** sin, cos, tan, asin, acos, atan, sinh, cosh, tanh, asinh, acosh, atanh, ln, log, abs. */
    data class Fn(val name: String, val arg: Sym) : Sym() {
        override fun toString() = "$name[$arg]"
    }
}

val ZERO = Sym.Num(BigRational.ZERO)
val ONE = Sym.Num(BigRational.ONE)
val TWO = Sym.Num(BigRational.TWO)
val MINUS_ONE = Sym.Num(-BigRational.ONE)
val HALF = Sym.Num(BigRational.HALF)
val PI = Sym.Const("pi")
val E = Sym.Const("e")

fun num(n: Long): Sym = Sym.Num(BigRational.of(n))
fun num(q: BigRational): Sym = Sym.Num(q)
fun rat(n: Long, d: Long): Sym = Sym.Num(BigRational.of(n, d))

val Sym.isZero get() = this is Sym.Num && q.isZero
val Sym.isOne get() = this is Sym.Num && q == BigRational.ONE

/** The rational value, if this is a number. */
val Sym.rational: BigRational? get() = (this as? Sym.Num)?.q

/** The integer value, if this is a whole number of reasonable size. */
val Sym.int: Int? get() = (this as? Sym.Num)?.q?.takeIf { it.isInteger && it.num.bitLength() < 31 }?.num?.toInt()

fun Sym.dependsOn(x: String): Boolean = when (this) {
    is Sym.Var -> name == x
    is Sym.Num, is Sym.Const -> false
    is Sym.Add -> terms.any { it.dependsOn(x) }
    is Sym.Mul -> factors.any { it.dependsOn(x) }
    is Sym.Pow -> base.dependsOn(x) || exp.dependsOn(x)
    is Sym.Fn -> arg.dependsOn(x)
}

/** Number of nodes, a rough measure of how complicated an expression is. */
val Sym.size: Int
    get() = when (this) {
        is Sym.Num, is Sym.Const, is Sym.Var -> 1
        is Sym.Add -> 1 + terms.sumOf { it.size }
        is Sym.Mul -> 1 + factors.sumOf { it.size }
        is Sym.Pow -> 1 + base.size + exp.size
        is Sym.Fn -> 1 + arg.size
    }

/** Replaces every occurrence of [target] with [by], rebuilding in canonical form. */
fun Sym.subst(target: Sym, by: Sym): Sym {
    if (this == target) return by
    return when (this) {
        is Sym.Num, is Sym.Const, is Sym.Var -> this
        is Sym.Add -> add(terms.map { it.subst(target, by) })
        is Sym.Mul -> mul(factors.map { it.subst(target, by) })
        is Sym.Pow -> {
            // u = x² also matches x⁴ = (x²)², and u = eˣ matches e²ˣ and e⁻ˣ.
            val t = target as? Sym.Pow
            if (t != null && t.base == base) {
                val k = div(exp, t.exp).rational
                if (k != null && k.isInteger && k.signum != 0) return pow(by, Sym.Num(k))
            }
            pow(base.subst(target, by), exp.subst(target, by))
        }
        is Sym.Fn -> fn(name, arg.subst(target, by))
    }
}

/** Every distinct subexpression, children first. */
fun Sym.subexpressions(): List<Sym> {
    val out = LinkedHashSet<Sym>()
    fun visit(s: Sym) {
        when (s) {
            is Sym.Add -> s.terms.forEach(::visit)
            is Sym.Mul -> s.factors.forEach(::visit)
            is Sym.Pow -> { visit(s.base); visit(s.exp) }
            is Sym.Fn -> visit(s.arg)
            else -> {}
        }
        out += s
    }
    visit(this)
    return out.toList()
}

// ---------------------------------------------------------------------------------------------
// Canonical constructors

private val maxPowBits = 4096

/** Sort order inside sums and products: numbers first, then constants, variables, powers, functions, sums. */
private fun rank(s: Sym): Int = when (s) {
    is Sym.Num -> 0
    is Sym.Const -> 1
    is Sym.Var -> 2
    is Sym.Pow -> 3
    is Sym.Fn -> 4
    is Sym.Mul -> 5
    is Sym.Add -> 6
}

private val order = Comparator<Sym> { a, b ->
    val r = rank(a).compareTo(rank(b))
    if (r != 0) r else a.toString().compareTo(b.toString())
}

/** Splits a term into its numeric coefficient and the rest: 3x² → (3, x²). */
fun splitCoefficient(s: Sym): Pair<BigRational, Sym> = when {
    s is Sym.Num -> s.q to ONE
    s is Sym.Mul && s.factors.first() is Sym.Num -> {
        val c = (s.factors.first() as Sym.Num).q
        val rest = s.factors.drop(1)
        c to (if (rest.size == 1) rest[0] else Sym.Mul(rest))
    }
    else -> BigRational.ONE to s
}

fun add(vararg terms: Sym): Sym = add(terms.toList())

fun add(terms: List<Sym>): Sym {
    val flat = ArrayList<Sym>()
    for (t in terms) if (t is Sym.Add) flat += t.terms else flat += t
    var constant = BigRational.ZERO
    val groups = LinkedHashMap<Sym, BigRational>()
    for (t in flat) {
        if (t is Sym.Num) {
            constant += t.q
            continue
        }
        val (c, rest) = splitCoefficient(t)
        groups[rest] = (groups[rest] ?: BigRational.ZERO) + c
    }
    val out = ArrayList<Sym>()
    if (!constant.isZero) out += Sym.Num(constant)
    for ((rest, c) in groups) {
        if (c.isZero) continue
        out += if (c == BigRational.ONE) rest else mulRaw(Sym.Num(c), rest)
    }
    return when (out.size) {
        0 -> ZERO
        1 -> out[0]
        else -> Sym.Add(out.sortedWith(order))
    }
}

/** c·rest where rest is already canonical and not a number. */
private fun mulRaw(c: Sym.Num, rest: Sym): Sym =
    if (rest is Sym.Mul) Sym.Mul(listOf(c) + rest.factors) else Sym.Mul(listOf(c, rest))

fun mul(vararg factors: Sym): Sym = mul(factors.toList())

fun mul(factors: List<Sym>): Sym = mulPass(factors, 0)

private fun mulPass(factors: List<Sym>, pass: Int): Sym {
    val flat = ArrayList<Sym>()
    for (f in factors) if (f is Sym.Mul) flat += f.factors else flat += f
    var coefficient = BigRational.ONE
    val bases = LinkedHashMap<Sym, Sym>()
    for (f in flat) {
        when {
            f is Sym.Num -> coefficient *= f.q
            f is Sym.Pow -> bases[f.base] = bases[f.base]?.let { add(it, f.exp) } ?: f.exp
            else -> bases[f] = bases[f]?.let { add(it, ONE) } ?: ONE
        }
        if (coefficient.isZero) return ZERO
    }
    val out = ArrayList<Sym>()
    for ((base, exp) in bases) {
        val p = pow(base, exp)
        when {
            p is Sym.Num -> coefficient *= p.q
            p is Sym.Mul -> for (g in p.factors) if (g is Sym.Num) coefficient *= g.q else out += g
            else -> out += p
        }
    }
    if (coefficient.isZero) return ZERO
    // Factors made by pow() may now repeat a base, e.g. (2x)³·x; one more pass merges them.
    if (pass == 0 && out.map { (it as? Sym.Pow)?.base ?: it }.toSet().size < out.size) {
        return mulPass(listOf(Sym.Num(coefficient)) + out, 1)
    }
    val sorted = out.sortedWith(order)
    return when {
        sorted.isEmpty() -> Sym.Num(coefficient)
        coefficient == BigRational.ONE && sorted.size == 1 -> sorted[0]
        coefficient == BigRational.ONE -> Sym.Mul(sorted)
        else -> Sym.Mul(listOf(Sym.Num(coefficient)) + sorted)
    }
}

fun neg(s: Sym): Sym = mul(MINUS_ONE, s)
fun sub(a: Sym, b: Sym): Sym = add(a, neg(b))
fun div(a: Sym, b: Sym): Sym = mul(a, pow(b, MINUS_ONE))
fun sqrt(s: Sym): Sym = pow(s, HALF)
fun exp(s: Sym): Sym = pow(E, s)

fun pow(base: Sym, exp: Sym): Sym {
    if (exp.isZero) return ONE
    if (exp.isOne) return base
    if (base.isOne) return ONE
    if (base.isZero) {
        val e = exp.rational
        return if (e != null && e.signum > 0) ZERO else Sym.Pow(base, exp)
    }
    val e = exp.rational
    if (base is Sym.Num && e != null) numericPow(base.q, e)?.let { return it }
    // (b^m)^n = b^(mn) when n is an integer.
    if (base is Sym.Pow && e != null && e.isInteger) return pow(base.base, mul(base.exp, exp))
    // (uv)^n = uⁿvⁿ for integer n.
    if (base is Sym.Mul && e != null && e.isInteger) return mul(base.factors.map { pow(it, exp) })
    // e^(ln u) = u and e^(k·ln u) = uᵏ.
    if (base == E && exp is Sym.Fn && exp.name == "ln") return exp.arg
    if (base == E && exp is Sym.Mul && exp.factors.size == 2) {
        val (k, rest) = exp.factors
        if (k is Sym.Num && rest is Sym.Fn && rest.name == "ln") return pow(rest.arg, k)
    }
    return Sym.Pow(base, exp)
}

/** q^e for rational q and e when the result is rational (or a cleaner surd), else null. */
private fun numericPow(q: BigRational, e: BigRational): Sym? {
    if (e.isInteger) {
        val n = e.num
        if (n.abs().toInt() * maxOf(q.bitSize, 1) > maxPowBits) return null
        val r = q.pow(n.abs().toInt())
        return Sym.Num(if (n.signum() < 0) r.reciprocal() else r)
    }
    // Rational powers: exact roots, e.g. 8^(2/3) = 4 and (1/4)^(1/2) = 1/2.
    if (q.signum < 0) return null
    val k = e.den.toInt()
    val rootNum = exactRoot(q.num, k) ?: return null
    val rootDen = exactRoot(q.den, k) ?: return null
    val root = BigRational.of(rootNum, rootDen)
    return numericPow(root, BigRational.of(e.num, BigInteger.ONE))
}

private fun exactRoot(n: BigInteger, k: Int): BigInteger? {
    if (n.signum() == 0) return n
    if (k <= 0 || k > 64) return null
    val est = Math.round(Math.pow(n.toDouble(), 1.0 / k))
    for (d in -1L..1L) {
        val c = BigInteger.valueOf(est + d)
        if (c.signum() >= 0 && c.pow(k) == n) return c
    }
    return null
}

fun fn(name: String, arg: Sym): Sym {
    val a = arg.rational
    when (name) {
        "sin", "tan", "asin", "atan", "sinh", "tanh", "asinh", "atanh" -> {
            if (a != null && a.isZero) return ZERO
            // Odd functions: f(−u) = −f(u).
            val (c, _) = splitCoefficient(arg)
            if (c.signum < 0) return neg(fn(name, neg(arg)))
        }
        "cos", "cosh" -> {
            if (a != null && a.isZero) return ONE
            val (c, _) = splitCoefficient(arg)
            if (c.signum < 0) return fn(name, neg(arg))
        }
        "ln" -> {
            if (arg.isOne) return ZERO
            if (arg == E) return ONE
            if (arg is Sym.Pow && arg.base == E) return arg.exp
        }
        "abs" -> {
            if (a != null) return Sym.Num(a.abs())
            if (arg == PI || arg == E) return arg
            val (c, rest) = splitCoefficient(arg)
            if (c.signum < 0) return fn("abs", mul(Sym.Num(-c), rest))
            if (arg is Sym.Pow && arg.base == E) return arg
        }
    }
    when {
        name == "sin" && arg == PI -> return ZERO
        name == "cos" && arg == PI -> return MINUS_ONE
    }
    return Sym.Fn(name, arg)
}

// ---------------------------------------------------------------------------------------------
// Expansion

/** Multiplies out products of sums and whole-number powers of sums (up to the 8th). */
/** [s] multiplied out, unless that makes it longer: −2(−x + x·ln x) becomes 2x − 2x·ln x. */
fun tidy(s: Sym): Sym = expand(s).takeIf { it.size <= s.size } ?: s

fun expand(s: Sym): Sym = when (s) {
    is Sym.Add -> add(s.terms.map(::expand))
    is Sym.Mul -> s.factors.map(::expand).fold(ONE as Sym) { acc, f -> distribute(acc, f) }
    is Sym.Pow -> {
        val n = s.exp.int
        val b = expand(s.base)
        if (b is Sym.Add && n != null && n in 2..8) {
            var acc: Sym = ONE
            repeat(n) { acc = distribute(acc, b) }
            acc
        } else pow(b, s.exp)
    }
    else -> s
}

private fun distribute(a: Sym, b: Sym): Sym {
    val at = if (a is Sym.Add) a.terms else listOf(a)
    val bt = if (b is Sym.Add) b.terms else listOf(b)
    if (at.size * bt.size > 400) return mul(a, b)
    return add(at.flatMap { x -> bt.map { y -> mul(x, y) } })
}

// ---------------------------------------------------------------------------------------------
// Numeric evaluation

/** Evaluates with [x] bound to [value]; other variables are an error (NaN). */
fun Sym.eval(x: String, value: Double): Double = when (this) {
    is Sym.Num -> q.toDouble()
    is Sym.Const -> if (name == "pi") Math.PI else Math.E
    is Sym.Var -> if (name == x) value else Double.NaN
    is Sym.Add -> terms.sumOf { it.eval(x, value) }
    is Sym.Mul -> factors.fold(1.0) { acc, f -> acc * f.eval(x, value) }
    is Sym.Pow -> {
        val b = base.eval(x, value)
        val e = exp.eval(x, value)
        val r = exp.rational
        // Odd roots of negative numbers are real: ∛(−8) = −2.
        if (b < 0 && r != null && !r.isInteger && r.den.testBit(0)) {
            val m = Math.pow(-b, e)
            if (r.num.testBit(0)) -m else m
        } else Math.pow(b, e)
    }
    is Sym.Fn -> {
        val a = arg.eval(x, value)
        when (name) {
            "sin" -> Math.sin(a)
            "cos" -> Math.cos(a)
            "tan" -> Math.tan(a)
            "asin" -> Math.asin(a)
            "acos" -> Math.acos(a)
            "atan" -> Math.atan(a)
            "sinh" -> Math.sinh(a)
            "cosh" -> Math.cosh(a)
            "tanh" -> Math.tanh(a)
            "asinh" -> Math.log(a + Math.sqrt(a * a + 1))
            "acosh" -> Math.log(a + Math.sqrt(a * a - 1))
            "atanh" -> 0.5 * Math.log((1 + a) / (1 - a))
            "ln" -> Math.log(a)
            "log" -> Math.log10(a)
            "abs" -> Math.abs(a)
            else -> Double.NaN
        }
    }
}

/** Variables other than [x] that appear in this expression (parameters such as A). */
fun Sym.otherVariables(x: String): Set<String> {
    val out = HashSet<String>()
    for (s in subexpressions()) if (s is Sym.Var && s.name != x) out += s.name
    return out
}
