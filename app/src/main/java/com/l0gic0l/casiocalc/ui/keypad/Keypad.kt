package com.l0gic0l.casiocalc.ui.keypad

import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.ui.adaptive.KeypadShape
import com.l0gic0l.casiocalc.ui.math.MathView
import com.l0gic0l.casiocalc.ui.math.defaultMathColors
import com.l0gic0l.casiocalc.ui.theme.LocalKeyLabelColors

/**
 * The full calculator keypad; it fills the height it is given. SHIFT and ALPHA are latched in
 * [state] and the resolved action of every other key is passed to [onAction].
 * [shape] STACKED puts the function block above the number block, WIDE puts them side by side.
 */
@Composable
fun Keypad(
    state: InputState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    shape: KeypadShape = KeypadShape.STACKED,
    haptics: Boolean = true,
    showHints: Boolean = true,
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

    @Composable
    fun KeyRow(row: List<KeySpec>, rowModifier: Modifier) {
        Row(rowModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (spec in row) Key(spec, state, showHints, Modifier.weight(1f).fillMaxHeight()) { press(spec) }
        }
    }

    val gap = 6.dp
    when (shape) {
        KeypadShape.STACKED -> Column(modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in Keys.functionRows) KeyRow(row, Modifier.weight(0.85f))
            for (row in Keys.numberRows) KeyRow(row, Modifier.weight(1f))
        }
        KeypadShape.WIDE -> Row(modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in Keys.functionRows) KeyRow(row, Modifier.weight(1f))
            }
            Column(Modifier.weight(5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in Keys.numberRows) KeyRow(row, Modifier.weight(1f))
            }
        }
    }
}

/** What a key currently shows: its own label, its SHIFT function or its ALPHA letter. */
private enum class Face { MAIN, SHIFT, ALPHA }

@Composable
private fun Key(spec: KeySpec, state: InputState, showHints: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
    val face = when {
        shiftOn && spec.shift != null && spec.style != KeyStyle.ALPHA -> Face.SHIFT
        alphaOn && !shiftOn && spec.alpha != null -> Face.ALPHA
        else -> Face.MAIN
    }
    val dimmed = (shiftOn && spec.shift == null || alphaOn && !shiftOn && spec.alpha == null) &&
        spec.style !in setOf(KeyStyle.SHIFT, KeyStyle.ALPHA, KeyStyle.NAV, KeyStyle.DELETE, KeyStyle.CLEAR)

    // Pressed keys shrink slightly and round off, then spring back.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "keyScale",
    )
    val corner by animateDpAsState(if (pressed) 28.dp else 16.dp, tween(120), label = "keyCorner")
    val contentAlpha by animateFloatAsState(if (dimmed) 0.4f else 1f, label = "keyDim")

    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }) {
            val hint = spec.shiftLabel
            if (showHints && face == Face.MAIN && hint != null && spec.style != KeyStyle.SHIFT) {
                Text(
                    hint,
                    color = if (spec.style == KeyStyle.EXE) content.copy(alpha = 0.8f) else labels.shift,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 7.dp, top = 2.dp),
                )
            }
            Crossfade(face, Modifier.align(Alignment.Center), animationSpec = tween(140), label = "keyFace") { f ->
                Box(Modifier.padding(top = if (f == Face.MAIN && showHints && hint != null) 6.dp else 0.dp)) {
                    when (f) {
                        Face.SHIFT -> if (spec.shiftMath != null) KeyMath(spec.shiftMath, labels.shift)
                        else KeyText(spec.shiftLabel ?: "", if (spec.style == KeyStyle.EXE) content else labels.shift, spec.style)
                        Face.ALPHA -> KeyText(spec.alphaLabel ?: "", labels.alpha, spec.style)
                        Face.MAIN -> when (val l = spec.label) {
                            is KeyLabel.Math -> KeyMath(l.row, LocalContentColor.current)
                            is KeyLabel.Text -> KeyText(l.text, LocalContentColor.current, spec.style)
                            is KeyLabel.Icon -> KeyIcon(l)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyText(text: String, color: Color, style: KeyStyle) {
    val size = when {
        style == KeyStyle.EXE && text == "=" -> 30.sp
        style == KeyStyle.NUMBER && text.length == 1 -> 24.sp
        style == KeyStyle.OPERATOR && text.length == 1 -> 24.sp
        text.length >= 5 -> 12.sp
        text.length >= 4 -> 14.sp
        else -> 18.sp
    }
    Text(
        text,
        color = color,
        fontSize = size,
        fontWeight = when (style) {
            KeyStyle.EXE, KeyStyle.SHIFT, KeyStyle.ALPHA -> FontWeight.SemiBold
            KeyStyle.NUMBER -> FontWeight.Medium
            else -> FontWeight.Normal
        },
        maxLines = 1,
    )
}

@Composable
private fun KeyIcon(label: KeyLabel.Icon) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = label.description },
    ) {
        Icon(label.image, contentDescription = null, modifier = Modifier.size(if (label.caption == null) 24.dp else 18.dp))
        if (label.caption != null) Text(label.caption, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1)
    }
}

@Composable
private fun KeyMath(row: MathRow, color: Color) {
    CompositionLocalProvider(LocalContentColor provides color) {
        MathView(row, fontSize = 13.sp, colors = defaultMathColors().copy(normal = color, function = color, operator = color, placeholder = color.copy(alpha = 0.7f)))
    }
}
