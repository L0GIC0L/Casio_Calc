package com.l0gic0l.casiocalc.ui.stat

import com.l0gic0l.casiocalc.ui.components.appBarInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.numeric.Stats
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.graph.GridColors
import com.l0gic0l.casiocalc.ui.graph.Viewport
import com.l0gic0l.casiocalc.ui.graph.drawGridAndAxes
import com.l0gic0l.casiocalc.ui.graph.graphPalette
import com.l0gic0l.casiocalc.ui.graph.niceStep
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max

/** The regression equation as a Y= expression in X. */
fun regressionRow(reg: Stats.Regression): MathRow {
    val f = Formatter(Settings())
    val c = reg.coefficients
    val nodes = ArrayList<Node>()
    fun x() = TokenNode(Tokens.X)
    fun num(v: Double) = f.decimalNodes(v)
    fun signed(v: Double) {
        if (v < 0) {
            nodes += TokenNode(Tokens.SUB); nodes += num(-v)
        } else {
            nodes += TokenNode(Tokens.ADD); nodes += num(v)
        }
    }
    fun power(n: Int) = Template.create(TemplateKind.POWER, listOf(TokenNode(Tokens.digit(n))))
    when (reg.type) {
        Stats.RegType.LINEAR -> { nodes += num(c[0]); nodes += x(); signed(c[1]) }
        Stats.RegType.LINEAR_ALT -> { nodes += num(c[0]); signed(c[1]); nodes += x() }
        Stats.RegType.QUADRATIC, Stats.RegType.CUBIC, Stats.RegType.QUARTIC -> {
            val degree = c.size - 1
            c.forEachIndexed { i, k ->
                val p = degree - i
                if (i == 0) nodes += num(k) else signed(k)
                if (p >= 1) nodes += x()
                if (p >= 2) nodes += power(p)
            }
        }
        Stats.RegType.LOG -> {
            nodes += num(c[0]); signed(c[1]); nodes += TokenNode(Tokens.fn("ln")); nodes += x(); nodes += TokenNode(Tokens.RPAREN)
        }
        Stats.RegType.EXP -> {
            nodes += num(c[0]); nodes += TokenNode(Tokens.E)
            nodes += Template.create(TemplateKind.POWER, num(c[1]) + x())
        }
        Stats.RegType.AB_EXP -> {
            nodes += num(c[0]); nodes += TokenNode(Tokens.MUL); nodes += num(c[1])
            nodes += Template.create(TemplateKind.POWER, listOf(x()))
        }
        Stats.RegType.POWER -> {
            nodes += num(c[0]); nodes += x()
            nodes += Template.create(TemplateKind.POWER, num(c[1]))
        }
    }
    return MathRow(nodes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatGraphScreen(vm: CalcViewModel, onBack: () -> Unit) {
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var showReg by rememberSaveable { mutableStateOf(true) }
    val s = vm.statSettings
    fun data(n: Int) = vm.memory.getList(n)?.items?.map { if (it.isReal) it.re.toDouble() else Double.NaN } ?: emptyList()
    val xs = data(s.xList)
    val ys = data(s.yList)
    val freq = if (s.freqList == 0) null else data(s.freqList)
    val palette = graphPalette()
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val reg = vm.lastRegression
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = appBarInsets,
                title = { Text("Stat Graph") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                val labels = listOf("Scatter", "xyLine", "Histogram", "Box")
                labels.forEachIndexed { i, l ->
                    SegmentedButton(selected = kind == i, onClick = { kind = i }, shape = SegmentedButtonDefaults.itemShape(i, labels.size)) { Text(l, maxLines = 1) }
                }
            }
            if (kind <= 1 && reg != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Checkbox(checked = showReg, onCheckedChange = { showReg = it })
                    Text("Show ${reg.type.label} (${reg.type.formula})", style = MaterialTheme.typography.bodyMedium)
                }
            }
            val paired = kind <= 1
            val valid = if (paired) xs.isNotEmpty() && xs.size == ys.size else xs.isNotEmpty()
            if (!valid) {
                Text(
                    if (paired) "List ${s.xList} and List ${s.yList} need the same number of values." else "List ${s.xList} is empty.",
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }
            Canvas(Modifier.fillMaxSize().background(scheme.surfaceContainerLowest)) {
                val colors = GridColors(scheme.outlineVariant.copy(alpha = 0.5f), scheme.onSurfaceVariant, scheme.onSurfaceVariant)
                val dot = palette[0]
                val line = palette[1]
                fun padded(lo: Double, hi: Double): Pair<Double, Double> {
                    val span = if (hi - lo == 0.0) max(1.0, kotlin.math.abs(hi)) else hi - lo
                    return lo - span * 0.1 to hi + span * 0.1
                }
                when (kind) {
                    0, 1 -> {
                        val (x0, x1) = padded(xs.minOrNull()!!, xs.maxOrNull()!!)
                        val (y0, y1) = padded(ys.minOrNull()!!, ys.maxOrNull()!!)
                        val vp = Viewport(x0, x1, y0, y1, size.width, size.height)
                        drawGridAndAxes(vp, 0.0, 0.0, vm.prefs.graphGrid, true, vm.prefs.graphLabels, colors, measurer)
                        if (kind == 1) {
                            val order = xs.indices.sortedBy { xs[it] }
                            val path = Path()
                            order.forEachIndexed { i, k ->
                                if (i == 0) path.moveTo(vp.sx(xs[k]), vp.sy(ys[k])) else path.lineTo(vp.sx(xs[k]), vp.sy(ys[k]))
                            }
                            drawPath(path, dot, style = Stroke(2.dp.toPx()))
                        }
                        for (i in xs.indices) {
                            drawRect(dot, topLeft = Offset(vp.sx(xs[i]) - 4.dp.toPx(), vp.sy(ys[i]) - 4.dp.toPx()), size = Size(8.dp.toPx(), 8.dp.toPx()))
                        }
                        if (reg != null && showReg) {
                            val path = Path()
                            var pen = false
                            var px = 0f
                            while (px <= size.width) {
                                val y = reg.eval(vp.wx(px))
                                if (y.isFinite()) {
                                    val py = vp.sy(y).coerceIn(-size.height, size.height * 2)
                                    if (pen) path.lineTo(px, py) else path.moveTo(px, py)
                                    pen = true
                                } else pen = false
                                px += 2f
                            }
                            drawPath(path, line, style = Stroke(2.5.dp.toPx()))
                        }
                    }
                    2 -> {
                        val w = freq ?: List(xs.size) { 1.0 }
                        val lo = xs.minOrNull()!!
                        val hi = xs.maxOrNull()!!
                        val n = w.sum()
                        val bins = ceil(ln(max(n, 1.0)) / ln(2.0) + 1).toInt().coerceIn(1, 30)
                        val width = if (hi == lo) 1.0 else niceStep(hi - lo, bins.toFloat(), 1f, 0.0)
                        val start = floor(lo / width) * width
                        val count = ceil((hi - start) / width).toInt().coerceAtLeast(1) + if ((hi - start) % width == 0.0) 1 else 0
                        val heights = DoubleArray(count)
                        xs.indices.forEach { i ->
                            val b = ((xs[i] - start) / width).toInt().coerceIn(0, count - 1)
                            heights[b] += w[i]
                        }
                        val top = heights.maxOrNull() ?: 1.0
                        val vp = Viewport(start - width * 0.5, start + width * (count + 0.5), -top * 0.08, top * 1.15, size.width, size.height)
                        drawGridAndAxes(vp, width, 0.0, vm.prefs.graphGrid, true, vm.prefs.graphLabels, colors, measurer)
                        for (b in 0 until count) {
                            val left = vp.sx(start + b * width)
                            val right = vp.sx(start + (b + 1) * width)
                            val y = vp.sy(heights[b])
                            drawRect(dot.copy(alpha = 0.35f), Offset(left, y), Size(right - left, vp.sy(0.0) - y))
                            drawRect(dot, Offset(left, y), Size(right - left, vp.sy(0.0) - y), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                    else -> {
                        val st = Stats.oneVar(xs, freq)
                        val (x0, x1) = padded(st.min, st.max)
                        val vp = Viewport(x0, x1, -1.0, 1.0, size.width, size.height)
                        drawGridAndAxes(vp, 0.0, 0.0, vm.prefs.graphGrid, false, vm.prefs.graphLabels, colors, measurer)
                        val stroke = 2.dp.toPx()
                        val yMid = vp.sy(0.0)
                        val h = size.height * 0.18f
                        drawLine(dot, Offset(vp.sx(st.min), yMid), Offset(vp.sx(st.q1), yMid), stroke)
                        drawLine(dot, Offset(vp.sx(st.q3), yMid), Offset(vp.sx(st.max), yMid), stroke)
                        drawLine(dot, Offset(vp.sx(st.min), yMid - h / 3), Offset(vp.sx(st.min), yMid + h / 3), stroke)
                        drawLine(dot, Offset(vp.sx(st.max), yMid - h / 3), Offset(vp.sx(st.max), yMid + h / 3), stroke)
                        drawRect(dot.copy(alpha = 0.25f), Offset(vp.sx(st.q1), yMid - h / 2), Size(vp.sx(st.q3) - vp.sx(st.q1), h))
                        drawRect(dot, Offset(vp.sx(st.q1), yMid - h / 2), Size(vp.sx(st.q3) - vp.sx(st.q1), h), style = Stroke(stroke))
                        drawLine(line, Offset(vp.sx(st.median), yMid - h / 2), Offset(vp.sx(st.median), yMid + h / 2), stroke * 1.5f)
                    }
                }
            }
        }
    }
}
