package com.l0gic0l.casiocalc.math.numeric

import com.l0gic0l.casiocalc.math.dimError
import com.l0gic0l.casiocalc.math.mathError
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sqrt

/** Single- and paired-variable statistics and regressions from the STAT mode. */
object Stats {

    class OneVar(
        val n: Double,
        val mean: Double,
        val sum: Double,
        val sumSq: Double,
        val popSd: Double,
        val sampleSd: Double,
        val min: Double,
        val q1: Double,
        val median: Double,
        val q3: Double,
        val max: Double,
        val modes: List<Double>,
        val modeFreq: Double,
    ) {
        /** Rows as shown by the calculator: label to value. */
        fun rows(): List<Pair<String, Double>> = listOf(
            "x̄" to mean, "Σx" to sum, "Σx²" to sumSq, "σx" to popSd, "sx" to sampleSd, "n" to n,
            "minX" to min, "Q1" to q1, "Med" to median, "Q3" to q3, "maxX" to max,
        ) + modes.take(3).mapIndexed { i, m -> (if (i == 0) "Mod" else "Mod${i + 1}") to m } +
            listOf("Mod:F" to modeFreq)
    }

    class TwoVar(
        val n: Double,
        val meanX: Double, val sumX: Double, val sumX2: Double, val popSdX: Double, val sampleSdX: Double,
        val meanY: Double, val sumY: Double, val sumY2: Double, val popSdY: Double, val sampleSdY: Double,
        val sumXY: Double, val minX: Double, val maxX: Double, val minY: Double, val maxY: Double,
    ) {
        fun rows(): List<Pair<String, Double>> = listOf(
            "x̄" to meanX, "Σx" to sumX, "Σx²" to sumX2, "σx" to popSdX, "sx" to sampleSdX, "n" to n,
            "ȳ" to meanY, "Σy" to sumY, "Σy²" to sumY2, "σy" to popSdY, "sy" to sampleSdY, "Σxy" to sumXY,
            "minX" to minX, "maxX" to maxX, "minY" to minY, "maxY" to maxY,
        )
    }

    private fun weights(n: Int, freq: List<Double>?): List<Double> {
        if (freq == null) return List(n) { 1.0 }
        if (freq.size != n) dimError("frequency list size")
        if (freq.any { it < 0 }) mathError("negative frequency")
        return freq
    }

    fun oneVar(xs: List<Double>, freq: List<Double>? = null): OneVar {
        if (xs.isEmpty()) dimError("empty list")
        val w = weights(xs.size, freq)
        val n = w.sum()
        if (n <= 0) mathError("no data")
        val sum = xs.indices.sumOf { xs[it] * w[it] }
        val sumSq = xs.indices.sumOf { xs[it] * xs[it] * w[it] }
        val mean = sum / n
        val ss = xs.indices.sumOf { (xs[it] - mean) * (xs[it] - mean) * w[it] }
        val popSd = sqrt(ss / n)
        val sampleSd = if (n > 1) sqrt(ss / (n - 1)) else Double.NaN

        // Order statistics on the data expanded by (integer) frequencies.
        val pairs = xs.indices.filter { w[it] > 0 }.map { xs[it] to w[it] }.sortedBy { it.first }
        val integerFreq = pairs.all { it.second == floor(it.second) }
        val sorted: List<Double> = if (integerFreq && n <= 100_000) {
            pairs.flatMap { (x, f) -> List(f.toInt()) { x } }
        } else emptyList()
        fun medianOf(l: List<Double>): Double = when {
            l.isEmpty() -> Double.NaN
            l.size % 2 == 1 -> l[l.size / 2]
            else -> (l[l.size / 2 - 1] + l[l.size / 2]) / 2
        }
        val median = medianOf(sorted)
        val half = sorted.size / 2
        // The CG50 leaves the median out of both halves for odd counts.
        val lower = sorted.subList(0, half)
        val upper = sorted.subList(if (sorted.size % 2 == 1) half + 1 else half, sorted.size)
        val q1 = if (sorted.size == 1) sorted[0] else medianOf(lower)
        val q3 = if (sorted.size == 1) sorted[0] else medianOf(upper)

        val grouped = LinkedHashMap<Double, Double>()
        pairs.forEach { (x, f) -> grouped[x] = (grouped[x] ?: 0.0) + f }
        val maxF = grouped.values.maxOrNull() ?: 0.0
        val modes = grouped.filter { it.value == maxF }.keys.toList()

        return OneVar(n, mean, sum, sumSq, popSd, sampleSd, pairs.first().first, q1, median, q3, pairs.last().first, modes, maxF)
    }

