package com.l0gic0l.casiocalc.math.format

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.eval.ComplexMode
import com.l0gic0l.casiocalc.math.eval.DisplayFormat
import com.l0gic0l.casiocalc.math.eval.FracResult
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.eval.Trig
import com.l0gic0l.casiocalc.math.num.BigRational
import com.l0gic0l.casiocalc.math.num.C
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.R
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/** Turns values into natural-display rows, choosing exact or decimal form like the CG50. */
class Formatter(private val settings: Settings) {

    /** Display style requested for one result. */
    enum class Style { AUTO, DECIMAL, DMS, RECT, POLAR }

    fun format(v: Value, style: Style = Style.AUTO): MathRow = when (v) {
        is Value.Num -> MathRow(complex(v.z, style))
        is Value.Lst -> {
            val nodes = ArrayList<Node>()
            nodes += tok(Tokens.LBRACE)
            v.items.forEachIndexed { i, z ->
                if (i > 0) nodes += tok(Tokens.COMMA)
                nodes += complex(z, style)
            }
            nodes += tok(Tokens.RBRACE)
            MathRow(nodes)
        }
        is Value.Mat -> {
            val cells = v.cells.map { MathRow(complex(it, style)) }
            MathRow(listOf(Template.matrix(v.rows, v.cols, cells)))
        }
    }

    /** True when [v] has an exact form that differs from its decimal form (so S⇔D does something). */
    fun hasAlternateForm(v: Value): Boolean {
        val z = (v as? Value.Num)?.z ?: return v !is Value.Num
        return listOf(z.re, z.im).any { r ->
            val exact = exactRow(r)
            exact != null && exact.any { it is Template || (it as TokenNode).tok === Tokens.PI }
        }
    }

    private fun tok(t: Tok): Node = TokenNode(t)

    // ---- complex numbers ------------------------------------------------------------------

    private fun complex(z: Complex, style: Style): List<Node> {
        if (style == Style.DMS && z.isReal) return dms(z.re.toDouble())
        val decimal = style == Style.DECIMAL
        if (z.isReal) return real(z.re, decimal)
        val polar = style == Style.POLAR || (style != Style.RECT && settings.complex == ComplexMode.POLAR)
        if (polar) {
            val r = C.abs(z)
            val theta = Trig.atan2(z.im, z.re, settings.angle)
            return real(r, decimal) + tok(Tokens.ANGLE) + real(theta, decimal)
        }
        val out = ArrayList<Node>()
        if (!z.re.isZero) out += real(z.re, decimal)
        val im = z.im
        val negative = im.signum() < 0
        val mag = if (negative) R.neg(im) else im
        if (negative) out += tok(Tokens.SUB) else if (out.isNotEmpty()) out += tok(Tokens.ADD)
        if (mag != Real.ONE && !(mag is Real.Approx && mag.v == 1.0)) out += real(mag, decimal)
        out += tok(Tokens.I)
        return out
    }

    // ---- reals ----------------------------------------------------------------------------

    fun real(x: Real, decimal: Boolean = false): List<Node> {
        if (!decimal && settings.exactOutput) {
            exactRow(x)?.let { return it }
        }
        return decimalNodes(x.toDouble())
    }

    /** Natural form of [x] (fraction, surd, π multiple) if it has one that fits the display. */
    fun exactRow(x: Real): List<Node>? {
        val e: Real.Exact = when (x) {
            is Real.Exact -> x
            is Real.Approx -> recognize(x.v) ?: return null
        }
        return exactNodes(e)
    }

    private fun digitsOf(n: BigInteger): List<Node> = n.abs().toString().map { tok(Tokens.digit(it - '0')) }

    private fun tooLong(n: BigInteger) = n.abs().toString().length > 10

