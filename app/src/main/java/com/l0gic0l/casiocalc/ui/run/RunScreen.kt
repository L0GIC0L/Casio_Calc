package com.l0gic0l.casiocalc.ui.run

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.data.HistoryEntry
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.adaptive.CalcScreenLayout
import com.l0gic0l.casiocalc.ui.adaptive.Fold
import com.l0gic0l.casiocalc.ui.adaptive.currentFold
import com.l0gic0l.casiocalc.ui.components.CalcKeypad
import com.l0gic0l.casiocalc.ui.components.StatusLine
import com.l0gic0l.casiocalc.ui.effects.ParticleField
import com.l0gic0l.casiocalc.ui.effects.rememberParticleState
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.ui.math.MathInputField
import com.l0gic0l.casiocalc.ui.math.MathView
import com.l0gic0l.casiocalc.ui.math.ScrollingMath
import com.l0gic0l.casiocalc.ui.math.defaultMathColors
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.launch

@Composable
fun RunScreen(vm: CalcViewModel, onBack: () -> Unit, onSettings: () -> Unit, fold: Fold = currentFold()) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        CalcScreenLayout(
            modifier = Modifier.padding(padding),
            fold = fold,
            display = { m -> Display(vm, onBack, onSettings, m) },
            keypad = { shape, m ->
                CalcKeypad(
                    vm = vm,
                    input = vm.runInput,
                    onCommand = {},
                    onSetup = onSettings,
                    shape = shape,
                    modifier = m,
                    handle = vm::runKey,
                )
            },
        )
    }
}

