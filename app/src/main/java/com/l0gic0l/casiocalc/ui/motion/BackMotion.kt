package com.l0gic0l.casiocalc.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The system back gesture in progress, if any: where the finger is, which edge it came from and how far it has gone. */
@Stable
class BackGestureState {
    var event by mutableStateOf<NavigationEvent?>(null)
        private set

    /** Finger height when the gesture started. */
    var startY by mutableFloatStateOf(0f)
        private set

    internal fun update(e: NavigationEvent?) {
        if (e != null && event == null) startY = e.touchY
        event = e
    }
}

/** The back gesture as published by the activity's navigation-event dispatcher. */
@Composable
fun rememberBackGesture(dispatcher: NavigationEventDispatcher? = rememberNavigationEventDispatcher()): BackGestureState {
    val state = remember { BackGestureState() }
    LaunchedEffect(dispatcher) {
        dispatcher?.transitionState?.collect { s -> state.update(s.backEvent()) }
    }
    return state
}

@Composable
fun rememberNavigationEventDispatcher(): NavigationEventDispatcher? =
    LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher

/** The event of a back gesture in progress, or null when idle or going forward. */
fun NavigationEventTransitionState.backEvent(): NavigationEvent? =
    (this as? NavigationEventTransitionState.InProgress)
        ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
        ?.latestEvent

/**
 * Settings-style predictive back for a whole screen. While the back gesture is popping this
 * screen it rounds its corners, leans toward the side the finger is moving to and follows the
 * finger up and down (the navigation transition shrinks it and reveals the screen underneath).
 * If the gesture is cancelled it springs back; if it completes it stays where the finger left it
 * while it slides away.
 */
@Composable
fun Modifier.predictiveBack(scope: AnimatedVisibilityScope, gesture: BackGestureState): Modifier {
    val transition = scope.transition
    val lean = remember { Animatable(0f) } // -1..1, fraction of the sideways travel
    val drop = remember { Animatable(0f) } // vertical offset as a fraction of the screen height
    val round = remember { Animatable(0f) } // 0..1 of the full corner radius
    // The screen being revealed underneath is dimmed, and brightens as the gesture goes on.
    var underneath by remember { mutableStateOf(false) }
    val dim = transition.animateFloat({ tween(320, easing = LinearEasing) }, label = "backScrim") { if (it == EnterExitState.PreEnter) 1f else 0f }
    LaunchedEffect(transition, gesture) {
        snapshotFlow { Triple(gesture.event != null, transition.currentState, transition.targetState) }.collect { (active, current, target) ->
            val entering = current == EnterExitState.PreEnter && target == EnterExitState.Visible
            if (entering && active) underneath = true else if (!entering) underneath = false
        }
    }
    LaunchedEffect(transition, gesture) {
        snapshotFlow { gesture.event to (transition.targetState == EnterExitState.PostExit) }
            .collectLatest { (e, exiting) ->
                when {
                    e != null && exiting -> {
                        val side = if (e.swipeEdge == NavigationEvent.EDGE_RIGHT) -1f else 1f
                        lean.snapTo(side * e.progress)
                        drop.snapTo(gesture.startY.let { start -> (e.touchY - start) })
                        round.snapTo((e.progress * 4f).coerceAtMost(1f))
                    }
                    exiting -> Unit
                    else -> coroutineScope {
                        launch { lean.animateTo(0f, spring()) }
                        launch { drop.animateTo(0f, spring()) }
                        launch { round.animateTo(0f, spring()) }
                    }
                }
            }
    }
    return drawWithContent {
        drawContent()
        if (underneath && dim.value > 0f) drawRect(Color.Black, alpha = 0.3f * dim.value)
    }.graphicsLayer {
        val r = round.value
        if (r == 0f && lean.value == 0f && drop.value == 0f) return@graphicsLayer
        translationX = lean.value * size.width * 0.06f
        // The screen follows about a third of the finger's travel, but never further than the
        // margin its shrinking has opened, so it settles back together with the progress.
        val gap = size.height * 0.05f * LinearOutSlowInEasing.transform(abs(lean.value))
        translationY = (drop.value * 0.3f).coerceIn(-gap, gap)
        if (r > 0f) {
            shape = RoundedCornerShape(32.dp * r)
            clip = true
        }
    }
}
