package com.l0gic0l.casiocalc.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.l0gic0l.casiocalc.data.AccentColor
import com.l0gic0l.casiocalc.data.KeyShape
import com.l0gic0l.casiocalc.data.KeySpacing
import com.l0gic0l.casiocalc.data.KeypadSize
import com.l0gic0l.casiocalc.data.StartScreen
import com.l0gic0l.casiocalc.ui.theme.accentSwatch

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.data.LineWeight
import com.l0gic0l.casiocalc.data.TextScale
import com.l0gic0l.casiocalc.data.UiPrefs
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.eval.ComplexMode
import com.l0gic0l.casiocalc.math.eval.DisplayFormat
import com.l0gic0l.casiocalc.math.eval.FracResult
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.appBarInsets
import com.l0gic0l.casiocalc.ui.theme.ThemeMode

/** Which theme colour a settings group uses for its icons. */
private enum class Accent { PRIMARY, SECONDARY, TERTIARY }

/** Something that asks before it is done. */
private enum class Confirm { MEMORY, HISTORY, SETTINGS }

/** Set Up and app settings, laid out like Android's Settings: a large title and rounded groups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CalcViewModel, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val s = vm.settings
    val p = vm.prefs
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            LargeTopAppBar(
                windowInsets = appBarInsets,
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        // Settings read best as a column of moderate width, even on unfolded and tablet screens.
        Box(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Group("Calculation", Accent.PRIMARY) {
                    item { Segmented("Angle", Icons.Default.Straighten, AngleUnit.entries.map { it.label }, s.angle.ordinal) { i -> vm.updateSettings { it.copy(angle = AngleUnit.entries[i]) } } }
                    item { DisplayPicker(s.display) { d -> vm.updateSettings { it.copy(display = d) } } }
                    item { SwitchItem("Engineering notation", "Exponents in multiples of 3", Icons.Default.Numbers, s.eng) { v -> vm.updateSettings { it.copy(eng = v) } } }
                    item { Segmented("Output", Icons.Default.Calculate, listOf("Math (exact)", "Decimal"), if (s.exactOutput) 0 else 1) { i -> vm.updateSettings { it.copy(exactOutput = i == 0) } } }
                    item { Segmented("Fraction result", Icons.Default.Percent, FracResult.entries.map { it.label }, s.fracResult.ordinal) { i -> vm.updateSettings { it.copy(fracResult = FracResult.entries[i]) } } }
                    item { Segmented("Complex mode", Icons.Default.Adjust, ComplexMode.entries.map { it.label }, s.complex.ordinal) { i -> vm.updateSettings { it.copy(complex = ComplexMode.entries[i]) } } }
                }

                Group("Appearance", Accent.TERTIARY) {
                    item {
                        Segmented("Theme", Icons.Default.BrightnessMedium, listOf("System", "Light", "Dark"), p.theme.ordinal) { i -> vm.updatePrefs { it.copy(theme = ThemeMode.entries[i]) } }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        item { SwitchItem("Dynamic colour", "Use colours from your wallpaper", Icons.Default.Wallpaper, p.dynamicColor) { v -> vm.updatePrefs { it.copy(dynamicColor = v) } } }
                    }
                    item {
                        val wallpaper = p.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        AccentPicker(p.accent, enabled = !wallpaper) { a -> vm.updatePrefs { it.copy(accent = a) } }
                    }
                    item { SwitchItem("Pure black", "Black background in the dark theme", Icons.Default.Contrast, p.pureBlack) { v -> vm.updatePrefs { it.copy(pureBlack = v) } } }
                    item {
                        Segmented("Display text size", Icons.Default.FormatSize, TextScale.entries.map { it.label }, p.displaySize.ordinal) { i -> vm.updatePrefs { it.copy(displaySize = TextScale.entries[i]) } }
                    }
                }

                Group("Motion", Accent.SECONDARY) {
                    item { SwitchItem("Home screen particles", "Drifting sparks behind the menu", Icons.Default.AutoAwesome, p.particles, enabled = !p.reduceMotion) { v -> vm.updatePrefs { it.copy(particles = v, resultParticles = it.answerSparkles) } } }
                    item { SwitchItem("Answer sparkles", "A burst of sparks when a result appears", Icons.Default.Celebration, p.answerSparkles, enabled = !p.reduceMotion) { v -> vm.updatePrefs { it.copy(resultParticles = v) } } }
                    item { SwitchItem("Reduce motion", "Turn off particles and decorative animation", Icons.Default.Animation, p.reduceMotion) { v -> vm.updatePrefs { it.copy(reduceMotion = v) } } }
                }

                Group("Keypad", Accent.PRIMARY) {
                    item { Segmented("Keypad height", Icons.Default.Height, KeypadSize.entries.map { it.label }, p.keypadSize.ordinal) { i -> vm.updatePrefs { it.copy(keypadSize = KeypadSize.entries[i]) } } }
                    item { Segmented("Key shape", Icons.Default.RoundedCorner, KeyShape.entries.map { it.label }, p.keyShape.ordinal) { i -> vm.updatePrefs { it.copy(keyShape = KeyShape.entries[i]) } } }
                    item { Segmented("Key spacing", Icons.Default.SpaceBar, KeySpacing.entries.map { it.label }, p.keySpacing.ordinal) { i -> vm.updatePrefs { it.copy(keySpacing = KeySpacing.entries[i]) } } }
                    item { Segmented("Key label size", Icons.Default.TextFields, TextScale.entries.map { it.label }, p.keyLabels.ordinal) { i -> vm.updatePrefs { it.copy(keyLabels = TextScale.entries[i]) } } }
                    item { SwitchItem("SHIFT labels", "Each key's SHIFT function in small print above it", Icons.Default.Keyboard, p.keyHints) { v -> vm.updatePrefs { it.copy(keyHints = v) } } }
                    item { SwitchItem("Key vibration", null, Icons.Default.Vibration, p.haptics) { v -> vm.updatePrefs { it.copy(haptics = v) } } }
                    item { SwitchItem("Key click sound", "Needs Touch sounds on in the system sound settings", Icons.AutoMirrored.Filled.VolumeUp, p.keySound) { v -> vm.updatePrefs { it.copy(keySound = v) } } }
                }

                Group("Screen", Accent.TERTIARY) {
                    item { Segmented("Open the app on", Icons.Default.Home, StartScreen.entries.map { it.label }, p.startScreen.ordinal) { i -> vm.updatePrefs { it.copy(startScreen = StartScreen.entries[i]) } } }
                    item { SwitchItem("Live preview", "Show the answer to a simple calculation under the Run-Matrix line as you type", Icons.Default.Visibility, p.livePreview) { v -> vm.updatePrefs { it.copy(livePreview = v) } } }
                    item { SwitchItem("Keep screen on", "While the app is open", Icons.Default.ScreenLockPortrait, p.keepScreenOn) { v -> vm.updatePrefs { it.copy(keepScreenOn = v) } } }
                }

                Group("Graph", Accent.SECONDARY) {
                    item { SwitchItem("Grid", null, Icons.Default.GridOn, p.graphGrid) { v -> vm.updatePrefs { it.copy(graphGrid = v) } } }
                    item { SwitchItem("Axes", null, Icons.Default.Add, p.graphAxes) { v -> vm.updatePrefs { it.copy(graphAxes = v) } } }
                    item { SwitchItem("Axis labels", null, Icons.Default.Numbers, p.graphLabels) { v -> vm.updatePrefs { it.copy(graphLabels = v) } } }
                    item {
                        Segmented("Line thickness", Icons.Default.LineWeight, LineWeight.entries.map { it.label }, p.graphLine.ordinal) { i -> vm.updatePrefs { it.copy(graphLine = LineWeight.entries[i]) } }
                    }
                }

                Group("Memory and reset", Accent.SECONDARY) {
                    item { ActionItem("Reset variables", "A–Z, Ans, lists and matrices", Icons.Default.Memory) { confirm = Confirm.MEMORY } }
                    item { ActionItem("Clear calculation history", null, Icons.Default.DeleteSweep) { confirm = Confirm.HISTORY } }
                    item { ActionItem("Reset settings", "Everything on this page back to its default", Icons.Default.SettingsBackupRestore) { confirm = Confirm.SETTINGS } }
                }

                Group("About", Accent.PRIMARY) {
                    item {
                        val context = LocalContext.current
                        val version = remember {
                            @Suppress("DEPRECATION")
                            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                        }
                        InfoItem("CG Calc $version", "A natural display graphing calculator modelled on the Casio fx-CG50", Icons.Default.Info)
                    }
                    item { InfoItem("Tips", "Tap a mode chip on the Run-Matrix screen to change it. Pinch a graph sideways or up and down to zoom one axis.", Icons.Default.TouchApp) }
                }
            }
        }
    }
    val c = confirm
    if (c != null) {
        val (title, text) = when (c) {
            Confirm.MEMORY -> "Reset memory?" to "Clears A–Z, Ans, every list and every matrix."
            Confirm.HISTORY -> "Clear history?" to "Removes every calculation from the Run-Matrix screen."
            Confirm.SETTINGS -> "Reset settings?" to "Puts every setting on this page back to its default. Your calculations and memory stay."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    when (c) {
                        Confirm.MEMORY -> vm.resetMemory()
                        Confirm.HISTORY -> vm.clearHistory()
                        Confirm.SETTINGS -> {
                            vm.updateSettings { Settings() }
                            vm.updatePrefs { UiPrefs() }
                        }
                    }
                    confirm = null
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** Collects a group's rows so each can be given its place in the rounded stack. */
private class GroupScope(val accent: Accent) {
    val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

private val LocalAccent = androidx.compose.runtime.compositionLocalOf { Accent.PRIMARY }

/**
 * A titled group of rows. Rows sit in a stack of rounded cards with a small gap between them;
 * the stack's outer corners are fully rounded, as in Android's Settings.
 */
@Composable
private fun Group(title: String, accent: Accent, build: GroupScope.() -> Unit) {
    val scope = GroupScope(accent).apply(build)
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val n = scope.items.size
        scope.items.forEachIndexed { i, content ->
            val big = 24.dp
            val small = 6.dp
            val shape: Shape = RoundedCornerShape(
                topStart = if (i == 0) big else small,
                topEnd = if (i == 0) big else small,
                bottomStart = if (i == n - 1) big else small,
                bottomEnd = if (i == n - 1) big else small,
            )
            Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAccent provides accent) { content() }
            }
        }
    }
}

