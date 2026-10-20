package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.math.cas.Calculus
import com.l0gic0l.casiocalc.math.cas.Definite
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.cas.Differentiator
import com.l0gic0l.casiocalc.math.cas.Render
import com.l0gic0l.casiocalc.math.cas.Step
import com.l0gic0l.casiocalc.math.cas.eval
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.math.numeric.Numeric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasTest {
    private val memory = Memory()
    private val settings = Settings()
    private val render = Render()

    private fun text(s: com.l0gic0l.casiocalc.math.cas.Sym) = RowText.linear(render.row(s))

    private fun titles(steps: List<Step>): Set<String> {
        val out = HashSet<String>()
        fun walk(s: Step) {
            out += s.title
            s.children.forEach(::walk)
        }
        steps.forEach(::walk)
        return out
    }

    @Test fun derivativesMatchNumericDerivatives() {
        val cases = listOf(
            "X{pow:3}", "sin(X{pow:2})", "X*e{pow:X}", "{frac:X|X+1}", "ln(X{pow:2}+1)", "X{pow:X}", "atan(2X)",
            "{sqrt:X{pow:2}+1}", "e{pow:sin(X)}", "tan(X)", "{abs:X}", "log(X)", "asin({frac:X|3})", "cos(X)sin(X)",
            "{frac:1|X}", "{frac:X{pow:2}+1|X{pow:3}-2X}", "2{pow:X}", "{root:3|X}", "sinh(3X)", "X*ln(X)-X",
        )
        val failures = ArrayList<String>()
        for (c in cases) {
            val f = Calculus.parse(row(c))
            val d = Differentiator("X").withSteps(f).first
            // Points clear of the poles of tan and 1/(x³ − 2x), where a numeric derivative is unreliable.
            for (t in listOf(0.4, 0.9, 1.2, 2.3)) {
                val expected = Numeric.derivative({ f.eval("X", it) }, t)
                val actual = d.eval("X", t)
                if (expected.isFinite() && Math.abs(expected - actual) > 1e-6 * maxOf(1.0, Math.abs(expected))) {
                    failures += "$c at $t: $actual vs $expected (${text(d)})"
                    break
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun derivativeDisplay() {
        fun d(s: String) = text(Calculus.derivative(row(s), 1, null, memory, settings).derivative)
        assertEquals("3X^2", d("X{pow:3}"))
        assertEquals("cos(X)", d("sin(X)"))
        assertEquals("1/X", d("ln(X)"))
        assertEquals("2Xcos(X^2)", d("sin(X{pow:2})"))
        assertEquals("1/(2√X)", d("{sqrt:X}"))
        val steps = titles(Calculus.derivative(row("X{pow:2}sin(X)"), 1, null, memory, settings).steps)
        assertTrue(steps.toString(), "Product rule" in steps && "Power rule" in steps && "Derivative of sin" in steps)
        val chain = titles(Calculus.derivative(row("sin(X{pow:2})"), 1, null, memory, settings).steps)
        assertTrue(chain.toString(), "Chain rule" in chain)
        val second = Calculus.derivative(row("X{pow:4}"), 2, null, memory, settings)
        assertEquals("12X^2", text(second.derivative))
    }

    @Test fun integralsAreFoundAndVerified() {
        val cases = listOf(
            "X{pow:2}", "3X{pow:2}+2X+1", "{frac:1|X}", "{sqrt:X}", "e{pow:X}", "sin(X)", "cos(3X)", "X*e{pow:X}",
            "X{pow:2}e{pow:X}", "X*sin(X)", "ln(X)", "X*ln(X)", "e{pow:X}sin(X)", "e{pow:X}cos(X)", "2X*cos(X{pow:2})",
            "X*e{pow:X{pow:2}}", "{frac:X|X{pow:2}+1}", "{frac:1|X{pow:2}-1}", "{frac:1|X{pow:2}+1}", "{frac:1|X{pow:2}+4}",
            "{frac:2X+3|X{pow:2}+3X+2}", "{frac:X{pow:3}|X{pow:2}+1}", "sin(X){pow:2}", "cos(X){pow:2}", "sin(X){pow:3}",
            "tan(X)", "tan(X){pow:2}", "{frac:1|{sqrt:1-X{pow:2}}}", "atan(X)", "X{sqrt:X+1}", "(X+1){pow:5}",
            "{frac:ln(X)|X}", "sin(X)cos(X)", "2{pow:X}", "{frac:1|X(X+1)}", "{frac:X+1|X{pow:2}(X-1)}", "X*cos(2X)",
            "{frac:1|cos(X){pow:2}}", "e{pow:2X+1}", "AX{pow:2}", "{frac:3|2X+1}", "sinh(X)", "{frac:1|X{pow:2}+2X+5}",
            "X{pow:3}e{pow:X{pow:2}}", "{frac:e{pow:X}|1+e{pow:X}}", "sin(X){pow:2}cos(X){pow:2}", "X{pow:2}ln(X)",
            "{frac:1|X*ln(X)}", "cos({sqrt:X})", "{frac:X|{sqrt:X{pow:2}+1}}",
            "{frac:1|{sqrt:X}*(1+X)}", "ln(X){pow:2}", "sin(ln(X))", "sin(X){pow:3}*cos(X){pow:2}", "{frac:1|{sqrt:4-X{pow:2}}}",
            "{frac:1|{sqrt:2X-X{pow:2}}}", "{frac:e{pow:X}|1+e{pow:2X}}", "{frac:1|X*{sqrt:X{pow:2}-1}}", "{frac:1|X{pow:2}+A{pow:2}}",
            "{sqrt:1-X{pow:2}}", "{sqrt:X{pow:2}+4}", "{frac:X|X{pow:4}+1}", "{frac:1|e{pow:X}+1}", "{frac:X{pow:2}|{sqrt:1-X{pow:2}}}",
            "{abs:X}", "cosh(X){pow:2}-sinh(X){pow:2}", "sin(3X)*cos(2X)", "X{pow:3}*ln(X)",
        )
        val failures = ArrayList<String>()
        for (c in cases) {
            val s = runCatching { Calculus.integral(row(c), null, null, memory, settings) }.getOrElse {
                failures += "$c threw $it"
                null
            } ?: continue
            when {
                s.antiderivative == null -> failures += "$c: not found"
                !s.verified -> failures += "$c: wrong ${text(s.antiderivative)}"
            }
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun integralDisplayAndSteps() {
        fun i(s: String) = Calculus.integral(row(s), null, null, memory, settings)
        assertEquals("(X^2)/2", text(i("X").antiderivative!!))
        assertEquals("sin(X)", text(i("cos(X)").antiderivative!!))
        assertTrue("Integration by parts" in titles(i("X*e{pow:X}").steps))
        assertTrue("Solve for the integral" in titles(i("e{pow:X}sin(X)").steps))
        assertTrue("Partial fractions" in titles(i("{frac:1|X{pow:2}-1}").steps))
        assertTrue("Substitution" in titles(i("2X*cos(X{pow:2})").steps))
        assertTrue("Trigonometric identity" in titles(i("sin(X){pow:2}").steps))
        // Textbook-shaped answers: the natural substitution, product-to-sum, parts before x = eᵘ.
        assertEquals("2tan⁻¹(√X)", text(i("{frac:1|{sqrt:X}*(1+X)}").antiderivative!!))
        assertEquals("−(cos(5X))/(10)−(cos(X))/2", text(i("sin(3X)*cos(2X)").antiderivative!!))
        assertEquals("X", text(i("cosh(X){pow:2}-sinh(X){pow:2}").antiderivative!!))
        assertTrue("Integration by parts" in titles(i("X{pow:3}*ln(X)").steps))
        assertTrue("Complete the square" in titles(i("{frac:1|{sqrt:2X-X{pow:2}}}").steps))
    }

    @Test(expected = CalcException::class) fun divisionByZeroIsAnError() {
        Calculus.integral(row("{frac:1|0}"), null, null, memory, settings)
    }

    private fun definite(s: String, a: String, b: String) = Calculus.integral(row(s), row(a), row(b), memory, settings).definite!!

    @Test fun definiteIntegralsAreExact() {
        val f = Formatter(settings)
        fun exact(s: String, a: String, b: String): String {
            val d = definite(s, a, b)
            assertEquals(s, Definite.Status.EXACT, d.status)
            return RowText.linear(f.format(d.value!!))
        }
        assertEquals("2", exact("sin(X)", "0", "π"))
        assertEquals("1/3", exact("X{pow:2}", "0", "1"))
        assertEquals("1", exact("{frac:1|X}", "1", "e"))
        val gauss = definite("e{pow:~X{pow:2}}", "0", "1")
        assertEquals(Definite.Status.NUMERIC, gauss.status)
        assertEquals(0.746824, gauss.numeric!!, 1e-6)
    }

    /** A pole of the integrand inside or at the end of the interval: no finite value, and none is shown. */
    @Test fun divergentIntegralsSaySo() {
        for ((s, a, b) in listOf(
            Triple("{frac:1|X{pow:2}}", "~1", "1"),
            Triple("{frac:1|X}", "0", "1"),
            Triple("{frac:X+5|X{pow:2}+X-2}", "0", "1"),
        )) {
            val d = definite(s, a, b)
            assertEquals("$s on [$a, $b]", Definite.Status.DIVERGES, d.status)
        }
    }

    /** Improper but convergent, or hard for quadrature: F(b) − F(a) still applies because F is continuous. */
    @Test fun continuousAntiderivativesGiveExactValues() {
        fun value(s: String, a: String, b: String): Double {
            val d = definite(s, a, b)
            assertEquals("$s on [$a, $b]", Definite.Status.EXACT, d.status)
            return (d.value as Value.Num).z.re.toDouble()
        }
        assertEquals(0.0, value("{frac:1|{root:3|X}}", "~1", "1"), 1e-9)
        assertEquals(-1.0, value("ln(X)", "0", "1"), 1e-9)
        val start = System.nanoTime()
        assertEquals(Math.sin(25000.0) / 50, value("cos(50X)", "0", "500"), 1e-9)
        val seconds = (System.nanoTime() - start) / 1e9
        assertTrue("took $seconds s", seconds < 3)
    }

    @Test fun lettersLeaveDefiniteIntegralsSymbolic() {
        val d = definite("A*e{pow:X{pow:2}}", "0", "1")
        assertEquals(Definite.Status.PARAMETERS, d.status)
        assertEquals(null, d.symbolic)
    }

    @Test fun derivativeValueAtAPoint() {
        fun at(s: String, p: String) = Calculus.derivative(row(s), 1, row(p), memory, settings)
        // x/|x| at 0 would simplify to 0·0⁻¹ = 0; it is not defined there.
        val abs = at("{abs:X}", "0")
        assertEquals(null, abs.symbolicAt)
        assertEquals(null, abs.valueAt)
        // A stays a letter, whatever is stored in A.
        memory.setVar("A", Value.of(5))
        val param = at("AX{pow:2}", "1")
        assertEquals("2A", text(param.symbolicAt!!))
        assertEquals(null, param.valueAt)
        val pi = at("X{pow:2}sin(X)", "π")
        assertEquals("−π^2", text(pi.symbolicAt!!))
        assertEquals(-Math.PI * Math.PI, (pi.valueAt as Value.Num).z.re.toDouble(), 1e-9)
    }

    /** Poles away from the middle of the interval, which the old two-grid test missed depending on where the samples fell. */
    @Test fun polesAnywhereInTheIntervalDiverge() {
        for ((s, a, b) in listOf(
            Triple("{frac:1|X{pow:2}}", "~3", "{frac:π|2}"),
            Triple("tan(X)", "0", "2"),
            Triple("{frac:1|cos(X){pow:2}}", "0", "2"),
            Triple("{frac:1|cos(X){pow:2}}", "0", "3"),
            Triple("{frac:1|X}", "~1", "0.3"),
            Triple("{frac:1|sin(X){pow:2}}", "~2", "10"),
            Triple("{frac:1|sin(X){pow:2}}", "1", "4"),
            Triple("{frac:1|cos(X)}", "0", "2"),
            // The integrator's running totals used to cancel to 0 after a 10³¹ term at the pole.
            Triple("{frac:1|X{pow:3}}", "~2", "1.2"),
            Triple("{frac:1|X{pow:2}}", "~0.7", "0.9"),
            // A steep x³ part used to take every place the old test looked at.
            Triple("X{pow:2}+{frac:1|X}", "~1", "20"),
            Triple("{frac:1|X{pow:2}}", "~1#~11", "2#~11"),
        )) {
            val d = definite(s, a, b)
            assertEquals("$s on [$a, $b]", Definite.Status.DIVERGES, d.status)
        }
    }

    /** F has a log singularity at an end; simplifying F(b) cancelled ln 0 against ln 0 and gave a wrong exact value. */
    @Test fun logSingularityAtAnEndFallsBackToTheNumericValue() {
        val d = definite("ln(4-X{pow:2})", "0", "2")
        assertEquals(Definite.Status.NUMERIC, d.status)
        assertEquals(8 * Math.log(2.0) - 4, d.numeric!!, 1e-8)
        val e = definite("ln(1-X{pow:2})", "~1", "1")
        assertEquals(Definite.Status.NUMERIC, e.status)
        assertEquals(4 * Math.log(2.0) - 4, e.numeric!!, 1e-8)
    }

    /** Bounded but faster than the sample grid: still F(b) − F(a), not "does not converge". */
    @Test fun fastOscillationsAreNotDivergent() {
        for ((s, b, expected) in listOf(
            Triple("sin(1000X)", "1000", (1 - Math.cos(1e6)) / 1000),
            Triple("sin(X)", "500000", 1 - Math.cos(5e5)),
        )) {
            val d = definite(s, "0", b)
            assertEquals("$s on [0, $b]", Definite.Status.EXACT, d.status)
            assertEquals(expected, (d.value as Value.Num).z.re.toDouble(), 1e-6)
        }
    }

    @Test fun integrableSingularitiesStayExact() {
        for ((s, a, b, expected) in listOf(
            listOf("{frac:1|{sqrt:X-1000000}}", "1000000", "1000001", "2"),
            listOf("(X-1000){pow:~0.7}", "1000", "1001", "${10.0 / 3}"),
            listOf("X{pow:~0.97}", "0", "1", "${100.0 / 3}"),
        )) {
            val d = definite(s, a, b)
            assertEquals("$s on [$a, $b]", Definite.Status.EXACT, d.status)
            assertEquals(expected.toDouble(), (d.value as Value.Num).z.re.toDouble(), 1e-6)
        }
    }

    /** F(b) − F(a) overflows on the way (cosh 240), but the integral itself is in range. */
    @Test fun overflowOnTheWayIsNotTooLarge() {
        val d = definite("tanh(X)", "0", "240")
        assertEquals(Definite.Status.NUMERIC, d.status)
        assertEquals(240 - Math.log(2.0), d.numeric!!, 1e-6)
        assertEquals(Definite.Status.NUMERIC, definite("e{pow:X}", "230", "230.5").status)
        val p = Calculus.derivative(row("ln(cosh(X))"), 1, row("240"), memory, settings)
        assertEquals(1.0, (p.valueAt as Value.Num).z.re.toDouble(), 1e-12)
        assertEquals(false, p.tooLarge)
    }

    @Test fun hugeValuesAreTooLargeNotDivergent() {
        assertEquals(Definite.Status.TOO_LARGE, definite("e{pow:X}", "0", "1000").status)
        assertEquals(Definite.Status.TOO_LARGE, definite("e{pow:X}", "0", "300").status)
        // 2^(2³²) used to wrap round to 2⁰ = 1.
        assertEquals(Definite.Status.TOO_LARGE, definite("2{pow:4294967296}X", "0", "1").status)
    }

    /** X in a limit or in the point is the variable, not the stored X. */
    @Test fun xInALimitOrPointStaysSymbolic() {
        memory.setVar("X", Value.of(3))
        val d = definite("X{pow:2}", "0", "X")
        assertEquals(Definite.Status.PARAMETERS, d.status)
        assertEquals("X^3/3", text(d.symbolic!!).replace("(", "").replace(")", ""))
        val log = Calculus.derivative(row("ln(X)"), 1, row("X"), memory, settings)
        assertNotNull(log.symbolicAt)
        assertEquals(null, log.valueAt)
    }

    @Test fun removableZeroOverZeroAtAPointIsDefined() {
        fun at(s: String, p: String) = Calculus.derivative(row(s), 1, row(p), memory, settings)
        for (s in listOf("X{abs:X}", "X{pow:2}{abs:X}", "{abs:X{pow:3}}")) {
            val d = at(s, "0")
            assertEquals(s, "0", d.symbolicAt?.let(::text))
        }
        // tan has a pole at π/2 even though the double cos(π/2) is not quite 0.
        assertEquals(null, at("tan(X)", "{frac:π|2}").symbolicAt)
        // Defined but out of range: the formula stays, with no value.
        val big = at("e{pow:X{pow:2}}", "30")
        assertNotNull(big.symbolicAt)
        assertEquals(null, big.valueAt)
        assertEquals(true, big.tooLarge)
        // Steeper, or away from 0: the gap still shrinks with the step.
        assertEquals("0", at("1000X{abs:X}", "0").symbolicAt?.let(::text))
        assertEquals("0", at("(X-1000){abs:X-1000}", "1000").symbolicAt?.let(::text))
        // Kinks, however small or steep, have no derivative; nor does a point where f is undefined.
        for (s in listOf("20000X+{abs:X}", "{frac:{abs:X}|100000}", "X{pow:2}ln({abs:X})", "{frac:X{pow:3}|{abs:X}}")) {
            assertEquals(s, null, at(s, "0").symbolicAt)
        }
        // A constant with letters about is a value, not "too large".
        val constant = at("AX{pow:2}", "0")
        assertEquals(false, constant.tooLarge)
        assertEquals(0.0, (constant.valueAt as Value.Num).z.re.toDouble(), 0.0)
    }

    @Test fun quadraticTableWithLettersHasNoRootsOfNegatives() {
        for (s in listOf("{frac:1|X{pow:2}-A{pow:2}}", "{frac:1|A-X{pow:2}}", "{frac:1|AX{pow:2}-1}")) {
            val sol = Calculus.integral(row(s), null, null, memory, settings)
            val anti = sol.antiderivative ?: continue
            assertTrue("$s: ${text(anti)}", sol.verified)
            assertTrue("$s: ${text(anti)}", "√(−" !in text(anti) && "√(-" !in text(anti))
        }
    }

    @Test fun answersDefinedOnlyFurtherOutAreChecked() {
        for (s in listOf("{sqrt:X-5}", "ln(X-4)", "{frac:1|{sqrt:X{pow:2}-9}}")) {
            val sol = Calculus.integral(row(s), null, null, memory, settings)
            assertNotNull(s, sol.antiderivative)
            assertTrue(s, sol.verified)
        }
    }

    @Test fun zeroOverZeroIsAnError() {
        for (s in listOf("{frac:0|0}", "{frac:X-X|X-X}", "0/0")) {
            val failed = runCatching { Calculus.integral(row(s), row("0"), row("1"), memory, settings) }.exceptionOrNull()
            assertTrue(s, failed is CalcException)
        }
    }
}
