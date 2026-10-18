package com.l0gic0l.casiocalc.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.l0gic0l.casiocalc.data.HistoryEntry
import com.l0gic0l.casiocalc.math.Calculator
import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.ui.run.RunScreen
import com.l0gic0l.casiocalc.ui.theme.CasioCalcTheme
import com.l0gic0l.casiocalc.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun vmWithHistory(vararg inputs: String): CalcViewModel {
        val vm = CalcViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.history.clear()
        for (s in inputs) {
            val r = row(s)
            val res = Calculator.evaluate(r, vm.memory, vm.settings)
            vm.history += HistoryEntry(r, res.value, res.style)
        }
        vm.runInput.load(row("{frac:3|4}+{sqrt:{frac:1|2}}"))
        return vm
    }

    private fun run(theme: ThemeMode, name: String) {
        val vm = vmWithHistory("{frac:2|3}+{frac:1|6}", "{sqrt:8}", "sin(60)", "{int:0|1|X²}", "{mat2x2:1|2|3|4}{pow:~1}")
        compose.setContent {
            CasioCalcTheme(mode = theme, dynamicColor = false) {
                RunScreen(vm, onBack = {}, onSettings = {})
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun runLight() = run(ThemeMode.LIGHT, "run_light")
    @Test fun runDark() = run(ThemeMode.DARK, "run_dark")
}
