package com.l0gic0l.casiocalc.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.keypad.Cmd
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.keypad.KeyAction
import com.l0gic0l.casiocalc.ui.keypad.Keypad

/**
 * The keypad plus everything its menu keys open (OPTN, VARS, matrix and list pickers).
 * [handle] applies an action and returns any command it did not consume; those go to [onCommand].
 */
@Composable
fun CalcKeypad(
    vm: CalcViewModel,
    input: InputState,
    onCommand: (Cmd) -> Unit,
    modifier: Modifier = Modifier,
    xVar: Tok = Tokens.X,
    onSetup: () -> Unit = {},
    functionKeyHeight: Dp = 44.dp,
    numberKeyHeight: Dp = 52.dp,
    handle: (KeyAction) -> Cmd? = { a -> if (input.apply(a, xVar)) null else (a as? KeyAction.Command)?.cmd },
) {
    var sheet by remember { mutableStateOf<Cmd?>(null) }

    fun dispatch(action: KeyAction) {
        val cmd = handle(action) ?: return
        when (cmd) {
            Cmd.OPTN, Cmd.VARS, Cmd.MAT_PICK, Cmd.LIST_PICK, Cmd.MAT_TEMPLATE -> sheet = cmd
            Cmd.SETUP -> onSetup()
            else -> onCommand(cmd)
        }
    }

    Keypad(
        state = input,
        onAction = ::dispatch,
        modifier = modifier,
        functionKeyHeight = functionKeyHeight,
        numberKeyHeight = numberKeyHeight,
        haptics = vm.prefs.haptics,
    )

    val insertTok: (Tok) -> Unit = { dispatch(KeyAction.Insert(it)) }
    when (sheet) {
        Cmd.OPTN -> OptnSheet(onDismiss = { if (sheet == Cmd.OPTN) sheet = null }) { action ->
            sheet = null
            dispatch(action)
        }
        Cmd.VARS -> VarsSheet(vm.memory, vm.formatter(), onDismiss = { sheet = null }, onPick = insertTok)
        Cmd.MAT_PICK -> MatrixPickerDialog(vm.memory, onDismiss = { sheet = null }, onPick = insertTok)
        Cmd.LIST_PICK -> ListPickerDialog(vm.memory, onDismiss = { sheet = null }, onPick = insertTok)
        Cmd.MAT_TEMPLATE -> MatrixSizeDialog(onDismiss = { sheet = null }) { r, c ->
            input.edit { insertMatrix(r, c) }
        }
        else -> {}
    }
}
