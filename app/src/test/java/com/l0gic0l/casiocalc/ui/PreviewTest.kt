package com.l0gic0l.casiocalc.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.test.core.app.ApplicationProvider
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.ui.keypad.Cmd
import com.l0gic0l.casiocalc.ui.keypad.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreviewTest {
    private val vm = CalcViewModel(ApplicationProvider.getApplicationContext<Application>()).also {
        it.clearHistory()
        it.updatePrefs { p -> p.copy(livePreview = true) }
    }

    private fun key(a: KeyAction) = vm.runKey(a)
    private fun type(vararg toks: com.l0gic0l.casiocalc.math.editor.Tok) = toks.forEach { key(KeyAction.Insert(it)) }

    /** Lets the preview's pause, its background evaluation and the hand-back to the main thread run. */
    private fun settle() {
        repeat(60) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            Thread.sleep(2)
        }
    }

    private fun preview() = vm.runPreview?.let { RowText.linear(vm.formatter().format(it)) }

    @Test fun simpleCalculationShowsItsValueWithoutTouchingAns() {
        val ans = vm.memory.ans
        type(Tokens.digit(1), Tokens.ADD, Tokens.digit(2))
        settle()
        assertEquals("3", preview())
        assertEquals(ans, vm.memory.ans)
        // EXE clears the line, and the preview with it.
        key(KeyAction.Command(Cmd.EXE))
        settle()
        assertNull(vm.runPreview)
    }

    @Test fun halfTypedOrBareNumbersShowNothing() {
        type(Tokens.digit(7))
        settle()
        assertNull(vm.runPreview)
        type(Tokens.ADD)
        settle()
        assertNull(vm.runPreview)
    }

    @Test fun sideEffectsAndRandomNumbersWaitForExe() {
        vm.memory.setVar("A", Value.of(1))
        type(Tokens.digit(5), Tokens.STORE, Tokens.letter('A'))
        settle()
        assertNull(vm.runPreview)
        assertEquals(Value.of(1), vm.memory.getVar("A"))
        key(KeyAction.Command(Cmd.AC))
        type(Tokens.RAN, Tokens.ADD, Tokens.digit(1))
        settle()
        assertNull(vm.runPreview)
    }

    @Test fun settingTurnsItOff() {
        vm.updatePrefs { it.copy(livePreview = false) }
        type(Tokens.digit(2), Tokens.MUL, Tokens.digit(3))
        settle()
        assertNull(vm.runPreview)
        vm.updatePrefs { it.copy(livePreview = true) }
        settle()
        assertEquals("6", preview())
    }
}
