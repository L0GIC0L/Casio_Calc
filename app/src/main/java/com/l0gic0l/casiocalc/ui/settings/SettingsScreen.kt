package com.l0gic0l.casiocalc.ui.settings

import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import com.l0gic0l.casiocalc.ui.components.appBarInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.eval.ComplexMode
import com.l0gic0l.casiocalc.math.eval.DisplayFormat
import com.l0gic0l.casiocalc.math.eval.FracResult
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CalcViewModel, onBack: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    val s = vm.settings
    val p = vm.prefs
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = appBarInsets,
                title = { Text("Set Up") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        // Settings read best as a column of moderate width, even on unfolded and tablet screens.
        Box(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(bottom = 24.dp)) {
                Section("Calculation", Icons.Default.Calculate)
                Segmented("Angle", AngleUnit.entries.map { it.label }, s.angle.ordinal) { i ->
                    vm.updateSettings { it.copy(angle = AngleUnit.entries[i]) }
                }
                DisplayPicker(s.display) { d -> vm.updateSettings { it.copy(display = d) } }
                SwitchRow("Engineering notation", "Exponents in multiples of 3", s.eng, Icons.Default.Straighten) { v -> vm.updateSettings { it.copy(eng = v) } }
                Segmented("Output", listOf("Math (exact)", "Decimal"), if (s.exactOutput) 0 else 1) { i ->
                    vm.updateSettings { it.copy(exactOutput = i == 0) }
                }
                Segmented("Fraction result", FracResult.entries.map { it.label }, s.fracResult.ordinal) { i ->
                    vm.updateSettings { it.copy(fracResult = FracResult.entries[i]) }
                }
                Segmented("Complex mode", ComplexMode.entries.map { it.label }, s.complex.ordinal) { i ->
                    vm.updateSettings { it.copy(complex = ComplexMode.entries[i]) }
                }

                Section("Graph", Icons.AutoMirrored.Filled.ShowChart)
                SwitchRow("Grid", null, p.graphGrid, Icons.Default.GridOn) { v -> vm.updatePrefs { it.copy(graphGrid = v) } }
                SwitchRow("Axes", null, p.graphAxes, Icons.Default.Add) { v -> vm.updatePrefs { it.copy(graphAxes = v) } }
                SwitchRow("Axis labels", null, p.graphLabels, Icons.Default.Numbers) { v -> vm.updatePrefs { it.copy(graphLabels = v) } }

                Section("Appearance", Icons.Default.Palette)
                Segmented("Theme", listOf("System", "Light", "Dark"), p.theme.ordinal) { i ->
                    vm.updatePrefs { it.copy(theme = ThemeMode.entries[i]) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Dynamic colour", "Use colours from your wallpaper", p.dynamicColor, Icons.Default.Wallpaper) { v -> vm.updatePrefs { it.copy(dynamicColor = v) } }
                }
                SwitchRow("SHIFT labels on keys", "Small print showing each key's SHIFT function", p.keyHints, Icons.Default.Keyboard) { v -> vm.updatePrefs { it.copy(keyHints = v) } }
                SwitchRow("Key vibration", null, p.haptics, Icons.Default.Vibration) { v -> vm.updatePrefs { it.copy(haptics = v) } }

                Section("Memory", Icons.Default.Memory)
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Reset variables, lists and matrices")
                }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset memory?") },
            text = { Text("Clears A–Z, Ans, every list and every matrix.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetMemory()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, icon: ImageVector) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Segmented(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(o, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, icon: ImageVector, onChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayPicker(current: DisplayFormat, onPick: (DisplayFormat) -> Unit) {
    val options = buildList {
        add(DisplayFormat(DisplayFormat.Kind.NORM1))
        add(DisplayFormat(DisplayFormat.Kind.NORM2))
        for (d in 0..9) add(DisplayFormat(DisplayFormat.Kind.FIX, d))
        for (d in 0..9) add(DisplayFormat(DisplayFormat.Kind.SCI, d))
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        OutlinedTextField(
            value = current.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Display") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label + if (o.kind == DisplayFormat.Kind.SCI && o.digits == 0) " (10 digits)" else "") },
                    onClick = {
                        onPick(o)
                        expanded = false
                    },
                )
            }
        }
    }
}