/** The calculator's screen: status line, history and the line being typed. */
@Composable
private fun Display(vm: CalcViewModel, onBack: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            StatusLine(
                vm,
                leading = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Main menu") }
                },
                trailing = {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Clear history") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    vm.clearHistory()
                                    menu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Set up") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onSettings()
                                },
                            )
                        }
                    }
                },
            )
            HistoryAndInput(vm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HistoryAndInput(vm: CalcViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    val history = vm.history
    val formatter = vm.formatter()
    val scale = vm.prefs.displaySize.factor
    val particles = rememberParticleState(seed = 11)
    val density = LocalDensity.current.density
    // The list is anchored at the bottom; bring the input line back into view when typing.
    LaunchedEffect(vm.runInput.revision, history.size) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.animateScrollToItem(0)
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Short displays (phone landscape) put each calculation on one line so the answer fits.
        val compact = maxHeight < 200.dp
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        // A new answer arrives with a small shower of sparks. Keyed on the newest entry, not the
        // count, which stops growing once the history is full.
        val newest = history.lastOrNull()?.id
        var seen by remember { mutableStateOf(newest) }
        LaunchedEffect(newest) {
            if (newest != null && newest != seen && vm.prefs.showResultParticles) {
                // Roughly where the newest answer lands: just above the input line, at the right.
                particles.burst(width * 0.8f, (height - (if (compact) 66f else 110f) * density).coerceAtLeast(height * 0.3f), 28, density)
            }
            seen = newest
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, reverseLayout = true) {
            item(key = "input") {
                Column(Modifier.fillMaxWidth().heightIn(min = if (compact) 48.dp else 72.dp).padding(horizontal = 16.dp, vertical = if (compact) 2.dp else 8.dp)) {
                    MathInputField(vm.runInput, Modifier.fillMaxWidth(), fontSize = (if (compact) 24f else 30f * scale).sp)
                    LivePreview(vm, formatter, (if (compact) 16f else 20f * scale).sp, Modifier.align(Alignment.End))
                    AnimatedVisibility(vm.computing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("  Calculating…  AC to stop", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    val error = vm.runError
                    AnimatedVisibility(error != null && !vm.computing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically(), modifier = Modifier.align(Alignment.End)) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(error ?: "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
            items(history.asReversed(), key = { it.id }) { entry ->
                HistoryItem(entry, formatter, vm, compact, scale, maxWidth * 0.6f, Modifier.animateItem())
            }
        }
        ParticleField(
            particles,
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary),
            Modifier.fillMaxSize(),
            count = 0,
            running = vm.prefs.showResultParticles,
        )
    }
}

/**
 * "= value" under the line being typed, in a quiet colour, for simple calculations. It is hidden
 * while a calculation runs, after an error, and when it would only repeat the input (π, 1/2).
 */
@Composable
private fun LivePreview(vm: CalcViewModel, formatter: Formatter, fontSize: TextUnit, modifier: Modifier) {
    val value = vm.runPreview
    val input = vm.runInput.row
    @Suppress("UNUSED_VARIABLE") val typing = vm.runInput.revision
    val result = remember(value, formatter) { value?.let { formatter.format(it) } }
    val visible = result != null && !vm.computing && vm.runError == null && RowText.linear(result) != RowText.linear(input)
    // Keeps the last value on screen while it fades out.
    var shown by remember { mutableStateOf<MathRow?>(null) }
    if (visible) shown = result
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val row = shown ?: return@AnimatedVisibility
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        val line = remember(row) { MathRow(listOf<Node>(TokenNode(Tokens.EQ)) + row.copy().nodes) }
        Box(Modifier.semantics { contentDescription = "Preview " + RowText.linear(line) }) {
            MathView(line, fontSize = fontSize, colors = defaultMathColors().copy(normal = muted, function = muted, operator = muted))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    entry: HistoryEntry,
    formatter: Formatter,
    vm: CalcViewModel,
    compact: Boolean,
    scale: Float,
    /** In a one-line row the answer gets at most this much, so the input always shows too. */
    answerMaxWidth: Dp,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    fun copy(text: String) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("CG Calc", text))) }
    }
    val result = remember(entry, entry.decimal, formatter) {
        val style = if (entry.decimal) Formatter.Style.DECIMAL else entry.style
        formatter.format(entry.value, style)
    }
    val scheme = MaterialTheme.colorScheme
    val muted = defaultMathColors().copy(normal = scheme.onSurfaceVariant, operator = scheme.onSurfaceVariant)
    // A new answer grows into place.
    val appear = remember { Animatable(if (entry.fresh && !vm.prefs.reduceMotion) 0f else 1f) }
    LaunchedEffect(entry) {
        entry.fresh = false
        appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    Box(modifier) {
        val click = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { vm.loadIntoInput(entry.input) }, onLongClick = { menu = true })
        val answer = Modifier.graphicsLayer {
            val p = appear.value
            alpha = p.coerceIn(0f, 1f)
            scaleX = 0.85f + 0.15f * p
            scaleY = 0.85f + 0.15f * p
            transformOrigin = TransformOrigin(1f, 0.5f)
        }
        if (compact) {
            Row(click.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScrollingMath(entry.input, fontSize = 16.sp, colors = muted) }
                Box(answer.padding(start = 12.dp).widthIn(max = answerMaxWidth)) { ScrollingMath(result, fontSize = 20.sp, reverse = true) }
            }
        } else {
            Column(click.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ScrollingMath(entry.input, fontSize = (19f * scale).sp, colors = muted)
                Box(Modifier.fillMaxWidth().then(answer), contentAlignment = Alignment.CenterEnd) {
                    ScrollingMath(result, fontSize = (28f * scale).sp, reverse = true)
                }
            }
        }
        HorizontalDivider(
            color = scheme.outlineVariant.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            @Composable
            fun MenuItem(text: String, icon: ImageVector, action: () -> Unit) {
                DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, contentDescription = null) }, onClick = {
                    action()
                    menu = false
                })
            }
            MenuItem("Edit", Icons.Default.Edit) { vm.loadIntoInput(entry.input) }
            MenuItem("Insert result", Icons.Default.Input) { vm.insertIntoInput(result) }
            MenuItem("Copy input", Icons.Default.ContentCopy) { copy(RowText.linear(entry.input)) }
            MenuItem("Copy result", Icons.Default.ContentCopy) { copy(RowText.linear(result)) }
            MenuItem(if (entry.decimal) "Show exact" else "Show decimal", Icons.Default.SwapHoriz) { entry.decimal = !entry.decimal }
            MenuItem("Delete", Icons.Default.Delete) { vm.removeHistory(entry) }
        }
    }
}
