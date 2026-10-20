package com.l0gic0l.casiocalc.ui.calculus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.cas.Render
import com.l0gic0l.casiocalc.math.cas.Step
import com.l0gic0l.casiocalc.math.cas.Sym
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.ui.math.MathView
import com.l0gic0l.casiocalc.ui.math.ScrollingMath

/** The problem as typed so far, in natural display; tapping it opens the editor. */
@Composable
internal fun ProblemCard(label: String, row: MathRow, selected: Boolean, description: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$label: $description" },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.CenterStart) {
                ScrollingMath(row, fontSize = 28.sp)
            }
            Text("Tap to edit", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** A small labelled box for a limit or a point, tapped to edit like a table cell. */
@Composable
internal fun SmallInput(label: String, row: MathRow, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
        modifier = modifier.semantics { contentDescription = "$label: " + if (row.isEmpty) "empty" else RowText.linear(row) },
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
                if (row.isEmpty) Text("—", fontSize = 20.sp, color = scheme.outline) else ScrollingMath(row, fontSize = 20.sp)
            }
        }
    }
}

/** Solve, Clear and a menu of worked examples. */
@Composable
internal fun Actions(canSolve: Boolean, solving: Boolean, examples: List<MathRow>, onSolve: () -> Unit, onClear: () -> Unit, onExample: (MathRow) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column {
        // Wraps onto a second line with large text or on a narrow screen instead of squeezing Examples.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onSolve, enabled = canSolve && !solving) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Solve", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onClear) { Text("Clear") }
            Box {
                TextButton(onClick = { menu = true }) {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Examples", modifier = Modifier.padding(start = 6.dp))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    for (e in examples) {
                        DropdownMenuItem(
                            text = { MathView(e, fontSize = 20.sp) },
                            onClick = {
                                menu = false
                                onExample(e)
                            },
                            modifier = Modifier.semantics { contentDescription = RowText.linear(e) },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(solving, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }
}

@Composable
internal fun ErrorCard(message: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** A new answer fades and grows in; [content] is keyed on [solution] so each solve animates. */
@Composable
internal fun Appearing(solution: Any, content: @Composable () -> Unit) {
    key(solution) {
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(appear, enter = fadeIn() + expandVertically()) { content() }
    }
}

@Composable
internal fun ResultCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        // TalkBack reads a new answer out when it arrives.
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Result", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            content()
        }
    }
}

/** Math in a result or a step, which TalkBack reads in its linear form, as (X^4ln(X))/4. */
@Composable
internal fun SpokenMath(row: MathRow, modifier: Modifier = Modifier, fontSize: TextUnit = 24.sp) {
    ScrollingMath(row, modifier.semantics { contentDescription = RowText.linear(row) }, fontSize = fontSize)
}

/** A note with a tick (checked) or a warning icon, used under results. */
@Composable
internal fun CheckNote(ok: Boolean, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (ok) scheme.primary else scheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = scheme.onSecondaryContainer, modifier = Modifier.padding(start = 8.dp))
    }
}

/** An exact value with a button to switch to its decimal form, as S⇔D does. */
@Composable
internal fun ValueLine(prefix: MathRow, value: Value, formatter: Formatter) {
    var decimal by remember(value) { mutableStateOf(false) }
    val row = remember(value, decimal, formatter) {
        val v = formatter.format(value, if (decimal) Formatter.Style.DECIMAL else Formatter.Style.AUTO)
        MathRow(prefix.copy().nodes.map { it.copy() } + v.nodes)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpokenMath(row, Modifier.weight(1f), fontSize = 24.sp)
        if (formatter.hasAlternateForm(value)) OutlinedButton(onClick = { decimal = !decimal }) { Text("S⇔D") }
    }
}

/**
 * prefix = value. A symbolic answer such as −π² or 2ln(2) is shown as it is, with its decimal
 * value on the line below; a plain number gets the S⇔D button instead.
 */
@Composable
internal fun ExactValue(prefix: MathRow, symbolic: Sym?, value: Value, formatter: Formatter) {
    if (symbolic == null || symbolic is Sym.Num) {
        ValueLine(prefix, value, formatter)
        return
    }
    val exact = remember(prefix, symbolic) { MathRow(prefix.copy().nodes.map { it.copy() } + Render().row(symbolic).nodes) }
    SpokenMath(exact, fontSize = 24.sp)
    val approx = remember(value, formatter) {
        MathRow(listOf<Node>(TokenNode(Render.label("≈"))) + formatter.format(value, Formatter.Style.DECIMAL).nodes)
    }
    SpokenMath(approx, fontSize = 24.sp)
}

/** The worked solution: one card of steps, the first two levels open. */
@Composable
internal fun StepsCard(steps: List<Step>) {
    if (steps.isEmpty()) return
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text("Steps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
            }
            for (s in steps) StepItem(s, depth = 0)
        }
    }
}

private val stepColours: List<@Composable () -> Color> = listOf(
    { MaterialTheme.colorScheme.primary },
    { MaterialTheme.colorScheme.tertiary },
    { MaterialTheme.colorScheme.secondary },
)

@Composable
private fun StepItem(step: Step, depth: Int) {
    var open by remember(step) { mutableStateOf(depth < 2) }
    val colour = stepColours[depth % stepColours.size]()
    val scheme = MaterialTheme.colorScheme
    val hasDetail = step.children.isNotEmpty()
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = hasDetail) { open = !open }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(colour, CircleShape))
            Text(step.title, style = MaterialTheme.typography.titleSmall, color = colour, modifier = Modifier.weight(1f).padding(start = 10.dp))
            if (hasDetail) {
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Hide details" else "Show details", tint = scheme.onSurfaceVariant)
            }
        }
        step.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp)) }
        for (m in step.math) SpokenMath(m, Modifier.padding(start = 20.dp, top = 4.dp), fontSize = 20.sp)
        if (hasDetail) {
            AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                // A line down the left joins a step to its details.
                Column(
                    Modifier
                        .padding(start = 4.dp)
                        .drawBehind { drawLine(colour.copy(alpha = 0.4f), Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx()) }
                        .padding(start = 12.dp),
                ) {
                    for (c in step.children) StepItem(c, depth + 1)
                }
            }
        }
    }
}

@Composable
internal fun Footnote(text: String) {
    Row(Modifier.padding(top = 4.dp, bottom = 24.dp)) {
        Box(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
