package com.l0gic0l.casiocalc.math.num

import com.l0gic0l.casiocalc.math.mathError
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh

/** A complex number whose parts keep their exactness independently. */
class Complex(val re: Real, val im: Real = Real.ZERO) {
    val isReal get() = im.isZero
    val isExact get() = re is Real.Exact && im is Real.Exact

    override fun equals(other: Any?) = other is Complex && re == other.re && im == other.im
    override fun hashCode() = re.hashCode() * 31 + im.hashCode()
    override fun toString() = if (isReal) re.toString() else "($re) + ($im)i"

    companion object {
        val ZERO = Complex(Real.ZERO)
        val ONE = Complex(Real.ONE)
        val I = Complex(Real.ZERO, Real.ONE)
        fun of(n: Long) = Complex(Real.of(n))
        fun of(r: Real) = Complex(r)
        fun approx(re: Double, im: Double = 0.0) = Complex(Real.Approx(re), Real.Approx(im))
    }
}

object C {
    fun neg(z: Complex) = Complex(R.neg(z.re), R.neg(z.im))
    fun conj(z: Complex) = Complex(z.re, R.neg(z.im))

    fun add(a: Complex, b: Complex) = Complex(R.add(a.re, b.re), R.add(a.im, b.im))
    fun sub(a: Complex, b: Complex) = Complex(R.sub(a.re, b.re), R.sub(a.im, b.im))

    fun mul(a: Complex, b: Complex): Complex {
        if (a.isReal && b.isReal) return Complex(R.mul(a.re, b.re))
        if (a.isReal) return Complex(R.mul(a.re, b.re), R.mul(a.re, b.im))
        if (b.isReal) return Complex(R.mul(a.re, b.re), R.mul(a.im, b.re))
        return Complex(
            R.sub(R.mul(a.re, b.re), R.mul(a.im, b.im)),
            R.add(R.mul(a.re, b.im), R.mul(a.im, b.re)),
        )
    }

    fun div(a: Complex, b: Complex): Complex {
        if (b.re.isZero && b.im.isZero) mathError("division by zero")
        if (b.isReal) return Complex(R.div(a.re, b.re), R.div(a.im, b.re))
        val den = R.add(R.mul(b.re, b.re), R.mul(b.im, b.im))
        val n = mul(a, conj(b))
        return Complex(R.div(n.re, den), R.div(n.im, den))
    }

    fun abs(z: Complex): Real {
        if (z.isReal) return R.abs(z.re)
        if (z.re.isZero) return R.abs(z.im)
        val sq = R.add(R.mul(z.re, z.re), R.mul(z.im, z.im))
        return R.sqrt(sq)
    }

    /** Argument in radians (as a double); see [Functions.arg] for the angle-unit aware version. */
    fun argRad(z: Complex) = atan2(z.im.toDouble(), z.re.toDouble())

    fun sqrt(z: Complex): Complex {
        if (z.isReal) {
            return if (z.re.signum() >= 0) Complex(R.sqrt(z.re)) else Complex(Real.ZERO, R.sqrt(R.neg(z.re)))
        }
        val m = hypot(z.re.toDouble(), z.im.toDouble())
        val re = kotlin.math.sqrt((m + z.re.toDouble()) / 2)
        var im = kotlin.math.sqrt((m - z.re.toDouble()) / 2)
        if (z.im.toDouble() < 0) im = -im
        return Complex.approx(re, im)
    }

    fun exp(z: Complex): Complex {
        val ea = exp(z.re.toDouble())
        if (z.isReal) return Complex(Real.Approx(ea))
        val b = z.im.toDouble()
        return Complex.approx(ea * cos(b), ea * sin(b))
    }

    fun ln(z: Complex): Complex {
        if (z.re.isZero && z.im.isZero) mathError("ln 0")
        val m = hypot(z.re.toDouble(), z.im.toDouble())
        return Complex.approx(ln(m), argRad(z))
    }

    /** General power z^w through exp(w·ln z) for the non-real cases. */
    fun powGeneral(z: Complex, w: Complex): Complex {
        if (z.re.isZero && z.im.isZero) {
            if (w.re.toDouble() > 0) return Complex.ZERO
            mathError("0^w")
        }
        return exp(mul(w, ln(z)))
    }

    fun sinRad(z: Complex): Complex {
        val a = z.re.toDouble(); val b = z.im.toDouble()
        return Complex.approx(sin(a) * cosh(b), cos(a) * sinh(b))
    }

    fun cosRad(z: Complex): Complex {
        val a = z.re.toDouble(); val b = z.im.toDouble()
        return Complex.approx(cos(a) * cosh(b), -sin(a) * sinh(b))
    }

    fun approx(z: Complex) = Complex.approx(z.re.toDouble(), z.im.toDouble())
}
