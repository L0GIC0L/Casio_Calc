package com.l0gic0l.casiocalc.ui.math

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.layout.CursorStop
import com.l0gic0l.casiocalc.math.layout.DrawOp
import com.l0gic0l.casiocalc.math.layout.LayoutEngine
import com.l0gic0l.casiocalc.math.layout.MathLayout
import com.l0gic0l.casiocalc.math.layout.Role
import com.l0gic0l.casiocalc.math.layout.TextWidth

/** Colours used for the parts of an expression. */
data class MathColors(val normal: Color, val function: Color, val operator: Color, val placeholder: Color, val cursor: Color)

@Composable
fun defaultMathColors(): MathColors {
    val content = LocalContentColor.current
    val scheme = MaterialTheme.colorScheme
    return MathColors(
        normal = content,
        function = scheme.primary,
        operator = content,
        placeholder = scheme.outline,
        cursor = scheme.primary,
    )
}

/** Caches text layouts so measuring and drawing share the same work. */
class MathTextCache(private val measurer: TextMeasurer, private val density: Density) {
    private val cache = HashMap<Triple<String, Float, Role>, TextLayoutResult>()

    fun layout(text: String, sizePx: Float, role: Role): TextLayoutResult =
        cache.getOrPut(Triple(text, sizePx, role)) {
            val sp = with(density) { sizePx.toSp() }
            measurer.measure(
                text,
                TextStyle(fontSize = sp, fontFamily = FontFamily.Default),
            )
        }

    val widths = TextWidth { text, size, role -> layout(text, size, role).size.width.toFloat() }
}

@Composable
fun rememberMathTextCache(): MathTextCache {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current
    return remember(measurer, density) { MathTextCache(measurer, density) }
}

/** Lays out [row] at [fontSize]; [revision] must change whenever the (mutable) row changes. */
@Composable
fun rememberMathLayout(row: MathRow, fontSize: TextUnit, revision: Int = 0, cache: MathTextCache = rememberMathTextCache()): MathLayout {
    val sizePx = with(LocalDensity.current) { fontSize.toPx() }
    return remember(row, revision, sizePx, cache) { LayoutEngine(cache.widths).layout(row, sizePx) }
}

/**
 * Draws an expression in natural display. With [cursor] set, a blinking cursor is drawn there;
 * [onTap] receives the cursor position nearest to a tap.
 */
@Composable
fun MathView(
    row: MathRow,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 26.sp,
    revision: Int = 0,
    cursor: Pair<MathRow, Int>? = null,
    colors: MathColors = defaultMathColors(),
    onTap: ((CursorStop) -> Unit)? = null,
) {
    val cache = rememberMathTextCache()
    val layout = rememberMathLayout(row, fontSize, revision, cache)
    MathCanvas(layout, cache, modifier, cursor, colors, onTap)
}

/** Draws an already computed [layout]. */
@Composable
fun MathCanvas(
    layout: MathLayout,
    cache: MathTextCache,
    modifier: Modifier = Modifier,
    cursor: Pair<MathRow, Int>? = null,
    colors: MathColors = defaultMathColors(),
    onTap: ((CursorStop) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val blink = if (cursor != null) {
        val transition = rememberInfiniteTransition(label = "cursor")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 1000
                    1f at 0
                    1f at 499
                    0f at 500
                    0f at 999
                },
                RepeatMode.Restart,
            ),
            label = "blink",
        )
    } else null

    val sizeModifier = with(density) { Modifier.size(layout.width.toDp(), layout.height.toDp()) }
    val tapModifier = if (onTap != null) {
        Modifier.pointerInput(layout) {
            detectTapGestures { pos -> layout.hitTest(pos.x, pos.y)?.let(onTap) }
        }
    } else Modifier

    Canvas(modifier.then(sizeModifier).then(tapModifier)) {
        drawMath(layout, cache, colors)
        if (cursor != null) {
            val stop = layout.stopFor(cursor.first, cursor.second)
            if (stop != null && (blink?.value ?: 1f) > 0.5f) {
                drawLine(
                    colors.cursor,
                    Offset(stop.x, stop.top),
                    Offset(stop.x, stop.bottom),
                    strokeWidth = (stop.size * 0.07f).coerceAtLeast(2f),
                )
            }
        }
    }
}

