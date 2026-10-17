package com.l0gic0l.casiocalc.ui.equation

import com.l0gic0l.casiocalc.ui.components.appBarInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.Calculator
import com.l0gic0l.casiocalc.math.ErrorKind
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.eval.Evaluator
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.numeric.Equations
import com.l0gic0l.casiocalc.math.numeric.Numeric
import com.l0gic0l.casiocalc.math.parse.Expr
import com.l0gic0l.casiocalc.math.parse.Parser
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.components.EditorPanel
import com.l0gic0l.casiocalc.ui.components.HeaderCell
import com.l0gic0l.casiocalc.ui.components.Stepper
import com.l0gic0l.casiocalc.ui.components.ValueCell
import com.l0gic0l.casiocalc.ui.adaptive.EditorLayout
import com.l0gic0l.casiocalc.ui.adaptive.rememberEditSession
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.math.MathInputField
import com.l0gic0l.casiocalc.ui.math.MathView
import com.l0gic0l.casiocalc.ui.math.ScrollingMath

private sealed class Target {
    data class Sim(val index: Int) : Target()
    data class Poly(val index: Int) : Target()
    data object Equation : Target()
    data class Variable(val name: String) : Target()
}

/** Results shown under the inputs: labelled values or a message. */
private class Outcome(val rows: List<Pair<String, Value>> = emptyList(), val message: String? = null)

