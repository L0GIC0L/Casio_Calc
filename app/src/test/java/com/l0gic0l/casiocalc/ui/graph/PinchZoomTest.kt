package com.l0gic0l.casiocalc.ui.graph

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class PinchZoomTest {
    private val min = 50f

    @Test fun sidewaysPinchZoomsOnlyX() {
        // Fingers side by side, slightly uneven, spreading apart horizontally.
        val (zx, zy) = axisZoom(listOf(Offset(400f, 500f), Offset(600f, 520f)), listOf(Offset(300f, 500f), Offset(700f, 524f)), min)
        assertEquals(2f, zx, 1e-4f)
        assertEquals(1f, zy, 0f)
    }

    @Test fun verticalPinchZoomsOnlyY() {
        val (zx, zy) = axisZoom(listOf(Offset(500f, 400f), Offset(510f, 600f)), listOf(Offset(500f, 450f), Offset(510f, 550f)), min)
        assertEquals(1f, zx, 0f)
        assertEquals(0.5f, zy, 1e-4f)
    }

    @Test fun diagonalPinchZoomsEachAxisByItsOwnAmount() {
        // Diagonal fingers moving mostly sideways: x zooms more than y.
        val (zx, zy) = axisZoom(listOf(Offset(400f, 400f), Offset(600f, 600f)), listOf(Offset(300f, 380f), Offset(700f, 620f)), min)
        assertEquals(2f, zx, 1e-4f)
        assertEquals(1.2f, zy, 1e-4f)
    }

    @Test fun oneFingerDoesNotZoom() {
        assertEquals(1f to 1f, axisZoom(listOf(Offset(1f, 1f)), listOf(Offset(5f, 5f)), min))
    }
}