    fun twoVar(xs: List<Double>, ys: List<Double>, freq: List<Double>? = null): TwoVar {
        if (xs.size != ys.size) dimError("list sizes differ")
        if (xs.isEmpty()) dimError("empty list")
        val w = weights(xs.size, freq)
        val n = w.sum()
        if (n <= 0) mathError("no data")
        fun s(f: (Int) -> Double) = xs.indices.sumOf { f(it) * w[it] }
        val sumX = s { xs[it] }
        val sumY = s { ys[it] }
        val meanX = sumX / n
        val meanY = sumY / n
        val ssx = s { (xs[it] - meanX) * (xs[it] - meanX) }
        val ssy = s { (ys[it] - meanY) * (ys[it] - meanY) }
        val used = xs.indices.filter { w[it] > 0 }
        return TwoVar(
            n,
            meanX, sumX, s { xs[it] * xs[it] }, sqrt(ssx / n), if (n > 1) sqrt(ssx / (n - 1)) else Double.NaN,
            meanY, sumY, s { ys[it] * ys[it] }, sqrt(ssy / n), if (n > 1) sqrt(ssy / (n - 1)) else Double.NaN,
            s { xs[it] * ys[it] },
            used.minOf { xs[it] }, used.maxOf { xs[it] }, used.minOf { ys[it] }, used.maxOf { ys[it] },
        )
    }

    enum class RegType(val label: String, val formula: String, val coefficientNames: List<String>) {
        LINEAR("Linear ax+b", "y=ax+b", listOf("a", "b")),
        LINEAR_ALT("Linear a+bx", "y=a+bx", listOf("a", "b")),
        QUADRATIC("Quadratic", "y=ax²+bx+c", listOf("a", "b", "c")),
        CUBIC("Cubic", "y=ax³+bx²+cx+d", listOf("a", "b", "c", "d")),
        QUARTIC("Quartic", "y=ax⁴+bx³+cx²+dx+e", listOf("a", "b", "c", "d", "e")),
        LOG("Logarithmic", "y=a+b·ln x", listOf("a", "b")),
        EXP("Exponential ae^bx", "y=a·e^(bx)", listOf("a", "b")),
        AB_EXP("Exponential ab^x", "y=a·b^x", listOf("a", "b")),
        POWER("Power", "y=a·x^b", listOf("a", "b")),
    }

    class Regression(val type: RegType, val coefficients: List<Double>, val r: Double?, val r2: Double) {
        fun eval(x: Double): Double {
            val c = coefficients
            return when (type) {
                RegType.LINEAR -> c[0] * x + c[1]
                RegType.LINEAR_ALT -> c[0] + c[1] * x
                RegType.QUADRATIC, RegType.CUBIC, RegType.QUARTIC -> c.fold(0.0) { acc, k -> acc * x + k }
                RegType.LOG -> c[0] + c[1] * ln(x)
                RegType.EXP -> c[0] * exp(c[1] * x)
                RegType.AB_EXP -> c[0] * Math.pow(c[1], x)
                RegType.POWER -> c[0] * Math.pow(x, c[1])
            }
        }

        fun rows(): List<Pair<String, Double>> =
            type.coefficientNames.zip(coefficients) + listOfNotNull(r?.let { "r" to it }, "r²" to r2)
    }

