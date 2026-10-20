package com.l0gic0l.casiocalc.ui

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.l0gic0l.casiocalc.data.UiPrefs
import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.ui.calculus.DerivativeScreen
import com.l0gic0l.casiocalc.ui.calculus.IntegralScreen
import com.l0gic0l.casiocalc.ui.theme.CasioCalcTheme
import com.l0gic0l.casiocalc.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CalculusScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val vm by lazy {
        CalcViewModel(ApplicationProvider.getApplicationContext<Application>()).apply { updatePrefs { UiPrefs(dynamicColor = false) } }
    }

    /**
     * Waits for a solve started by a click. Its answer comes back through the paused main looper,
     * which waitUntil does not run while the progress bar animates, so this runs it.
     */
    private fun await(done: () -> Boolean) {
        repeat(400) {
            if (done()) {
                compose.waitForIdle()
                return
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(5)
        }
        throw AssertionError("the solve did not finish")
    }

    private fun spoken(text: String) = compose.onAllNodes(hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    /** TalkBack can read the answers; the emulator QA script waits on these same descriptions. */
    @Test fun answersHaveSpokenDescriptions() {
        vm.integrand = row("sin(X)²")
        vm.solveIntegral()
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { IntegralScreen(vm, {}) } }
        compose.waitUntil(20_000) { vm.integralSolution != null }
        compose.waitForIdle()
        assertTrue(spoken("sin(2X)"))
    }

    @Test fun derivativeOrdersAtAPoint() {
        vm.derivFunction = row("X²sin(X)")
        vm.derivAtEnabled = true
        vm.derivAt = row("1")
        vm.derivOrder = 1
        vm.solveDerivative()
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { DerivativeScreen(vm, {}) } }
        compose.waitUntil(20_000) { vm.derivativeSolution != null }
        compose.waitForIdle()
        assertTrue(spoken("2sin(1)"))
        compose.onNodeWithText("d²/dx²").performClick()
        await { vm.derivativeSolution?.order == 2 }
        compose.waitForIdle()
        assertTrue(spoken("4cos(1)"))
    }

    /** Solve while the keypad is still up uses what was typed, not the last saved function. */
    @Test fun solveKeepsWhatIsTypedInTheOpenBox() {
        vm.integrand = row("X")
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { IntegralScreen(vm, {}) } }
        compose.onNodeWithContentDescription("Integrate:", substring = true).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("AC").onFirst().performClick()
        compose.onAllNodesWithText("X,θ,T").onFirst().performClick()
        compose.onAllNodesWithText("x²").onFirst().performClick()
        compose.onNodeWithText("Solve").performClick()
        await { vm.integralSolution != null }
        assertEquals(RowText.linear(row("X²")), RowText.linear(vm.integrand))
    }
}
