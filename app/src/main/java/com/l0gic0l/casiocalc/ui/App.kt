package com.l0gic0l.casiocalc.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.l0gic0l.casiocalc.ui.basen.BaseNScreen
import com.l0gic0l.casiocalc.ui.equation.EquationScreen
import com.l0gic0l.casiocalc.ui.graph.GraphListScreen
import com.l0gic0l.casiocalc.ui.graph.GraphViewScreen
import com.l0gic0l.casiocalc.ui.graph.TableScreen
import com.l0gic0l.casiocalc.ui.matrix.MatrixScreen
import com.l0gic0l.casiocalc.ui.menu.MainMenuScreen
import com.l0gic0l.casiocalc.ui.recursion.RecursionScreen
import com.l0gic0l.casiocalc.ui.run.RunScreen
import com.l0gic0l.casiocalc.ui.settings.SettingsScreen
import com.l0gic0l.casiocalc.ui.stat.StatGraphScreen
import com.l0gic0l.casiocalc.ui.stat.StatScreen
import com.l0gic0l.casiocalc.ui.theme.CasioCalcTheme

private const val DURATION = 320

// Material shared-axis X: screens slide a short way and cross-fade.
private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(DURATION))
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { -it / 6 } + fadeOut(tween(DURATION / 2))
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(DURATION, easing = FastOutSlowInEasing)) { -it / 6 } + fadeIn(tween(DURATION))
}

// Back (including the predictive back gesture, which scrubs this transition): the screen shrinks
// toward the centre and fades, revealing the one underneath.
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    scaleOut(tween(DURATION), targetScale = 0.9f, transformOrigin = TransformOrigin.Center) + fadeOut(tween(DURATION))
}

@Composable
fun CasioCalcApp(vm: CalcViewModel = viewModel()) {
    CasioCalcTheme(mode = vm.prefs.theme, dynamicColor = vm.prefs.dynamicColor) {
        val nav = rememberNavController()
        // Guards stop a double tap from popping the main menu or opening a screen twice.
        val back: () -> Unit = { if (nav.previousBackStackEntry != null) nav.popBackStack() }
        val open: (String) -> Unit = { route -> nav.navigate(route) { launchSingleTop = true } }
        val settings: () -> Unit = { open("settings") }
        NavHost(
            navController = nav,
            startDestination = "menu",
            // A solid background so screens never show through each other mid-transition.
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            enterTransition = enter,
            exitTransition = exit,
            popEnterTransition = popEnter,
            popExitTransition = popExit,
        ) {
            composable("menu") { MainMenuScreen(onOpen = open) }
            composable("run") { RunScreen(vm, onBack = back, onSettings = settings) }
            composable("stat") { StatScreen(vm, onBack = back, onGraph = { open("statgraph") }) }
            composable("statgraph") { StatGraphScreen(vm, onBack = back) }
            composable("graph") {
                GraphListScreen(vm, onBack = back, onDraw = { open("graphview") }, onTable = { open("table") }, onSettings = settings)
            }
            composable("graphview") { GraphViewScreen(vm, onBack = back) }
            composable("table") { TableScreen(vm, onBack = back) }
            composable("equation") { EquationScreen(vm, onBack = back) }
            composable("matrix") { MatrixScreen(vm, onBack = back) }
            composable("recursion") { RecursionScreen(vm, onBack = back) }
            composable("basen") { BaseNScreen(vm, onBack = back) }
            composable("settings") { SettingsScreen(vm, onBack = back) }
        }
    }
}
