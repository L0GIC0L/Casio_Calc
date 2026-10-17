package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.eval.ComplexMode
import com.l0gic0l.casiocalc.math.eval.DisplayFormat
import com.l0gic0l.casiocalc.math.eval.FracResult
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.math.num.Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CalculatorTest {
    private val memory = Memory()

    private fun calc(input: String, settings: Settings = Settings()): String {
        val result = Calculator.evaluate(row(input), memory, settings)
        return RowText.structural(Formatter(settings).format(result.value, result.style))
    }

    private fun dec(input: String, settings: Settings = Settings()): String {
        val result = Calculator.evaluate(row(input), memory, settings)
        return RowText.structural(Formatter(settings).format(result.value, Formatter.Style.DECIMAL))
    }

    private fun error(input: String, settings: Settings = Settings()): ErrorKind {
        try {
            Calculator.evaluate(row(input), memory, settings)
        } catch (e: CalcException) {
            return e.kind
        }
        fail("expected an error for $input")
        throw IllegalStateException()
    }

    private val rad = Settings(angle = AngleUnit.RAD)

    @Test fun fractions() {
        assertEquals("{frac:2|3}", calc("{frac:2|3}"))
        assertEquals("{frac:1|2}", calc("{frac:1|3}+{frac:1|6}"))
        assertEquals("{frac:3|10}", calc("0.1+0.2"))
        assertEquals("−{frac:1|4}", calc("{frac:1|4}-{frac:1|2}"))
        assertEquals("2", calc("{frac:4|2}"))
        assertEquals("{mixed:1|1|2}", calc("{frac:3|2}", Settings(fracResult = FracResult.MIXED)))
        assertEquals("{frac:7|4}", calc("{mixed:1|3|4}"))
    }

    @Test fun surdsAndPi() {
        assertEquals("2{sqrt:2}", calc("{sqrt:8}"))
        assertEquals("{frac:{sqrt:2}|2}", calc("{sqrt:{frac:1|2}}"))
        assertEquals("−1+{sqrt:2}", calc("{frac:1|1+{sqrt:2}}"))
        assertEquals("{frac:1+{sqrt:5}|2}", calc("{frac:1+{sqrt:5}|2}"))
        assertEquals("{frac:2π|3}", calc("{frac:2π|3}"))
        assertEquals("π", calc("π"))
        assertEquals("6", calc("{sqrt:2}{sqrt:18}"))
        assertEquals("{sqrt:6}", calc("{sqrt:2}{sqrt:3}"))
        assertEquals("2", calc("{root:3|8}"))
        assertEquals("−2", calc("{root:3|~8}"))
    }

    @Test fun precedenceLikeCasio() {
        assertEquals("1", calc("6/2(1+2)"))
        assertEquals("−4", calc("~2²"))
        assertEquals("12", calc("2*3!"))
        memory.setVar("A", Value.of(3))
        assertEquals("6", calc("2A"))
        assertEquals("9", calc("A²"))
        assertEquals("120", calc("10nCr3"))
        assertEquals("720", calc("10nPr3"))
        assertEquals("1", calc("2<3"))
    }

    @Test fun trigExact() {
        assertEquals("{frac:1|2}", calc("sin(30)"))
        assertEquals("{frac:{sqrt:3}|2}", calc("sin(60)"))
        assertEquals("{frac:{sqrt:2}|2}", calc("cos(45)"))
        assertEquals("{sqrt:3}", calc("tan(60)"))
        assertEquals("−{frac:1|2}", calc("cos(120)"))
        assertEquals(ErrorKind.MATH, error("tan(90)"))
        assertEquals("{frac:1|2}", calc("sin({frac:π|6})", rad))
        assertEquals("{frac:π|6}", calc("asin({frac:1|2})", rad))
        assertEquals("{frac:π|4}", calc("atan(1)", rad))
        assertEquals("30", calc("asin({frac:1|2})"))
        assertEquals("{frac:π|2}", calc("90°", rad))
        assertEquals("0.8414709848", calc("sin(1)", rad))
    }

    @Test fun powersAndLogs() {
        assertEquals("1024", calc("2{pow:10}"))
        assertEquals("{frac:1|2}", calc("2{pow:~1}"))
        assertEquals("2", calc("4{pow:{frac:1|2}}"))
        assertEquals("3", calc("log(1000)"))
        assertEquals("3", calc("{logb:2|8}"))
        assertEquals("{frac:1|3}", calc("{logb:8|2}"))
        assertEquals("0", calc("ln(1)"))
        assertEquals("1", calc("ln(e)"))
        assertEquals("120", calc("5!"))
    }

    @Test fun complexNumbers() {
        assertEquals(ErrorKind.NON_REAL, error("{sqrt:~4}"))
        assertEquals("2i", calc("{sqrt:~4}", Settings(complex = ComplexMode.RECT)))
        assertEquals("2i", calc("(1+i)²"))
        assertEquals("{frac:1|2}−{frac:1|2}i", calc("{frac:1|1+i}"))
        assertEquals("{sqrt:2}", calc("{abs:1+i}"))
    }

    @Test fun calculus() {
        assertEquals("{frac:1|3}", calc("{int:0|1|X²}"))
        assertEquals("12", calc("{d:X{pow:3}|2}"))
        assertEquals("55", calc("{sum:X|1|10|X}"))
        assertEquals("385", calc("{sum:X|1|10|X²}"))
        assertEquals("2", calc("{int:0|π|sin(X)}", rad))
    }

    @Test fun matricesAndLists() {
        assertEquals("−2", calc("Det({mat2x2:1|2|3|4})"))
        assertEquals("{mat2x2:−2|1|{frac:3|2}|−{frac:1|2}}", calc("{mat2x2:1|2|3|4}{pow:~1}"))
        assertEquals("{mat2x2:7|10|15|22}", calc("{mat2x2:1|2|3|4}²"))
        assertEquals("{frac:5|2}", calc("Mean([1,2,3,4])"))
        assertEquals("{2,4,6}", calc("2[1,2,3]"))
        calc("[1,2,3]→List1")
        assertEquals("6", calc("Sum(List1)"))
    }

    @Test fun storeAndAns() {
        calc("5→B")
        assertEquals("10", calc("2B"))
        calc("7")
        assertEquals("14", calc("Ans*2"))
    }

    @Test fun decimalFormatting() {
        assertEquals("0.3333333333", dec("{frac:1|3}"))
        assertEquals("1.23456789×10{pow:11}", dec("123456789000"))
        assertEquals("1×10{pow:−3}", dec("0.001"))
        assertEquals("0.001", dec("0.001", Settings(display = DisplayFormat(DisplayFormat.Kind.NORM2))))
        assertEquals("3.14", dec("π", Settings(display = DisplayFormat(DisplayFormat.Kind.FIX, 2))))
        assertEquals("3.142×10{pow:0}", dec("π", Settings(display = DisplayFormat(DisplayFormat.Kind.SCI, 4))))
        assertEquals("12.3456×10{pow:3}", dec("12345.6", Settings(eng = true)))
    }

    @Test fun distributions() {
        val v = Calculator.evaluate(row("NormCD(~1,1)"), memory, Settings()).value as Value.Num
        assertEquals(0.682689492137, v.z.re.toDouble(), 1e-11)
        val inv = Calculator.evaluate(row("InvNormCD(0.975)"), memory, Settings()).value as Value.Num
        assertEquals(1.959963984540, inv.z.re.toDouble(), 1e-10)
        val b = Calculator.evaluate(row("BinomialPD(3,10,0.5)"), memory, Settings()).value as Value.Num
        assertEquals(0.1171875, b.z.re.toDouble(), 1e-12)
    }

    @Test fun errors() {
        assertEquals(ErrorKind.MATH, error("1/0"))
        assertEquals(ErrorKind.SYNTAX, error("2+"))
        assertEquals(ErrorKind.SYNTAX, error("1..2"))
        assertTrue(error("Det({mat2x2:1|2|3|4}+[1,2])") in setOf(ErrorKind.DIMENSION, ErrorKind.ARGUMENT))
    }

    @Test fun moreCasioBehaviour() {
        assertEquals("6", calc("2(3)"))
        assertEquals("9", calc("(1+2)(3)"))
        assertEquals("{frac:1|4}", calc("sin(30)²"))
        assertEquals("9", calc("(~3)²"))
        assertEquals("8", calc("5-~3"))
        assertEquals("2000", calc("2#3"))
        assertEquals("{frac:3|2000}", calc("1.5#~3"))
        assertEquals("{2{sqrt:2},45}", calc("Pol(2,2)"))
        assertEquals("{1,{sqrt:3}}", calc("Rec(2,60)"))
        assertEquals("{sqrt:2}", calc("2{pow:0.5}"))
        assertEquals("4", calc("8{pow:{frac:2|3}}"))
        assertEquals("4", calc("(~8){pow:{frac:2|3}}"))
        assertEquals(ErrorKind.NON_REAL, error("(~8){pow:{frac:1|2}}"))
        assertEquals("πi", calc("ln(~1)", Settings(complex = ComplexMode.RECT)))
        assertEquals("2", calc("e{pow:ln(2)}"))
        assertEquals(ErrorKind.MATH, error("70!"))
        assertEquals(ErrorKind.MATH, error("1#99*10"))
        assertEquals("1.711224524×10{pow:98}", calc("69!"))
        assertEquals("{frac:5|2}", calc("{abs:~{frac:5|2}}"))
        assertEquals("3", calc("Int({frac:7|2})"))
        assertEquals("−4", calc("Intg(~{frac:7|2})"))
        assertEquals("2", calc("MOD(17,5)"))
        assertEquals("12", calc("LCM(4,6)"))
        assertEquals("2∠{frac:π|3}", calc("1+{sqrt:3}i", Settings(angle = AngleUnit.RAD, complex = ComplexMode.POLAR)))
    }
}
