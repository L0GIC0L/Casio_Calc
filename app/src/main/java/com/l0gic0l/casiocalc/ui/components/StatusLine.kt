package com.l0gic0l.casiocalc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.eval.ComplexMode
import com.l0gic0l.casiocalc.math.eval.DisplayFormat
import com.l0gic0l.casiocalc.ui.CalcViewModel

/**
 * The calculator's status bar: the current modes (SHIFT and ALPHA show on their keys). Tap a
 * mode to cycle it. [leading] and [trailing] hold navigation and actions when the screen has no
 * app bar.
 */
@Composable
fun StatusLine(
    vm: CalcViewModel,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val s = vm.settings
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(Icons.Filled.Architecture, s.angle.label) {
                vm.updateSettings { it.copy(angle = AngleUnit.entries[(it.angle.ordinal + 1) % AngleUnit.entries.size]) }
            }
            Chip(Icons.Filled.Numbers, s.display.label + if (s.eng) " Eng" else "") {
                val next = when (s.display.kind) {
                    DisplayFormat.Kind.NORM1 -> DisplayFormat(DisplayFormat.Kind.NORM2)
                    else -> DisplayFormat(DisplayFormat.Kind.NORM1)
                }
                vm.updateSettings { it.copy(display = next) }
            }
            Chip(Icons.Filled.Functions, if (s.exactOutput) "Math" else "Deci") {
                vm.updateSettings { it.copy(exactOutput = !it.exactOutput) }
            }
            Chip(Icons.Filled.Adjust, s.complex.label) {
                vm.updateSettings { it.copy(complex = ComplexMode.entries[(it.complex.ordinal + 1) % ComplexMode.entries.size]) }
            }
        }
        trailing()
    }
}

@Composable
private fun Chip(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(start = 7.dp, end = 9.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "chip") { t ->
                Text(t, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