private val subscripts = "₀₁₂₃₄₅₆₇₈₉"
private fun sub(n: Int) = n.toString().map { subscripts[it - '0'] }.joinToString("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquationScreen(vm: CalcViewModel, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val session = rememberEditSession<Target>()
    val target = session.current
    var error by remember { mutableStateOf<String?>(null) }
    var outcome by remember(tab) { mutableStateOf<Outcome?>(null) }
    val formatter = vm.formatter()

    val input = remember(session.shown) {
        val initial = when (val t = session.shown) {
            is Target.Sim -> formatter.format(vm.simCoefficients[t.index])
            is Target.Poly -> formatter.format(vm.polyCoefficients[t.index])
            Target.Equation -> vm.solverEquation
            is Target.Variable -> formatter.format(vm.memory.getVar(t.name))
            null -> MathRow()
        }
        InputState(initial)
    }

    fun commit() {
        val t = target ?: return
        if (t == Target.Equation) {
            vm.solverEquation = input.row.copy()
            vm.scheduleSave()
            session.close()
            outcome = null
            return
        }
        val result = vm.evaluateCell(input.row)
        val v = result.getOrNull()
        if (v !is Value.Num) {
            error = result.exceptionOrNull()?.let { CalcViewModel.errorMessage(it) } ?: ErrorKind.DIMENSION.message
            return
        }
        error = null
        outcome = null
        when (t) {
            is Target.Sim -> {
                vm.simCoefficients[t.index] = v
                val n = vm.simUnknowns
                val r = t.index / 7
                val c = t.index % 7
                when {
                    c < n -> session.open(Target.Sim(r * 7 + c + 1))
                    r + 1 < n -> session.open(Target.Sim((r + 1) * 7))
                    else -> session.close()
                }
            }
            is Target.Poly -> {
                vm.polyCoefficients[t.index] = v
                if (t.index < vm.polyDegree) session.open(Target.Poly(t.index + 1)) else session.close()
            }
            is Target.Variable -> {
                vm.memory.setVar(t.name, v)
                session.close()
            }
            Target.Equation -> {}
        }
        vm.scheduleSave()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = appBarInsets,
                title = { Text("Equation") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        EditorLayout(
            editing = target != null,
            modifier = Modifier.padding(padding),
            content = { m ->
                Column(m) {
                    PrimaryTabRow(selectedTabIndex = tab) {
                        listOf(
                            Triple("Simultaneous", Icons.Default.GridView, 0),
                            Triple("Polynomial", Icons.Default.Functions, 1),
                            Triple("Solver", Icons.Default.Calculate, 2),
                        ).forEach { (title, icon, i) ->
                            Tab(
                                selected = tab == i,
                                onClick = { tab = i; session.close() },
                                text = { Text(title, maxLines = 1) },
                                icon = { Icon(icon, contentDescription = null) },
                            )
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val select: (Target) -> Unit = { session.open(it); error = null }
                        when (tab) {
                            0 -> Simultaneous(vm, target, onSelect = select, onSolve = { outcome = solveSimultaneous(vm) })
                            1 -> Polynomial(vm, target, onSelect = select, onSolve = { outcome = solvePolynomial(vm) })
                            else -> Solver(vm, target, onSelect = select, onSolve = { name -> outcome = solveEquation(vm, name) })
                        }
                        outcome?.let { o ->
                            // Each new result fades in.
                            key(o) {
                                val appear = remember { MutableTransitionState(false).apply { targetState = true } }
                                AnimatedVisibility(appear, enter = fadeIn() + expandVertically()) { ResultCard(o, vm) }
                            }
                        }
                    }
                }
            },
            editor = { shape, m ->
                session.shown?.let { t ->
                    val title = when (t) {
                        is Target.Sim -> "Row ${t.index / 7 + 1}, " + if (t.index % 7 == vm.simUnknowns) "b" else "a${sub(t.index % 7 + 1)}"
                        is Target.Poly -> "Coefficient ${'a' + t.index}"
                        Target.Equation -> "Equation (use = between the sides)"
                        is Target.Variable -> "Value of ${if (t.name == "theta") "θ" else t.name}"
                    }
                    EditorPanel(vm, title, input, onDone = ::commit, onCancel = { session.close(); error = null }, shape = shape, modifier = m, error = error)
                }
            },
        )
    }
}

@Composable
private fun Simultaneous(vm: CalcViewModel, target: Target?, onSelect: (Target) -> Unit, onSolve: () -> Unit) {
    val n = vm.simUnknowns
    val formatter = vm.formatter()
    Stepper("Unknowns", n, 2..6) { vm.simUnknowns = it; vm.scheduleSave() }
    Text(
        "a${sub(1)}x${sub(1)} + … + a${sub(n)}x${sub(n)} = b",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            for (c in 0 until n) HeaderCell("a${sub(c + 1)}", 84.dp)
            HeaderCell("b", 84.dp)
        }
        for (r in 0 until n) {
            Row {
                for (c in 0..n) {
                    val index = r * 7 + c
                    ValueCell(vm.simCoefficients[index], formatter, target == Target.Sim(index), 84.dp, description = "Row ${r + 1} " + if (c == n) "b" else "a${c + 1}", onClick = { onSelect(Target.Sim(index)) })
                }
            }
        }
    }
    Button(onClick = onSolve) { Text("Solve") }
}

@Composable
private fun Polynomial(vm: CalcViewModel, target: Target?, onSelect: (Target) -> Unit, onSolve: () -> Unit) {
    val d = vm.polyDegree
    val formatter = vm.formatter()
    Stepper("Degree", d, 2..6) { vm.polyDegree = it; vm.scheduleSave() }
    val names = (0..d).map { 'a' + it }
    Text(
        (0..d).joinToString(" + ") { i ->
            val p = d - i
            "${names[i]}" + when (p) {
                0 -> ""
                1 -> "x"
                else -> "x" + p.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")
            }
        } + " = 0",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row { for (i in 0..d) HeaderCell("${names[i]}", 84.dp) }
        Row {
            for (i in 0..d) ValueCell(vm.polyCoefficients[i], formatter, target == Target.Poly(i), 84.dp, description = "Coefficient ${'a' + i}", onClick = { onSelect(Target.Poly(i)) })
        }
    }
    Button(onClick = onSolve) { Text("Solve") }
}

@Composable
private fun Solver(vm: CalcViewModel, target: Target?, onSelect: (Target) -> Unit, onSolve: (String) -> Unit) {
    val eq = vm.solverEquation
    val variables = remember(eq) { variablesIn(eq) }
    var solveFor by rememberSaveable(variables) { mutableStateOf(variables.firstOrNull { it == "X" } ?: variables.firstOrNull() ?: "X") }
    @Suppress("UNUSED_VARIABLE") val version = vm.memory.version
    Surface(
        onClick = { onSelect(Target.Equation) },
        shape = RoundedCornerShape(16.dp),
        color = if (target == Target.Equation) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Equation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (eq.isEmpty) Text("Tap to enter an equation") else ScrollingMath(eq, fontSize = 24.sp)
        }
    }
    if (variables.isNotEmpty()) {
        Text("Solve for (other variables use their current values):", style = MaterialTheme.typography.bodyMedium)
        for (name in variables) {
            Row(
                Modifier.fillMaxWidth().selectable(selected = solveFor == name, onClick = { solveFor = name }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = solveFor == name, onClick = { solveFor = name })
                Text(if (name == "theta") "θ" else name, fontSize = 18.sp, modifier = Modifier.width(36.dp))
                ValueCell(vm.memory.getVar(name), vm.formatter(), target == Target.Variable(name), 140.dp, description = "Value of $name", onClick = { onSelect(Target.Variable(name)) })
            }
        }
        Text(
            "The value of the selected variable is the starting guess.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(onClick = { onSolve(solveFor) }, enabled = !eq.isEmpty) { Text("Solve") }
}

@Composable
private fun ResultCard(outcome: Outcome, vm: CalcViewModel) {
    val formatter = vm.formatter()
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            outcome.message?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            for ((label, value) in outcome.rows) {
                var decimal by remember(value) { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$label =", fontSize = 18.sp, modifier = Modifier.width(64.dp))
                    val row = remember(value, decimal, formatter) {
                        formatter.format(value, if (decimal) com.l0gic0l.casiocalc.math.format.Formatter.Style.DECIMAL else com.l0gic0l.casiocalc.math.format.Formatter.Style.AUTO)
                    }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) { MathView(row, fontSize = 24.sp) }
                    if (formatter.hasAlternateForm(value)) {
                        OutlinedButton(onClick = { decimal = !decimal }) { Text("S⇔D") }
                    }
                }
            }
        }
    }
}

