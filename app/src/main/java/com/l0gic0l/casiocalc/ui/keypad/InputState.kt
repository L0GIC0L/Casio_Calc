package com.l0gic0l.casiocalc.ui.keypad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.l0gic0l.casiocalc.math.editor.MathEditor
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Tok

/** Compose-observable wrapper around a [MathEditor] plus the SHIFT/ALPHA modifier state. */
class InputState(initial: MathRow = MathRow()) {
    val editor = MathEditor(initial.copy()).also { it.moveToEnd() }

    /** Bumped on every edit so views re-layout the mutable tree. */
    var revision by mutableIntStateOf(0)
        private set

    var shift by mutableStateOf(false)
    var alpha by mutableStateOf(false)
    var alphaLock by mutableStateOf(false)

    val row: MathRow get() = editor.root
    val cursor: Pair<MathRow, Int> get() = editor.row to editor.index
    val isEmpty get() = editor.isEmpty

    fun edit(block: MathEditor.() -> Unit) {
        editor.block()
        revision++
    }

    fun load(row: MathRow) = edit { load(row) }
    fun clear() = edit { clear() }

    /**
     * Applies editing actions (insertions, cursor moves, DEL, AC). Returns false for commands the
     * screen must handle itself, such as EXE or OPTN.
     */
    fun apply(action: KeyAction, xVar: Tok): Boolean {
        when (action) {
            is KeyAction.Insert -> edit { insertAll(action.toks) }
            is KeyAction.InsertTemplate -> edit { insertTemplate(action.kind, *action.prefill.toTypedArray()) }
            is KeyAction.Sequence -> {
                action.actions.forEach { apply(it, xVar) }
            }
            KeyAction.XVar -> edit { insert(xVar) }
            is KeyAction.Command -> when (action.cmd) {
                Cmd.LEFT -> edit { left() }
                Cmd.RIGHT -> edit { right() }
                Cmd.HOME -> edit { moveToStart() }
                Cmd.END -> edit { moveToEnd() }
                Cmd.DEL -> edit { backspace() }
                Cmd.AC -> clear()
                Cmd.UP -> {
                    var moved = false
                    edit { moved = up() }
                    return moved
                }
                Cmd.DOWN -> {
                    var moved = false
                    edit { moved = down() }
                    return moved
                }
                else -> return false
            }
        }
        return true
    }
}
