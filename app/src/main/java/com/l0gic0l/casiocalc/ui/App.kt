package com.l0gic0l.casiocalc.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
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

@Composable
fun CasioCalcApp(vm: CalcViewModel = viewModel()) {
    CasioCalcTheme(mode = vm.prefs.theme, dynamicColor = vm.prefs.dynamicColor) {
        val nav = rememberNavController()
        val back: () -> Unit = { nav.popBackStack() }
        val settings: () -> Unit = { nav.navigate("settings") }
        NavHost(navController = nav, startDestination = "menu") {
            composable("menu") { MainMenuScreen(onOpen = { nav.navigate(it) }) }
            composable("run") { RunScreen(vm, onBack = back, onSettings = settings) }
            composable("stat") { StatScreen(vm, onBack = back, onGraph = { nav.navigate("statgraph") }) }
            composable("statgraph") { StatGraphScreen(vm, onBack = back) }
            composable("graph") {
                GraphListScreen(vm, onBack = back, onDraw = { nav.navigate("graphview") }, onTable = { nav.navigate("table") }, onSettings = settings)
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
