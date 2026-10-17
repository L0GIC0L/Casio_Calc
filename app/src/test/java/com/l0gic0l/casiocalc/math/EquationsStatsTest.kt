package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.numeric.Equations
import com.l0gic0l.casiocalc.math.numeric.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquationsStatsTest {
    private val f = Formatter(Settings())
    private fun show(z: Complex) = RowText.structural(f.format(Value.Num(z)))
    private fun poly(vararg c: Long) = Equations.polynomialRoots(c.map { Real.of(it) })

    @Test fun quadraticExact() {
        assertEquals(listOf("−1", "3"), poly(1, -2, -3).map(::show))
        assertEquals(listOf("{frac:1−{sqrt:5}|2}", "{frac:1+{sqrt:5}|2}"), poly(1, -1, -1).map(::show))
        assertEquals(listOf("−1+{sqrt:2}i", "−1−{sqrt:2}i"), poly(1, 2, 3).map(::show))
        assertEquals(listOf("{frac:1|2}", "{frac:2|3}"), poly(6, -7, 2).map(::show))
    }

    @Test fun higherDegreeExact() {
        // (x − 1)(x − 2)(x + 3) = x³ − 7x + 6
        assertEquals(listOf("−3", "1", "2"), poly(1, 0, -7, 6).map(::show))
        // x³ − 2x = x(x² − 2)
        assertEquals(listOf("−{sqrt:2}", "0", "{sqrt:2}"), poly(1, 0, -2, 0).map(::show))
        // (x² − 2)(x² + 1)
        assertEquals(listOf("−{sqrt:2}", "{sqrt:2}", "i", "−i"), poly(1, 0, -1, 0, -2).map(::show))
        // x⁵ − 1 has one real root and four complex ones
        val r = poly(1, 0, 0, 0, 0, -1)
        assertEquals("1", show(r[0]))
        assertEquals(5, r.size)
        r.forEach { z ->
            val v = Equations.evaluate(listOf(1, 0, 0, 0, 0, -1).map { Real.of(it.toLong()) }, z)
            assertTrue(kotlin.math.hypot(v.re.toDouble(), v.im.toDouble()) < 1e-9)
        }
    }

    @Test fun linearSystemExact() {
        // x + y = 3, x − y = 1  →  x = 2, y = 1;   2x + 3y = 1, x − y = 0 → 1/5, 1/5
        val m = Value.Mat(2, 3, listOf(1, 1, 3, 1, -1, 1).map { Complex.of(it.toLong()) })
        val res = Equations.solveLinear(m) as Equations.LinearResult.Unique
        assertEquals(listOf("2", "1"), res.values.map(::show))
        val m2 = Value.Mat(2, 3, listOf(2, 3, 1, 1, -1, 0).map { Complex.of(it.toLong()) })
        assertEquals(listOf("{frac:1|5}", "{frac:1|5}"), (Equations.solveLinear(m2) as Equations.LinearResult.Unique).values.map(::show))
        val singular = Value.Mat(2, 3, listOf(1, 1, 1, 2, 2, 3).map { Complex.of(it.toLong()) })
        assertEquals(Equations.LinearResult.NoSolution, Equations.solveLinear(singular))
        val dependent = Value.Mat(2, 3, listOf(1, 1, 1, 2, 2, 2).map { Complex.of(it.toLong()) })
        assertEquals(Equations.LinearResult.Infinite, Equations.solveLinear(dependent))
    }

    @Test fun oneVariable() {
        val s = Stats.oneVar(listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0))
        assertEquals(4.0, s.mean, 1e-12)
        assertEquals(2.0, s.popSd, 1e-12)
        assertEquals(2.0, s.q1, 1e-12)
        assertEquals(4.0, s.median, 1e-12)
        assertEquals(6.0, s.q3, 1e-12)
        val withFreq = Stats.oneVar(listOf(1.0, 2.0), listOf(3.0, 1.0))
        assertEquals(1.25, withFreq.mean, 1e-12)
        assertEquals(listOf(1.0), withFreq.modes)
    }

    @Test fun regressions() {
        val xs = listOf(1.0, 2.0, 3.0, 4.0)
        val lin = Stats.regression(Stats.RegType.LINEAR, xs, xs.map { 2 * it + 1 })
        assertEquals(2.0, lin.coefficients[0], 1e-12)
        assertEquals(1.0, lin.coefficients[1], 1e-12)
        assertEquals(1.0, lin.r!!, 1e-12)
        val quad = Stats.regression(Stats.RegType.QUADRATIC, xs, xs.map { it * it - 3 * it + 2 })
        assertEquals(1.0, quad.coefficients[0], 1e-9)
        assertEquals(-3.0, quad.coefficients[1], 1e-9)
        assertEquals(2.0, quad.coefficients[2], 1e-9)
        val ex = Stats.regression(Stats.RegType.EXP, xs, xs.map { 3 * kotlin.math.exp(0.5 * it) })
        assertEquals(3.0, ex.coefficients[0], 1e-9)
        assertEquals(0.5, ex.coefficients[1], 1e-9)
        val pw = Stats.regression(Stats.RegType.POWER, xs, xs.map { 2 * Math.pow(it, 1.5) })
        assertEquals(1.5, pw.coefficients[1], 1e-9)
    }
}