/** Renders the draw operations of [layout]. */
fun DrawScope.drawMath(layout: MathLayout, cache: MathTextCache, colors: MathColors, origin: Offset = Offset.Zero) {
    fun color(role: Role) = when (role) {
        Role.NORMAL -> colors.normal
        Role.FUNCTION -> colors.function
        Role.OPERATOR -> colors.operator
        Role.PLACEHOLDER -> colors.placeholder
    }
    for (op in layout.ops) {
        when (op) {
            is DrawOp.Text -> {
                val tl = cache.layout(op.text, op.size, op.role)
                drawText(tl, color(op.role), topLeft = Offset(origin.x + op.x, origin.y + op.baseline - tl.firstBaseline))
            }
            is DrawOp.Line -> drawLine(
                color(op.role),
                Offset(origin.x + op.x1, origin.y + op.y1),
                Offset(origin.x + op.x2, origin.y + op.y2),
                strokeWidth = op.stroke,
                cap = StrokeCap.Butt,
            )
            is DrawOp.Polyline -> {
                val p = Path()
                p.moveTo(origin.x + op.points[0], origin.y + op.points[1])
                var i = 2
                while (i < op.points.size) {
                    p.lineTo(origin.x + op.points[i], origin.y + op.points[i + 1])
                    i += 2
                }
                drawPath(p, color(op.role), style = Stroke(op.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            is DrawOp.Delimiter -> drawDelimiter(op, colors.normal, origin)
            is DrawOp.IntegralSign -> {
                val x = origin.x + op.x
                val w = op.width
                val top = origin.y + op.top
                val bottom = origin.y + op.bottom
                val p = Path().apply {
                    moveTo(x + 0.95f * w, top + 0.12f * w)
                    cubicTo(x + 0.88f * w, top - 0.06f * w, x + 0.62f * w, top - 0.04f * w, x + 0.56f * w, top + 0.3f * w)
                    lineTo(x + 0.44f * w, bottom - 0.3f * w)
                    cubicTo(x + 0.38f * w, bottom + 0.04f * w, x + 0.12f * w, bottom + 0.06f * w, x + 0.05f * w, bottom - 0.12f * w)
                }
                drawPath(p, colors.normal, style = Stroke(op.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            is DrawOp.Placeholder -> {
                val stroke = (op.height * 0.05f).coerceAtLeast(1.5f)
                drawRoundRect(
                    colors.placeholder,
                    topLeft = Offset(origin.x + op.x, origin.y + op.top),
                    size = androidx.compose.ui.geometry.Size(op.width, op.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 1.5f),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2f, stroke * 1.6f))),
                )
            }
        }
    }
}

private fun DrawScope.drawDelimiter(op: DrawOp.Delimiter, color: Color, origin: Offset) {
    val x = origin.x + op.x
    val w = op.width
    val top = origin.y + op.top
    val bottom = origin.y + op.bottom
    val h = bottom - top
    val mid = (top + bottom) / 2
    val p = Path()
    when (op.kind) {
        '(' -> {
            p.moveTo(x + 0.78f * w, top)
            p.cubicTo(x + 0.12f * w, top + 0.22f * h, x + 0.12f * w, bottom - 0.22f * h, x + 0.78f * w, bottom)
        }
        ')' -> {
            p.moveTo(x + 0.22f * w, top)
            p.cubicTo(x + 0.88f * w, top + 0.22f * h, x + 0.88f * w, bottom - 0.22f * h, x + 0.22f * w, bottom)
        }
        '[' -> {
            p.moveTo(x + 0.8f * w, top)
            p.lineTo(x + 0.3f * w, top)
            p.lineTo(x + 0.3f * w, bottom)
            p.lineTo(x + 0.8f * w, bottom)
        }
        ']' -> {
            p.moveTo(x + 0.2f * w, top)
            p.lineTo(x + 0.7f * w, top)
            p.lineTo(x + 0.7f * w, bottom)
            p.lineTo(x + 0.2f * w, bottom)
        }
        '|' -> {
            p.moveTo(x + 0.5f * w, top)
            p.lineTo(x + 0.5f * w, bottom)
        }
        '{' -> {
            p.moveTo(x + 0.85f * w, top)
            p.quadraticTo(x + 0.45f * w, top, x + 0.45f * w, top + 0.18f * h)
            p.lineTo(x + 0.45f * w, mid - 0.12f * h)
            p.quadraticTo(x + 0.45f * w, mid, x + 0.1f * w, mid)
            p.quadraticTo(x + 0.45f * w, mid, x + 0.45f * w, mid + 0.12f * h)
            p.lineTo(x + 0.45f * w, bottom - 0.18f * h)
            p.quadraticTo(x + 0.45f * w, bottom, x + 0.85f * w, bottom)
        }
        '}' -> {
            p.moveTo(x + 0.15f * w, top)
            p.quadraticTo(x + 0.55f * w, top, x + 0.55f * w, top + 0.18f * h)
            p.lineTo(x + 0.55f * w, mid - 0.12f * h)
            p.quadraticTo(x + 0.55f * w, mid, x + 0.9f * w, mid)
            p.quadraticTo(x + 0.55f * w, mid, x + 0.55f * w, mid + 0.12f * h)
            p.lineTo(x + 0.55f * w, bottom - 0.18f * h)
            p.quadraticTo(x + 0.55f * w, bottom, x + 0.15f * w, bottom)
        }
    }
    drawPath(p, color, style = Stroke(op.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
