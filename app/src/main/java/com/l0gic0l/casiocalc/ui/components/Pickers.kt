package com.l0gic0l.casiocalc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.data.AppMemory
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.math.num.Value

/** Short text form of a value for menus and tables. */
fun Formatter.brief(v: Value): String = when (v) {
    is Value.Num -> RowText.linear(MathRow(real(v.z.re, decimal = true))).let { re ->
        if (v.z.isReal) re else RowText.linear(format(v, Formatter.Style.DECIMAL))
    }
    is Value.Lst -> "{${v.items.size} items}"
    is Value.Mat -> "${v.rows}×${v.cols}"
}

@Composable
private fun PickCell(title: String, subtitle: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, fontSize = 16.sp, maxLines = 1)
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** VARS: insert a variable, matrix or list, showing what each currently holds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VarsSheet(memory: AppMemory, formatter: Formatter, onDismiss: () -> Unit, onPick: (Tok) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val version = memory.version
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column {
            Text("VARS", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("Variables", "Matrices", "Lists").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            val entries: List<Triple<String, String?, Tok>> = when (tab) {
                0 -> listOf(Triple("Ans", formatter.brief(memory.ans), Tokens.ANS)) +
                    Memory.variableNames.map { name ->
                        val tok = Tokens.byId(name)!!
                        Triple(tok.display, memory.variables[name]?.let { formatter.brief(it) } ?: "0", tok)
                    }
                1 -> ('A'..'Z').map { c ->
                    Triple("Mat $c", memory.getMatrix(c)?.let { "${it.rows}×${it.cols}" } ?: "—", Tokens.matrix(c))
                }
                else -> (1..26).map { n ->
                    Triple("List $n", memory.getList(n)?.let { "${it.items.size} items" } ?: "—", Tokens.list(n))
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 440.dp),
            ) {
                items(entries) { (title, sub, tok) ->
                    PickCell(title, sub) {
                        onPick(tok)
                        onDismiss()
                    }
                }
            }
        }
    }
}

/** Mat A–Z picker (SHIFT 2). */
@Composable
fun MatrixPickerDialog(memory: AppMemory, onDismiss: () -> Unit, onPick: (Tok) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Matrix") },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(('A'..'Z').toList()) { c ->
                    PickCell("$c", memory.getMatrix(c)?.let { "${it.rows}×${it.cols}" }) {
                        onPick(Tokens.matrix(c)); onDismiss()
                    }
                }
            }
        },
    )
}

/** List 1–26 picker (SHIFT 1). */
@Composable
fun ListPickerDialog(memory: AppMemory, onDismiss: () -> Unit, onPick: (Tok) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("List") },
        text = {
            LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items((1..26).toList()) { n ->
                    PickCell("$n", memory.getList(n)?.let { "${it.items.size}" }) {
                        onPick(Tokens.list(n)); onDismiss()
                    }
                }
            }
        },
    )
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(72.dp))
        FilledTonalIconButton(onClick = { if (value > range.first) onChange(value - 1) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $label")
        }
        Text("$value", fontSize = 20.sp, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        FilledTonalIconButton(onClick = { if (value < range.last) onChange(value + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "Increase $label")
        }
    }
}

/** Asks for the size of a new matrix template. */
@Composable
fun MatrixSizeDialog(onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var rows by remember { mutableIntStateOf(2) }
    var cols by remember { mutableIntStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Matrix size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper("Rows", rows, 1..6) { rows = it }
                Stepper("Columns", cols, 1..6) { cols = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(rows, cols); onDismiss() }) { Text("Insert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
