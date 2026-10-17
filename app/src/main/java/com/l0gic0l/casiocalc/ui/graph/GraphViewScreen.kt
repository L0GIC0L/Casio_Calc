package com.l0gic0l.casiocalc.ui.graph

import com.l0gic0l.casiocalc.ui.components.ToolButton
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.AspectRatio
import com.l0gic0l.casiocalc.ui.components.appBarInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.data.GraphType
import com.l0gic0l.casiocalc.data.ViewWindow
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.eval.Trig
import com.l0gic0l.casiocalc.math.graph.GraphSolve
import com.l0gic0l.casiocalc.math.numeric.Numeric
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.NumberFieldsDialog
import com.l0gic0l.casiocalc.ui.math.MathView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the canvas is doing: free pan/zoom, tracing a graph, or showing a G-Solv result. */
private sealed class Mode {
    data object Pan : Mode()
    data class Trace(val plot: Int, val param: Double) : Mode()
    data class Result(
        val title: String,
        val plot: Plot?,
        val points: List<Pair<Double, Double>>,
        val index: Int = 0,
        val area: Pair<Double, Double>? = null,
        val value: String? = null,
    ) : Mode()
}

private enum class Solve(val label: String) {
    ROOT("Root"), MAX("Max"), MIN("Min"), Y_ICEPT("Y-Intercept"), ISECT("Intersection"),
    Y_CAL("Y-Cal"), X_CAL("X-Cal"), INTEGRAL("∫dx"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphViewScreen(vm: CalcViewModel, onBack: () -> Unit) {
    val palette = graphPalette()
    @Suppress("UNUSED_VARIABLE") val version = vm.memory.version
    val (plots, errors) = remember(vm.graphFunctions.toList(), vm.settings, palette) {
        preparePlots(vm.graphFunctions, vm.memory, vm.settings, palette)
    }
    var mode by remember { mutableStateOf<Mode>(Mode.Pan) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoomMenu by remember { mutableStateOf(false) }
    var solveMenu by remember { mutableStateOf(false) }
    var showWindow by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Solve?>(null) }
    var picked by remember { mutableStateOf<List<Plot>?>(null) }
    var message by remember { mutableStateOf<String?>(if (errors.isNotEmpty()) "Syntax ERROR in ${errors.joinToString()}" else null) }
    val formatter = vm.formatter()
    val unit = vm.settings.angle
    val yPlots = plots.filter { it.fn.type == GraphType.Y }

    fun fmt(v: Double) = if (v.isNaN()) "—" else formatter.decimalString(v)

    /** Point on a plot for its parameter (x for Y=, θ/T otherwise, y for X=). */
    fun pointOf(p: Plot, t: Double): Pair<Double, Double> = when (p.fn.type) {
        GraphType.Y -> t to p.f.eval(t)
        GraphType.X -> p.f.eval(t) to t
        GraphType.R -> {
            val r = p.f.eval(Trig.fromRadians(t, unit))
            r * cos(t) to r * sin(t)
        }
        GraphType.PARAM -> p.f.eval(t) to (p.g?.eval(t) ?: Double.NaN)
    }

    // The stored default window is square on the calculator's screen; make it square on this one.
    LaunchedEffect(size) {
        if (size.width > 0 && vm.viewWindow == ViewWindow()) {
            vm.updateViewWindow(Zoom.initial(vm.viewWindow, size.height.toDouble() / size.width))
        }
    }

    fun runSolve(kind: Solve, chosen: List<Plot>, inputs: List<Double> = emptyList()) {
        val w = vm.viewWindow
        val p = chosen.first()
        mode = when (kind) {
            Solve.ROOT -> Mode.Result("Root", p, GraphSolve.roots(p.f, w.xmin, w.xmax).map { it to 0.0 })
            Solve.MAX -> Mode.Result("Maximum", p, GraphSolve.extrema(p.f, w.xmin, w.xmax, true))
            Solve.MIN -> Mode.Result("Minimum", p, GraphSolve.extrema(p.f, w.xmin, w.xmax, false))
            Solve.Y_ICEPT -> Mode.Result("Y-Intercept", p, listOf(0.0 to p.f.eval(0.0)).filter { it.second.isFinite() })
            Solve.ISECT -> {
                val q = chosen[1]
                Mode.Result("Intersection", p, GraphSolve.intersections(p.f, q.f, w.xmin, w.xmax).map { it to p.f.eval(it) })
            }
            Solve.Y_CAL -> Mode.Result("Y-Cal", p, listOf(inputs[0] to p.f.eval(inputs[0])).filter { it.second.isFinite() })
            Solve.X_CAL -> Mode.Result("X-Cal", p, GraphSolve.xForY(p.f, inputs[0], w.xmin, w.xmax).map { it to inputs[0] })
            Solve.INTEGRAL -> {
                val a = min(inputs[0], inputs[1])
                val b = max(inputs[0], inputs[1])
                val v = runCatching { Numeric.integrate({ p.f.eval(it) }, inputs[0], inputs[1]) }.getOrNull()
                Mode.Result("∫dx", p, emptyList(), area = a to b, value = v?.let { "∫dx = " + fmt(it) } ?: "Math ERROR")
            }
        }
        val r = mode as Mode.Result
        if (r.points.isEmpty() && r.value == null) message = "Not found"
    }

    fun startSolve(kind: Solve) {
        if (yPlots.isEmpty()) {
            message = "G-Solv works on Y= graphs"
            return
        }
        val needed = if (kind == Solve.ISECT) 2 else 1
        if (yPlots.size < needed) {
            message = "Needs two Y= graphs"
            return
        }
        pending = kind
        picked = if (yPlots.size == needed) yPlots else null
    }

    // In landscape the graph gets the height; the back button moves into the tool bar.
    val short = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 480.dp
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!short) TopAppBar(
                windowInsets = appBarInsets,
                title = { Text("Graph") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (short) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                    ToolButton(Icons.Default.TouchApp, "Trace", onClick = {
                        mode = if (mode is Mode.Trace || plots.isEmpty()) Mode.Pan else {
                            val p = plots.first()
                            val w = vm.viewWindow
                            Mode.Trace(0, when (p.fn.type) {
                                GraphType.Y -> (w.xmin + w.xmax) / 2
                                GraphType.X -> (w.ymin + w.ymax) / 2
                                else -> w.tmin
                            })
                        }
                    }, modifier = Modifier.weight(1f))
                    Box(Modifier.weight(1f)) {
                        ToolButton(Icons.Default.ZoomIn, "Zoom", onClick = { zoomMenu = true }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = zoomMenu, onDismissRequest = { zoomMenu = false }) {
                            val aspect = if (size.width > 0) size.height.toDouble() / size.width else 1.0
                            listOf(
                                "Zoom in ×2" to { w: ViewWindow -> Zoom.factor(w, 0.5) },
                                "Zoom out ×2" to { w: ViewWindow -> Zoom.factor(w, 2.0) },
                                "Auto (fit Y)" to { w: ViewWindow -> Zoom.auto(w, yPlots) },
                                "Standard ±10" to { w: ViewWindow -> Zoom.standard(w) },
                                "Trig" to { w: ViewWindow -> Zoom.trig(w, unit) },
                                "Initial" to { w: ViewWindow -> Zoom.initial(w, aspect) },
                                "Square" to { w: ViewWindow -> Zoom.square(w, aspect) },
                            ).forEach { (label, op) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    vm.updateViewWindow(op(vm.viewWindow))
                                    zoomMenu = false
                                    if (mode is Mode.Result) mode = Mode.Pan
                                })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        ToolButton(Icons.Default.Calculate, "G-Solv", onClick = { solveMenu = true }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = solveMenu, onDismissRequest = { solveMenu = false }) {
                            Solve.entries.forEach { s ->
                                DropdownMenuItem(text = { Text(s.label) }, onClick = {
                                    solveMenu = false
                                    startSolve(s)
                                })
                            }
                        }
                    }
                    ToolButton(Icons.Default.AspectRatio, "V-Win", onClick = { showWindow = true }, modifier = Modifier.weight(1f))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            GraphCanvas(
                vm = vm,
                plots = plots,
                mode = mode,
                pointOf = ::pointOf,
                onSize = { size = it },
                onTrace = { t -> (mode as? Mode.Trace)?.let { mode = it.copy(param = t) } },
            )
            Column(Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                when (val m = mode) {
                    is Mode.Trace -> plots.getOrNull(m.plot)?.let { p ->
                        val (x, y) = pointOf(p, m.param)
                        InfoCard(
                            p = p,
                            lines = buildList {
                                if (p.fn.type == GraphType.R) add("θ=${fmt(Trig.fromRadians(m.param, unit))}   r=${fmt(p.f.eval(Trig.fromRadians(m.param, unit)))}")
                                if (p.fn.type == GraphType.PARAM) add("T=${fmt(m.param)}")
                                add("X=${fmt(x)}   Y=${fmt(y)}")
                            },
                            onPrev = { mode = m.copy(param = m.param - step(p, vm.viewWindow)) },
                            onNext = { mode = m.copy(param = m.param + step(p, vm.viewWindow)) },
                            onUp = { mode = m.copy(plot = (m.plot + plots.size - 1) % plots.size) },
                            onDown = { mode = m.copy(plot = (m.plot + 1) % plots.size) },
                            onClose = { mode = Mode.Pan },
                        )
                    }
                    is Mode.Result -> {
                        val pt = m.points.getOrNull(m.index)
                        InfoCard(
                            p = m.plot,
                            title = m.title + if (m.points.size > 1) "  ${m.index + 1}/${m.points.size}" else "",
                            lines = listOfNotNull(pt?.let { "X=${fmt(it.first)}   Y=${fmt(it.second)}" }, m.value),
                            onPrev = if (m.points.size > 1) ({ mode = m.copy(index = (m.index + m.points.size - 1) % m.points.size) }) else null,
                            onNext = if (m.points.size > 1) ({ mode = m.copy(index = (m.index + 1) % m.points.size) }) else null,
                            onClose = { mode = Mode.Pan },
                        )
                    }
                    Mode.Pan -> if (plots.isEmpty()) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text("No graphs selected. Enter functions on the Graph list.", modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }

    if (showWindow) ViewWindowDialog(vm, onDismiss = { showWindow = false })

    message?.let { msg ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            text = { Text(msg) },
        )
    }

    val kind = pending
    if (kind != null) {
        val chosen = picked
        if (chosen == null) {
            PlotPicker(yPlots, count = if (kind == Solve.ISECT) 2 else 1, onDismiss = { pending = null }) { picked = it }
        } else {
            when (kind) {
                Solve.Y_CAL -> NumberFieldsDialog("Y-Cal", listOf("X"), listOf(null), onDismiss = { pending = null }) { runSolve(kind, chosen, it) }
                Solve.X_CAL -> NumberFieldsDialog("X-Cal", listOf("Y"), listOf(null), onDismiss = { pending = null }) { runSolve(kind, chosen, it) }
                Solve.INTEGRAL -> NumberFieldsDialog("∫dx", listOf("Lower", "Upper"), listOf(null, null), onDismiss = { pending = null }) { runSolve(kind, chosen, it) }
                else -> {
                    runSolve(kind, chosen)
                    pending = null
                }
            }
        }
    }
}

private fun step(p: Plot, w: ViewWindow): Double = when (p.fn.type) {
    GraphType.Y -> (w.xmax - w.xmin) / 126
    GraphType.X -> (w.ymax - w.ymin) / 126
    else -> w.tstep
}

@Composable
private fun PlotPicker(plots: List<Plot>, count: Int, onDismiss: () -> Unit, onPick: (List<Plot>) -> Unit) {
    var chosen by remember { mutableStateOf(listOf<Plot>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 2) "Choose two graphs" else "Choose a graph") },
        text = {
            Column {
                for (p in plots) {
                    ListItem(
                        modifier = Modifier.pointerInput(p) {
                            detectTapGestures {
                                chosen = if (p in chosen) chosen - p else (chosen + p).takeLast(count)
                                if (count == 1) onPick(listOf(p))
                            }
                        },
                        leadingContent = { Box(Modifier.size(12.dp).background(p.color, CircleShape)) },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.label + "=  ")
                                MathView(p.fn.expr, fontSize = 16.sp)
                            }
                        },
                        tonalElevation = if (p in chosen) 4.dp else 0.dp,
                    )
                }
            }
        },
        confirmButton = {
            if (count == 2) TextButton(enabled = chosen.size == 2, onClick = { onPick(chosen) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoCard(
    p: Plot?,
    lines: List<String>,
    title: String? = null,
    onPrev: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onClose: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), tonalElevation = 2.dp) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p != null) {
                    Box(Modifier.size(10.dp).background(p.color, CircleShape))
                    Text(" ${p.label}=", style = MaterialTheme.typography.labelLarge)
                    Box(Modifier.weight(1f, fill = false)) { MathView(p.fn.expr, fontSize = 16.sp) }
                }
                if (title != null) Text("  $title", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (onUp != null) IconButton(onClick = onUp) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous graph") }
                if (onDown != null) IconButton(onClick = onDown) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next graph") }
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                }
                if (onPrev != null) IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous") }
                if (onNext != null) IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next") }
            }
        }
    }
}

