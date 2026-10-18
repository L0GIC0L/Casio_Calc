package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.editor.MathEditor
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.format.RowText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTest {
    private val ed = MathEditor()
    private fun text() = RowText.structural(ed.root)
    private fun digits(s: String) = s.forEach { ed.insert(Tokens.digit(it - '0')) }

    @Test fun fractionTakesPrecedingNumber() {
        digits("1")
        ed.insert(Tokens.ADD)
        digits("23")
        ed.insertTemplate(TemplateKind.FRACTION)
        assertEquals("1+{frac:23|}", text())
        digits("4")
        ed.right()
        ed.insert(Tokens.MUL)
        assertEquals("1+{frac:23|4}×", text())
        assertTrue(ed.atEnd)
    }

    @Test fun emptyFractionStartsInNumerator() {
        ed.insert(Tokens.ADD)
        ed.insertTemplate(TemplateKind.FRACTION)
        digits("2")
        assertTrue(ed.down())
        digits("3")
        assertTrue(ed.up())
        assertFalse(ed.up())
        assertEquals("+{frac:2|3}", text())
    }

    @Test fun arrowsWalkThroughSlots() {
        ed.insertTemplate(TemplateKind.FRACTION)
        digits("1")
        ed.right() // end of numerator → denominator
        digits("2")
        ed.right() // leave the fraction
        digits("5")
        assertEquals("{frac:1|2}5", text())
        ed.left() // before 5
        ed.backspace() // deletes the denominator's 2
        assertEquals("{frac:1|}5", text())
    }

    @Test fun deleteDissolvesAtFirstSlotStart() {
        ed.insertTemplate(TemplateKind.SQRT)
        digits("9")
        ed.left()
        ed.backspace()
        assertEquals("9", text())
    }

    @Test fun deleteAfterTemplateEntersIt() {
        ed.insertTemplate(TemplateKind.POWER, listOf(Tokens.digit(2)))
        assertEquals("{pow:2}", text())
        assertTrue(ed.atEnd)
        ed.backspace()
        assertEquals("", text())
        ed.insertTemplate(TemplateKind.FRACTION)
        digits("12")
        ed.down()
        digits("3")
        ed.right()
        ed.backspace()
        assertEquals("{frac:12|}", text())
        ed.backspace()
        assertEquals("{frac:1|}", text())
    }

    @Test fun powerAndRoots() {
        ed.insert(Tokens.X)
        ed.insertTemplate(TemplateKind.POWER)
        digits("3")
        ed.right()
        ed.insert(Tokens.ADD)
        ed.insertTemplate(TemplateKind.ROOT, listOf(Tokens.digit(3)))
        digits("8")
        assertEquals("X{pow:3}+{root:3|8}", text())
    }

    @Test fun matrixDeleteDoesNotSpillCells() {
        ed.insertMatrix(2, 2)
        digits("1")
        ed.right()
        ed.backspace() // at start of cell 2 → back to cell 1
        ed.backspace()
        ed.backspace() // empty matrix start → removes it
        assertEquals("", text())
    }
}
