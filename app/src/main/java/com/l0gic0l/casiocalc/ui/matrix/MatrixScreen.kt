package com.l0gic0l.casiocalc.ui.matrix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.EditorPanel
import com.l0gic0l.casiocalc.ui.components.HeaderCell
import com.l0gic0l.casiocalc.ui.components.Stepper
import com.l0gic0l.casiocalc.ui.components.ValueCell
import com.l0gic0l.casiocalc.ui.components.keypadHeights
import com.l0gic0l.casiocalc.ui.keypad.InputState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(vm: CalcViewModel, onBack: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<Char?>(null) }
    @Suppress("UNUSED_VARIABLE") val version = vm.memory.version
    BackHandler(enabled = selected != null) { selected = null }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.let { "Mat $it" } ?: "Matrix") },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) selected = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val c = selected
                    if (c != null && vm.memory.getMatrix(c) != null) {
                        IconButton(onClick = { vm.memory.setMatrix(c, null) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Mat $c")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val c = selected
        if (c == null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(('A'..'Z').toList()) { letter ->
                    val m = vm.memory.getMatrix(letter)
                    Surface(
                        onClick = { selected = letter },
                        shape = RoundedCornerShape(16.dp),
                        color = if (m != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.height(72.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("Mat $letter", style = MaterialTheme.typography.titleMedium)
                            Text(m?.let { "${it.rows}×${it.cols}" } ?: "None", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        } else {
            MatrixEditor(vm, c, Modifier.padding(padding))
        }
    }
}

@Composable
private fun MatrixEditor(vm: CalcViewModel, letter: Char, modifier: Modifier) {
    val formatter = vm.formatter()
    val m = vm.memory.getMatrix(letter)
    val rows = m?.rows ?: 2
    val cols = m?.cols ?: 2
    var editing by remember(letter) { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val input = remember(letter, editing) {
        InputState(editing?.let { i -> m?.let { formatter.format(Value.Num(it.cells[i])) } } ?: com.l0gic0l.casiocalc.math.editor.MathRow())
    }

    fun resize(r: Int, c: Int) {
        val resized = Value.Mat.build(r, c) { i, j -> if (m != null && i < m.rows && j < m.cols) m[i, j] else Complex.ZERO }
        vm.memory.setMatrix(letter, resized)
        editing = null
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val heights = keypadHeights(maxHeight).let { (a, b) -> a * 0.9f to b * 0.9f }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stepper("Rows", rows, 1..10) { resize(it, cols) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stepper("Columns", cols, 1..10) { resize(rows, it) }
                }
                if (m == null) {
                    Text("Empty. Set a size or tap a cell to create it.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                }
                Column(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    Row {
                        HeaderCell("", 32.dp)
                        for (j in 0 until cols) HeaderCell("${j + 1}", 96.dp)
                    }
                    for (i in 0 until rows) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeaderCell("${i + 1}", 32.dp)
                            for (j in 0 until cols) {
                                val index = i * cols + j
                                ValueCell(
                                    value = Value.Num(m?.cells?.get(index) ?: Complex.ZERO),
                                    formatter = formatter,
                                    selected = editing == index,
                                    width = 96.dp,
                                    onClick = {
                                        if (m == null) resize(rows, cols)
                                        editing = index
                                        error = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
            val index = editing
            if (index != null) {
                EditorPanel(
                    vm = vm,
                    title = "Mat $letter  [${index / cols + 1}, ${index % cols + 1}]",
                    input = input,
                    error = error,
                    keyHeights = heights,
                    onCancel = { editing = null },
                    onDone = {
                        val result = vm.evaluateCell(input.row)
                        val v = result.getOrNull()
                        if (v is Value.Num) {
                            val current = vm.memory.getMatrix(letter) ?: Value.Mat.build(rows, cols) { _, _ -> Complex.ZERO }
                            val cells = current.cells.toMutableList()
                            cells[index] = v.z
                            vm.memory.setMatrix(letter, Value.Mat(current.rows, current.cols, cells))
                            error = null
                            editing = if (index + 1 < rows * cols) index + 1 else null
                        } else {
                            error = result.exceptionOrNull()?.let { CalcViewModel.errorMessage(it) } ?: "Dimension ERROR"
                        }
                    },
                )
            }
        }
    }
}
