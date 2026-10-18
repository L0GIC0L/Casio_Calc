package com.l0gic0l.casiocalc.ui.graph

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Zoom factors for a pinch, one per axis, from the fingers' previous and current positions.
 * Spreading the fingers sideways zooms x, up and down zooms y, and diagonally zooms both
 * (each by its own amount). An axis zooms only while the fingers are spread along it by at least
 * [minSpan] pixels and by at least half as much as along the other axis, so a sideways pinch
 * with slightly uneven fingers does not wobble the y-axis.
 */
fun axisZoom(previous: List<Offset>, current: List<Offset>, minSpan: Float): Pair<Float, Float> {
    if (previous.size < 2 || previous.size != current.size) return 1f to 1f
    fun spans(points: List<Offset>): Pair<Float, Float> {
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / points.size
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / points.size
        // Average distance from the centroid, doubled, like Android's ScaleGestureDetector span.
        val sx = points.sumOf { abs(it.x - cx).toDouble() }.toFloat() / points.size * 2f
        val sy = points.sumOf { abs(it.y - cy).toDouble() }.toFloat() / points.size * 2f
        return sx to sy
    }
    val (px, py) = spans(previous)
    val (cx, cy) = spans(current)
    fun ratio(prev: Float, cur: Float, prevOther: Float): Float =
        if (prev >= minSpan && cur >= minSpan && prev >= prevOther * 0.5f) cur / prev else 1f
    return ratio(px, cx, py) to ratio(py, cy, px)
}

/**
 * Like detectTransformGestures, but reports a separate zoom for each axis (see [axisZoom]).
 * [onGesture] gets the centroid, the pan since the last event and the x and y zoom factors.
 */
suspend fun PointerInputScope.detectAxisZoomGestures(
    onGesture: (centroid: Offset, pan: Offset, zoomX: Float, zoomY: Float) -> Unit,
) {
    val minSpan = 32.dp.toPx()
    awaitEachGesture {
        var moved = false
        var panTotal = Offset.Zero
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.any { it.isConsumed }) break
            val pressed = event.changes.filter { it.pressed && it.previousPressed }
            val (zx, zy) = axisZoom(pressed.map { it.previousPosition }, pressed.map { it.position }, minSpan)
            val pan = event.calculatePan()
            if (!moved) {
                panTotal += pan
                // Wait for a deliberate movement before acting, as detectTransformGestures does.
                moved = panTotal.getDistance() > viewConfiguration.touchSlop || zx != 1f || zy != 1f
            }
            if (moved) {
                val centroid = event.calculateCentroid(useCurrent = false)
                if (zx != 1f || zy != 1f || pan != Offset.Zero) onGesture(centroid, pan, zx, zy)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
