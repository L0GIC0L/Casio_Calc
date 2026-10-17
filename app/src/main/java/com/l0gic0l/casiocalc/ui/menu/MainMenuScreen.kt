package com.l0gic0l.casiocalc.ui.menu

import com.l0gic0l.casiocalc.ui.components.appBarInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class MenuEntry(val number: String, val title: String, val icon: ImageVector, val route: String)

val menuEntries = listOf(
    MenuEntry("1", "Run-Matrix", Icons.Default.Calculate, "run"),
    MenuEntry("2", "Statistics", Icons.Default.QueryStats, "stat"),
    MenuEntry("3", "Graph", Icons.AutoMirrored.Filled.ShowChart, "graph"),
    MenuEntry("4", "Table", Icons.Default.TableChart, "table"),
    MenuEntry("5", "Equation", Icons.Default.Functions, "equation"),
    MenuEntry("6", "Matrix", Icons.Default.GridOn, "matrix"),
    MenuEntry("7", "Recursion", Icons.Default.Insights, "recursion"),
    MenuEntry("8", "Base-N", Icons.Default.Memory, "basen"),
    MenuEntry("9", "System", Icons.Default.Settings, "settings"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(onOpen: (String) -> Unit) {
    // The large title collapses as the grid scrolls, which matters in landscape.
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { LargeTopAppBar(title = { Text("Main Menu") }, windowInsets = appBarInsets, scrollBehavior = scroll) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            items(menuEntries) { e ->
                Surface(
                    onClick = { onOpen(e.route) },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().height(116.dp),
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary) {
                            Icon(
                                e.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(10.dp).size(28.dp),
                            )
                        }
                        Text(
                            e.title,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