@Composable
private fun GraphCanvas(
    vm: CalcViewModel,
    plots: List<Plot>,
    mode: Mode,
    pointOf: (Plot, Double) -> Pair<Double, Double>,
    onSize: (IntSize) -> Unit,
    onTrace: (Double) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val colors = GridColors(scheme.outlineVariant.copy(alpha = 0.5f), scheme.onSurfaceVariant, scheme.onSurfaceVariant)
    val window by rememberUpdatedState(vm.viewWindow)
    val tracing = mode is Mode.Trace
    val tracePlot = (mode as? Mode.Trace)?.let { plots.getOrNull(it.plot) }
    val currentTracePlot by rememberUpdatedState(tracePlot)
    val prefs = vm.prefs
    val lineWidth = with(androidx.compose.ui.platform.LocalDensity.current) { prefs.graphLine.dp.dp.toPx() }

    val gestures = if (tracing) {
        Modifier.pointerInput(true) {
            fun traceTo(px: Float) {
                val p = currentTracePlot ?: return
                val w = window
                val t = when (p.fn.type) {
                    GraphType.Y -> w.xmin + px / size.width * (w.xmax - w.xmin)
                    GraphType.X -> w.ymax - px / size.width * (w.ymax - w.ymin)
                    else -> w.tmin + px / size.width * (w.tmax - w.tmin)
                }
                onTrace(t)
            }
            detectDragGestures { change, _ -> traceTo(change.position.x) }
        }.pointerInput("tap") {
            detectTapGestures { pos ->
                val p = currentTracePlot ?: return@detectTapGestures
                val w = window
                if (p.fn.type == GraphType.Y) onTrace(w.xmin + pos.x / size.width * (w.xmax - w.xmin))
            }
        }
    } else {
        Modifier.pointerInput(false) {
            // Pinch sideways to zoom x, up and down to zoom y, diagonally to zoom both.
            detectAxisZoomGestures { centroid, pan, zoomX, zoomY ->
                val w = window
                val sx = (w.xmax - w.xmin) / size.width
                val sy = (w.ymax - w.ymin) / size.height
                val cx = w.xmin + centroid.x * sx
                val cy = w.ymax - centroid.y * sy
                val nx0 = cx - (cx - w.xmin) / zoomX - pan.x * sx
                val nx1 = cx + (w.xmax - cx) / zoomX - pan.x * sx
                val ny0 = cy - (cy - w.ymin) / zoomY + pan.y * sy
                val ny1 = cy + (w.ymax - cy) / zoomY + pan.y * sy
                if (nx1 - nx0 > 1e-9 && ny1 - ny0 > 1e-9 && nx1 - nx0 < 1e12 && ny1 - ny0 < 1e12) {
                    vm.updateViewWindow(w.copy(xmin = nx0, xmax = nx1, ymin = ny0, ymax = ny1))
                }
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .background(scheme.surfaceContainerLowest)
            .onSizeChanged(onSize)
            .then(gestures),
    ) {
        val w = window
        val vp = Viewport(w.xmin, w.xmax, w.ymin, w.ymax, size.width, size.height)
        drawGridAndAxes(vp, w.xscl, w.yscl, prefs.graphGrid, prefs.graphAxes, prefs.graphLabels, colors, measurer)

        (mode as? Mode.Result)?.let { r ->
            val area = r.area
            val p = r.plot
            if (area != null && p != null) shade(vp, p, area.first, area.second)
        }
        for (p in plots) drawPlot(vp, p, w, lineWidth, pointOf)

        val marker: Pair<Pair<Double, Double>, androidx.compose.ui.graphics.Color>? = when (mode) {
            is Mode.Trace -> tracePlot?.let { pointOf(it, mode.param) to it.color }
            is Mode.Result -> mode.points.getOrNull(mode.index)?.let { it to (mode.plot?.color ?: scheme.primary) }
            Mode.Pan -> null
        }
        marker?.let { (pt, color) ->
            val (x, y) = pt
            if (x.isFinite() && y.isFinite()) {
                val px = vp.sx(x)
                val py = vp.sy(y)
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                drawLine(scheme.onSurface.copy(alpha = 0.5f), Offset(px, 0f), Offset(px, size.height), 1.5f, pathEffect = dash)
                drawLine(scheme.onSurface.copy(alpha = 0.5f), Offset(0f, py), Offset(size.width, py), 1.5f, pathEffect = dash)
                drawCircle(color, radius = 7.dp.toPx(), center = Offset(px, py))
                drawCircle(scheme.surface, radius = 3.dp.toPx(), center = Offset(px, py))
            }
        }
    }
}

private fun DrawScope.drawPlot(vp: Viewport, p: Plot, w: ViewWindow, lineWidth: Float, pointOf: (Plot, Double) -> Pair<Double, Double>) {
    val path = Path()
    var penDown = false
    var lastX = 0f
    var lastY = 0f
    val limit = size.height * 4
    fun add(px: Float, py: Float) {
        if (!px.isFinite() || !py.isFinite()) {
            penDown = false
            return
        }
        val jump = penDown && (abs(py - lastY) > size.height * 1.5f && (abs(px - lastX) < 4f))
        val cx = px.coerceIn(-limit, size.width + limit)
        val cy = py.coerceIn(-limit, size.height + limit)
        if (!penDown || jump) path.moveTo(cx, cy) else path.lineTo(cx, cy)
        penDown = true
        lastX = px
        lastY = py
    }
    when (p.fn.type) {
        GraphType.Y -> {
            var px = 0f
            while (px <= size.width) {
                val y = p.f.eval(vp.wx(px))
                if (y.isFinite()) add(px, vp.sy(y)) else penDown = false
                px += 1.5f
            }
        }
        GraphType.X -> {
            var py = 0f
            while (py <= size.height) {
                val x = p.f.eval(vp.wy(py))
                if (x.isFinite()) {
                    val sx = vp.sx(x)
                    if (penDown && abs(sx - lastX) > size.width * 1.5f) penDown = false
                    if (!penDown) path.moveTo(sx.coerceIn(-limit, size.width + limit), py) else path.lineTo(sx.coerceIn(-limit, size.width + limit), py)
                    penDown = true
                    lastX = sx
                } else penDown = false
                py += 1.5f
            }
        }
        GraphType.R, GraphType.PARAM -> {
            val steps = ((w.tmax - w.tmin) / w.tstep).toInt().coerceIn(1, 20_000)
            for (i in 0..steps) {
                val t = w.tmin + (w.tmax - w.tmin) * i / steps
                val (x, y) = pointOf(p, t)
                if (x.isFinite() && y.isFinite()) {
                    val sx = vp.sx(x); val sy = vp.sy(y)
                    if (!penDown) path.moveTo(sx.coerceIn(-limit, size.width + limit), sy.coerceIn(-limit, size.height + limit))
                    else path.lineTo(sx.coerceIn(-limit, size.width + limit), sy.coerceIn(-limit, size.height + limit))
                    penDown = true
                } else penDown = false
            }
        }
    }
    drawPath(path, p.color, style = Stroke(lineWidth))
}

private fun DrawScope.shade(vp: Viewport, p: Plot, a: Double, b: Double) {
    val path = Path()
    val x0 = vp.sx(a)
    val x1 = vp.sx(b)
    val base = vp.sy(0.0)
    path.moveTo(x0, base)
    var px = x0
    while (px <= x1) {
        val y = p.f.eval(vp.wx(px))
        path.lineTo(px, if (y.isFinite()) vp.sy(y).coerceIn(-size.height, size.height * 2) else base)
        px += 1.5f
    }
    path.lineTo(x1, base)
    path.close()
    drawPath(path, p.color.copy(alpha = 0.28f))
}

/** View-window presets from the Zoom menu. */
object Zoom {
    fun factor(w: ViewWindow, f: Double): ViewWindow {
        val cx = (w.xmin + w.xmax) / 2
        val cy = (w.ymin + w.ymax) / 2
        val hx = (w.xmax - w.xmin) / 2 * f
        val hy = (w.ymax - w.ymin) / 2 * f
        return w.copy(xmin = cx - hx, xmax = cx + hx, ymin = cy - hy, ymax = cy + hy)
    }

    fun standard(w: ViewWindow) = w.copy(xmin = -10.0, xmax = 10.0, xscl = 1.0, ymin = -10.0, ymax = 10.0, yscl = 1.0)

    fun trig(w: ViewWindow, unit: AngleUnit): ViewWindow {
        val (x, s) = when (unit) {
            AngleUnit.DEG -> 540.0 to 90.0
            AngleUnit.RAD -> 3 * Math.PI to Math.PI / 2
            AngleUnit.GRA -> 600.0 to 100.0
        }
        return w.copy(xmin = -x, xmax = x, xscl = s, ymin = -1.6, ymax = 1.6, yscl = 0.5)
    }

    fun initial(w: ViewWindow, aspect: Double) =
        w.copy(xmin = -6.3, xmax = 6.3, xscl = 1.0, ymin = -6.3 * aspect, ymax = 6.3 * aspect, yscl = 1.0)

    fun square(w: ViewWindow, aspect: Double): ViewWindow {
        val cy = (w.ymin + w.ymax) / 2
        val half = (w.xmax - w.xmin) * aspect / 2
        return w.copy(ymin = cy - half, ymax = cy + half)
    }

    fun auto(w: ViewWindow, plots: List<Plot>): ViewWindow {
        val ys = ArrayList<Double>()
        for (p in plots) for (i in 0..200) {
            val y = p.f.eval(w.xmin + (w.xmax - w.xmin) * i / 200)
            if (y.isFinite()) ys += y
        }
        if (ys.isEmpty()) return w
        ys.sort()
        // Ignore the extreme 2% so asymptotes do not flatten the picture.
        val lo = ys[(ys.size * 0.02).toInt()]
        val hi = ys[((ys.size - 1) * 0.98).toInt()]
        val pad = max((hi - lo) * 0.1, 1e-6)
        return w.copy(ymin = lo - pad, ymax = hi + pad)
    }
}
