package com.l0gic0l.casiocalc.math.num

import com.l0gic0l.casiocalc.math.mathError
import java.math.BigInteger
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A real number that is either exact or a double approximation.
 *
 * Exact values have the form (a + b·√r)·π^pi with rational a, b, a square-free radicand r and
 * pi ∈ {0, 1}. That covers every result the CG50 shows in natural form (fractions, surds and
 * multiples of π) while keeping arithmetic closed and cheap.
 */
sealed class Real {
    abstract fun toDouble(): Double
    abstract val isZero: Boolean
    abstract fun signum(): Int

    class Exact internal constructor(
        val a: BigRational,
        val b: BigRational,
        val r: Long,
        val pi: Int,
    ) : Real() {
        val isRational get() = b.isZero && pi == 0
        val isInteger get() = isRational && a.isInteger
        override val isZero get() = a.isZero && b.isZero

        override fun toDouble(): Double {
            var v = a.toDouble()
            if (!b.isZero) v += b.toDouble() * sqrt(r.toDouble())
            return if (pi == 1) v * Math.PI else v
        }

        override fun signum(): Int {
            val sa = a.signum
            val sb = b.signum
            if (sb == 0) return sa
            if (sa == 0 || sa == sb) return sb
            // Opposite signs: compare a² with b²·r.
            val lhs = a * a
            val rhs = b * b * BigRational.of(r)
            return if (lhs > rhs) sa else sb
        }

        override fun equals(other: Any?) =
            other is Exact && a == other.a && b == other.b && r == other.r && pi == other.pi

        override fun hashCode() = ((a.hashCode() * 31 + b.hashCode()) * 31 + r.hashCode()) * 31 + pi
        override fun toString() = buildString {
            append(a)
            if (!b.isZero) append(" + ").append(b).append("√").append(r)
            if (pi == 1) append(" π")
        }
    }

    class Approx(val v: Double) : Real() {
        override fun toDouble() = v
        override val isZero get() = v == 0.0
        override fun signum() = if (v > 0) 1 else if (v < 0) -1 else 0
        override fun equals(other: Any?) = other is Approx && v == other.v
        override fun hashCode() = v.hashCode()
        override fun toString() = v.toString()
    }

    companion object {
        /** Exact arithmetic gives up (falls back to doubles) beyond this many bits. */
        private const val MAX_BITS = 160

        val ZERO: Real = Exact(BigRational.ZERO, BigRational.ZERO, 1, 0)
        val ONE: Real = Exact(BigRational.ONE, BigRational.ZERO, 1, 0)
        val PI: Real = Exact(BigRational.ONE, BigRational.ZERO, 1, 1)
        val E: Real = Approx(Math.E)

        fun of(n: Long): Real = Exact(BigRational.of(n), BigRational.ZERO, 1, 0)
        fun of(q: BigRational): Real = exact(q)
        fun of(n: BigInteger): Real = exact(BigRational.of(n))
        fun approx(v: Double): Real = Approx(v)

        fun exact(a: BigRational, b: BigRational = BigRational.ZERO, r: Long = 1, pi: Int = 0): Real {
            var aa = a
            var bb = b
            var rr = r
            if (bb.isZero) rr = 1
            if (rr == 1L && !bb.isZero) {
                aa += bb; bb = BigRational.ZERO
            }
            if (aa.bitSize > MAX_BITS || bb.bitSize > MAX_BITS) {
                return Approx(Exact(aa, bb, rr, pi).toDouble())
            }
            val p = if (aa.isZero && bb.isZero) 0 else pi
            return Exact(aa, bb, rr, p)
        }

        /** Builds q·√n for a non-negative integer n, simplifying square factors. */
        fun surd(q: BigRational, n: BigInteger): Real {
            if (n.signum() == 0 || q.isZero) return ZERO
            val (k, m) = squareFree(n) ?: return Approx(q.toDouble() * sqrt(n.toDouble()))
            return exact(BigRational.ZERO, q * BigRational.of(k), m)
        }

        /**
         * Splits n = k²·m with m square-free. Returns null when n is too large to factor quickly.
         */
        fun squareFree(n: BigInteger): Pair<BigInteger, Long>? {
            if (n.signum() <= 0) return null
            if (n.bitLength() > 62) return null
            var m = n.toLong()
            var k = 1L
            var free = 1L
            var p = 2L
            while (p <= 1_000_000 && p * p <= m) {
                if (m % p == 0L) {
                    var count = 0
                    while (m % p == 0L) {
                        m /= p; count++
                    }
                    repeat(count / 2) { k *= p }
                    if (count % 2 == 1) free *= p
                }
                p += if (p == 2L) 1 else 2
            }
            // m is now 1, a prime, or a product of primes above 10^6.
            if (m > 1) {
                if (m >= 1_000_000_000_000_000_000L) return null
                val s = isqrt(m)
                if (s * s == m) k *= s else free *= m
            }
            return Pair(BigInteger.valueOf(k), free)
        }

        fun isqrt(n: Long): Long {
            if (n < 0) return -1
            var x = sqrt(n.toDouble()).toLong()
            while (x * x > n) x--
            while ((x + 1) * (x + 1) <= n) x++
            return x
        }

        /** Exact integer k-th root of n when n is a perfect power, else null. */
        fun exactRoot(n: BigInteger, k: Int): BigInteger? {
            if (n.signum() < 0) {
                if (k % 2 == 0) return null
                return exactRoot(n.negate(), k)?.negate()
            }
            if (n.signum() == 0) return BigInteger.ZERO
            if (k == 1) return n
            // 2ᵏ > n, so only 1 can be a root; checking c = 2 would build 2ᵏ, which for 2^0.0000001 is huge.
            if (k >= n.bitLength()) return if (n == BigInteger.ONE) n else null
            val est = Math.pow(n.toDouble(), 1.0 / k)
            if (est.isInfinite() || est.isNaN()) return null
            val base = BigInteger.valueOf(Math.round(est))
            for (d in -1L..1L) {
                val c = base + BigInteger.valueOf(d)
                if (c.signum() >= 0 && c.pow(k) == n) return c
            }
            return null
        }
    }
}

