package com.l0gic0l.casiocalc.math.num

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** An exact fraction num/den, always stored in lowest terms with a positive denominator. */
class BigRational private constructor(val num: BigInteger, val den: BigInteger) : Comparable<BigRational> {

    val isZero get() = num.signum() == 0
    val isInteger get() = den == BigInteger.ONE
    val signum get() = num.signum()

    /** Largest bit length of numerator or denominator; used to cap the cost of exact arithmetic. */
    val bitSize get() = maxOf(num.bitLength(), den.bitLength())

    operator fun plus(o: BigRational) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: BigRational) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: BigRational) = of(num * o.num, den * o.den)
    operator fun div(o: BigRational): BigRational {
        if (o.isZero) throw ArithmeticException("Division by zero")
        return of(num * o.den, den * o.num)
    }
    operator fun unaryMinus() = BigRational(num.negate(), den)
    fun abs() = if (num.signum() < 0) -this else this
    fun reciprocal() = ONE / this

    fun pow(n: Int): BigRational = when {
        n >= 0 -> BigRational(num.pow(n), den.pow(n))
        else -> reciprocal().pow(-n)
    }

    /** Truncates toward zero. */
    fun truncate(): BigInteger = num.divide(den)

    fun floor(): BigInteger {
        val q = num.divide(den)
        return if (num.signum() < 0 && q * den != num) q - BigInteger.ONE else q
    }

    fun toDouble(): Double {
        if (num.bitLength() < 53 && den.bitLength() < 53) return num.toDouble() / den.toDouble()
        return BigDecimal(num).divide(BigDecimal(den), MathContext.DECIMAL64).toDouble()
    }

    override fun compareTo(other: BigRational) = (num * other.den).compareTo(other.num * den)
    override fun equals(other: Any?) = other is BigRational && num == other.num && den == other.den
    override fun hashCode() = num.hashCode() * 31 + den.hashCode()
    override fun toString() = if (isInteger) num.toString() else "$num/$den"

    companion object {
        val ZERO = BigRational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = BigRational(BigInteger.ONE, BigInteger.ONE)
        val TWO = BigRational(BigInteger.valueOf(2), BigInteger.ONE)
        val HALF = BigRational(BigInteger.ONE, BigInteger.valueOf(2))

        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): BigRational {
            require(d.signum() != 0) { "zero denominator" }
            var nn = n
            var dd = d
            if (dd.signum() < 0) {
                nn = nn.negate(); dd = dd.negate()
            }
            val g = nn.gcd(dd)
            if (g != BigInteger.ONE && g.signum() != 0) {
                nn /= g; dd /= g
            }
            if (nn.signum() == 0) dd = BigInteger.ONE
            return BigRational(nn, dd)
        }

        fun of(n: Long, d: Long = 1) = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        /** Parses decimal text such as "12.5", ".25" or "3" exactly. */
        fun parseDecimal(text: String): BigRational {
            val bd = BigDecimal(if (text.startsWith(".")) "0$text" else text)
            return of(bd)
        }

        fun of(bd: BigDecimal): BigRational {
            return if (bd.scale() > 0) of(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
            else of(bd.unscaledValue() * BigInteger.TEN.pow(-bd.scale()))
        }

        /**
         * Best rational approximation of [x] with denominator at most [maxDen], found with continued
         * fractions. Returns null when no such fraction is within [relTol] of x.
         */
        fun approximate(x: Double, maxDen: Long, relTol: Double): BigRational? {
            if (x.isNaN() || x.isInfinite()) return null
            if (x == 0.0) return ZERO
            val neg = x < 0
            val ax = kotlin.math.abs(x)
            if (ax > 1e15) return null
            var h0 = 0L; var h1 = 1L
            var k0 = 1L; var k1 = 0L
            var v = ax
            repeat(40) {
                val a = kotlin.math.floor(v).toLong()
                val h2 = a * h1 + h0
                val k2 = a * k1 + k0
                if (k2 > maxDen || k2 <= 0) return null
                h0 = h1; h1 = h2; k0 = k1; k1 = k2
                val approx = h1.toDouble() / k1.toDouble()
                if (kotlin.math.abs(approx - ax) <= relTol * maxOf(1.0, ax)) {
                    val r = of(h1, k1)
                    return if (neg) -r else r
                }
                val frac = v - a
                if (frac < 1e-18) return null
                v = 1.0 / frac
            }
            return null
        }
    }
}
