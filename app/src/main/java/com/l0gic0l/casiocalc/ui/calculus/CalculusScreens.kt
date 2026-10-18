package com.l0gic0l.casiocalc.ui.calculus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.cas.Calculus
import com.l0gic0l.casiocalc.math.cas.DerivativeSolution
import com.l0gic0l.casiocalc.math.cas.IntegralSolution
import com.l0gic0l.casiocalc.math.cas.Render
import com.l0gic0l.casiocalc.math.cas.Sym
import com.l0gic0l.casiocalc.math.cas.add
import com.l0gic0l.casiocalc.math.cas.div
import com.l0gic0l.casiocalc.math.cas.exp
import com.l0gic0l.casiocalc.math.cas.fn
import com.l0gic0l.casiocalc.math.cas.mul
import com.l0gic0l.casiocalc.math.cas.num
import com.l0gic0l.casiocalc.math.cas.pow
import com.l0gic0l.casiocalc.math.cas.sqrt
import com.l0gic0l.casiocalc.math.cas.sub
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.format.RowText
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.ui.CalcViewModel
import com.l0gic0l.casiocalc.ui.adaptive.EditorLayout
import com.l0gic0l.casiocalc.ui.adaptive.rememberEditSession
import com.l0gic0l.casiocalc.ui.components.EditorPanel
import com.l0gic0l.casiocalc.ui.components.appBarInsets
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.math.ScrollingMath

private enum class Field { FUNCTION, LOWER, UPPER, AT }

/** The x in dx and d/dx, drawn lower case like the calculator's own ∫ template. */
private val xRow get() = MathRow(listOf(TokenNode(Render.label("x"))))

private const val NOTE = "Angles are in radians. Letters other than X are treated as constants."

private val X = Sym.Var(Calculus.X)

/** A typed sum between ∫ and dx is bracketed, as the steps are: ∫(X + 1)dx. */
private fun integrandRow(row: MathRow): MathRow {
    val sum = row.nodes.drop(1).any { it is TokenNode && (it.tok == Tokens.ADD || it.tok == Tokens.SUB) }
    val nodes = row.copy().nodes
    return MathRow(if (sum) listOf(TokenNode(Tokens.LPAREN)) + nodes + TokenNode(Tokens.RPAREN) else nodes)
}

/** Worked examples, one per method, written with the solver's own renderer. */
private val integralExamples: List<MathRow> by lazy {
    val r = Render()
    listOf(
        mul(X, exp(X)),
        mul(exp(X), fn("sin", X)),
        mul(num(2), X, fn("cos", pow(X, num(2)))),
        div(add(X, num(5)), add(pow(X, num(2)), X, num(-2))),
        pow(fn("sin", X), num(2)),
        fn("ln", X),
        div(num(1), sqrt(sub(num(4), pow(X, num(2))))),
        div(num(1), add(pow(X, num(2)), mul(num(2), X), num(5))),
    ).map(r::row)
}