/** Arithmetic on [Real] that stays exact whenever the result fits the exact form. */
object R {
    private fun ex(x: Real) = x as? Real.Exact

    fun neg(x: Real): Real = when (x) {
        is Real.Exact -> Real.exact(-x.a, -x.b, x.r, x.pi)
        is Real.Approx -> Real.Approx(-x.v)
    }

    fun add(x: Real, y: Real): Real {
        val a = ex(x)
        val b = ex(y)
        if (a != null && b != null) {
            if (a.isZero) return b
            if (b.isZero) return a
            if (a.pi == b.pi) {
                val r = when {
                    a.b.isZero -> b.r
                    b.b.isZero -> a.r
                    a.r == b.r -> a.r
                    else -> -1L
                }
                if (r > 0) return Real.exact(a.a + b.a, a.b + b.b, r, a.pi)
            }
        }
        return Real.Approx(x.toDouble() + y.toDouble())
    }

    fun sub(x: Real, y: Real) = add(x, neg(y))

    fun mul(x: Real, y: Real): Real {
        val a = ex(x)
        val b = ex(y)
        if (a != null && b != null) {
            if (a.isZero || b.isZero) return Real.ZERO
            val pi = a.pi + b.pi
            if (pi <= 1) {
                when {
                    a.b.isZero -> return Real.exact(a.a * b.a, a.a * b.b, b.r, pi)
                    b.b.isZero -> return Real.exact(a.a * b.a, a.b * b.a, a.r, pi)
                    a.r == b.r -> return Real.exact(
                        a.a * b.a + a.b * b.b * BigRational.of(a.r),
                        a.a * b.b + a.b * b.a,
                        a.r,
                        pi,
                    )
                    a.a.isZero && b.a.isZero -> {
                        val g = gcd(a.r, b.r)
                        val m = runCatching { Math.multiplyExact(a.r / g, b.r / g) }.getOrNull()
                        if (m != null) {
                            return Real.exact(BigRational.ZERO, a.b * b.b * BigRational.of(g), m, pi)
                        }
                    }
                }
            }
        }
        return Real.Approx(x.toDouble() * y.toDouble())
    }

    fun inv(x: Real): Real {
        if (x.isZero) mathError("division by zero")
        val a = ex(x) ?: return Real.Approx(1.0 / x.toDouble())
        if (a.pi == 1) return Real.Approx(1.0 / x.toDouble())
        if (a.b.isZero) return Real.exact(a.a.reciprocal())
        // 1/(c + d√s) = (c − d√s)/(c² − d²s)
        val den = a.a * a.a - a.b * a.b * BigRational.of(a.r)
        return Real.exact(a.a / den, -a.b / den, a.r, 0)
    }

