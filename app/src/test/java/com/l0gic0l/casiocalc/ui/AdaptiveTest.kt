package com.l0gic0l.casiocalc.ui

import android.app.Application
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.l0gic0l.casiocalc.data.HistoryEntry
import com.l0gic0l.casiocalc.math.Calculator
import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.ui.adaptive.CalcLayout
import com.l0gic0l.casiocalc.ui.adaptive.Fold
import com.l0gic0l.casiocalc.ui.adaptive.chooseLayout
import com.l0gic0l.casiocalc.ui.matrix.MatrixScreen
import com.l0gic0l.casiocalc.ui.run.RunScreen
import com.l0gic0l.casiocalc.ui.theme.CasioCalcTheme
import com.l0gic0l.casiocalc.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AdaptiveTest {
    @get:Rule
    val compose = createComposeRule()

    private val vm by lazy {
        CalcViewModel(ApplicationProvider.getApplicationContext<Application>()).apply {
            history.clear()
            for (s in listOf("{frac:2|3}+{frac:1|6}", "{sqrt:8}", "sin(60)", "{mat2x2:1|2|3|4}{pow:~1}")) {
                val r = row(s)
                val res = Calculator.evaluate(r, memory, settings)
                history += HistoryEntry(r, res.value, res.style)
            }
            runInput.load(row("{frac:3|4}+{sqrt:{frac:1|2}}"))
        }
    }

    @Test fun layoutChoice() {
        assertEquals(CalcLayout.PORTRAIT, chooseLayout(411.dp, 891.dp, Fold()))
        assertEquals(CalcLayout.WIDE, chooseLayout(891.dp, 411.dp, Fold()))
        assertEquals(CalcLayout.WIDE, chooseLayout(673.dp, 841.dp, Fold()))
        assertEquals(CalcLayout.TABLETOP, chooseLayout(841.dp, 701.dp, Fold(tabletop = true)))
        assertEquals(CalcLayout.BOOK, chooseLayout(841.dp, 701.dp, Fold(book = true)))
    }

    /** The bug reported from the Pixel: the size steppers did not refresh until a cell was edited. */
    @Test fun matrixSizeSteppersUpdateImmediately() {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { MatrixScreen(vm, {}) } }
        compose.onNodeWithText("Mat A").performClick()
        compose.onNodeWithContentDescription("Increase Rows").performClick()
        compose.onNodeWithContentDescription("Increase Rows").performClick()
        compose.onNodeWithContentDescription("Increase Columns").performClick()
        compose.waitForIdle()
        val m = vm.memory.getMatrix('A')!!
        assertEquals(4, m.rows)
        assertEquals(3, m.cols)
    }

    private fun shot(name: String, fold: Fold = Fold()) {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.DARK, dynamicColor = false) { RunScreen(vm, {}, {}, fold) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun phonePortrait() = shot("adaptive_phone_portrait")

    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    @Test fun phoneLandscape() = shot("adaptive_phone_landscape")

    @Config(qualifiers = "w673dp-h841dp-xhdpi")
    @Test fun foldUnfoldedPortrait() = shot("adaptive_fold_portrait")

    @Config(qualifiers = "w841dp-h701dp-xhdpi")
    @Test fun foldUnfoldedLandscape() = shot("adaptive_fold_landscape")

    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    @Test fun tablet() = shot("adaptive_tablet")

    // A Pixel Fold half-opened like a laptop: the 701 × 841dp window with the fold across the middle.
    @Config(qualifiers = "w701dp-h841dp-xhdpi")
    @Test fun tabletop() = shot("adaptive_tabletop", Fold(tabletop = true, hinge = Rect(0f, 841f, 1402f, 841f)))

    @Config(qualifiers = "w841dp-h701dp-xhdpi")
    @Test fun book() = shot("adaptive_book", Fold(book = true, hinge = Rect(836f, 0f, 846f, 1402f)))

    private fun withData(): CalcViewModel = vm.apply {
        updateSettings { it.copy(angle = com.l0gic0l.casiocalc.math.eval.AngleUnit.RAD) }
        setGraphFunction(0, com.l0gic0l.casiocalc.data.GraphFunction(com.l0gic0l.casiocalc.data.GraphType.Y, row("2sin(X)"), colorIndex = 0))
        setGraphFunction(1, com.l0gic0l.casiocalc.data.GraphFunction(com.l0gic0l.casiocalc.data.GraphType.Y, row("{frac:X²|4}-1"), colorIndex = 1))
        memory.setList(1, com.l0gic0l.casiocalc.math.num.Value.Lst((1..6).map { com.l0gic0l.casiocalc.math.num.Complex.of(it.toLong()) }))
    }

    private fun statEditor(name: String) {
        val model = withData()
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { com.l0gic0l.casiocalc.ui.stat.StatScreen(model, {}, {}) } }
        compose.onNodeWithContentDescription("List 1, row 3", substring = true).performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun statEditorPortrait() = statEditor("adaptive_stat_editor_portrait")

    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    @Test fun statEditorLandscape() = statEditor("adaptive_stat_editor_landscape")

    @Config(qualifiers = "w841dp-h701dp-xhdpi")
    @Test fun statEditorFold() = statEditor("adaptive_stat_editor_fold")

    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    @Test fun graphLandscape() {
        val model = withData()
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { com.l0gic0l.casiocalc.ui.graph.GraphViewScreen(model, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/adaptive_graph_landscape.png")
    }

    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    @Test fun baseNLandscape() {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { com.l0gic0l.casiocalc.ui.basen.BaseNScreen(vm, {}) } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/adaptive_basen_landscape.png")
    }

    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    @Test fun menuLandscape() {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { com.l0gic0l.casiocalc.ui.menu.MainMenuScreen {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/adaptive_menu_landscape.png")
    }
}
