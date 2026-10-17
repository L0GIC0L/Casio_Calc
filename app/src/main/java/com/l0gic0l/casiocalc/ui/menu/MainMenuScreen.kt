package com.l0gic0l.casiocalc.ui.menu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.data.UiPrefs
import com.l0gic0l.casiocalc.ui.components.appBarInsets
import com.l0gic0l.casiocalc.ui.adaptive.currentFold
import com.l0gic0l.casiocalc.ui.effects.ParticleField
import com.l0gic0l.casiocalc.ui.effects.particleTouch
import com.l0gic0l.casiocalc.ui.effects.rememberParticleState
import kotlinx.coroutines.delay

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
    MenuEntry("9", "Settings", Icons.Default.Settings, "settings"),
)

/**
 * The home screen: a large title over drifting particles, then one tile per mode.
 * [status] is a one-line summary of the current set-up, shown under the title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(prefs: UiPrefs = UiPrefs(), status: String? = null, onOpen: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val grid = rememberLazyGridState()
    // The small title fades in once the large one has scrolled away, like Android's Settings.
    val collapsed by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 120 } }
    val titleAlpha by animateFloatAsState(if (collapsed) 1f else 0f, label = "menuTitle")
    val barColour by animateColorAsState(if (collapsed) scheme.surfaceContainer else Color.Transparent, label = "menuBar")
    // Tiles fly in once per launch, not every time you come back to the menu.
    var introPlayed by rememberSaveable { mutableStateOf(prefs.reduceMotion) }
    LaunchedEffect(Unit) {
        delay(600)
        introPlayed = true
    }
    val particles = rememberParticleState()
    // Landscape phones get a smaller header so both rows of tiles fit.
    val window = LocalWindowInfo.current.containerSize
    val short = with(LocalDensity.current) { window.height.toDp() } < 480.dp

    // The touch watcher sits on the parent: the Scaffold in front would hide touches from the field itself.
    Box(Modifier.fillMaxSize().background(scheme.background).particleTouch(particles, enabled = prefs.showParticles)) {
        if (prefs.showParticles) {
            ParticleField(particles, listOf(scheme.primary, scheme.tertiary, scheme.secondary), Modifier.fillMaxSize())
        }
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text("CG Calc", Modifier.graphicsLayer { alpha = titleAlpha }) },
                    windowInsets = appBarInsets,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = barColour, scrolledContainerColor = barColour),
                )
            },
        ) { padding ->
            LazyVerticalGrid(
                state = grid,
                // 104dp gives the calculator's 3 × 3 menu on phones and more columns on wide screens.
                // Half-folded like a book, an even number of columns keeps a gap on the fold.
                columns = if (currentFold().book) GridCells.Fixed(6) else GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                    Header(status, short, Modifier.graphicsLayer { alpha = 1f - titleAlpha })
                }
                itemsIndexed(menuEntries, key = { _, e -> e.route }) { i, e ->
                    Tile(e, i, animate = !introPlayed, short = short, onClick = { onOpen(e.route) })
                }
            }
        }
    }
}

@Composable
private fun Header(status: String?, short: Boolean, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().padding(top = if (short) 0.dp else 24.dp, bottom = if (short) 8.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "CG Calc",
            style = if (short) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!short) {
            Text(
                "Natural display graphing calculator",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (status != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(top = if (short) 6.dp else 14.dp),
            ) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Accent colours for the tiles' icons, cycled so neighbouring tiles differ. */
@Composable
private fun accent(index: Int): Pair<Color, Color> {
    val s = MaterialTheme.colorScheme
    return when (index % 3) {
        0 -> s.primary to s.onPrimary
        1 -> s.tertiary to s.onTertiary
        else -> s.secondary to s.onSecondary
    }
}

@Composable
private fun Tile(e: MenuEntry, index: Int, animate: Boolean, short: Boolean, onClick: () -> Unit) {
    // Entrance: each tile rises and fades in a moment after the one before it.
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            delay(40L * index)
            appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tilePress",
    )
    val (iconBg, iconFg) = accent(index)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
        // A translucent colour is not one contentColorFor recognises, so name the text colour.
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (short) 96.dp else 120.dp)
            .graphicsLayer {
                val p = appear.value
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 32.dp.toPx()
                val s = (0.9f + 0.1f * p) * pressScale
                scaleX = s
                scaleY = s
            },
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                e.number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp),
            )
            Column(
                Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = RoundedCornerShape(18.dp), color = iconBg) {
                    Icon(e.icon, contentDescription = null, tint = iconFg, modifier = Modifier.padding(11.dp).size(26.dp))
                }
                Text(
                    e.title,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
