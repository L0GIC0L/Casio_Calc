package com.l0gic0l.casiocalc.ui.basen

import androidx.compose.ui.unit.min
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import com.l0gic0l.casiocalc.ui.adaptive.HingeSplit
import com.l0gic0l.casiocalc.ui.adaptive.currentFold
import com.l0gic0l.casiocalc.ui.components.appBarInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.basen.BaseN
import com.l0gic0l.casiocalc.math.basen.Radix
import com.l0gic0l.casiocalc.ui.CalcViewModel

private data class BKey(val label: String, val insert: String? = null, val action: String? = null, val digit: Int = -1)

private val keyRows = listOf(
    listOf(BKey("A", "A", digit = 10), BKey("B", "B", digit = 11), BKey("C", "C", digit = 12), BKey("D", "D", digit = 13), BKey("E", "E", digit = 14), BKey("F", "F", digit = 15)),
    listOf(BKey("and", " and "), BKey("or", " or "), BKey("xor", " xor "), BKey("xnor", " xnor "), BKey("not", "not "), BKey("Neg", "Neg ")),
    listOf(BKey("d", "d"), BKey("h", "h"), BKey("b", "b"), BKey("o", "o"), BKey("(", "("), BKey(")", ")")),
    listOf(BKey("7", "7", digit = 7), BKey("8", "8", digit = 8), BKey("9", "9", digit = 9), BKey("DEL", action = "del"), BKey("AC", action = "ac")),
    listOf(BKey("4", "4", digit = 4), BKey("5", "5", digit = 5), BKey("6", "6", digit = 6), BKey("×", "×"), BKey("÷", "÷")),
    listOf(BKey("1", "1", digit = 1), BKey("2", "2", digit = 2), BKey("3", "3", digit = 3), BKey("+", "+"), BKey("−", "−")),
    listOf(BKey("0", "0", digit = 0), BKey("Ans", action = "ans"), BKey("EXE", action = "exe")),
)

private val words = listOf("xnor", "xor", "and", "or", "not", "Neg")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseNScreen(vm: CalcViewModel, onBack: () -> Unit) {
    var radix by rememberSaveable { mutableStateOf(Radix.DEC) }
    var text by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<Int?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme

    fun press(k: BKey) {
        if (vm.prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        error = null
        when (k.action) {
            "del" -> text = text.trimEnd().let { t ->
                val word = words.firstOrNull { t.endsWith(it) }
                if (word != null) t.dropLast(word.length).trimEnd() else t.dropLast(1)
            }
            "ac" -> {
                text = ""
                result = null
            }
            // Ans is inserted with its radix prefix so it means the same value in any mode.
            "ans" -> result?.let { text += if (it < 0) "Neg d${-it.toLong()}" else "d$it" }
            "exe" -> try {
                result = BaseN.evaluate(text, radix)
            } catch (e: CalcException) {
                error = e.kind.message
            }
            else -> text += k.insert
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = appBarInsets,
                title = { Text("Base-N") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        @Composable
        fun Display(modifier: Modifier) {
            Column(modifier) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Radix.entries.forEachIndexed { i, r ->
                        SegmentedButton(
                            selected = radix == r,
                            onClick = {
                                radix = r
                                text = ""
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, Radix.entries.size),
                        ) { Text(r.label) }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = scheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
                ) {
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Text(text + "▏", fontSize = 26.sp, fontFamily = FontFamily.Monospace)
                        }
                        error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.titleMedium) }
                        result?.let { v ->
                            for (r in Radix.entries) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.label, color = if (r == radix) scheme.primary else scheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
                                    Text(
                                        BaseN.format(v, r),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = if (r == radix) 22.sp else 15.sp,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun Keys(modifier: Modifier) {
            Column(modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in keyRows) {
                    Row(Modifier.weight(if (row.size == 6) 0.85f else 1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (k in row) {
                            val enabled = k.digit < 0 || k.digit < radix.base
                            val (bg, fg) = when {
                                k.action == "exe" -> scheme.primary to scheme.onPrimary
                                k.action == "ac" -> scheme.errorContainer to scheme.onErrorContainer
                                k.action == "del" -> scheme.tertiaryContainer to scheme.onTertiaryContainer
                                k.digit >= 0 -> scheme.surfaceContainerHighest to scheme.onSurface
                                k.label in listOf("×", "÷", "+", "−") -> scheme.secondaryContainer to scheme.onSecondaryContainer
                                else -> scheme.surfaceContainerHigh to scheme.onSurface
                            }
                            Surface(
                                onClick = { press(k) },
                                enabled = enabled,
                                shape = RoundedCornerShape(16.dp),
                                color = bg,
                                contentColor = if (enabled) fg else fg.copy(alpha = 0.3f),
                                modifier = Modifier.weight(if (k.action == "exe") 2f else 1f).fillMaxHeight(),
                            ) {
                                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    if (k.action == "del") {
                                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                                    } else {
                                        Text(k.label, fontSize = if (k.label.length > 1 && k.label.all { it.isLetter() }) 15.sp else 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val fold = currentFold()
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val height = maxHeight
            if (fold.book || fold.tabletop) {
                // Half-folded: display on one half, keys on the other, nothing across the fold.
                HingeSplit(vertical = fold.book, hinge = fold.hinge, first = { Display(it) }) { m ->
                    Box(m, contentAlignment = Alignment.Center) { Keys(Modifier.widthIn(max = 640.dp).fillMaxSize()) }
                }
            } else if (maxWidth > height) {
                // Landscape: display beside the keys.
                Row(Modifier.fillMaxSize()) {
                    Display(Modifier.weight(1f).fillMaxHeight())
                    Keys(Modifier.weight(1.2f).widthIn(max = 640.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Display(Modifier.weight(1f).fillMaxWidth())
                    Keys(Modifier.widthIn(max = 560.dp).fillMaxWidth().height(min(height * 0.6f, 440.dp)))
                }
            }
        }
    }
}
