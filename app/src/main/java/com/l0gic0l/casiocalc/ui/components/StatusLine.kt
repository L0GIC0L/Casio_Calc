package com.l0gic0l.casiocalc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.eval.ComplexMode
import com.l0gic0l.casiocalc.math.eval.DisplayFormat
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.theme.LocalKeyLabelColors

/** Key heights that let the keypad share the screen with the display area. */
fun keypadHeights(available: Dp): Pair<Dp, Dp> {
    val fh = (available.value * 0.062f).coerceIn(32f, 46f)
    val nh = (available.value * 0.074f).coerceIn(38f, 56f)
    return fh.dp to nh.dp
}

/** The calculator's status bar: tap an item to cycle that setting. */
@Composable
fun StatusLine(vm: CalcViewModel, input: InputState?) {
    val s = vm.settings
    val labels = LocalKeyLabelColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (input != null) {
            when {
                input.shift -> Badge("S", labels.shift)
                input.alphaLock -> Badge("A-Lock", labels.alpha)
                input.alpha -> Badge("A", labels.alpha)
            }
        }
        Chip(s.angle.label) {
            vm.updateSettings { it.copy(angle = AngleUnit.entries[(it.angle.ordinal + 1) % AngleUnit.entries.size]) }
        }
        Chip(s.display.label + if (s.eng) " Eng" else "") {
            val next = when (s.display.kind) {
                DisplayFormat.Kind.NORM1 -> DisplayFormat(DisplayFormat.Kind.NORM2)
                else -> DisplayFormat(DisplayFormat.Kind.NORM1)
            }
            vm.updateSettings { it.copy(display = next) }
        }
        Chip(if (s.exactOutput) "Math" else "Deci") {
            vm.updateSettings { it.copy(exactOutput = !it.exactOutput) }
        }
        Chip(s.complex.label) {
            vm.updateSettings { it.copy(complex = ComplexMode.entries[(it.complex.ordinal + 1) % ComplexMode.entries.size]) }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    ) {
        Text(text, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
