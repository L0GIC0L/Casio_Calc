package com.l0gic0l.casiocalc.ui.recursion

import com.l0gic0l.casiocalc.ui.components.appBarInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.Calculator
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.parse.Expr
import com.l0gic0l.casiocalc.math.parse.Parser
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.EditorPanel
import com.l0gic0l.casiocalc.ui.components.NumberFieldsDialog
import com.l0gic0l.casiocalc.ui.adaptive.EditorLayout
import com.l0gic0l.casiocalc.ui.adaptive.rememberEditSession
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.math.MathView

/** One sequence: explicit aₙ = f(n) or recursive aₙ₊₁ = f(n, aₙ, bₙ) with a start value. */
private data class Sequence(val recursive: Boolean, val expr: MathRow, val first: MathRow)

private enum class Field { A_EXPR, A_FIRST, B_EXPR, B_FIRST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecursionScreen(vm: CalcViewModel, onBack: () -> Unit) {
    var a by remember { mutableStateOf(Sequence(true, MathRow(), MathRow())) }
    var b by remember { mutableStateOf(Sequence(true, MathRow(), MathRow())) }
    var start by remember { mutableIntStateOf(1) }
    var end by remember { mutableIntStateOf(10) }
    val session = rememberEditSession<Field>()
    val editing = session.current
    var rangeDialog by remember { mutableStateOf(false) }
    var table by remember { mutableStateOf<List<Triple<Int, Value?, Value?>>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val formatter = vm.formatter()

    val input = remember(session.shown) {
        InputState(
            when (session.shown) {
                Field.A_EXPR -> a.expr
                Field.A_FIRST -> a.first
                Field.B_EXPR -> b.expr
                Field.B_FIRST -> b.first
                null -> MathRow()
            },
        )
    }

    fun commit() {
        val row = input.row.copy()
        if (!row.isEmpty) {
            try {
                Parser.parse(row)
            } catch (e: CalcException) {
                error = e.kind.message
                return
            }
        }
        when (session.current) {
            Field.A_EXPR -> a = a.copy(expr = row)
            Field.A_FIRST -> a = a.copy(first = row)
            Field.B_EXPR -> b = b.copy(expr = row)
            Field.B_FIRST -> b = b.copy(first = row)
            null -> {}
        }
        error = null
        session.close()
        table = null
    }

    fun compute() {
        try {
            val exprA = if (a.expr.isEmpty) null else Parser.parse(a.expr)
            val exprB = if (b.expr.isEmpty) null else Parser.parse(b.expr)
            if (exprA == null && exprB == null) {
                error = "Enter aₙ or bₙ first"
                return
            }
            val all = listOfNotNull(exprA, exprB).fold<Expr, Expr>(Expr.Num(com.l0gic0l.casiocalc.math.num.Real.ZERO)) { acc, e -> Expr.Binary("+", acc, e) }
            val ev = Calculator.evaluator(all, vm.memory, vm.settings)
            fun firstValue(row: MathRow): Value? = if (row.isEmpty) null else ev.eval(Parser.parse(row))
            var an: Value? = if (a.recursive) firstValue(a.first) ?: Value.of(0) else null
            var bn: Value? = if (b.recursive) firstValue(b.first) ?: Value.of(0) else null
            val rows = ArrayList<Triple<Int, Value?, Value?>>()
            for (n in start..end) {
                val nv = Value.of(n.toLong())
                fun explicit(e: Expr?, an: Value?, bn: Value?) = e?.let {
                    ev.withLocal("seqN", nv) { ev.withLocal("seqA", an ?: Value.of(0)) { ev.withLocal("seqB", bn ?: Value.of(0)) { ev.eval(it) } } }
                }
                val curA = if (exprA == null) null else if (a.recursive) an else explicit(exprA, null, null)
                val curB = if (exprB == null) null else if (b.recursive) bn else explicit(exprB, curA, null)
                rows += Triple(n, curA, curB)
                // Advance the recursive sequences to n + 1.
                val nextA = if (exprA != null && a.recursive) explicit(exprA, curA, curB) else null
                val nextB = if (exprB != null && b.recursive) explicit(exprB, curA, curB) else null
                an = nextA
                bn = nextB
            }
            table = rows
            error = null
        } catch (e: Exception) {
            error = CalcViewModel.errorMessage(e)
            table = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = appBarInsets,
                title = { Text("Recursion") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        EditorLayout(
            editing = editing != null,
            modifier = Modifier.padding(padding),
            content = { m ->
                Column(m.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SequenceCard("a", a, editing, onType = { a = a.copy(recursive = it); table = null }, onEdit = { session.open(it) }, exprField = Field.A_EXPR, firstField = Field.A_FIRST, start = start)
                    SequenceCard("b", b, editing, onType = { b = b.copy(recursive = it); table = null }, onEdit = { session.open(it) }, exprField = Field.B_EXPR, firstField = Field.B_FIRST, start = start)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { rangeDialog = true }, label = { Text("n = $start … $end") })
                        Button(onClick = ::compute) { Text("Table") }
                    }
                    if (editing == null && error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                    table?.let { rows -> RecursionTable(rows, formatter, a.expr.isEmpty.not(), b.expr.isEmpty.not()) }
                }
            },
            editor = { shape, m ->
                session.shown?.let { field ->
                    Column(m) {
                        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(Tokens.SEQ_N, Tokens.SEQ_A, Tokens.SEQ_B).forEach { tok ->
                                AssistChip(onClick = { input.edit { insert(tok) } }, label = { MathView(MathRow(listOf(com.l0gic0l.casiocalc.math.editor.TokenNode(tok))), fontSize = 16.sp) })
                            }
                        }
                        val title = when (field) {
                            Field.A_EXPR -> if (a.recursive) "aₙ₊₁ =" else "aₙ ="
                            Field.B_EXPR -> if (b.recursive) "bₙ₊₁ =" else "bₙ ="
                            Field.A_FIRST -> "a at n = $start"
                            Field.B_FIRST -> "b at n = $start"
                        }
                        EditorPanel(
                            vm, title, input, onDone = ::commit, onCancel = { session.close(); error = null },
                            shape = shape, modifier = Modifier.weight(1f), error = error, xVar = Tokens.SEQ_N,
                        )
                    }
                }
            },
        )
    }
    if (rangeDialog) {
        NumberFieldsDialog("Range of n", listOf("Start", "End"), listOf(start.toDouble(), end.toDouble()), onDismiss = { rangeDialog = false }) { v ->
            val s = v[0].toInt()
            val e = v[1].toInt()
            if (e >= s && e - s <= 199) {
                start = s; end = e; table = null
            }
        }
    }
}

@Composable
private fun SequenceCard(
    name: String,
    seq: Sequence,
    editing: Field?,
    onType: (Boolean) -> Unit,
    onEdit: (Field) -> Unit,
    exprField: Field,
    firstField: Field,
    start: Int,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !seq.recursive, onClick = { onType(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("${name}ₙ = f(n)") }
                SegmentedButton(selected = seq.recursive, onClick = { onType(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("${name}ₙ₊₁ = f(${name}ₙ)") }
            }
            EditableLine(if (seq.recursive) "${name}ₙ₊₁ =" else "${name}ₙ =", seq.expr, editing == exprField) { onEdit(exprField) }
            if (seq.recursive) EditableLine("${name} at n=$start:", seq.first, editing == firstField) { onEdit(firstField) }
        }
    }
}

@Composable
private fun EditableLine(label: String, row: MathRow, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            if (row.isEmpty) Text("tap to enter", color = MaterialTheme.colorScheme.outline) else MathView(row, fontSize = 20.sp)
        }
    }
}

@Composable
private fun RecursionTable(rows: List<Triple<Int, Value?, Value?>>, formatter: com.l0gic0l.casiocalc.math.format.Formatter, showA: Boolean, showB: Boolean) {
    Column {
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("n", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
            if (showA) MathView(MathRow(listOf(com.l0gic0l.casiocalc.math.editor.TokenNode(Tokens.SEQ_A))), fontSize = 16.sp, modifier = Modifier.width(150.dp))
            if (showB) MathView(MathRow(listOf(com.l0gic0l.casiocalc.math.editor.TokenNode(Tokens.SEQ_B))), fontSize = 16.sp)
        }
        HorizontalDivider()
        for ((n, av, bv) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                Text("$n", modifier = Modifier.width(56.dp), color = MaterialTheme.colorScheme.primary)
                if (showA) Row(Modifier.width(150.dp).horizontalScroll(rememberScrollState())) { av?.let { MathView(formatter.format(it), fontSize = 18.sp) } }
                if (showB) Row(Modifier.horizontalScroll(rememberScrollState())) { bv?.let { MathView(formatter.format(it), fontSize = 18.sp) } }
            }
        }
    }
}
