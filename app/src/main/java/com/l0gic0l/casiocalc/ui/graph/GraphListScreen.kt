package com.l0gic0l.casiocalc.ui.graph

import com.l0gic0l.casiocalc.ui.components.appBarInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.data.GraphType
import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.parse.Parser
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.EditorPanel
import com.l0gic0l.casiocalc.ui.adaptive.EditorLayout
import com.l0gic0l.casiocalc.ui.adaptive.rememberEditSession
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.math.MathView

private data class Editing(val index: Int, val second: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphListScreen(vm: CalcViewModel, onBack: () -> Unit, onDraw: () -> Unit, onTable: () -> Unit, onSettings: () -> Unit) {
    val session = rememberEditSession<Editing>()
    var showWindow by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val palette = graphPalette()

    val input = remember(session.shown) {
        val e = session.shown
        InputState(if (e == null) MathRow() else vm.graphFunctions[e.index].let { if (e.second) it.expr2 else it.expr })
    }

    fun save() {
        val e = session.current ?: return
        val row = input.row.copy()
        if (!row.isEmpty) {
            try {
                Parser.parse(row)
            } catch (ex: CalcException) {
                error = ex.kind.message
                return
            }
        }
        val f = vm.graphFunctions[e.index]
        vm.setGraphFunction(e.index, if (e.second) f.copy(expr2 = row, enabled = true) else f.copy(expr = row, enabled = true))
        error = null
        if (f.type == GraphType.PARAM && !e.second) session.open(Editing(e.index, true)) else session.close()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = appBarInsets,
                title = { Text("Graph") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { showWindow = true }) { Icon(Icons.Default.Tune, contentDescription = "View window") }
                    IconButton(onClick = onTable) { Icon(Icons.Default.TableChart, contentDescription = "Table") }
                },
            )
        },
        floatingActionButton = {
            if (session.current == null) {
                ExtendedFloatingActionButton(onClick = onDraw, icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null) }, text = { Text("Draw") })
            }
        },
    ) { padding ->
        EditorLayout(
            editing = session.current != null,
            modifier = Modifier.padding(padding),
            content = { m ->
                LazyColumn(m, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 88.dp)) {
                    itemsIndexed(vm.graphFunctions) { i, f ->
                        FunctionRow(
                            index = i,
                            vm = vm,
                            colorDot = palette[f.colorIndex % palette.size],
                            editing = session.current,
                            onEdit = { second -> session.open(Editing(i, second)); error = null },
                        )
                    }
                }
            },
            editor = { shape, m ->
                session.shown?.let { e ->
                    val f = vm.graphFunctions[e.index]
                    val label = when (f.type) {
                        GraphType.PARAM -> if (e.second) "Yt${e.index + 1}=" else "Xt${e.index + 1}="
                        else -> f.type.label.dropLast(1) + "${e.index + 1}="
                    }
                    val xVar = when (f.type) {
                        GraphType.Y -> Tokens.X
                        GraphType.R -> Tokens.THETA
                        GraphType.PARAM -> Tokens.T
                        GraphType.X -> Tokens.letter('Y')
                    }
                    EditorPanel(
                        vm, label, input, onDone = ::save, onCancel = { session.close(); error = null },
                        shape = shape, modifier = m, error = error, xVar = xVar, onSetup = onSettings,
                    )
                }
            },
        )
    }
    if (showWindow) ViewWindowDialog(vm, onDismiss = { showWindow = false })
}

@Composable
private fun FunctionRow(index: Int, vm: CalcViewModel, colorDot: androidx.compose.ui.graphics.Color, editing: Editing?, onEdit: (Boolean) -> Unit) {
    val f = vm.graphFunctions[index]
    var typeMenu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (editing?.index == index) scheme.primaryContainer else scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            Checkbox(checked = f.enabled && !f.isEmpty, enabled = !f.isEmpty, onCheckedChange = { vm.setGraphFunction(index, f.copy(enabled = it)) })
            Box(
                Modifier.size(14.dp).background(colorDot, CircleShape).clickable {
                    vm.setGraphFunction(index, f.copy(colorIndex = (f.colorIndex + 1) % CalcViewModel.GRAPH_COLORS))
                },
            )
            Box {
                Text(
                    if (f.type == GraphType.PARAM) "Xt${index + 1}\nYt${index + 1}" else f.type.label.dropLast(1) + "${index + 1}=",
                    fontSize = 15.sp,
                    color = scheme.primary,
                    modifier = Modifier.width(56.dp).clickable { typeMenu = true }.padding(start = 8.dp),
                )
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    GraphType.entries.forEach { t ->
                        DropdownMenuItem(text = { Text(t.label + "  (" + t.variable + ")") }, onClick = {
                            vm.setGraphFunction(index, f.copy(type = t))
                            typeMenu = false
                        })
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                ExpressionLine(f.expr, "tap to enter") { onEdit(false) }
                if (f.type == GraphType.PARAM) ExpressionLine(f.expr2, "tap to enter Yt") { onEdit(true) }
            }
        }
    }
}

@Composable
private fun ExpressionLine(row: MathRow, hint: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).horizontalScroll(rememberScrollState()),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (row.isEmpty) {
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
        } else {
            MathView(row, fontSize = 20.sp)
        }
    }
}