    private fun exactNodes(e: Real.Exact): List<Node>? {
        if (e.isZero) return listOf(tok(Tokens.digit(0)))
        val den = lcm(e.a.den, e.b.den)
        val a = e.a.num * (den / e.a.den)
        val b = e.b.num * (den / e.b.den)
        if (tooLong(a) || tooLong(b) || tooLong(den) || e.r > 9_999_999_999L) return null

        val pi = e.pi == 1
        fun surdTerm(coef: BigInteger): List<Node> {
            val out = ArrayList<Node>()
            if (coef.abs() != BigInteger.ONE) out += digitsOf(coef)
            out += Template.create(TemplateKind.SQRT, digitsOf(BigInteger.valueOf(e.r)))
            return out
        }

        // Single term: sign goes in front of the whole fraction.
        if (a.signum() == 0 || b.signum() == 0) {
            val negative = (if (b.signum() == 0) a else b).signum() < 0
            val num = ArrayList<Node>()
            if (b.signum() == 0) {
                if (!(pi && a.abs() == BigInteger.ONE)) num += digitsOf(a)
            } else {
                num += surdTerm(b)
            }
            if (pi) num += tok(Tokens.PI)
            val out = ArrayList<Node>()
            if (negative) out += tok(Tokens.SUB)
            if (den == BigInteger.ONE) {
                out += num
            } else if (settings.fracResult == FracResult.MIXED && b.signum() == 0 && !pi && a.abs() > den) {
                val whole = a.abs() / den
                val rem = a.abs() % den
                out += Template.create(TemplateKind.MIXED, digitsOf(whole), digitsOf(rem), digitsOf(den))
            } else {
                out += Template.create(TemplateKind.FRACTION, num, digitsOf(den))
            }
            return out
        }

        // Two terms: (a ± b√r)/den.
        val num = ArrayList<Node>()
        if (a.signum() < 0) num += tok(Tokens.SUB)
        num += digitsOf(a)
        num += tok(if (b.signum() < 0) Tokens.SUB else Tokens.ADD)
        num += surdTerm(b)
        val withPi = if (pi) listOf(tok(Tokens.LPAREN)) + num + tok(Tokens.RPAREN) + tok(Tokens.PI) else num
        return if (den == BigInteger.ONE) withPi else listOf(Template.create(TemplateKind.FRACTION, withPi, digitsOf(den)))
    }

    private fun lcm(a: BigInteger, b: BigInteger) = a / a.gcd(b) * b

    // ---- decimals -------------------------------------------------------------------------

    fun decimalNodes(v: Double): List<Node> {
        val s = decimalString(v)
        return textToNodes(s)
    }

    /** Converts "−1.25E−12" style text to tokens, rendering the exponent as ×10 with a superscript. */
    private fun textToNodes(s: String): List<Node> {
        val out = ArrayList<Node>()
        val ePos = s.indexOf('E')
        val mantissa = if (ePos >= 0) s.substring(0, ePos) else s
        for (ch in mantissa) {
            out += when (ch) {
                '-' -> tok(Tokens.SUB)
                '.' -> tok(Tokens.POINT)
                else -> tok(Tokens.digit(ch - '0'))
            }
        }
        if (ePos >= 0) {
            val exp = s.substring(ePos + 1)
            out += tok(Tokens.MUL)
            out += tok(Tokens.TEN)
            val expNodes = exp.map { if (it == '-') tok(Tokens.NEG) else tok(Tokens.digit(it - '0')) }
            out += Template.create(TemplateKind.POWER, expNodes)
        }
        return out
    }

