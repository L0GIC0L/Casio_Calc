package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.math.cas.Calculus
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

    @Test fun definiteIntegralsAreExact() {
        val f = Formatter(settings)
        fun d(s: String, a: String, b: String) = Calculus.integral(row(s), row(a), row(b), memory, settings).definite!!
        val sin = d("sin(X)", "0", "π")
        assertEquals("2", RowText.linear(f.format(sin.value!!)))
        val third = d("X{pow:2}", "0", "1")
        assertEquals("1/3", RowText.linear(f.format(third.value!!)))
        val log = d("{frac:1|X}", "1", "e")
        assertEquals("1", RowText.linear(f.format(log.value!!)))
        assertNotNull(d("e{pow:~X{pow:2}}", "0", "1").numeric)
    }
}
