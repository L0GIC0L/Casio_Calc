package com.l0gic0l.casiocalc.math.eval

import com.l0gic0l.casiocalc.math.mathError
import com.l0gic0l.casiocalc.math.num.BigRational
import com.l0gic0l.casiocalc.math.num.R
import com.l0gic0l.casiocalc.math.num.Real

/**
 * Angle-aware trigonometry. Angles that are exact multiples of 30° or 45° give exact results
 * (sin 60° = √3/2), and the inverse functions map those values back to exact angles (π/6).
 */
object Trig {
    private val Q = BigRational.Companion
    private val half = Real.of(BigRational.HALF)
    private val r2 = Real.exact(BigRational.ZERO, BigRational.HALF, 2) // √2/2
    private val r3 = Real.exact(BigRational.ZERO, BigRational.HALF, 3) // √3/2
    private val r3third = Real.exact(BigRational.ZERO, Q.of(1, 3), 3) // √3/3
    private val sqrt3 = Real.exact(BigRational.ZERO, BigRational.ONE, 3)

    private val sin12 = listOf(
        Real.ZERO, half, r3, Real.ONE, r3, half,
        Real.ZERO, R.neg(half), R.neg(r3), R.neg(Real.ONE), R.neg(r3), R.neg(half),
    )
    private val sin8 = listOf(
        Real.ZERO, r2, Real.ONE, r2, Real.ZERO, R.neg(r2), R.neg(Real.ONE), R.neg(r2),
    )

    fun toRadians(x: Double, unit: AngleUnit) = when (unit) {
        AngleUnit.DEG -> x * Math.PI / 180
        AngleUnit.RAD -> x
        AngleUnit.GRA -> x * Math.PI / 200
    }

    fun fromRadians(x: Double, unit: AngleUnit) = when (unit) {
        AngleUnit.DEG -> x * 180 / Math.PI
        AngleUnit.RAD -> x
        AngleUnit.GRA -> x * 200 / Math.PI
    }

    /** Exact angle in degrees expressed in [unit]. */
    fun fromDegrees(d: BigRational, unit: AngleUnit): Real = when (unit) {
        AngleUnit.DEG -> Real.of(d)
        AngleUnit.RAD -> Real.exact(d / Q.of(180), pi = 1)
        AngleUnit.GRA -> Real.of(d * Q.of(10, 9))
    }

    /** Converts an angle between units, exactly when possible (90° → π/2). */
    fun convert(x: Real, from: AngleUnit, to: AngleUnit): Real {
        if (from == to) return x
        val deg: Real = when (from) {
            AngleUnit.DEG -> x
            AngleUnit.RAD -> R.div(R.mul(x, Real.of(180)), Real.PI)
            AngleUnit.GRA -> R.mul(x, Real.of(Q.of(9, 10)))
        }
        return when (to) {
            AngleUnit.DEG -> deg
            AngleUnit.RAD -> R.mul(deg, R.div(Real.PI, Real.of(180)))
            AngleUnit.GRA -> R.mul(deg, Real.of(Q.of(10, 9)))
        }
    }

    /** Fraction of a full turn in [0, 1) when [x] is an exact angle, else null. */
    private fun turns(x: Real, unit: AngleUnit): BigRational? {
        val e = x as? Real.Exact ?: return null
        if (!e.b.isZero) return null
        if (e.isZero) return BigRational.ZERO
        val t = when (unit) {
            AngleUnit.DEG -> if (e.pi == 0) e.a / Q.of(360) else null
            AngleUnit.RAD -> if (e.pi == 1) e.a / Q.of(2) else null
            AngleUnit.GRA -> if (e.pi == 0) e.a / Q.of(400) else null
        } ?: return null
        return t - BigRational.of(t.floor())
    }

    private fun sinTurns(t: BigRational): Real? {
        val k12 = t * Q.of(12)
        if (k12.isInteger) return sin12[k12.num.toInt()]
        val k8 = t * Q.of(8)
        if (k8.isInteger) return sin8[k8.num.toInt()]
        return null
    }

