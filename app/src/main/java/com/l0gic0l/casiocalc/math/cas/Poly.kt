package com.l0gic0l.casiocalc.math.cas

import com.l0gic0l.casiocalc.math.num.BigRational
import java.math.BigInteger

/** A polynomial with rational coefficients; [c] holds the coefficient of xᵏ at index k. */
class Poly(coefficients: List<BigRational>) {
    val c: List<BigRational> = coefficients.dropLastWhile { it.isZero }

    val degree get() = c.size - 1
    val isZero get() = c.isEmpty()
    val lead: BigRational get() = c.last()

    operator fun get(k: Int): BigRational = c.getOrElse(k) { BigRational.ZERO }

    operator fun plus(o: Poly) = Poly(List(maxOf(c.size, o.c.size)) { this[it] + o[it] })
    operator fun minus(o: Poly) = Poly(List(maxOf(c.size, o.c.size)) { this[it] - o[it] })
    operator fun times(o: Poly): Poly {
        if (isZero || o.isZero) return Poly(emptyList())
        val out = MutableList(c.size + o.c.size - 1) { BigRational.ZERO }
        for (i in c.indices) for (j in o.c.indices) out[i + j] = out[i + j] + c[i] * o.c[j]
        return Poly(out)
    }

    fun scale(k: BigRational) = Poly(c.map { it * k })

    /** Quotient and remainder of long division. */
    fun divRem(d: Poly): Pair<Poly, Poly> {
        require(!d.isZero)
        var rem = this
        val q = MutableList(maxOf(degree - d.degree + 1, 0)) { BigRational.ZERO }
        while (!rem.isZero && rem.degree >= d.degree) {
            val k = rem.degree - d.degree
            val f = rem.lead / d.lead
            q[k] = f
            rem = rem - d.shift(k).scale(f)
        }
        return Poly(q) to rem
    }

    fun shift(k: Int) = Poly(List(k) { BigRational.ZERO } + c)

    fun eval(x: BigRational): BigRational = c.foldRight(BigRational.ZERO) { a, acc -> acc * x + a }

    fun toSym(x: String): Sym = add(c.mapIndexed { k, a -> mul(Sym.Num(a), pow(Sym.Var(x), num(k.toLong()))) })

    /** Distinct rational roots, by the rational root theorem. */
    fun rationalRoots(): List<BigRational> {
        if (degree < 1) return emptyList()
        // Clear denominators to get integer coefficients.
        val l = c.fold(BigInteger.ONE) { acc, q -> acc / acc.gcd(q.den) * q.den }
        val ints = c.map { (it * BigRational.of(l, BigInteger.ONE)).num }
        val roots = ArrayList<BigRational>()
        var low = 0
        while (low < ints.size && ints[low].signum() == 0) low++
        if (low > 0) roots += BigRational.ZERO
        val a0 = ints[low].abs()
        val an = ints.last().abs()
        val ps = divisors(a0) ?: return roots
        val qs = divisors(an) ?: return roots
        for (p in ps) for (q in qs) for (sign in listOf(1, -1)) {
            val cand = BigRational.of(p * BigInteger.valueOf(sign.toLong()), q)
            if (cand !in roots && eval(cand).isZero) roots += cand
        }
        return roots
    }

    /** Linear factors x − r with their multiplicities, and what is left over. */
    fun factorRational(): Pair<List<Pair<BigRational, Int>>, Poly> {
        var rest = this
        val out = ArrayList<Pair<BigRational, Int>>()
        for (root in rationalRoots()) {
            var m = 0
            val lin = Poly(listOf(-root, BigRational.ONE))
            while (rest.degree >= 1) {
                val (q, rem) = rest.divRem(lin)
                if (!rem.isZero) break
                rest = q
                m++
            }
            if (m > 0) out += root to m
        }
        return out to rest
    }

    override fun equals(other: Any?) = other is Poly && other.c == c
    override fun hashCode() = c.hashCode()
    override fun toString() = c.toString()

    companion object {
        fun constant(k: BigRational) = Poly(listOf(k))
        val ONE = constant(BigRational.ONE)

        /** The polynomial in [x] that [s] expands to, if its coefficients are rational numbers. */
        fun of(s: Sym, x: String): Poly? {
            val e = expand(s)
            val terms = if (e is Sym.Add) e.terms else listOf(e)
            val coeffs = HashMap<Int, BigRational>()
            for (t in terms) {
                val (k, rest) = splitCoefficient(t)
                val deg = when {
                    rest.isOne -> 0
                    rest == Sym.Var(x) -> 1
                    rest is Sym.Pow && rest.base == Sym.Var(x) -> rest.exp.int?.takeIf { it in 0..64 } ?: return null
                    else -> return null
                }
                coeffs[deg] = (coeffs[deg] ?: BigRational.ZERO) + k
            }
            val max = coeffs.keys.maxOrNull() ?: return Poly(emptyList())
            return Poly(List(max + 1) { coeffs[it] ?: BigRational.ZERO })
        }

        private fun divisors(n: BigInteger): List<BigInteger>? {
            if (n.signum() == 0) return listOf(BigInteger.ONE)
            if (n.bitLength() > 40) return null
            val v = n.toLong()
            val out = ArrayList<BigInteger>()
            var d = 1L
            while (d * d <= v) {
                if (v % d == 0L) {
                    out += BigInteger.valueOf(d)
                    if (d != v / d) out += BigInteger.valueOf(v / d)
                }
                d++
                if (d > 2_000_000) return null
            }
            return out
        }
    }
}

/** Solves the square system [a]·x = [b] over the rationals, or null if it is singular. */
fun solveLinear(a: List<List<BigRational>>, b: List<BigRational>): List<BigRational>? {
    val n = b.size
    val m = Array(n) { i -> (a[i] + b[i]).toTypedArray() }
    for (col in 0 until n) {
        val pivot = (col until n).firstOrNull { !m[it][col].isZero } ?: return null
        val tmp = m[pivot]; m[pivot] = m[col]; m[col] = tmp
        val p = m[col][col]
        for (j in col..n) m[col][j] = m[col][j] / p
        for (i in 0 until n) {
            if (i == col || m[i][col].isZero) continue
            val f = m[i][col]
            for (j in col..n) m[i][j] = m[i][j] - f * m[col][j]
        }
    }
    return List(n) { m[it][n] }
}
