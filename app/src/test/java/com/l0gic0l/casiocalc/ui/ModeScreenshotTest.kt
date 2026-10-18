package com.l0gic0l.casiocalc.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.l0gic0l.casiocalc.data.AccentColor
import com.l0gic0l.casiocalc.data.GraphFunction
import com.l0gic0l.casiocalc.data.KeyShape
import com.l0gic0l.casiocalc.data.KeySpacing
import com.l0gic0l.casiocalc.data.KeypadSize
import com.l0gic0l.casiocalc.data.TextScale
import com.l0gic0l.casiocalc.data.UiPrefs
import com.l0gic0l.casiocalc.ui.keypad.KeypadLook
import com.l0gic0l.casiocalc.ui.keypad.LocalKeypadLook
import com.l0gic0l.casiocalc.data.GraphType
import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.ui.basen.BaseNScreen
import com.l0gic0l.casiocalc.ui.calculus.DerivativeScreen
import com.l0gic0l.casiocalc.ui.calculus.IntegralScreen
import com.l0gic0l.casiocalc.ui.equation.EquationScreen
import com.l0gic0l.casiocalc.ui.graph.GraphListScreen
import com.l0gic0l.casiocalc.ui.graph.GraphViewScreen
import com.l0gic0l.casiocalc.ui.graph.TableScreen
import com.l0gic0l.casiocalc.ui.matrix.MatrixScreen
import com.l0gic0l.casiocalc.ui.menu.MainMenuScreen
import com.l0gic0l.casiocalc.ui.recursion.RecursionScreen
import com.l0gic0l.casiocalc.ui.run.RunScreen
import com.l0gic0l.casiocalc.ui.settings.SettingsScreen
import com.l0gic0l.casiocalc.ui.stat.StatGraphScreen
import com.l0gic0l.casiocalc.ui.stat.StatScreen
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
class ModeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val vm by lazy {
        CalcViewModel(ApplicationProvider.getApplicationContext<Application>()).apply {
            updateSettings { it.copy(angle = AngleUnit.RAD) }
            setGraphFunction(0, GraphFunction(GraphType.Y, row("2sin(X)"), colorIndex = 0))
            setGraphFunction(1, GraphFunction(GraphType.Y, row("{frac:X²|4}-1"), colorIndex = 1))
            setGraphFunction(2, GraphFunction(GraphType.R, row("2cos(3θ)"), colorIndex = 2, enabled = false))
            memory.setList(1, Value.Lst((1..8).map { Complex.of(it.toLong()) }))
            memory.setList(2, Value.Lst(listOf(2.1, 3.9, 6.2, 7.8, 10.1, 12.2, 13.8, 16.1).map { Complex.approx(it) }))
            memory.setMatrix('A', Value.Mat(2, 2, listOf(1, 2, 3, 4).map { Complex.of(it.toLong()) }))
            polyCoefficients[0] = Value.of(1); polyCoefficients[1] = Value.of(-1); polyCoefficients[2] = Value.of(-1)
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun menu() = shot("menu") { MainMenuScreen(onOpen = {}) }
    @Test fun graphList() = shot("graph_list") { GraphListScreen(vm, {}, {}, {}, {}) }
    @Test fun graphView() = shot("graph_view") { GraphViewScreen(vm, {}) }
    @Test fun table() = shot("table") { TableScreen(vm, {}) }
    @Test fun stat() = shot("stat") { StatScreen(vm, {}, {}) }
    @Test fun statGraph() = shot("stat_graph") { StatGraphScreen(vm, {}) }
    @Test fun matrix() = shot("matrix") { MatrixScreen(vm, {}) }
    @Test fun recursion() = shot("recursion") { RecursionScreen(vm, {}) }
    @Test fun baseN() = shot("basen") { BaseNScreen(vm, {}) }
    @Config(qualifiers = "w411dp-h2600dp-xxhdpi")
    @Test fun settings() = shot("settings") { SettingsScreen(vm, {}) }

    /** Pill keys, roomy spacing, a tall keypad and the teal accent. */
    @Test fun keypadLook() {
        val prefs = UiPrefs(keyShape = KeyShape.PILL, keySpacing = KeySpacing.ROOMY, keypadSize = KeypadSize.TALL, accent = AccentColor.TEAL, keyLabels = TextScale.LARGE)
        compose.setContent {
            CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false, accentHue = prefs.accent.hueShift) {
                CompositionLocalProvider(LocalKeypadLook provides KeypadLook.of(prefs)) { RunScreen(vm, {}, {}) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/run_keypad_look.png")
    }

    private fun solved(name: String, done: () -> Boolean, content: @Composable () -> Unit) {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { content() } }
        compose.waitUntil(20_000) { done() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun integral() {
        vm.integrand = row("X{pow:2}e{pow:X}")
        vm.solveIntegral()
        solved("integral", { vm.integralSolution != null }) { IntegralScreen(vm, {}) }
    }

    // A tall window, to see the whole worked solution.
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi")
    @Test fun integralSteps() {
        vm.integrand = row("{frac:X+5|X{pow:2}+X-2}")
        vm.integralDefinite = true
        vm.integralLower = row("2")
        vm.integralUpper = row("3")
        vm.solveIntegral()
        solved("integral_steps", { vm.integralSolution != null }) { IntegralScreen(vm, {}) }
    }

    @Config(qualifiers = "w411dp-h1400dp-xxhdpi")
    @Test fun derivative() {
        vm.derivFunction = row("X{pow:2}sin(X)")
        vm.derivAtEnabled = true
        vm.derivAt = row("π")
        vm.solveDerivative()
        solved("derivative", { vm.derivativeSolution != null }) { DerivativeScreen(vm, {}) }
    }

    @Test fun polynomial() {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { EquationScreen(vm, {}) } }
        compose.onNodeWithText("Polynomial").performClick()
        compose.onNodeWithText("Solve").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/equation_poly.png")
    }

    @Test fun optnSheet() {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { RunScreen(vm, {}, {}) } }
        compose.onNodeWithText("OPTN").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/optn.png")
    }

    @Test fun shiftActive() {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { RunScreen(vm, {}, {}) } }
        compose.onNodeWithText("SHIFT").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/run_shift.png")
    }
}