    /** Decimal text of [v] under the current Norm/Fix/Sci/Eng setting, exponent marked by 'E'. */
    fun decimalString(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v == 0.0) {
            return when (settings.display.kind) {
                DisplayFormat.Kind.FIX -> if (settings.display.digits == 0) "0" else "0." + "0".repeat(settings.display.digits)
                DisplayFormat.Kind.SCI -> sci(0.0, settings.display.digits)
                else -> "0"
            }
        }
        val d = settings.display
        if (settings.eng) return eng(v, if (d.kind == DisplayFormat.Kind.SCI) d.digits else 10)
        return when (d.kind) {
            DisplayFormat.Kind.NORM1, DisplayFormat.Kind.NORM2 -> {
                val small = BigDecimal(if (d.kind == DisplayFormat.Kind.NORM1) "0.01" else "1E-9")
                val rounded = BigDecimal(v).round(MathContext(10, RoundingMode.HALF_UP))
                val av = rounded.abs()
                if (av < small || av >= BigDecimal("1E10")) sci(v, 10, strip = true)
                else plain(rounded.stripTrailingZeros())
            }
            DisplayFormat.Kind.FIX -> {
                val bd = BigDecimal(v).setScale(d.digits, RoundingMode.HALF_UP)
                if (bd.abs() >= BigDecimal("1E10")) sci(v, 10, strip = true) else plain(bd)
            }
            DisplayFormat.Kind.SCI -> sci(v, if (d.digits == 0) 10 else d.digits, strip = d.digits == 0)
        }
    }

    private fun plain(bd: BigDecimal): String {
        val s = bd.toPlainString()
        return if (s == "-0") "0" else s
    }

    private fun sci(v: Double, sig: Int, strip: Boolean = false): String {
        if (v == 0.0) return "0" + (if (sig > 1 && !strip) "." + "0".repeat(sig - 1) else "") + "E0"
        var bd = BigDecimal(v).round(MathContext(sig, RoundingMode.HALF_UP))
        val exp = bd.precision() - bd.scale() - 1
        var mant = bd.movePointLeft(exp)
        mant = mant.setScale(sig - 1, RoundingMode.HALF_UP)
        if (strip) mant = mant.stripTrailingZeros()
        var ms = mant.toPlainString()
        if (strip && ms.contains('.')) ms = ms.trimEnd('0').trimEnd('.')
        return "${ms}E$exp"
    }

    private fun eng(v: Double, sig: Int): String {
        val bd = BigDecimal(v).round(MathContext(sig, RoundingMode.HALF_UP))
        val exp = bd.precision() - bd.scale() - 1
        val e3 = Math.floorDiv(exp, 3) * 3
        val mant = bd.movePointLeft(e3).stripTrailingZeros()
        return "${plain(mant)}E$e3"
    }

    private fun dms(v: Double): List<Node> {
        val neg = v < 0
        var a = abs(v)
        var deg = kotlin.math.floor(a)
        a = (a - deg) * 60
        var min = kotlin.math.floor(a + 1e-9)
        var sec = (a - min) * 60
        sec = BigDecimal(sec).setScale(2, RoundingMode.HALF_UP).toDouble()
        if (sec >= 60) {
            sec -= 60; min += 1
        }
        if (min >= 60) {
            min -= 60; deg += 1
        }
        val secText = BigDecimal(sec).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        val out = ArrayList<Node>()
        if (neg) out += tok(Tokens.SUB)
        out += textToNodes(deg.toLong().toString())
        out += tok(Tokens.DMS)
        out += textToNodes(min.toLong().toString())
        out += tok(Tokens.DMS)
        out += textToNodes(secText)
        out += tok(Tokens.DMS)
        return out
    }

    companion object {
        /**
         * Recognizes doubles from numeric work (integrals, derivatives, logs) that are really simple
         * fractions, multiples of π or surds, the way the calculator shows ∫₀¹x²dx as 1/3.
         */
        fun recognize(v: Double): Real.Exact? {
            if (v.isNaN() || v.isInfinite() || v == 0.0) return null
            if (abs(v) >= 1e10 || abs(v) < 1e-6) return null
            BigRational.approximate(v, 1000, 1e-11)?.let { return Real.of(it) as Real.Exact }
            BigRational.approximate(v / Math.PI, 100, 1e-11)?.let {
                return Real.exact(it, pi = 1) as? Real.Exact
            }
            val sq = BigRational.approximate(v * v, 100, 1e-11)
            if (sq != null && sq.signum > 0 && sq.num.bitLength() < 40) {
                val s = R.sqrt(Real.of(sq))
                if (s is Real.Exact && s.r <= 10_000) return (if (v < 0) R.neg(s) else s) as Real.Exact
            }
            return null
        }
    }
}