/** Colour swatches for the theme colour; with wallpaper colours on they are shown but dimmed. */
@Composable
private fun AccentPicker(selected: AccentColor, enabled: Boolean, onPick: (AccentColor) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Palette)
            Column(Modifier.padding(start = 16.dp)) {
                Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
                if (!enabled) {
                    Text("Turn off Dynamic colour to choose one", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (a in AccentColor.entries) {
                val chosen = a == selected
                Surface(
                    onClick = { onPick(a) },
                    enabled = enabled,
                    shape = CircleShape,
                    color = accentSwatch(a.hueShift).copy(alpha = if (enabled) 1f else 0.38f),
                    border = if (chosen && enabled) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                    modifier = Modifier.size(40.dp).semantics {
                        contentDescription = a.label
                        this.selected = chosen
                    },
                ) {
                    if (chosen && enabled) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

/** A row's icon in a tinted circle. */
@Composable
private fun IconBadge(icon: ImageVector?) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (LocalAccent.current) {
        Accent.PRIMARY -> scheme.primaryContainer to scheme.onPrimaryContainer
        Accent.SECONDARY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Accent.TERTIARY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Surface(shape = CircleShape, color = if (icon == null) Color.Transparent else bg, modifier = Modifier.size(40.dp)) {
        if (icon != null) Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun SwitchItem(title: String, subtitle: String?, icon: ImageVector, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    // The whole row toggles, as in Android's Settings, so the label is a target too.
    ListItem(
        leadingContent = { IconBadge(icon) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked && enabled, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(checked && enabled, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun ActionItem(title: String, subtitle: String?, icon: ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        ListItem(
            leadingContent = { IconBadge(icon) },
            headlineContent = { Text(title) },
            supportingContent = subtitle?.let { { Text(it) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
private fun InfoItem(title: String, text: String, icon: ImageVector) {
    ListItem(
        leadingContent = { IconBadge(icon) },
        headlineContent = { Text(title) },
        supportingContent = { Text(text) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Segmented(label: String, icon: ImageVector?, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    icon = {
                        // Theme options carry their own icons; the others keep the usual check mark.
                        if (label == "Theme") {
                            Icon(listOf(Icons.Default.BrightnessMedium, Icons.Default.LightMode, Icons.Default.DarkMode)[i], contentDescription = null, modifier = Modifier.size(18.dp))
                        } else {
                            SegmentedButtonDefaults.Icon(i == selected)
                        }
                    },
                ) { Text(o, maxLines = 1) }
            }
        }
    }
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
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Default.Tag)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f).padding(start = 16.dp),
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
}
