package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.basen.BaseN
import com.l0gic0l.casiocalc.math.basen.Radix
import org.junit.Assert.assertEquals
import org.junit.Test

class BaseNTest {
    @Test fun arithmeticAndLogic() {
        assertEquals(255, BaseN.evaluate("FF", Radix.HEX))
        assertEquals(10, BaseN.evaluate("1010", Radix.BIN))
        assertEquals(-1, BaseN.evaluate("FFFFFFFF", Radix.HEX))
        assertEquals(3, BaseN.evaluate("7÷2", Radix.DEC))
        assertEquals(26, BaseN.evaluate("h10 + b1010", Radix.DEC))
        assertEquals(0b1000, BaseN.evaluate("1100 and 1010", Radix.BIN))
        assertEquals(0b0110, BaseN.evaluate("1100 xor 1010", Radix.BIN))
        assertEquals(-6, BaseN.evaluate("not 5", Radix.DEC))
        assertEquals(-5, BaseN.evaluate("Neg 5", Radix.DEC))
        assertEquals("FFFFFFFF", BaseN.format(-1, Radix.HEX))
        assertEquals("0000 0000 0000 0000 0000 0000 0000 1010", BaseN.format(10, Radix.BIN))
    }

    @Test(expected = CalcException::class)
    fun overflow() {
        BaseN.evaluate("2147483647+1", Radix.DEC)
    }
}
