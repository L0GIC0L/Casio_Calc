package com.l0gic0l.casiocalc.ui.graph

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.data.GraphFunction
import com.l0gic0l.casiocalc.data.GraphType
import com.l0gic0l.casiocalc.math.Calculator
import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.graph.FastCompiler
import com.l0gic0l.casiocalc.math.graph.RealFn
import com.l0gic0l.casiocalc.math.parse.Parser
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import java.util.Locale

private val lightPalette = listOf(
    Color(0xFF1E63D6), Color(0xFFD32F2F), Color(0xFF2E7D32), Color(0xFF8E24AA), Color(0xFFEF6C00), Color(0xFF00838F),
)
private val darkPalette = listOf(
    Color(0xFF82B1FF), Color(0xFFFF8A80), Color(0xFF81C784), Color(0xFFCE93D8), Color(0xFFFFB74D), Color(0xFF4DD0E1),
)

/** Graph line colours that read well on the current surface. */
@Composable
fun graphPalette(): List<Color> =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) darkPalette else lightPalette

/** A graph slot compiled for plotting. [g] is Yt for parametric graphs. */
class Plot(val index: Int, val fn: GraphFunction, val f: RealFn, val g: RealFn?, val color: Color) {
    val label: String
        get() = when (fn.type) {
            GraphType.Y -> "Y${index + 1}"
            GraphType.R -> "r${index + 1}"
            GraphType.PARAM -> "Xt${index + 1}"
            GraphType.X -> "X${index + 1}"
        }
}

/** Variable id each graph type is written in. */
fun GraphType.variableId() = when (this) {
    GraphType.Y -> "X"
    GraphType.R -> "theta"
    GraphType.PARAM -> "T"
    GraphType.X -> "Y"
}

fun compileRow(row: com.l0gic0l.casiocalc.math.editor.MathRow, variable: String, memory: Memory, settings: Settings): RealFn {
    val expr = Parser.parse(row)
    return FastCompiler(Calculator.evaluator(expr, memory, settings), variable).compile(expr)
}

/** Compiles the enabled functions; returns the plots plus labels of slots that failed to parse. */
fun preparePlots(functions: List<GraphFunction>, memory: Memory, settings: Settings, palette: List<Color>): Pair<List<Plot>, List<String>> {
    val plots = ArrayList<Plot>()
    val errors = ArrayList<String>()
    functions.forEachIndexed { i, fn ->
        if (!fn.enabled || fn.isEmpty) return@forEachIndexed
        try {
            val v = fn.type.variableId()
            val f = compileRow(fn.expr, v, memory, settings)
            val g = if (fn.type == GraphType.PARAM) compileRow(fn.expr2, v, memory, settings) else null
            plots += Plot(i, fn, f, g, palette[fn.colorIndex % palette.size])
        } catch (e: CalcException) {
            errors += "${fn.type.label.dropLast(1)}${i + 1}"
        }
    }
    return plots to errors
}

/** Maps between world coordinates and pixels for a canvas of [width]×[height]. */
class Viewport(val xmin: Double, val xmax: Double, val ymin: Double, val ymax: Double, val width: Float, val height: Float) {
    fun sx(x: Double) = ((x - xmin) / (xmax - xmin) * width).toFloat()
    fun sy(y: Double) = ((ymax - y) / (ymax - ymin) * height).toFloat()
    fun wx(px: Float) = xmin + px / width * (xmax - xmin)
    fun wy(py: Float) = ymax - py / height * (ymax - ymin)
}

/** A "nice" tick step (1, 2 or 5 × 10ⁿ) so ticks are at least [minPx] apart. */
fun niceStep(range: Double, pixels: Float, minPx: Float, preferred: Double): Double {
    if (preferred > 0 && preferred / range * pixels >= minPx) return preferred
    val raw = range / pixels * minPx
    val mag = 10.0.pow(floor(log10(raw)))
    val f = raw / mag
    val n = when {
        f <= 1 -> 1.0
        f <= 2 -> 2.0
        f <= 5 -> 5.0
        else -> 10.0
    }
    return n * mag
}

fun tickLabel(v: Double, step: Double): String {
    if (abs(v) < step * 1e-6) return "0"
    val decimals = (-floor(log10(step))).toInt().coerceIn(0, 6)
    return if (abs(v) >= 1e6 || abs(v) < 1e-4) "%.2e".format(Locale.US, v) else "%.${decimals}f".format(Locale.US, v).let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }
}

data class GridColors(val grid: Color, val axis: Color, val label: Color)

fun DrawScope.drawGridAndAxes(
    vp: Viewport,
    xscl: Double,
    yscl: Double,
    grid: Boolean,
    axes: Boolean,
    labels: Boolean,
    colors: GridColors,
    measurer: TextMeasurer,
) {
    val minPx = 22.dp.toPx()
    val xs = niceStep(vp.xmax - vp.xmin, vp.width, minPx, xscl)
    val ys = niceStep(vp.ymax - vp.ymin, vp.height, minPx, yscl)
    if (grid) {
        var x = ceil(vp.xmin / xs) * xs
        while (x <= vp.xmax) {
            val px = vp.sx(x)
            drawLine(colors.grid, Offset(px, 0f), Offset(px, vp.height), 1f)
            x += xs
        }
        var y = ceil(vp.ymin / ys) * ys
        while (y <= vp.ymax) {
            val py = vp.sy(y)
            drawLine(colors.grid, Offset(0f, py), Offset(vp.width, py), 1f)
            y += ys
        }
    }
    if (!axes) return
    val ax = vp.sx(0.0).coerceIn(0f, vp.width)
    val ay = vp.sy(0.0).coerceIn(0f, vp.height)
    val stroke = 1.5.dp.toPx()
    drawLine(colors.axis, Offset(0f, ay), Offset(vp.width, ay), stroke)
    drawLine(colors.axis, Offset(ax, 0f), Offset(ax, vp.height), stroke)
    val tick = 4.dp.toPx()
    val style = TextStyle(fontSize = 10.sp, color = colors.label)
    val labelEvery = ((56.dp.toPx() / (xs / (vp.xmax - vp.xmin) * vp.width)).toInt()).coerceAtLeast(1)
    var i = ceil(vp.xmin / xs).toLong()
    while (i * xs <= vp.xmax) {
        val px = vp.sx(i * xs)
        drawLine(colors.axis, Offset(px, ay - tick), Offset(px, ay + tick), stroke)
        if (labels && i != 0L && i % labelEvery == 0L) {
            val t = measurer.measure(tickLabel(i * xs, xs), style)
            val ty = if (ay + tick + t.size.height > vp.height) ay - tick - t.size.height else ay + tick
            drawText(t, topLeft = Offset(px - t.size.width / 2f, ty))
        }
        i++
    }
    val yLabelEvery = ((40.dp.toPx() / (ys / (vp.ymax - vp.ymin) * vp.height)).toInt()).coerceAtLeast(1)
    var j = ceil(vp.ymin / ys).toLong()
    while (j * ys <= vp.ymax) {
        val py = vp.sy(j * ys)
        drawLine(colors.axis, Offset(ax - tick, py), Offset(ax + tick, py), stroke)
        if (labels && j != 0L && j % yLabelEvery == 0L) {
            val t = measurer.measure(tickLabel(j * ys, ys), style)
            val tx = if (ax + tick + 2 + t.size.width > vp.width) ax - tick - 2 - t.size.width else ax + tick + 2
            drawText(t, topLeft = Offset(tx, py - t.size.height / 2f))
        }
        j++
    }
}
