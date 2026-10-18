package com.l0gic0l.casiocalc.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.adaptive.KeypadShape
import com.l0gic0l.casiocalc.ui.keypad.Cmd
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.math.MathInputField
import kotlin.coroutines.cancellation.CancellationException

/**
 * Editing area used by the table-like modes: a titled natural-display input line above the
 * keypad, filling [modifier]'s bounds. EXE calls [onDone]; close or the back gesture calls
 * [onCancel], with the panel following the gesture as it is made.
 */
@Composable
fun EditorPanel(
    vm: CalcViewModel,
    title: String,
    input: InputState,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    shape: KeypadShape,
    modifier: Modifier = Modifier,
    error: String? = null,
    xVar: Tok = Tokens.X,
    onSetup: () -> Unit = {},
    /** False while the panel animates away; it then leaves the back gesture to the screen. */
    active: Boolean = true,
) {
    val backProgress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) { if (active) backProgress.floatValue = 0f }
    PredictiveBackHandler(enabled = active) { progress ->
        try {
            progress.collect { backProgress.floatValue = it.progress }
            onCancel()
        } catch (e: CancellationException) {
            backProgress.floatValue = 0f
            throw e
        }
    }
    Column(
        modifier.graphicsLayer {
            val p = backProgress.floatValue
            translationY = p * size.height * 0.15f
            scaleX = 1f - p * 0.08f
            scaleY = 1f - p * 0.08f
            alpha = 1f - p * 0.4f
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(start = 14.dp, end = 4.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Close editor") }
                }
                MathInputField(input, Modifier.fillMaxWidth().heightIn(min = 40.dp), fontSize = 24.sp)
            }
        }
        CalcKeypad(
            vm = vm,
            input = input,
            xVar = xVar,
            onCommand = { if (it == Cmd.EXE || it == Cmd.EXE_DECIMAL) onDone() },
            onSetup = onSetup,
            shape = shape,
            modifier = Modifier.weight(1f),
        )
    }
}
