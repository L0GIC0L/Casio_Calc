package com.l0gic0l.casiocalc.ui

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.ui.keypad.Cmd
import com.l0gic0l.casiocalc.ui.keypad.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RunFlowTest {
    private val vm = CalcViewModel(ApplicationProvider.getApplicationContext<Application>()).also { it.clearHistory() }

    private fun key(a: KeyAction) = vm.runKey(a)
    private fun digit(d: Int) = key(KeyAction.Insert(Tokens.digit(d)))
    private fun exe() {
        key(KeyAction.Command(Cmd.EXE))
        repeat(200) {
            if (!vm.computing) return
            Thread.sleep(5)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
    private fun lastResult() = RowText.structural(vm.formatter().format(vm.history.last().value))

    @Test fun fractionThenContinueFromAns() {
        digit(2)
        key(KeyAction.InsertTemplate(TemplateKind.FRACTION))
        digit(3)
        exe()
        assertEquals("{frac:2|3}", lastResult())
        // An operator on the empty line continues from Ans.
        key(KeyAction.Insert(Tokens.ADD))
        assertEquals("Ans+", RowText.structural(vm.runInput.row))
        key(KeyAction.InsertTemplate(TemplateKind.FRACTION))
        digit(1)
        key(KeyAction.Command(Cmd.DOWN))
        digit(3)
        exe()
        assertEquals("1", lastResult())
    }

    @Test fun arrowsReopenLastCalculationAndErrorsKeepInput() {
        digit(4)
        key(KeyAction.Insert(Tokens.DIV))
        digit(0)
        exe()
        assertEquals("Math ERROR", vm.runError)
        assertEquals("4÷0", RowText.structural(vm.runInput.row))
        key(KeyAction.Command(Cmd.DEL))
        digit(8)
        exe()
        assertNull(vm.runError)
        assertEquals("{frac:1|2}", lastResult())
        key(KeyAction.Command(Cmd.LEFT))
        assertEquals("4÷8", RowText.structural(vm.runInput.row))
    }

    @Test fun storeAndRecallVariable() {
        digit(7)
        key(KeyAction.Insert(Tokens.STORE))
        key(KeyAction.Insert(Tokens.letter('A')))
        exe()
        key(KeyAction.Insert(Tokens.letter('A')))
        key(KeyAction.InsertTemplate(TemplateKind.POWER, listOf(listOf(Tokens.digit(2)))))
        exe()
        assertEquals("49", lastResult())
        key(KeyAction.Command(Cmd.SD))
        assertEquals(true, vm.history.last().decimal)
    }
}
