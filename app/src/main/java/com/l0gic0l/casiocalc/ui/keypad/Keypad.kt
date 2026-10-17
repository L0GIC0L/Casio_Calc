package com.l0gic0l.casiocalc.ui.keypad

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    sound: Boolean = false,
) {
    val view = LocalView.current
    fun press(spec: KeySpec) {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sound) view.playSoundEffect(SoundEffectConstants.CLICK)
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

    BoxWithConstraints(modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
        val rows = when (shape) {
            KeypadShape.STACKED -> Keys.functionRows.size + Keys.numberRows.size
            KeypadShape.WIDE -> maxOf(Keys.functionRows.size, Keys.numberRows.size)
        }
        // SHIFT labels go above the keys, as on the calculator, unless that would leave the keys
        // too short; then they sit inside the top of each key.
        val hints = when {
            !showHints -> Hints.NONE
            (maxHeight - 2.dp * (rows - 1)) / rows - HintHeight >= 40.dp -> Hints.ABOVE
            else -> Hints.INSIDE
        }
        // Labels above the keys already separate the rows.
        val gap = if (hints == Hints.ABOVE) 2.dp else 6.dp

        @Composable
        fun KeyRow(row: List<KeySpec>, rowModifier: Modifier) {
            Row(rowModifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (spec in row) Key(spec, state, hints, Modifier.weight(1f).fillMaxHeight()) { press(spec) }
            }
        }

        when (shape) {
            KeypadShape.STACKED -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in Keys.functionRows) KeyRow(row, Modifier.weight(0.85f))
                for (row in Keys.numberRows) KeyRow(row, Modifier.weight(1f))
            }
            KeypadShape.WIDE -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(6f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (row in Keys.functionRows) KeyRow(row, Modifier.weight(1f))
                }
                Column(Modifier.weight(5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    for (row in Keys.numberRows) KeyRow(row, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Where a key's SHIFT function is printed. */
private enum class Hints { NONE, ABOVE, INSIDE }

/** What a key currently shows: its own label, its SHIFT function or its ALPHA letter. */
private enum class Face { MAIN, SHIFT, ALPHA }

/** Height of the SHIFT label printed above each key, as on the calculator's case. */
private val HintHeight = 14.dp

@Composable
private fun Key(spec: KeySpec, state: InputState, hints: Hints, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val labels = LocalKeyLabelColors.current
    val shiftOn = state.shift
    val alphaOn = state.alpha || state.alphaLock
    val active = when (spec.style) {
        KeyStyle.SHIFT -> shiftOn
        KeyStyle.ALPHA -> alphaOn
        else -> false
    }
    val (containerTarget, contentTarget) = when (spec.style) {
        KeyStyle.NUMBER -> scheme.surfaceContainerHighest to scheme.onSurface
        KeyStyle.FUNCTION -> scheme.surfaceContainerHigh to scheme.onSurface
        KeyStyle.NAV -> scheme.primaryContainer to scheme.onPrimaryContainer
        KeyStyle.OPERATOR -> scheme.secondaryContainer to scheme.onSecondaryContainer
        KeyStyle.DELETE -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        KeyStyle.CLEAR -> scheme.errorContainer to scheme.onErrorContainer
        KeyStyle.EXE -> scheme.primary to scheme.onPrimary
        KeyStyle.SHIFT -> if (active) labels.shift to scheme.surface else scheme.surfaceContainerHigh to labels.shift
        KeyStyle.ALPHA -> if (active) labels.alpha to scheme.surface else scheme.surfaceContainerHigh to labels.alpha
    }
    val container by animateColorAsState(containerTarget, tween(180), label = "keyContainer")
    val content by animateColorAsState(contentTarget, tween(180), label = "keyContent")
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

    // The hint fades while SHIFT is on, because the key face then shows the same function.
    val hintAlpha by animateFloatAsState(if (face == Face.MAIN) 1f else 0f, tween(140), label = "keyHint")
    val hint = spec.shiftLabel?.takeIf { spec.style != KeyStyle.SHIFT }

    @Composable
    fun Hint(color: Color, size: Int, modifier: Modifier) {
        Box(modifier.graphicsLayer { alpha = hintAlpha }, contentAlignment = Alignment.Center) {
            if (hint != null) {
                Text(hint, color = color, fontSize = size.sp, lineHeight = (size + 2).sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
            }
        }
    }

    Column(modifier) {
        if (hints == Hints.ABOVE) Hint(labels.shift, 10, Modifier.fillMaxWidth().height(HintHeight))
        Surface(
            onClick = onClick,
            modifier = Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            shape = RoundedCornerShape(corner),
            color = container,
            contentColor = content,
            interactionSource = interaction,
        ) {
            // Surface stretches its children to fill it, so the hint and face get their own box.
            Box(Modifier.fillMaxSize()) {
                if (hints == Hints.INSIDE) {
                    Hint(
                        if (spec.style == KeyStyle.EXE) content.copy(alpha = 0.8f) else labels.shift,
                        9,
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = 3.dp).height(11.dp),
                    )
                }
                // Every face fills the same area and is centred in it, so swapping faces never moves the label.
                Crossfade(
                    face,
                    Modifier.fillMaxSize().padding(top = if (hints == Hints.INSIDE) 12.dp else 0.dp).graphicsLayer { alpha = contentAlpha },
                    animationSpec = tween(160),
                    label = "keyFace",
                ) { f ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
}

@Composable
private fun KeyText(text: String, color: Color, style: KeyStyle) {
    val size = when {
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
        val tint = when (label.accent) {
            KeyAccent.NONE -> LocalContentColor.current
            KeyAccent.PRIMARY -> MaterialTheme.colorScheme.primary
            KeyAccent.TERTIARY -> MaterialTheme.colorScheme.tertiary
        }
        Icon(label.image, contentDescription = null, tint = tint, modifier = Modifier.size(if (label.caption == null) 24.dp else 18.dp))
        if (label.caption != null) Text(label.caption, fontSize = 10.sp, lineHeight = 11.sp, maxLines = 1)
    }
}

@Composable
private fun KeyMath(row: MathRow, color: Color) {
    CompositionLocalProvider(LocalContentColor provides color) {
        MathView(row, fontSize = 13.sp, colors = defaultMathColors().copy(normal = color, function = color, operator = color, placeholder = color.copy(alpha = 0.7f)))
    }
}
