package com.l0gic0l.casiocalc.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.l0gic0l.casiocalc.data.AccentColor
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** White text on every accent's primary colour, as on Solve and EXE, needs 4.5:1. */
    @Test fun everyAccentKeepsReadableText() {
        for (accent in AccentColor.entries) {
            val c = contrast(accentSwatch(accent.hueShift), Color.White)
            assertTrue("${accent.label}: $c", c >= 4.5f)
        }
    }
}
