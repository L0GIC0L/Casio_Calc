package com.l0gic0l.casiocalc.ui.run

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.data.HistoryEntry
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.CalcKeypad
import com.l0gic0l.casiocalc.ui.components.StatusLine
import com.l0gic0l.casiocalc.ui.components.keypadHeights
import com.l0gic0l.casiocalc.ui.math.MathInputField
import com.l0gic0l.casiocalc.ui.math.ScrollingMath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(vm: CalcViewModel, onBack: () -> Unit, onSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Run-Matrix") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Main menu") }
                },
                actions = {
                    IconButton(onClick = vm::clearHistory) { Icon(Icons.Default.DeleteSweep, contentDescription = "Clear history") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Set up") }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val (fh, nh) = keypadHeights(maxHeight)
            Column(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column {
                        StatusLine(vm, vm.runInput)
                        HistoryAndInput(vm, Modifier.weight(1f))
                    }
                }
                CalcKeypad(
                    vm = vm,
                    input = vm.runInput,
                    onCommand = {},
                    onSetup = onSettings,
                    functionKeyHeight = fh,
                    numberKeyHeight = nh,
                    handle = vm::runKey,
                )
            }
        }
    }
}

@Composable
private fun HistoryAndInput(vm: CalcViewModel, modifier: Modifier) {
    val listState = rememberLazyListState()
    val history = vm.history
    val formatter = vm.formatter()
    LaunchedEffect(history.size, vm.runInput.revision, vm.runError) {
        listState.scrollToItem(history.size)
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState) {
        items(history, key = { System.identityHashCode(it) }) { entry ->
            HistoryItem(entry, formatter, vm)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 12.dp))
        }
        item {
            Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
                MathInputField(vm.runInput, Modifier.fillMaxWidth(), fontSize = 28.sp)
                val error = vm.runError
                if (vm.computing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Calculating…  AC to stop", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (error != null) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(entry: HistoryEntry, formatter: Formatter, vm: CalcViewModel) {
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    fun copy(text: String) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("CG Calc", text))) }
    }
    val result = remember(entry, entry.decimal, formatter) {
        val style = if (entry.decimal) Formatter.Style.DECIMAL else entry.style
        formatter.format(entry.value, style)
    }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { vm.loadIntoInput(entry.input) }, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ScrollingMath(entry.input, fontSize = 20.sp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                ScrollingMath(result, fontSize = 26.sp, reverse = true)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { vm.loadIntoInput(entry.input); menu = false })
            DropdownMenuItem(text = { Text("Insert result") }, onClick = { vm.insertIntoInput(result); menu = false })
            DropdownMenuItem(text = { Text("Copy input") }, onClick = {
                copy(RowText.linear(entry.input)); menu = false
            })
            DropdownMenuItem(text = { Text("Copy result") }, onClick = {
                copy(RowText.linear(result)); menu = false
            })
            DropdownMenuItem(text = { Text(if (entry.decimal) "Show exact" else "Show decimal") }, onClick = {
                entry.decimal = !entry.decimal; menu = false
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { vm.removeHistory(entry); menu = false })
        }
    }
}
