package com.l0gic0l.casiocalc.ui.keypad

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.ui.math.MathView
import com.l0gic0l.casiocalc.ui.math.defaultMathColors
import com.l0gic0l.casiocalc.ui.theme.LocalKeyLabelColors

/**
 * The full calculator keypad. SHIFT and ALPHA are latched in [state] and the resolved action of
 * every other key is passed to [onAction].
 */
@Composable
fun Keypad(
    state: InputState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    functionKeyHeight: Dp = 44.dp,
    numberKeyHeight: Dp = 52.dp,
    haptics: Boolean = true,
) {
    val view = LocalView.current
    fun press(spec: KeySpec) {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val cmd = (spec.action as? KeyAction.Command)?.cmd
        if (cmd == Cmd.SHIFT) {
            state.shift = !state.shift
            return
        }
        if (cmd == Cmd.ALPHA) {
            if (state.shift) {
                state.shift = false
                state.alphaLock = !state.alphaLock
                state.alpha = false
            } else if (state.alphaLock) {
                state.alphaLock = false
            } else {
                state.alpha = !state.alpha
            }
            return
        }
        val action = when {
            state.shift && spec.shift != null -> spec.shift
            (state.alpha || state.alphaLock) && spec.alpha != null -> spec.alpha
            else -> spec.action
        }
        state.shift = false
        if (!state.alphaLock) state.alpha = false
        onAction(action)
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in Keys.functionRows) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (spec in row) {
                    Key(spec, state, Modifier.weight(1f).height(functionKeyHeight)) { press(spec) }
                }
            }
        }
        for (row in Keys.numberRows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (spec in row) {
                    Key(spec, state, Modifier.weight(1f).height(numberKeyHeight)) { press(spec) }
                }
            }
        }
    }
}

@Composable
private fun Key(spec: KeySpec, state: InputState, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val labels = LocalKeyLabelColors.current
    val shiftOn = state.shift
    val alphaOn = state.alpha || state.alphaLock
    val active = when (spec.style) {
        KeyStyle.SHIFT -> shiftOn
        KeyStyle.ALPHA -> alphaOn
        else -> false
    }
    val (container, content) = when (spec.style) {
        KeyStyle.NUMBER -> scheme.surfaceContainerHighest to scheme.onSurface
        KeyStyle.FUNCTION -> scheme.surfaceContainerHigh to scheme.onSurface
        KeyStyle.NAV -> scheme.surfaceContainer to scheme.onSurfaceVariant
        KeyStyle.OPERATOR -> scheme.secondaryContainer to scheme.onSecondaryContainer
        KeyStyle.DELETE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        KeyStyle.CLEAR -> scheme.errorContainer to scheme.onErrorContainer
        KeyStyle.EXE -> scheme.primary to scheme.onPrimary
        KeyStyle.SHIFT -> if (active) labels.shift to scheme.surface else scheme.surfaceContainerHigh to labels.shift
        KeyStyle.ALPHA -> if (active) labels.alpha to scheme.surface else scheme.surfaceContainerHigh to labels.alpha
    }
    // While a modifier is on, the key shows what it will do.
    val showShift = shiftOn && spec.shift != null && spec.style != KeyStyle.ALPHA
    val showAlpha = !showShift && alphaOn && spec.alpha != null
    val dimmed = (shiftOn && spec.shift == null || alphaOn && !shiftOn && spec.alpha == null) &&
        spec.style != KeyStyle.SHIFT && spec.style != KeyStyle.ALPHA && spec.style != KeyStyle.NAV &&
        spec.style != KeyStyle.DELETE && spec.style != KeyStyle.CLEAR

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = container,
        contentColor = content.copy(alpha = if (dimmed) 0.45f else 1f),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!showShift && !showAlpha && spec.style != KeyStyle.SHIFT) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 1.dp).align(Alignment.TopCenter)) {
                    Text(
                        spec.shiftLabel ?: "",
                        color = labels.shift.copy(alpha = if (dimmed) 0.5f else 1f),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        spec.alphaLabel ?: "",
                        color = labels.alpha.copy(alpha = if (dimmed) 0.5f else 1f),
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Box(Modifier.align(Alignment.Center).padding(top = if (showShift || showAlpha) 0.dp else 6.dp)) {
                when {
                    showShift && spec.shiftMath != null -> KeyMath(spec.shiftMath, labels.shift)
                    showShift -> KeyText(spec.shiftLabel ?: "", if (spec.style == KeyStyle.EXE) content else labels.shift, spec.style)
                    showAlpha -> KeyText(spec.alphaLabel ?: "", labels.alpha, spec.style)
                    spec.label is KeyLabel.Math -> KeyMath(spec.label.row, LocalContentColor.current)
                    spec.label is KeyLabel.Text -> KeyText(spec.label.text, LocalContentColor.current, spec.style)
                }
            }
        }
    }
}

@Composable
private fun KeyText(text: String, color: Color, style: KeyStyle) {
    val size = when {
        style == KeyStyle.NUMBER && text.length == 1 -> 22.sp
        text.length >= 5 -> 12.sp
        text.length >= 4 -> 14.sp
        else -> 17.sp
    }
    Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = if (style == KeyStyle.EXE || style == KeyStyle.SHIFT || style == KeyStyle.ALPHA) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
    )
}

@Composable
private fun KeyMath(row: com.l0gic0l.casiocalc.math.editor.MathRow, color: Color) {
    CompositionLocalProvider(LocalContentColor provides color) {
        MathView(row, fontSize = 13.sp, colors = defaultMathColors().copy(normal = color, function = color, operator = color, placeholder = color.copy(alpha = 0.7f)))
    }
}
