package com.l0gic0l.casiocalc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.l0gic0l.casiocalc.math.Calculator
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.num.Value
import java.util.Locale

/**
 * Evaluates short typed text such as "-2π", "1/3" or "2.5E3" for dialogs that take a number.
 * Returns null when the text is not a real number.
 */
fun parseNumberText(text: String, settings: Settings = Settings()): Double? {
    val nodes = ArrayList<Node>()
    var i = 0
    val t = text.trim().replace("pi", "π")
    if (t.isEmpty()) return null
    while (i < t.length) {
        val c = t[i]
        val tok = when {
            c.isDigit() -> Tokens.digit(c - '0')
            c == '.' -> Tokens.POINT
            c == '-' || c == '−' -> Tokens.SUB
            c == '+' -> Tokens.ADD
            c == '*' || c == '×' -> Tokens.MUL
            c == '/' || c == '÷' -> Tokens.DIV
            c == '(' -> Tokens.LPAREN
            c == ')' -> Tokens.RPAREN
            c == 'π' -> Tokens.PI
            (c == 'E' || c == 'e') && i > 0 && (t[i - 1].isDigit() || t[i - 1] == '.') -> Tokens.EXP10
            c == 'e' -> Tokens.E
            c == ' ' -> null
            else -> return null
        }
        if (tok != null) nodes += TokenNode(tok)
        i++
    }
    return runCatching {
        val v = Calculator.evaluate(MathRow(nodes), Memory(), settings).value
        val z = (v as Value.Num).z
        if (!z.isReal) null else z.re.toDouble()
    }.getOrNull()
}

fun formatNumberText(v: Double): String {
    if (v == Math.rint(v) && kotlin.math.abs(v) < 1e12) return v.toLong().toString()
    return "%.10g".format(Locale.US, v).let { s -> if (s.contains('e')) s else s.trimEnd('0').trimEnd('.') }
}

/** Dialog with one text field per label; confirms only when every field is a number. */
@Composable
fun NumberFieldsDialog(
    title: String,
    labels: List<String>,
    initial: List<Double?>,
    onDismiss: () -> Unit,
    onConfirm: (List<Double>) -> Unit,
) {
    val texts = remember { mutableStateListOf(*initial.map { it?.let(::formatNumberText) ?: "" }.toTypedArray()) }
    val parsed = texts.map { parseNumberText(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = texts[i],
                        onValueChange = { texts[i] = it },
                        label = { Text(label) },
                        singleLine = true,
                        isError = texts[i].isNotEmpty() && parsed[i] == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed.all { it != null }, onClick = {
                onConfirm(parsed.map { it!! })
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