    fun div(x: Real, y: Real): Real {
        if (y.isZero) mathError("division by zero")
        val a = ex(x)
        val b = ex(y)
        if (a != null && b != null && b.pi == 1) {
            if (a.isZero) return Real.ZERO
            if (a.pi == 1) {
                return mul(Real.exact(a.a, a.b, a.r, 0), inv(Real.exact(b.a, b.b, b.r, 0)))
            }
            return Real.Approx(x.toDouble() / y.toDouble())
        }
        return mul(x, inv(y))
    }

    fun cmp(x: Real, y: Real): Int = sub(x, y).signum()

    fun abs(x: Real) = if (x.signum() < 0) neg(x) else x

    /** Square root of a non-negative real. */
    fun sqrt(x: Real): Real {
        if (x.signum() < 0) throw IllegalArgumentException("negative")
        val a = ex(x)
        if (a != null && a.isRational) {
            val q = a.a
            // √(n/d) = √(n·d)/d
            return Real.surd(BigRational.of(java.math.BigInteger.ONE, q.den), q.num * q.den)
        }
        return Real.Approx(kotlin.math.sqrt(x.toDouble()))
    }

    /** Integer power with exact arithmetic where possible. */
    fun powInt(x: Real, n: Long): Real {
        if (n == 0L) {
            if (x.isZero) mathError("0^0")
            return Real.ONE
        }
        if (n < 0) return inv(powInt(x, -n))
        if (x is Real.Exact && n <= 4096) {
            var result: Real = Real.ONE
            var base: Real = x
            var e = n
            while (e > 0) {
                if (e and 1L == 1L) result = mul(result, base)
                e = e shr 1
                if (e > 0) base = mul(base, base)
                if (result is Real.Approx || base is Real.Approx) {
                    return Real.Approx(Math.pow(x.toDouble(), n.toDouble()))
                }
            }
            return result
        }
        return Real.Approx(Math.pow(x.toDouble(), n.toDouble()))
    }

    /**
     * x^y for real results. Returns null when the result is not real (negative base with an
     * even-denominator or irrational exponent) so the caller can switch to complex arithmetic.
     */
    fun pow(x: Real, y: Real): Real? {
        val ye = ex(y)
        if (ye != null && ye.isRational) {
            val q = ye.a
            if (q.isInteger && q.num.bitLength() < 63) return powInt(x, q.num.toLong())
            val xe = ex(x)
            if (xe != null && xe.isRational && q.den.bitLength() < 31) {
                val k = q.den.toInt()
                if (xe.a.signum < 0 && k % 2 == 0) return null
                val rn = Real.exactRoot(xe.a.num, k)
                val rd = Real.exactRoot(xe.a.den, k)
                if (rn != null && rd != null && q.num.bitLength() < 31) {
                    return powInt(Real.exact(BigRational.of(rn, rd)), q.num.toLong())
                }
                if (k == 2 && q.num.bitLength() < 31) {
                    return powInt(sqrt(x), q.num.toLong())
                }
                if (xe.a.signum < 0) {
                    // Odd root of a negative number: real, negative.
                    val mag = Math.pow(-xe.a.toDouble(), 1.0 / k)
                    return Real.Approx(Math.pow(-mag, q.num.toDouble()))
                }
            }
            if (x.signum() < 0) {
                if (q.den.testBit(0)) {
                    val mag = Math.pow(-x.toDouble(), q.toDouble())
                    return Real.Approx(if (q.num.testBit(0)) -mag else mag)
                }
                return null
            }
        }
        val xv = x.toDouble()
        val yv = y.toDouble()
        if (xv < 0) return null
        if (xv == 0.0 && yv <= 0) mathError("0^0")
        return Real.Approx(Math.pow(xv, yv))
    }

    fun gcd(a: Long, b: Long): Long {
        var x = abs(a)
        var y = abs(b)
        while (y != 0L) {
            val t = x % y; x = y; y = t
        }
        return x
    }

    /** Converts an exact rational to an integer, or null. */
    fun toBigInteger(x: Real): BigInteger? {
        val e = ex(x) ?: run {
            val v = x.toDouble()
            if (v == kotlin.math.floor(v) && abs(v) < 1e15) return BigInteger.valueOf(v.toLong())
            return null
        }
        return if (e.isInteger) e.a.num else null
    }

    fun toIntOrNull(x: Real): Int? {
        val b = toBigInteger(x) ?: return null
        return if (b.bitLength() < 31) b.toInt() else null
    }
}