private fun solveSimultaneous(vm: CalcViewModel): Outcome {
    val n = vm.simUnknowns
    val mat = Value.Mat.build(n, n + 1) { r, c -> (vm.simCoefficients[r * 7 + c] as? Value.Num)?.z ?: Complex.ZERO }
    return try {
        when (val r = Equations.solveLinear(mat)) {
            is Equations.LinearResult.Unique -> Outcome(r.values.mapIndexed { i, z -> "x${sub(i + 1)}" to Value.Num(z) })
            Equations.LinearResult.Infinite -> Outcome(message = "Infinitely many solutions")
            Equations.LinearResult.NoSolution -> Outcome(message = "No solution")
        }
    } catch (e: Exception) {
        Outcome(message = CalcViewModel.errorMessage(e))
    }
}

private fun solvePolynomial(vm: CalcViewModel): Outcome {
    val d = vm.polyDegree
    val coefficients = (0..d).map { (vm.polyCoefficients[it] as? Value.Num)?.z ?: Complex.ZERO }
    if (coefficients.any { !it.isReal }) return Outcome(message = "Coefficients must be real")
    if (coefficients[0].re.isZero) return Outcome(message = "Coefficient a must not be 0")
    return try {
        val roots = Equations.polynomialRoots(coefficients.map { it.re })
        Outcome(roots.mapIndexed { i, z -> "x${sub(i + 1)}" to Value.Num(z) })
    } catch (e: Exception) {
        Outcome(message = CalcViewModel.errorMessage(e))
    }
}

private fun variablesIn(row: MathRow): List<String> = runCatching {
    val names = LinkedHashSet<String>()
    Parser.parse(row).walk { if (it is Expr.Var) names += it.name }
    names.toList()
}.getOrDefault(emptyList())

private fun solveEquation(vm: CalcViewModel, name: String): Outcome {
    return try {
        val expr = Parser.parse(vm.solverEquation)
        val (lhs, rhs) = if (expr is Expr.Binary && expr.op == "=") expr.l to expr.r else expr to Expr.Num(Real.ZERO)
        val ev: Evaluator = Calculator.evaluator(expr, vm.memory, vm.settings)
        val guess = (vm.memory.getVar(name) as? Value.Num)?.z?.re?.toDouble() ?: 0.0
        val f = { x: Double -> ev.evalReal(lhs, name, x) - ev.evalReal(rhs, name, x) }
        val root = Numeric.solve(f, guess)
        val value = Value.of(Real.Approx(root))
        vm.memory.setVar(name, value)
        val left = Value.of(Real.Approx(ev.evalReal(lhs, name, root)))
        val right = Value.of(Real.Approx(ev.evalReal(rhs, name, root)))
        Outcome(listOf((if (name == "theta") "θ" else name) to value, "Lft" to left, "Rgt" to right))
    } catch (e: CalcException) {
        Outcome(message = e.kind.message)
    } catch (e: Exception) {
        Outcome(message = CalcViewModel.errorMessage(e))
    }
}