private val derivativeExamples: List<MathRow> by lazy {
    val r = Render()
    listOf(
        mul(pow(X, num(2)), fn("sin", X)),
        fn("sin", pow(X, num(2))),
        div(X, add(X, num(1))),
        pow(X, X),
        exp(fn("sin", X)),
        fn("ln", add(pow(X, num(2)), num(1))),
        fn("atan", mul(num(2), X)),
        sqrt(add(pow(X, num(2)), num(1))),
    ).map(r::row)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculusScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = appBarInsets,
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

/** The scrolling column the problem, result and steps sit in; centred and width-limited on big screens. */
@Composable
private fun Page(modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

@Composable
fun IntegralScreen(vm: CalcViewModel, onBack: () -> Unit) {
    val session = rememberEditSession<Field>()
    val input = remember(session.opened) {
        InputState(
            when (session.shown) {
                Field.LOWER -> vm.integralLower
                Field.UPPER -> vm.integralUpper
                else -> vm.integrand
            },
        )
    }

    fun solve() {
        session.close()
        vm.solveIntegral()
    }

    // EXE stores the box and moves on to the next empty one; with nothing left to fill in, it solves.
    fun commit() {
        val row = input.row.copy()
        val field = session.current ?: return
        when (field) {
            Field.FUNCTION -> vm.integrand = row
            Field.LOWER -> vm.integralLower = row
            Field.UPPER -> vm.integralUpper = row
            Field.AT -> {}
        }
        vm.scheduleSave()
        val next = when {
            vm.integrand.isEmpty -> null
            !vm.integralDefinite -> null
            field == Field.FUNCTION && vm.integralLower.isEmpty -> Field.LOWER
            field != Field.UPPER && vm.integralUpper.isEmpty -> Field.UPPER
            else -> null
        }
        when {
            next != null -> session.open(next)
            vm.integrand.isEmpty -> session.close()
            else -> solve()
        }
    }

    // The box being edited shows what is typed so far.
    @Suppress("UNUSED_VARIABLE") val typing = input.revision
    fun live(field: Field, saved: MathRow) = if (session.current == field) input.row.copy() else saved

    val definite = vm.integralDefinite
    val ready = !vm.integrand.isEmpty && (!definite || (!vm.integralLower.isEmpty && !vm.integralUpper.isEmpty))
    CalculusScaffold("Integral", onBack) { m ->
        EditorLayout(
            editing = session.current != null,
            modifier = m,
            content = { cm ->
                Page(cm) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Indefinite", "Definite").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = definite == (i == 1),
                                onClick = {
                                    if (definite != (i == 1)) {
                                        vm.integralDefinite = i == 1
                                        vm.clearIntegral()
                                        vm.scheduleSave()
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(label) }
                        }
                    }
                    val integrand = integrandRow(live(Field.FUNCTION, vm.integrand))
                    val lower = live(Field.LOWER, vm.integralLower)
                    val upper = live(Field.UPPER, vm.integralUpper)
                    val problem = if (definite) {
                        MathRow(listOf(Template(TemplateKind.INTEGRAL, listOf(lower.copy(), upper.copy(), integrand))))
                    } else {
                        MathRow(listOf(Template(TemplateKind.INDEF_INTEGRAL, listOf(integrand, xRow))))
                    }
                    ProblemCard(
                        "Integrate",
                        problem,
                        selected = session.current == Field.FUNCTION,
                        description = if (vm.integrand.isEmpty) "empty" else RowText.linear(vm.integrand),
                        onClick = { session.open(Field.FUNCTION) },
                    )
                    AnimatedVisibility(definite) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SmallInput("Lower limit a", lower, session.current == Field.LOWER, Modifier.weight(1f)) { session.open(Field.LOWER) }
                            SmallInput("Upper limit b", upper, session.current == Field.UPPER, Modifier.weight(1f)) { session.open(Field.UPPER) }
                        }
                    }
                    Actions(
                        canSolve = ready,
                        solving = vm.solving,
                        examples = integralExamples,
                        onSolve = ::solve,
                        onClear = {
                            session.close()
                            vm.integrand = MathRow()
                            vm.integralLower = MathRow()
                            vm.integralUpper = MathRow()
                            vm.clearIntegral()
                            vm.scheduleSave()
                        },
                        onExample = { e ->
                            vm.integrand = e.copy()
                            if (definite && vm.integralLower.isEmpty) vm.integralLower = MathRow(listOf(TokenNode(Tokens.digit(0))))
                            if (definite && vm.integralUpper.isEmpty) vm.integralUpper = MathRow(listOf(TokenNode(Tokens.digit(1))))
                            solve()
                        },
                    )
                    vm.integralError?.let { ErrorCard(it) }
                    vm.integralSolution?.let { s ->
                        Appearing(s) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                IntegralResult(s, vm)
                                StepsCard(s.steps)
                            }
                        }
                    }
                    Footnote(NOTE)
                }
            },
            editor = { shape, em ->
                session.shown?.let { f ->
                    val title = when (f) {
                        Field.LOWER -> "Lower limit a"
                        Field.UPPER -> "Upper limit b"
                        else -> "Integrand f(X)"
                    }
                    EditorPanel(vm, title, input, onDone = ::commit, onCancel = { session.close() }, shape = shape, modifier = em, active = session.current != null)
                }
            },
        )
    }
}

