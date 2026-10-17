package com.l0gic0l.casiocalc.ui.adaptive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/** How the keypad's two blocks are arranged: function keys above number keys, or side by side. */
enum class KeypadShape { STACKED, WIDE }

/**
 * Screen arrangements:
 * PORTRAIT – display above a stacked keypad (phones);
 * WIDE – display above a 4-row side-by-side keypad (landscape, unfolded foldables, tablets);
 * BOOK – display and keypad on either side of a vertical fold;
 * TABLETOP – display above and keypad below a horizontal fold.
 */
enum class CalcLayout { PORTRAIT, WIDE, BOOK, TABLETOP }

/** Fold state that matters for layout; [hinge] is in window pixels. */
data class Fold(val tabletop: Boolean = false, val book: Boolean = false, val hinge: Rect? = null)

fun chooseLayout(width: Dp, height: Dp, fold: Fold): CalcLayout = when {
    fold.tabletop -> CalcLayout.TABLETOP
    fold.book -> CalcLayout.BOOK
    width >= 600.dp || width > height -> CalcLayout.WIDE
    else -> CalcLayout.PORTRAIT
}

/** Keypad height for the stacked phone layout. */
fun portraitKeypadHeight(available: Dp): Dp = min(available * 0.6f, 470.dp)

/** Keypad height for the 4-row wide layout. */
fun wideKeypadHeight(available: Dp): Dp = min(available * 0.56f, 300.dp)

@Composable
fun rememberFold(): Fold {
    val posture = currentWindowAdaptiveInfo().windowPosture
    val folded = posture.hingeList.firstOrNull { !it.isFlat || it.isSeparating }
    return remember(posture) {
        when {
            folded == null -> Fold()
            posture.isTabletop || !folded.isVertical -> Fold(tabletop = true, hinge = folded.bounds)
            else -> Fold(book = true, hinge = folded.bounds)
        }
    }
}

/**
 * Lays out [first] and [second] on either side of the fold ([vertical] fold: left/right,
 * otherwise top/bottom). Without hinge bounds the space is split in half.
 */
@Composable
fun HingeSplit(
    vertical: Boolean,
    hinge: Rect?,
    modifier: Modifier = Modifier,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    var origin by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val density = LocalDensity.current
        val totalPx = if (vertical) constraints.maxWidth else constraints.maxHeight
        val o = origin
        var startPx = totalPx / 2
        var endPx = totalPx / 2
        if (hinge != null && o != null) {
            val a = (if (vertical) hinge.left - o.x else hinge.top - o.y).toInt()
            val b = (if (vertical) hinge.right - o.x else hinge.bottom - o.y).toInt()
            // Ignore hinge data that would leave one side unusably small.
            if (a > totalPx / 4 && a < totalPx * 3 / 4 && b >= a) {
                startPx = a
                endPx = b.coerceAtMost(totalPx)
            }
        }
        val start = with(density) { startPx.toDp() }
        val gap = with(density) { (endPx - startPx).toDp() }
        if (vertical) {
            Row(Modifier.fillMaxSize()) {
                first(Modifier.width(start).fillMaxHeight())
                Spacer(Modifier.width(gap))
                second(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                first(Modifier.height(start).fillMaxWidth())
                Spacer(Modifier.height(gap))
                second(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** A calculator screen: a display area plus a keypad, arranged for the window and fold. */
@Composable
fun CalcScreenLayout(
    modifier: Modifier = Modifier,
    fold: Fold = rememberFold(),
    display: @Composable (Modifier) -> Unit,
    keypad: @Composable (KeypadShape, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxWidth = maxWidth
        val maxHeight = maxHeight
        when (chooseLayout(maxWidth, maxHeight, fold)) {
            CalcLayout.PORTRAIT -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                display(Modifier.weight(1f).fillMaxWidth())
                keypad(KeypadShape.STACKED, Modifier.widthIn(max = 560.dp).fillMaxWidth().height(portraitKeypadHeight(maxHeight)))
            }
            CalcLayout.WIDE -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                display(Modifier.weight(1f).fillMaxWidth())
                keypad(KeypadShape.WIDE, Modifier.widthIn(max = 1000.dp).fillMaxWidth().height(wideKeypadHeight(maxHeight)))
            }
            CalcLayout.BOOK -> HingeSplit(true, fold.hinge, first = display) { m ->
                Box(m, contentAlignment = Alignment.Center) {
                    keypad(KeypadShape.STACKED, Modifier.widthIn(max = 560.dp).fillMaxSize())
                }
            }
            CalcLayout.TABLETOP -> {
                val shape = if (maxWidth >= 600.dp) KeypadShape.WIDE else KeypadShape.STACKED
                HingeSplit(false, fold.hinge, first = display) { m ->
                    Box(m, contentAlignment = Alignment.Center) {
                        keypad(shape, Modifier.widthIn(max = 1000.dp).fillMaxSize())
                    }
                }
            }
        }
    }
}

/**
 * A screen whose cells are edited with an expression editor ([editor]: input line plus keypad).
 * The editor slides in at the bottom on phones, beside the content on large screens, and on the
 * far side of the fold on half-folded devices.
 */
@Composable
fun EditorLayout(
    editing: Boolean,
    modifier: Modifier = Modifier,
    fold: Fold = rememberFold(),
    content: @Composable (Modifier) -> Unit,
    editor: @Composable (KeypadShape, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxWidth = maxWidth
        val maxHeight = maxHeight
        val layout = chooseLayout(maxWidth, maxHeight, fold)
        val side = layout == CalcLayout.BOOK || (maxWidth >= 840.dp && maxHeight >= 480.dp)
        when {
            editing && layout == CalcLayout.BOOK -> HingeSplit(true, fold.hinge, first = content) { m ->
                editor(KeypadShape.STACKED, m)
            }
            editing && layout == CalcLayout.TABLETOP -> HingeSplit(false, fold.hinge, first = content) { m ->
                editor(if (maxWidth >= 600.dp) KeypadShape.WIDE else KeypadShape.STACKED, m)
            }
            side -> Row(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxHeight())
                AnimatedVisibility(
                    visible = editing,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    editor(KeypadShape.STACKED, Modifier.width(min(maxWidth * 0.45f, 460.dp)).fillMaxHeight())
                }
            }
            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                content(Modifier.weight(1f).fillMaxWidth())
                val wide = layout != CalcLayout.PORTRAIT
                // Short landscape windows give the editor the whole height while it is open.
                val height = when {
                    wide && maxHeight < 420.dp -> maxHeight
                    wide -> min(maxHeight * 0.62f, 400.dp)
                    else -> min(maxHeight * 0.68f, 560.dp)
                }
                AnimatedVisibility(
                    visible = editing,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    editor(
                        if (wide) KeypadShape.WIDE else KeypadShape.STACKED,
                        Modifier.widthIn(max = if (wide) 1000.dp else 560.dp).fillMaxWidth().height(height),
                    )
                }
            }
        }
    }
}

/**
 * Which cell an [EditorLayout] is editing. [shown] keeps the last target while the editor
 * animates away, so it does not empty out mid-animation.
 */
@Stable
class EditSession<T : Any> {
    var current by mutableStateOf<T?>(null)
        private set
    var shown by mutableStateOf<T?>(null)
        private set

    fun open(target: T) {
        current = target
        shown = target
    }

    fun close() {
        current = null
    }
}

@Composable
fun <T : Any> rememberEditSession(): EditSession<T> = remember { EditSession() }
