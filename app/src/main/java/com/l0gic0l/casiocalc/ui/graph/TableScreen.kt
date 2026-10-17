package com.l0gic0l.casiocalc.ui.graph

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.data.GraphType
import com.l0gic0l.casiocalc.data.TableRange
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.NumberFieldsDialog
import kotlin.math.abs
import java.util.Locale

/** Compact number text for table cells: up to 6 significant digits. */
fun compactNumber(v: Double): String = when {
    v.isNaN() || v.isInfinite() -> "ERROR"
    v == 0.0 -> "0"
    abs(v) >= 1e6 || abs(v) < 1e-4 -> "%.4e".format(Locale.US, v).replace("e+0", "E").replace("e-0", "E-").replace("e+", "E").replace("e-", "E-")
    else -> "%.6g".format(Locale.US, v).let { s -> if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableScreen(vm: CalcViewModel, onBack: () -> Unit) {
    var showRange by remember { mutableStateOf(false) }
    val palette = graphPalette()
    val (plots, _) = remember(vm.graphFunctions.toList(), vm.settings, palette) {
        preparePlots(vm.graphFunctions, vm.memory, vm.settings, palette)
    }
    val yPlots = plots.filter { it.fn.type == GraphType.Y }
    val r = vm.tableRange
    val xs = remember(r) {
        val n = ((r.end - r.start) / r.step).toLong()
        if (r.step <= 0 || n < 0) emptyList() else (0..minOf(n, 999)).map { r.start + it * r.step }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Table") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { showRange = true }) { Icon(Icons.Default.Tune, contentDescription = "Table range") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "X: ${compactNumber(r.start)} → ${compactNumber(r.end)}, step ${compactNumber(r.step)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (yPlots.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Enter Y= functions in Graph mode first.", modifier = Modifier.padding(24.dp))
                }
                return@Column
            }
            val scroll = rememberScrollState()
            val cellW = 104.dp
            Row(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp)) {
                Header("X", MaterialTheme.colorScheme.onSurface, cellW)
                for (p in yPlots) Header("Y${p.index + 1}", p.color, cellW)
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                items(xs) { x ->
                    Row(Modifier.horizontalScroll(scroll).padding(horizontal = 8.dp)) {
                        Cell(compactNumber(x), cellW, bold = true)
                        for (p in yPlots) Cell(compactNumber(p.f.eval(x)), cellW)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
    if (showRange) {
        NumberFieldsDialog("Table range", listOf("Start", "End", "Step"), listOf(r.start, r.end, r.step), onDismiss = { showRange = false }) { v ->
            if (v[2] > 0 && v[1] >= v[0]) vm.updateTableRange(TableRange(v[0], v[1], v[2]))
        }
    }
}

@Composable
private fun Header(text: String, color: androidx.compose.ui.graphics.Color, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = color, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, modifier = Modifier.width(width).padding(8.dp))
}

@Composable
private fun Cell(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.End,
        style = if (bold) MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary) else MaterialTheme.typography.bodyLarge,
        modifier = Modifier.width(width).height(40.dp).padding(horizontal = 8.dp, vertical = 8.dp),
        maxLines = 1,
    )
}