@Composable
private fun IntegralResult(s: IntegralSolution, vm: CalcViewModel) {
    val r = remember { Render(Calculus.X) }
    val formatter = vm.formatter()
    ResultCard {
        val anti = s.antiderivative
        if (anti != null) {
            ScrollingMath(remember(s) { r.equation(r.integral(s.integrand), r.plusC(r.row(anti))) }, fontSize = 24.sp)
            if (s.verified) {
                CheckNote(true, "Checked: differentiating the answer gives back the integrand.")
            } else {
                CheckNote(false, "This answer could not be checked numerically.")
            }
        } else {
            Text(
                "No antiderivative was found in terms of elementary functions.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        val d = s.definite ?: return@ResultCard
        HorizontalDivider()
        val left = remember(s) { r.definite(s.integrand, r.row(d.lower), r.row(d.upper)) }
        val eq = MathRow(left.nodes.map { it.copy() } + TokenNode(Tokens.EQ))
        when {
            !d.numericOnly && d.value != null -> {
                ExactValue(eq, d.symbolic, d.value, formatter)
                if (d.numeric != null) CheckNote(true, "F(b) − F(a) agrees with numerical integration.")
            }
            d.numeric != null -> {
                ValueLine(eq, Value.of(Real.Approx(d.numeric)), formatter)
                CheckNote(
                    false,
                    if (anti != null) "F(b) − F(a) does not apply across this interval, so the value was found numerically."
                    else "Found numerically.",
                )
            }
            d.symbolic != null -> ScrollingMath(remember(s) { r.equation(left, r.row(d.symbolic)) }, fontSize = 24.sp)
            else -> CheckNote(false, "The integral has no value on this interval.")
        }
    }
}

@Composable
fun DerivativeScreen(vm: CalcViewModel, onBack: () -> Unit) {
    val session = rememberEditSession<Field>()
    val input = remember(session.opened) { InputState(if (session.shown == Field.AT) vm.derivAt else vm.derivFunction) }

    fun solve() {
        session.close()
        vm.solveDerivative()
    }

    fun commit() {
        val field = session.current ?: return
        val row = input.row.copy()
        if (field == Field.AT) vm.derivAt = row else vm.derivFunction = row
        vm.scheduleSave()
        when {
            vm.derivFunction.isEmpty -> session.close()
            field == Field.FUNCTION && vm.derivAtEnabled && vm.derivAt.isEmpty -> session.open(Field.AT)
            else -> solve()
        }
    }

    @Suppress("UNUSED_VARIABLE") val typing = input.revision
    fun live(field: Field, saved: MathRow) = if (session.current == field) input.row.copy() else saved

    val ready = !vm.derivFunction.isEmpty && (!vm.derivAtEnabled || !vm.derivAt.isEmpty)
    CalculusScaffold("Derivative", onBack) { m ->
        EditorLayout(
            editing = session.current != null,
            modifier = m,
            content = { cm ->
                Page(cm) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("d/dx", "d²/dx²", "d³/dx³").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = vm.derivOrder == i + 1,
                                onClick = {
                                    vm.derivOrder = i + 1
                                    vm.scheduleSave()
                                    if (vm.derivativeSolution != null) vm.solveDerivative()
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(label) }
                        }
                    }
                    val order = vm.derivOrder
                    val problem = MathRow(
                        listOf(
                            Template(
                                TemplateKind.DIFF,
                                listOf(live(Field.FUNCTION, vm.derivFunction).copy(), xRow, MathRow(if (order > 1) listOf(TokenNode(Tokens.digit(order))) else emptyList())),
                            ),
                        ),
                    )
                    ProblemCard(
                        "Differentiate",
                        problem,
                        selected = session.current == Field.FUNCTION,
                        description = if (vm.derivFunction.isEmpty) "empty" else RowText.linear(vm.derivFunction),
                        onClick = { session.open(Field.FUNCTION) },
                    )
                    // The whole row toggles, so the label is a target too.
                    Row(
                        Modifier.fillMaxWidth().toggleable(vm.derivAtEnabled, role = Role.Switch) {
                            vm.derivAtEnabled = it
                            vm.clearDerivative()
                            vm.scheduleSave()
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Value at a point", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = vm.derivAtEnabled, onCheckedChange = null)
                    }
                    AnimatedVisibility(vm.derivAtEnabled) {
                        SmallInput("X =", live(Field.AT, vm.derivAt), session.current == Field.AT, Modifier.fillMaxWidth()) { session.open(Field.AT) }
                    }
                    Actions(
                        canSolve = ready,
                        solving = vm.solving,
                        examples = derivativeExamples,
                        onSolve = ::solve,
                        onClear = {
                            session.close()
                            vm.derivFunction = MathRow()
                            vm.derivAt = MathRow()
                            vm.clearDerivative()
                            vm.scheduleSave()
                        },
                        onExample = { e ->
                            vm.derivFunction = e.copy()
                            if (vm.derivAtEnabled && vm.derivAt.isEmpty) vm.derivAt = MathRow(listOf(TokenNode(Tokens.digit(1))))
                            solve()
                        },
                    )
                    vm.derivativeError?.let { ErrorCard(it) }
                    vm.derivativeSolution?.let { s ->
                        Appearing(s) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                DerivativeResult(s, vm)
                                StepsCard(s.steps)
                            }
                        }
                    }
                    Footnote(NOTE)
                }
            },
            editor = { shape, em ->
                session.shown?.let { f ->
                    val title = if (f == Field.AT) "Value of X" else "Function f(X)"
                    EditorPanel(vm, title, input, onDone = ::commit, onCancel = { session.close() }, shape = shape, modifier = em, active = session.current != null)
                }
            },
        )
    }
}

@Composable
private fun DerivativeResult(s: DerivativeSolution, vm: CalcViewModel) {
    val r = remember { Render(Calculus.X) }
    val formatter = vm.formatter()
    ResultCard {
        ScrollingMath(remember(s) { r.equation(r.derivative(s.function, Calculus.X, s.order), r.row(s.derivative)) }, fontSize = 24.sp)
        val at = s.at ?: return@ResultCard
        HorizontalDivider()
        // f′(a) =, with as many primes as the order.
        val prefix = remember(s) {
            MathRow(listOf(TokenNode(Render.label("f" + "′".repeat(s.order))), TokenNode(Tokens.LPAREN)) + r.row(at).nodes + TokenNode(Tokens.RPAREN) + TokenNode(Tokens.EQ))
        }
        val value = s.valueAt
        if (value != null) {
            ExactValue(prefix, s.symbolicAt, value, formatter)
        } else {
            ScrollingMath(prefix, fontSize = 24.sp)
            CheckNote(false, "The derivative is not defined at this point.")
        }
    }
}