    private fun quarterTurn(t: BigRational): BigRational {
        val s = t + Q.of(1, 4)
        return s - BigRational.of(s.floor())
    }

    fun sin(x: Real, unit: AngleUnit): Real {
        turns(x, unit)?.let { t -> sinTurns(t)?.let { return it } }
        return Real.Approx(snap(kotlin.math.sin(toRadians(x.toDouble(), unit))))
    }

    fun cos(x: Real, unit: AngleUnit): Real {
        turns(x, unit)?.let { t -> sinTurns(quarterTurn(t))?.let { return it } }
        return Real.Approx(snap(kotlin.math.cos(toRadians(x.toDouble(), unit))))
    }

    fun tan(x: Real, unit: AngleUnit): Real {
        turns(x, unit)?.let { t ->
            val s = sinTurns(t)
            val c = sinTurns(quarterTurn(t))
            if (s != null && c != null) {
                if (c.isZero) mathError("tan 90°")
                return R.div(s, c)
            }
        }
        val c = kotlin.math.cos(toRadians(x.toDouble(), unit))
        if (kotlin.math.abs(c) < 1e-15) mathError("tan 90°")
        return Real.Approx(snap(kotlin.math.tan(toRadians(x.toDouble(), unit))))
    }

    /** Rounds results within double noise of zero to zero (sin 180.0° and the like). */
    private fun snap(v: Double) = if (kotlin.math.abs(v) < 1e-15) 0.0 else v

    private fun exactAsinDegrees(x: Real): BigRational? {
        if (x !is Real.Exact) return null
        val neg = x.signum() < 0
        val a = if (neg) R.neg(x) else x
        val d = when (a) {
            Real.ZERO -> 0L
            half -> 30L
            r2 -> 45L
            r3 -> 60L
            Real.ONE -> 90L
            else -> return null
        }
        return Q.of(if (neg) -d else d)
    }

    private fun exactAtanDegrees(x: Real): BigRational? {
        if (x !is Real.Exact) return null
        val neg = x.signum() < 0
        val a = if (neg) R.neg(x) else x
        val d = when (a) {
            Real.ZERO -> 0L
            r3third -> 30L
            Real.ONE -> 45L
            sqrt3 -> 60L
            else -> return null
        }
        return Q.of(if (neg) -d else d)
    }

    /** Inverse sine for |x| ≤ 1 (the caller handles the complex case). */
    fun asin(x: Real, unit: AngleUnit): Real {
        exactAsinDegrees(x)?.let { return fromDegrees(it, unit) }
        return Real.Approx(fromRadians(kotlin.math.asin(x.toDouble()), unit))
    }

    fun acos(x: Real, unit: AngleUnit): Real {
        exactAsinDegrees(x)?.let { return fromDegrees(Q.of(90) - it, unit) }
        return Real.Approx(fromRadians(kotlin.math.acos(x.toDouble()), unit))
    }

    fun atan(x: Real, unit: AngleUnit): Real {
        exactAtanDegrees(x)?.let { return fromDegrees(it, unit) }
        return Real.Approx(fromRadians(kotlin.math.atan(x.toDouble()), unit))
    }

    /** Angle of the point (x, y), exact for the standard angles. */
    fun atan2(y: Real, x: Real, unit: AngleUnit): Real {
        if (x.isZero && y.isZero) return Real.ZERO
        if (x.isZero) return fromDegrees(Q.of(if (y.signum() > 0) 90 else -90), unit)
        val ratio = runCatching { R.div(y, x) }.getOrNull()
        val base = ratio?.let { exactAtanDegrees(it) }
        if (base != null) {
            val deg = when {
                x.signum() > 0 -> base
                y.signum() >= 0 -> base + Q.of(180)
                else -> base - Q.of(180)
            }
            return fromDegrees(deg, unit)
        }
        return Real.Approx(fromRadians(kotlin.math.atan2(y.toDouble(), x.toDouble()), unit))
    }
}
