package com.l0gic0l.casiocalc.ui.stat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.data.GraphFunction
import com.l0gic0l.casiocalc.data.GraphType
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.numeric.Stats
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.EditorPanel
import com.l0gic0l.casiocalc.ui.components.HeaderCell
import com.l0gic0l.casiocalc.ui.components.Stepper
import com.l0gic0l.casiocalc.ui.components.ValueCell
import com.l0gic0l.casiocalc.ui.components.keypadHeights
import com.l0gic0l.casiocalc.ui.keypad.InputState

private data class Cell(val list: Int, val index: Int)

/** What the results sheet shows. */
private class StatResult(val title: String, val rows: List<Pair<String, Double>>, val regression: Stats.Regression? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatScreen(vm: CalcViewModel, onBack: () -> Unit, onGraph: () -> Unit) {
    var editing by remember { mutableStateOf<Cell?>(null) }
    var cellMenu by remember { mutableStateOf<Cell?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<StatResult?>(null) }
    var regMenu by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE") val version = vm.memory.version
    val formatter = vm.formatter()
    val lists = (1..26).map { vm.memory.getList(it)?.items ?: emptyList() }
    val rowCount = (lists.maxOf { it.size } + 1).coerceAtLeast(8)

    val input = remember(editing) {
        val e = editing
        InputState(e?.let { c -> lists[c.list - 1].getOrNull(c.index)?.let { formatter.format(Value.Num(it), Formatter.Style.DECIMAL) } } ?: MathRow())
    }

    fun setList(n: Int, items: List<Complex>) = vm.memory.setList(n, Value.Lst(items))

    fun commit() {
        val c = editing ?: return
        val r = vm.evaluateCell(input.row)
        val v = r.getOrNull()
        val current = lists[c.list - 1].toMutableList()
        when (v) {
            is Value.Num -> {
                if (c.index < current.size) current[c.index] = v.z else current += v.z
                setList(c.list, current)
                editing = Cell(c.list, minOf(c.index + 1, current.size))
                error = null
            }
            is Value.Lst -> {
                // Entering a whole list such as {1,2,3} or Seq(...) fills the column.
                setList(c.list, v.items)
                editing = null
            }
            else -> error = r.exceptionOrNull()?.let { CalcViewModel.errorMessage(it) } ?: "Dimension ERROR"
        }
    }

    fun data(n: Int): List<Double>? = lists[n - 1].takeIf { it.isNotEmpty() }?.map { if (it.isReal) it.re.toDouble() else Double.NaN }

    fun calc(block: () -> StatResult) {
        try {
            result = block()
        } catch (e: IllegalStateException) {
            message = e.message
        } catch (e: Exception) {
            message = CalcViewModel.errorMessage(e)
        }
    }

    val s = vm.statSettings
    fun xData() = data(s.xList) ?: throw IllegalStateException("List ${s.xList} is empty")
    fun yData() = data(s.yList) ?: throw IllegalStateException("List ${s.yList} is empty")
    fun freq() = if (s.freqList == 0) null else data(s.freqList)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { IconButton(onClick = { showSetup = true }) { Icon(Icons.Default.Tune, contentDescription = "Calculation lists") } },
            )
        },
        bottomBar = {
            if (editing == null) {
                BottomAppBar {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = {
                            calc {
                                val st = Stats.oneVar(xData(), freq())
                                StatResult("1-Variable  (List ${s.xList})", st.rows())
                            }
                        }, modifier = Modifier.weight(1f)) { Text("1-Var", maxLines = 1) }
                        FilledTonalButton(onClick = {
                            calc {
                                val st = Stats.twoVar(xData(), yData(), freq())
                                StatResult("2-Variable  (List ${s.xList}, List ${s.yList})", st.rows())
                            }
                        }, modifier = Modifier.weight(1f)) { Text("2-Var", maxLines = 1) }
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                            FilledTonalButton(onClick = { regMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Reg", maxLines = 1) }
                            DropdownMenu(expanded = regMenu, onDismissRequest = { regMenu = false }) {
                                Stats.RegType.entries.forEach { t ->
                                    DropdownMenuItem(text = { Text("${t.label}   ${t.formula}") }, onClick = {
                                        regMenu = false
                                        calc {
                                            val reg = Stats.regression(t, xData(), yData(), freq())
                                            vm.lastRegression = reg
                                            StatResult(t.label + "   " + t.formula, reg.rows(), reg)
                                        }
                                    })
                                }
                            }
                        }
                        FilledTonalButton(onClick = onGraph, modifier = Modifier.weight(1f)) { Text("Graph", maxLines = 1) }
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val heights = keypadHeights(maxHeight).let { (a, b) -> a * 0.9f to b * 0.9f }
            Column(Modifier.fillMaxSize()) {
                val hScroll = rememberScrollState()
                val cellW = 96.dp
                Row(Modifier.horizontalScroll(hScroll).padding(horizontal = 4.dp)) {
                    HeaderCell("", 36.dp)
                    for (n in 1..26) HeaderCell("List $n", cellW)
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items((0 until rowCount).toList()) { i ->
                        Row(Modifier.horizontalScroll(hScroll).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            HeaderCell("${i + 1}", 36.dp, height = 44.dp)
                            for (n in 1..26) {
                                val items = lists[n - 1]
                                val value = items.getOrNull(i)
                                val canEdit = i <= items.size
                                ValueCell(
                                    value = value?.let { Value.Num(it) },
                                    formatter = formatter,
                                    selected = editing == Cell(n, i),
                                    width = cellW,
                                    height = 44.dp,
                                    decimal = true,
                                    onClick = { if (canEdit) { editing = Cell(n, i); error = null } },
                                    onLongClick = { if (value != null) cellMenu = Cell(n, i) },
                                )
                            }
                        }
                    }
                }
                val e = editing
                if (e != null) {
                    EditorPanel(vm, "List ${e.list}[${e.index + 1}]", input, onDone = ::commit, onCancel = { editing = null; error = null }, keyHeights = heights, error = error)
                }
            }
        }
    }

    cellMenu?.let { c ->
        AlertDialog(
            onDismissRequest = { cellMenu = null },
            title = { Text("List ${c.list}[${c.index + 1}]") },
            text = {
                Column {
                    TextButton(onClick = {
                        val l = lists[c.list - 1].toMutableList(); l.removeAt(c.index); setList(c.list, l); cellMenu = null
                    }) { Text("Delete this cell") }
                    TextButton(onClick = {
                        val l = lists[c.list - 1].toMutableList(); l.add(c.index, Complex.ZERO); setList(c.list, l); cellMenu = null
                    }) { Text("Insert a cell above") }
                    TextButton(onClick = { vm.memory.setList(c.list, null); cellMenu = null }) { Text("Clear List ${c.list}") }
                }
            },
            confirmButton = { TextButton(onClick = { cellMenu = null }) { Text("Cancel") } },
        )
    }

    if (showSetup) {
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text("Calculation lists") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stepper("XList", s.xList, 1..26) { vm.updateStatSettings(s.copy(xList = it)) }
                    Stepper("YList", s.yList, 1..26) { vm.updateStatSettings(s.copy(yList = it)) }
                    Stepper("Freq", s.freqList, 0..26) { vm.updateStatSettings(s.copy(freqList = it)) }
                    Text("Freq 0 means every value counts once.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showSetup = false }) { Text("Done") } },
        )
    }

    message?.let {
        AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, text = { Text(it) })
    }

    result?.let { res ->
        ModalBottomSheet(onDismissRequest = { result = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Text(res.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                for ((label, value) in res.rows) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(72.dp))
                        Text("=  " + if (value.isNaN()) "—" else formatter.decimalString(value), style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                    }
                }
                res.regression?.let { reg ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                        OutlinedButton(onClick = {
                            val slot = vm.graphFunctions.indexOfFirst { it.isEmpty }.takeIf { it >= 0 } ?: 0
                            vm.setGraphFunction(slot, GraphFunction(GraphType.Y, regressionRow(reg), colorIndex = slot % CalcViewModel.GRAPH_COLORS))
                            message = "Copied to Y${slot + 1}"
                            result = null
                        }) { Text("Copy to Y=") }
                        OutlinedButton(onClick = { result = null; onGraph() }) { Text("Draw") }
                    }
                }
            }
        }
    }
}
