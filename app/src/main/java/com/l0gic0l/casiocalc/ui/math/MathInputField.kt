package com.l0gic0l.casiocalc.ui.math

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.ui.keypad.InputState

/** Editable natural-display line: tap to place the cursor, scrolls sideways to keep it visible. */
@Composable
fun MathInputField(state: InputState, modifier: Modifier = Modifier, fontSize: TextUnit = 28.sp) {
    val scroll = rememberScrollState()
    val cache = rememberMathTextCache()
    val layout = rememberMathLayout(state.row, fontSize, state.revision, cache)
    val cursor = state.cursor
    LaunchedEffect(state.revision, layout) {
        val stop = layout.stopFor(cursor.first, cursor.second) ?: return@LaunchedEffect
        val margin = stop.size * 1.2f
        val viewport = scroll.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val target = when {
            stop.x < scroll.value + margin -> (stop.x - margin).toInt()
            stop.x > scroll.value + viewport - margin -> (stop.x - viewport + margin).toInt()
            else -> return@LaunchedEffect
        }
        scroll.animateScrollTo(target.coerceIn(0, scroll.maxValue.coerceAtLeast(target)))
    }
    Box(modifier.horizontalScroll(scroll)) {
        MathCanvas(
            layout,
            cache,
            cursor = cursor,
            blinkKey = state.revision,
            onTap = { stop -> state.edit { setCursor(stop.row, stop.index) } },
        )
    }
}

/** Read-only expression that scrolls sideways when it is wider than the screen. */
@Composable
fun ScrollingMath(row: MathRow, modifier: Modifier = Modifier, fontSize: TextUnit = 24.sp, colors: MathColors = defaultMathColors(), reverse: Boolean = false) {
    val scroll = rememberScrollState()
    Box(modifier.horizontalScroll(scroll, reverseScrolling = reverse)) {
        MathView(row, fontSize = fontSize, colors = colors)
    }
}
