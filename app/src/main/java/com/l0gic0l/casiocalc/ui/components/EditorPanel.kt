package com.l0gic0l.casiocalc.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.keypad.Cmd
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.math.MathInputField

/**
 * Bottom editing area used by the table-like modes: a titled natural-display input line above the
 * keypad. EXE calls [onDone]; the close button calls [onCancel].
 */
@Composable
fun EditorPanel(
    vm: CalcViewModel,
    title: String,
    input: InputState,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    keyHeights: Pair<Dp, Dp>,
    error: String? = null,
    xVar: Tok = Tokens.X,
    onSetup: () -> Unit = {},
) {
    BackHandler(onBack = onCancel)
    Column {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    if (error != null) {
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Close editor") }
                }
                MathInputField(input, Modifier.fillMaxWidth().heightIn(min = 44.dp), fontSize = 24.sp)
            }
        }
        CalcKeypad(
            vm = vm,
            input = input,
            xVar = xVar,
            onCommand = { if (it == Cmd.EXE || it == Cmd.EXE_DECIMAL) onDone() },
            onSetup = onSetup,
            functionKeyHeight = keyHeights.first,
            numberKeyHeight = keyHeights.second,
        )
    }
}