    private fun correlation(xs: List<Double>, ys: List<Double>, w: List<Double>): Double {
        val n = w.sum()
        val mx = xs.indices.sumOf { xs[it] * w[it] } / n
        val my = ys.indices.sumOf { ys[it] * w[it] } / n
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in xs.indices) {
            sxy += w[i] * (xs[i] - mx) * (ys[i] - my)
            sxx += w[i] * (xs[i] - mx) * (xs[i] - mx)
            syy += w[i] * (ys[i] - my) * (ys[i] - my)
        }
        return sxy / sqrt(sxx * syy)
    }

    /** Weighted least squares a0 + a1·x … for a straight line in transformed coordinates. */
    private fun line(xs: List<Double>, ys: List<Double>, w: List<Double>): Pair<Double, Double> {
        val n = w.sum()
        val mx = xs.indices.sumOf { xs[it] * w[it] } / n
        val my = ys.indices.sumOf { ys[it] * w[it] } / n
        var sxy = 0.0; var sxx = 0.0
        for (i in xs.indices) {
            sxy += w[i] * (xs[i] - mx) * (ys[i] - my)
            sxx += w[i] * (xs[i] - mx) * (xs[i] - mx)
        }
        if (sxx == 0.0) mathError("all x equal")
        val slope = sxy / sxx
        return (my - slope * mx) to slope
    }

    /** Least-squares polynomial fit by Householder QR; returns coefficients highest degree first. */
    private fun polyFit(xs: List<Double>, ys: List<Double>, w: List<Double>, degree: Int): List<Double> {
        val rows = xs.indices.filter { w[it] > 0 }
        val m = rows.size
        val cols = degree + 1
        if (m < cols) dimError("need at least $cols points")
        // Scale x for conditioning.
        val scale = rows.maxOf { abs(xs[it]) }.takeIf { it > 0 } ?: 1.0
        val a = Array(m) { i ->
            val sw = sqrt(w[rows[i]])
            DoubleArray(cols) { j -> sw * Math.pow(xs[rows[i]] / scale, j.toDouble()) }
        }
        val b = DoubleArray(m) { i -> sqrt(w[rows[i]]) * ys[rows[i]] }
        for (k in 0 until cols) {
            var norm = 0.0
            for (i in k until m) norm += a[i][k] * a[i][k]
            norm = sqrt(norm)
            if (norm == 0.0) mathError("singular fit")
            val alpha = if (a[k][k] > 0) -norm else norm
            val v = DoubleArray(m)
            for (i in k until m) v[i] = a[i][k]
            v[k] -= alpha
            var vv = 0.0
            for (i in k until m) vv += v[i] * v[i]
            if (vv == 0.0) continue
            for (j in k until cols) {
                var dot = 0.0
                for (i in k until m) dot += v[i] * a[i][j]
                val f = 2 * dot / vv
                for (i in k until m) a[i][j] -= f * v[i]
            }
            var dot = 0.0
            for (i in k until m) dot += v[i] * b[i]
            val f = 2 * dot / vv
            for (i in k until m) b[i] -= f * v[i]
        }
        val coef = DoubleArray(cols)
        for (k in cols - 1 downTo 0) {
            var s = b[k]
            for (j in k + 1 until cols) s -= a[k][j] * coef[j]
            coef[k] = s / a[k][k]
        }
        // Undo scaling and return highest power first.
        return (cols - 1 downTo 0).map { j -> coef[j] / Math.pow(scale, j.toDouble()) }
    }

    fun regression(type: RegType, xs: List<Double>, ys: List<Double>, freq: List<Double>? = null): Regression {
        if (xs.size != ys.size) dimError("list sizes differ")
        val w = weights(xs.size, freq)
        val needed = when (type) {
            RegType.QUADRATIC -> 3
            RegType.CUBIC -> 4
            RegType.QUARTIC -> 5
            else -> 2
        }
        if (xs.indices.count { w[it] > 0 } < needed) dimError("not enough data")
        fun rSquared(f: (Double) -> Double): Double {
            val n = w.sum()
            val my = ys.indices.sumOf { ys[it] * w[it] } / n
            var res = 0.0; var tot = 0.0
            for (i in xs.indices) {
                res += w[i] * (ys[i] - f(xs[i])).let { it * it }
                tot += w[i] * (ys[i] - my) * (ys[i] - my)
            }
            return if (tot == 0.0) 1.0 else 1 - res / tot
        }
        return when (type) {
            RegType.LINEAR, RegType.LINEAR_ALT -> {
                val (icept, slope) = line(xs, ys, w)
                val r = correlation(xs, ys, w)
                val c = if (type == RegType.LINEAR) listOf(slope, icept) else listOf(icept, slope)
                Regression(type, c, r, r * r)
            }
            RegType.QUADRATIC, RegType.CUBIC, RegType.QUARTIC -> {
                val degree = needed - 1
                val c = polyFit(xs, ys, w, degree)
                val reg = Regression(type, c, null, 0.0)
                Regression(type, c, null, rSquared(reg::eval))
            }
            RegType.LOG -> {
                if (xs.any { it <= 0 }) mathError("x must be positive")
                val lx = xs.map { ln(it) }
                val (a, b) = line(lx, ys, w)
                val r = correlation(lx, ys, w)
                Regression(type, listOf(a, b), r, r * r)
            }
            RegType.EXP, RegType.AB_EXP -> {
                if (ys.any { it <= 0 }) mathError("y must be positive")
                val ly = ys.map { ln(it) }
                val (lnA, b) = line(xs, ly, w)
                val r = correlation(xs, ly, w)
                val c = if (type == RegType.EXP) listOf(exp(lnA), b) else listOf(exp(lnA), exp(b))
                Regression(type, c, r, r * r)
            }
            RegType.POWER -> {
                if (xs.any { it <= 0 } || ys.any { it <= 0 }) mathError("data must be positive")
                val lx = xs.map { ln(it) }
                val ly = ys.map { ln(it) }
                val (lnA, b) = line(lx, ly, w)
                val r = correlation(lx, ly, w)
                Regression(type, listOf(exp(lnA), b), r, r * r)
            }
        }
    }
}
