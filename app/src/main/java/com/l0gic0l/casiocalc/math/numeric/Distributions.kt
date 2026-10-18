package com.l0gic0l.casiocalc.math.numeric

import com.l0gic0l.casiocalc.math.argError
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sqrt

/** Probability distributions from the CG50's DIST menu. */
object Distributions {
    private const val EPS = 1e-16
    private const val FPMIN = 1e-300

    /** ln Γ(x) by the Lanczos approximation (g = 7, n = 9). */
    fun lnGamma(x: Double): Double {
        if (x < 0.5) return ln(Math.PI / abs(kotlin.math.sin(Math.PI * x))) - lnGamma(1 - x)
        val g = 7.0
        val c = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012,
            9.9843695780195716e-6, 1.5056327351493116e-7,
        )
        val xx = x - 1
        var a = c[0]
        val t = xx + g + 0.5
        for (i in 1 until 9) a += c[i] / (xx + i)
        return 0.5 * ln(2 * Math.PI) + (xx + 0.5) * ln(t) - t + ln(a)
    }

    /** Regularized lower incomplete gamma P(a, x). */
    fun gammaP(a: Double, x: Double): Double {
        if (x <= 0) return 0.0
        return if (x < a + 1) gammaSeries(a, x) else 1 - gammaCf(a, x)
    }

    fun gammaQ(a: Double, x: Double): Double = if (x <= 0) 1.0 else if (x < a + 1) 1 - gammaSeries(a, x) else gammaCf(a, x)

    private fun gammaSeries(a: Double, x: Double): Double {
        var ap = a
        var sum = 1 / a
        var del = sum
        for (n in 0 until 1000) {
            ap += 1
            del *= x / ap
            sum += del
            if (abs(del) < abs(sum) * EPS) break
        }
        return sum * exp(-x + a * ln(x) - lnGamma(a))
    }

    private fun gammaCf(a: Double, x: Double): Double {
        var b = x + 1 - a
        var c = 1 / FPMIN
        var d = 1 / b
        var h = d
        for (i in 1 until 1000) {
            val an = -i * (i - a)
            b += 2
            d = an * d + b
            if (abs(d) < FPMIN) d = FPMIN
            c = b + an / c
            if (abs(c) < FPMIN) c = FPMIN
            d = 1 / d
            val del = d * c
            h *= del
            if (abs(del - 1) < EPS) break
        }
        return exp(-x + a * ln(x) - lnGamma(a)) * h
    }

    /** Regularized incomplete beta I_x(a, b). */
    fun betaI(a: Double, b: Double, x: Double): Double {
        if (x <= 0) return 0.0
        if (x >= 1) return 1.0
        val bt = exp(lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1 - x))
        return if (x < (a + 1) / (a + b + 2)) bt * betaCf(a, b, x) / a
        else 1 - bt * betaCf(b, a, 1 - x) / b
    }

    private fun betaCf(a: Double, b: Double, x: Double): Double {
        val qab = a + b
        val qap = a + 1
        val qam = a - 1
        var c = 1.0
        var d = 1 - qab * x / qap
        if (abs(d) < FPMIN) d = FPMIN
        d = 1 / d
        var h = d
        for (m in 1..1000) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((qam + m2) * (a + m2))
            d = 1 + aa * d
            if (abs(d) < FPMIN) d = FPMIN
            c = 1 + aa / c
            if (abs(c) < FPMIN) c = FPMIN
            d = 1 / d
            h *= d * c
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
            d = 1 + aa * d
            if (abs(d) < FPMIN) d = FPMIN
            c = 1 + aa / c
            if (abs(c) < FPMIN) c = FPMIN
            d = 1 / d
            val del = d * c
            h *= del
            if (abs(del - 1) < EPS) break
        }
        return h
    }

    /** Standard normal CDF Φ(z) through erfc, accurate to double precision. */
    fun phi(z: Double): Double {
        val x = z / sqrt(2.0)
        return if (x < 0) 0.5 * erfc(-x) else 1 - 0.5 * erfc(x)
    }

    fun erfc(x: Double): Double = if (x < 0) 2 - erfc(-x) else gammaQ(0.5, x * x)

    fun normPdf(x: Double, sigma: Double = 1.0, mu: Double = 0.0): Double {
        if (sigma <= 0) argError("σ")
        val z = (x - mu) / sigma
        return exp(-z * z / 2) / (sigma * sqrt(2 * Math.PI))
    }

    fun normCdf(lower: Double, upper: Double, sigma: Double = 1.0, mu: Double = 0.0): Double {
        if (sigma <= 0) argError("σ")
        return phi((upper - mu) / sigma) - phi((lower - mu) / sigma)
    }

    /** Inverse of the lower-tail normal CDF (Acklam's algorithm plus one Halley step). */
    fun invNorm(p: Double, sigma: Double = 1.0, mu: Double = 0.0): Double {
        if (p <= 0 || p >= 1 || sigma <= 0) argError("area")
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02,
            1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02,
            6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00,
            -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00,
            3.754408661907416e+00)
        val plow = 0.02425
        var x = when {
            p < plow -> {
                val q = sqrt(-2 * ln(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
            }
            p <= 1 - plow -> {
                val q = p - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1)
            }
            else -> {
                val q = sqrt(-2 * ln(1 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1)
            }
        }
        val e = phi(x) - p
        val u = e * sqrt(2 * Math.PI) * exp(x * x / 2)
        x -= u / (1 + x * u / 2)
        return mu + sigma * x
    }

    private fun lnChoose(n: Double, k: Double) = lnGamma(n + 1) - lnGamma(k + 1) - lnGamma(n - k + 1)

    private fun checkProb(p: Double) {
        if (p < 0 || p > 1) argError("p")
    }

    fun binomPdf(x: Double, n: Double, p: Double): Double {
        checkProb(p)
        if (n < 0 || n != floor(n)) argError("n")
        if (x < 0 || x > n || x != floor(x)) return 0.0
        if (p == 0.0) return if (x == 0.0) 1.0 else 0.0
        if (p == 1.0) return if (x == n) 1.0 else 0.0
        return exp(lnChoose(n, x) + x * ln(p) + (n - x) * ln(1 - p))
    }

    fun binomCdf(lower: Double, upper: Double, n: Double, p: Double): Double {
        var s = 0.0
        var k = maxOf(0.0, kotlin.math.ceil(lower))
        val hi = minOf(n, floor(upper))
        while (k <= hi) {
            s += binomPdf(k, n, p); k++
        }
        return s.coerceIn(0.0, 1.0)
    }

    fun poissonPdf(x: Double, mu: Double): Double {
        if (mu <= 0) argError("μ")
        if (x < 0 || x != floor(x)) return 0.0
        return exp(x * ln(mu) - mu - lnGamma(x + 1))
    }

    fun poissonCdf(lower: Double, upper: Double, mu: Double): Double {
        if (mu <= 0) argError("μ")
        val lo = maxOf(0.0, kotlin.math.ceil(lower))
        val hi = floor(upper)
        if (hi < lo) return 0.0
        // P(X ≤ k) = Q(k + 1, μ)
        val upperP = gammaQ(hi + 1, mu)
        val lowerP = if (lo <= 0) 0.0 else gammaQ(lo, mu)
        return (upperP - lowerP).coerceIn(0.0, 1.0)
    }

    fun geoPdf(x: Double, p: Double): Double {
        checkProb(p)
        if (x < 1 || x != floor(x)) return 0.0
        return p * Math.pow(1 - p, x - 1)
    }

    fun geoCdf(lower: Double, upper: Double, p: Double): Double {
        checkProb(p)
        val lo = maxOf(1.0, kotlin.math.ceil(lower))
        val hi = floor(upper)
        if (hi < lo) return 0.0
        return Math.pow(1 - p, lo - 1) - Math.pow(1 - p, hi)
    }

    fun tPdf(x: Double, df: Double): Double {
        if (df <= 0) argError("df")
        return exp(lnGamma((df + 1) / 2) - lnGamma(df / 2) - 0.5 * ln(df * Math.PI) -
            (df + 1) / 2 * ln(1 + x * x / df))
    }

    private fun tLeft(t: Double, df: Double): Double {
        val ib = betaI(df / 2, 0.5, df / (df + t * t))
        return if (t >= 0) 1 - ib / 2 else ib / 2
    }

    fun tCdf(lower: Double, upper: Double, df: Double): Double {
        if (df <= 0) argError("df")
        return tLeft(upper, df) - tLeft(lower, df)
    }

    fun chiPdf(x: Double, df: Double): Double {
        if (df <= 0) argError("df")
        if (x <= 0) return 0.0
        val k = df / 2
        return exp((k - 1) * ln(x) - x / 2 - k * ln(2.0) - lnGamma(k))
    }

    fun chiCdf(lower: Double, upper: Double, df: Double): Double {
        if (df <= 0) argError("df")
        return gammaP(df / 2, maxOf(upper, 0.0) / 2) - gammaP(df / 2, maxOf(lower, 0.0) / 2)
    }

    fun fPdf(x: Double, n: Double, d: Double): Double {
        if (n <= 0 || d <= 0) argError("df")
        if (x <= 0) return 0.0
        return exp(0.5 * (n * ln(n * x) + d * ln(d) - (n + d) * ln(n * x + d)) - ln(x) -
            (lnGamma(n / 2) + lnGamma(d / 2) - lnGamma((n + d) / 2)))
    }

    private fun fLeft(x: Double, n: Double, d: Double) = if (x <= 0) 0.0 else betaI(n / 2, d / 2, n * x / (n * x + d))

    fun fCdf(lower: Double, upper: Double, n: Double, d: Double): Double {
        if (n <= 0 || d <= 0) argError("df")
        return fLeft(upper, n, d) - fLeft(lower, n, d)
    }
}
