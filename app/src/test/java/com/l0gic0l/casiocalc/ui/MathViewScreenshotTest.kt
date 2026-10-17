package com.l0gic0l.casiocalc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.ui.math.MathView
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
class MathViewScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun naturalDisplayGallery() {
        val samples = listOf(
            "{frac:2|3}",
            "{frac:1|3}+{frac:1|6}={frac:1|2}",
            "{sqrt:8}=2{sqrt:2}",
            "{frac:1+{sqrt:5}|2}",
            "2{pow:10}+X{pow:{frac:1|2}}",
            "({frac:1|2}+{frac:3|4})²",
            "{root:3|27}+{logb:2|8}",
            "sin(30)+cos⁻¹",
            "{int:0|1|X²}",
            "{d:X{pow:3}|2}",
            "{sum:X|1|10|X²}",
            "{mat2x2:1|2|3|{frac:1|2}}",
            "{abs:~5}+{frac:|}",
        )
        compose.setContent {
            CasioCalcTheme(mode = ThemeMode.LIGHT, dynamicColor = false) {
                Surface {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (s in samples) {
                            val r = runCatching { row(s.replace("cos⁻¹", "acos(")) }.getOrNull() ?: continue
                            MathView(r, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/natural_display.png")
    }
}
