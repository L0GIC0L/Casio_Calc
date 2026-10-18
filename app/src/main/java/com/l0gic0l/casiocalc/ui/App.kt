package com.l0gic0l.casiocalc.ui

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigationevent.NavigationEvent
import com.l0gic0l.casiocalc.ui.adaptive.LocalFold
import com.l0gic0l.casiocalc.ui.adaptive.LocalScreenSettled
import com.l0gic0l.casiocalc.ui.adaptive.rememberFold
import com.l0gic0l.casiocalc.ui.basen.BaseNScreen
import com.l0gic0l.casiocalc.ui.equation.EquationScreen
import com.l0gic0l.casiocalc.ui.graph.GraphListScreen
import com.l0gic0l.casiocalc.ui.graph.GraphViewScreen
import com.l0gic0l.casiocalc.ui.graph.TableScreen
import com.l0gic0l.casiocalc.ui.matrix.MatrixScreen
import com.l0gic0l.casiocalc.ui.menu.MainMenuScreen
import com.l0gic0l.casiocalc.ui.motion.BackGestureState
import com.l0gic0l.casiocalc.ui.motion.predictiveBack
import com.l0gic0l.casiocalc.ui.motion.rememberBackGesture
import com.l0gic0l.casiocalc.ui.recursion.RecursionScreen
import com.l0gic0l.casiocalc.ui.run.RunScreen
import com.l0gic0l.casiocalc.ui.settings.SettingsScreen
import com.l0gic0l.casiocalc.ui.stat.StatGraphScreen
import com.l0gic0l.casiocalc.ui.stat.StatScreen
import com.l0gic0l.casiocalc.ui.theme.CasioCalcTheme

private const val DURATION = 320

// Opening a screen and the back button: Material shared-axis X, a short slide with a cross-fade.
private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(DURATION))
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(tween(DURATION / 2))
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween(DURATION))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { it / 6 } + fadeOut(tween(DURATION / 2))
}

/*
 * The back gesture scrubs these with the finger, like Android's Settings. A swipe usually lets go
 * at a third of the way or less, so the shrink is front-loaded (it shows from the start of the
 * drag) while the slide and fade are back-loaded: barely visible while dragging, then the screen
 * leaves toward the side the finger was moving to as soon as you let go. predictiveBack adds the
 * rounded corners, the finger tracking and the dimmed screen underneath.
 */
private const val GESTURE = 400

private val predictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = { edge ->
    val side = if (edge == NavigationEvent.EDGE_RIGHT) -1 else 1
    scaleOut(tween(GESTURE, easing = LinearOutSlowInEasing), targetScale = 0.9f, transformOrigin = TransformOrigin.Center) +
        slideOutHorizontally(tween(GESTURE, easing = EaseInCubic)) { side * it / 3 } +
        // Opaque through the first half, so the screen underneath never ghosts through it mid-drag.
        fadeOut(
            keyframes {
                durationMillis = GESTURE
                1f at GESTURE / 2 using LinearEasing
            },
        )
}

// No scale here: the revealed screen fills the window, so the dimming covers all of it.
private val predictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = {
    fadeIn(tween(GESTURE, easing = LinearEasing), initialAlpha = 0.5f)
}

@Composable
fun CasioCalcApp(vm: CalcViewModel = viewModel()) {
    val prefs = vm.prefs
    CasioCalcTheme(mode = prefs.theme, dynamicColor = prefs.dynamicColor, pureBlack = prefs.pureBlack) {
        val view = LocalView.current
        SideEffect { view.keepScreenOn = prefs.keepScreenOn }
        val nav = rememberNavController()
        val gesture = rememberBackGesture()
        CompositionLocalProvider(LocalFold provides rememberFold()) {
            NavHost(
                navController = nav,
                startDestination = "menu",
                // A solid background so screens never show through each other mid-transition.
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                enterTransition = enter,
                exitTransition = exit,
                popEnterTransition = popEnter,
                popExitTransition = popExit,
                predictivePopEnterTransition = predictivePopEnter,
                predictivePopExitTransition = predictivePopExit,
            ) {
                // Taps on a screen that is already leaving (a double tap on back, say) are ignored.
                screen("menu", gesture) {
                    val s = vm.settings
                    val entry = LocalLifecycleOwner.current
                    MainMenuScreen(
                        prefs = vm.prefs,
                        status = "${s.angle.label} · ${if (s.exactOutput) "Math" else "Decimal"} · ${s.display.label}",
                        onOpen = { route ->
                            if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                nav.navigate(route) { launchSingleTop = true }
                            }
                        },
                    )
                }
                screen("run", gesture) {
                    RunScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }, onSettings = dropUnlessResumed { nav.navigate("settings") })
                }
                screen("stat", gesture) {
                    StatScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }, onGraph = dropUnlessResumed { nav.navigate("statgraph") })
                }
                screen("statgraph", gesture) { StatGraphScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }) }
                screen("graph", gesture) {
                    GraphListScreen(
                        vm,
                        onBack = dropUnlessResumed { nav.popBackStack() },
                        onDraw = dropUnlessResumed { nav.navigate("graphview") },
                        onTable = dropUnlessResumed { nav.navigate("table") },
                        onSettings = dropUnlessResumed { nav.navigate("settings") },
                    )
                }
                screen("graphview", gesture) { GraphViewScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }) }
                screen("table", gesture) { TableScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }) }
                screen("equation", gesture) { EquationScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }) }
                screen("matrix", gesture) { MatrixScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }) }
                screen("recursion", gesture) { RecursionScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }) }
                screen("basen", gesture) { BaseNScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }) }
                screen("settings", gesture) { SettingsScreen(vm, onBack = dropUnlessResumed { nav.popBackStack() }) }
            }
        }
    }
}

/** A destination with the Settings-style back gesture and the settled flag for fold layouts. */
private fun NavGraphBuilder.screen(route: String, gesture: BackGestureState, content: @Composable AnimatedContentScope.() -> Unit) {
    composable(route) {
        val settled = transition.currentState == transition.targetState
        Box(Modifier.fillMaxSize().predictiveBack(this, gesture)) {
            CompositionLocalProvider(LocalScreenSettled provides settled) { content() }
        }
    }
}
