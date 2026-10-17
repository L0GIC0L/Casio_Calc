package com.l0gic0l.casiocalc.ui

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.l0gic0l.casiocalc.data.UiPrefs
import com.l0gic0l.casiocalc.ui.effects.ParticleState
import com.l0gic0l.casiocalc.ui.effects.particleTouch
import com.l0gic0l.casiocalc.ui.adaptive.EditSession
import com.l0gic0l.casiocalc.ui.matrix.MatrixScreen
import com.l0gic0l.casiocalc.ui.theme.CasioCalcTheme
import com.l0gic0l.casiocalc.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ReviewFixesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val vm by lazy {
        CalcViewModel(ApplicationProvider.getApplicationContext<Application>()).apply {
            resetMemory()
            updatePrefs { UiPrefs(dynamicColor = false) }
        }
    }

    /** Reopening the same cell must start from a fresh input, not the text abandoned last time. */
    @Test fun reopeningTheSameCellIsANewSession() {
        val session = EditSession<Int>()
        session.open(3)
        val first = session.opened
        session.close()
        session.open(3)
        assertNotEquals(first, session.opened)
        assertEquals(3, session.shown)
    }

    /** Deleting a matrix while one of its cells is being edited used to leave the editor writing past the end. */
    @Test fun deletingTheMatrixClosesItsEditor() {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) { MatrixScreen(vm, {}) } }
        compose.onNodeWithText("Mat A").performClick()
        compose.onNodeWithContentDescription("Increase Rows").performClick()
        compose.onNodeWithContentDescription("Increase Columns").performClick()
        compose.onNodeWithContentDescription("Mat A row 3 column 3", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithContentDescription("Execute").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Delete Mat A").performClick()
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithContentDescription("Execute").fetchSemanticsNodes().size)
    }

    /** Halfway through a back swipe the screen has shrunk, rounded and followed the finger, with the menu behind it. */
    @Test fun backGestureMidway() {
        compose.setContent { CasioCalcApp(vm) }
        compose.onNodeWithText("Graph").performClick()
        compose.waitForIdle()
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 1200f, 0.5f, BackEventCompat.EDGE_LEFT)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/back_gesture.png")
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
    }

    /** The Scaffold in front of the particle field used to swallow every touch before the particles saw it. */
    @Test fun touchesReachParticlesAndTheButtonUnderneath() {
        val particles = ParticleState()
        var clicked = false
        compose.setContent {
            Box(Modifier.fillMaxSize().particleTouch(particles)) {
                Scaffold { padding -> Button(onClick = { clicked = true }, modifier = Modifier.padding(padding)) { Text("Tap") } }
            }
        }
        compose.onNodeWithText("Tap").performClick()
        compose.waitForIdle()
        assertTrue("the tap should scatter sparks", particles.sparks.isNotEmpty())
        assertTrue("the button should still get the tap", clicked)
    }

    @Test fun menuDark() {
        compose.setContent { CasioCalcTheme(mode = ThemeMode.DARK, dynamicColor = false) { com.l0gic0l.casiocalc.ui.menu.MainMenuScreen(status = "Deg · Math · Norm1") {} } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/menu_dark.png")
    }
}
