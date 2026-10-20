package com.l0gic0l.casiocalc.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.l0gic0l.casiocalc.data.AppMemory
import com.l0gic0l.casiocalc.data.Codec
import com.l0gic0l.casiocalc.data.GraphDto
import com.l0gic0l.casiocalc.data.GraphFunction
import com.l0gic0l.casiocalc.data.GraphType
import com.l0gic0l.casiocalc.data.HistoryDto
import com.l0gic0l.casiocalc.data.HistoryEntry
import com.l0gic0l.casiocalc.data.SavedState
import com.l0gic0l.casiocalc.data.StatSettings
import com.l0gic0l.casiocalc.data.TableRange
import com.l0gic0l.casiocalc.data.UiPrefs
import com.l0gic0l.casiocalc.data.ViewWindow
import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.Calculator
import com.l0gic0l.casiocalc.math.ErrorKind
import com.l0gic0l.casiocalc.math.Preview
import com.l0gic0l.casiocalc.math.parse.Parser
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.ui.keypad.Cmd
import com.l0gic0l.casiocalc.ui.keypad.InputState
import com.l0gic0l.casiocalc.ui.keypad.KeyAction
import com.l0gic0l.casiocalc.ui.keypad.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.l0gic0l.casiocalc.math.cas.Calculus
import com.l0gic0l.casiocalc.math.cas.DerivativeSolution
import com.l0gic0l.casiocalc.math.cas.IntegralSolution
import com.l0gic0l.casiocalc.math.cas.UnsupportedInput
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** App-wide state shared by every mode, like the calculator's memory, plus saving it to disk. */
class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val file = File(app.filesDir, "state.json")
    private var saveJob: Job? = null
    private var loaded = false

    var settings by mutableStateOf(Settings())
        private set
    var prefs by mutableStateOf(UiPrefs())
        private set
    val memory = AppMemory { scheduleSave() }

    // ---- Run-Matrix -------------------------------------------------------------------------
    val runInput = InputState()
    val history = mutableStateListOf<HistoryEntry>()
    var runError by mutableStateOf<String?>(null)
        private set
    var computing by mutableStateOf(false)
        private set
    private var justEvaluated = false
    private var recallIndex = -1
    @Volatile private var cancelRequested = false

    /** The value of the line being typed, for simple calculations (see [Preview]); null when there is none to show. */
    var runPreview by mutableStateOf<Value?>(null)
        private set

    // ---- Graph / Table ----------------------------------------------------------------------
    val graphFunctions = mutableStateListOf<GraphFunction>().apply {
        repeat(GRAPH_SLOTS) { add(GraphFunction(colorIndex = it % GRAPH_COLORS)) }
    }
    var viewWindow by mutableStateOf(ViewWindow())
    var tableRange by mutableStateOf(TableRange())

    // ---- Statistics / Equation --------------------------------------------------------------
    var statSettings by mutableStateOf(StatSettings())
    var lastRegression by mutableStateOf<com.l0gic0l.casiocalc.math.numeric.Stats.Regression?>(null)
    var simUnknowns by mutableIntStateOf(2)
    val simCoefficients = mutableStateListOf<Value>().apply { repeat(6 * 7) { add(Value.of(0)) } }
    var polyDegree by mutableIntStateOf(2)
    val polyCoefficients = mutableStateListOf<Value>().apply { repeat(7) { add(Value.of(0)) } }
    var solverEquation by mutableStateOf(MathRow())

    // ---- Integral / Derivative ----------------------------------------------------------------
    var integrand by mutableStateOf(MathRow())
    var integralLower by mutableStateOf(MathRow())
    var integralUpper by mutableStateOf(MathRow())
    var integralDefinite by mutableStateOf(false)
    var integralSolution by mutableStateOf<IntegralSolution?>(null)
        private set
    var integralError by mutableStateOf<String?>(null)
        private set
    var derivFunction by mutableStateOf(MathRow())
    var derivOrder by mutableIntStateOf(1)
    var derivAt by mutableStateOf(MathRow())
    var derivAtEnabled by mutableStateOf(false)
    var derivativeSolution by mutableStateOf<DerivativeSolution?>(null)
        private set
    var derivativeError by mutableStateOf<String?>(null)
        private set
    /** True while that mode's solve runs; each mode has its own, so one never blocks or cancels the other. */
    var integralSolving by mutableStateOf(false)
        private set
    var derivativeSolving by mutableStateOf(false)
        private set
    private var integralJob: Job? = null
    private var derivativeJob: Job? = null

    init {
        load()
        // Every edit, and any change to the settings or memory it reads, works the preview out again;
        // collectLatest drops the one still pending.
        viewModelScope.launch {
            snapshotFlow { listOf(runInput.revision, prefs.livePreview, settings, memory.version) }.collectLatest { refreshPreview() }
        }
    }

    private suspend fun refreshPreview() {
        val row = runInput.row.copy()
        val expr = if (prefs.livePreview && !row.isEmpty) runCatching { Parser.parse(row) }.getOrNull() else null
        if (expr == null || !Preview.worthShowing(expr)) {
            runPreview = null
            return
        }
        // The old preview stays up until the new one is ready, so it does not flicker while typing.
        val currentSettings = settings
        val deadline = System.nanoTime() + PREVIEW_LIMIT_MS * 1_000_000
        // The evaluator on its own, not Calculator.evaluate: a preview must not change Ans.
        runPreview = withContext(Dispatchers.Default) {
            runCatching { Calculator.evaluator(expr, memory, currentSettings) { System.nanoTime() > deadline }.eval(expr) }.getOrNull()
        } as? Value.Num
    }

    /** Works out the integral in the background; gives up after [SOLVE_SECONDS]. */
    fun solveIntegral() {
        val f = integrand.copy()
        if (f.isEmpty) return
        val lower = if (integralDefinite) integralLower.copy() else null
        val upper = if (integralDefinite) integralUpper.copy() else null
        integralJob?.cancel()
        integralSolving = true
        integralJob = solve(
            work = { cancelled -> Calculus.integral(f, lower, upper, memory, settings, cancelled) },
            done = { integralSolving = false; integralSolution = it; integralError = null },
            failed = { integralSolving = false; integralSolution = null; integralError = it },
        )
    }

    fun solveDerivative() {
        val f = derivFunction.copy()
        if (f.isEmpty) return
        val at = if (derivAtEnabled) derivAt.copy() else null
        val order = derivOrder
        derivativeJob?.cancel()
        derivativeSolving = true
        derivativeJob = solve(
            work = { cancelled -> Calculus.derivative(f, order, at, memory, settings, cancelled) },
            done = { derivativeSolving = false; derivativeSolution = it; derivativeError = null },
            failed = { derivativeSolving = false; derivativeSolution = null; derivativeError = it },
        )
    }

    private fun <T> solve(work: (() -> Boolean) -> T, done: (T) -> Unit, failed: (String) -> Unit): Job {
        scheduleSave()
        return viewModelScope.launch {
            val deadline = System.nanoTime() + SOLVE_SECONDS * 1_000_000_000L
            val result = withContext(Dispatchers.Default) {
                runCatching { work { System.nanoTime() > deadline || !isActive } }
            }
            result.onSuccess(done).onFailure { e ->
                failed(
                    when (e) {
                        is UnsupportedInput -> e.message ?: "Can't solve this"
                        is CalcException -> e.kind.message
                        else -> "Can't solve this"
                    },
                )
            }
        }
    }

    /** Drops the result, and stops a solve still running so its answer does not appear afterwards. */
    fun clearIntegral() {
        integralJob?.cancel()
        integralSolving = false
        integralSolution = null
        integralError = null
    }

    fun clearDerivative() {
        derivativeJob?.cancel()
        derivativeSolving = false
        derivativeSolution = null
        derivativeError = null
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        settings = transform(settings)
        scheduleSave()
    }

    fun updatePrefs(transform: (UiPrefs) -> UiPrefs) {
        prefs = transform(prefs)
        scheduleSave()
    }

    fun formatter() = Formatter(settings)

    // ---- Run-Matrix key handling -----------------------------------------------------------

    /** Handles a key in Run-Matrix. Returns a command the screen must act on (OPTN, VARS …) or null. */
    fun runKey(action: KeyAction): Cmd? {
        val input = runInput
        if (computing) {
            if ((action as? KeyAction.Command)?.cmd == Cmd.AC) cancelRequested = true
            return null
        }
        if (action is KeyAction.Command) {
            when (action.cmd) {
                Cmd.EXE, Cmd.EXE_DECIMAL -> {
                    execute(action.cmd == Cmd.EXE_DECIMAL)
                    return null
                }
                Cmd.SD -> {
                    history.lastOrNull()?.let { it.decimal = !it.decimal }
                    return null
                }
                Cmd.AC -> {
                    input.clear()
                    runError = null
                    justEvaluated = false
                    recallIndex = -1
                    return null
                }
                Cmd.LEFT, Cmd.RIGHT -> if (input.isEmpty && history.isNotEmpty() && (justEvaluated || runError == null)) {
                    // Like the calculator: arrows after a result reopen that calculation for editing.
                    input.load(history.last().input)
                    if (action.cmd == Cmd.RIGHT) input.edit { moveToStart() }
                    justEvaluated = false
                    return null
                }
                Cmd.UP -> if (!input.apply(action, Tokens.X)) {
                    recall(older = true)
                    return null
                } else return null
                Cmd.DOWN -> if (!input.apply(action, Tokens.X)) {
                    recall(older = false)
                    return null
                } else return null
                else -> {}
            }
        }
        if (input.isEmpty && history.isNotEmpty() && Keys.continuesFromAns(action)) {
            input.edit { insert(Tokens.ANS) }
        }
        runError = null
        justEvaluated = false
        if (!input.apply(action, Tokens.X)) return (action as? KeyAction.Command)?.cmd
        return null
    }

    private fun recall(older: Boolean) {
        if (history.isEmpty()) return
        recallIndex = when {
            older && recallIndex < 0 -> history.size - 1
            older -> (recallIndex - 1).coerceAtLeast(0)
            recallIndex < 0 -> return
            else -> recallIndex + 1
        }
        if (recallIndex >= history.size) {
            recallIndex = -1
            runInput.clear()
            return
        }
        runInput.load(history[recallIndex].input)
    }

    fun loadIntoInput(row: MathRow) {
        runInput.load(row)
        runError = null
        justEvaluated = false
    }

    fun insertIntoInput(row: MathRow) {
        runInput.edit { insertRow(row) }
        runError = null
    }

    private fun execute(decimal: Boolean) {
        val row = runInput.row.copy()
        if (row.isEmpty) {
            // EXE on an empty line repeats the previous calculation.
            val last = history.lastOrNull() ?: return
            runInput.load(last.input)
            execute(decimal)
            return
        }
        computing = true
        cancelRequested = false
        val currentSettings = settings
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching { Calculator.evaluate(row, memory, currentSettings) { cancelRequested } }
            }
            computing = false
            result.onSuccess {
                history += HistoryEntry(row, it.value, it.style, decimal = decimal).apply { fresh = true }
                while (history.size > MAX_HISTORY) history.removeAt(0)
                runInput.clear()
                runError = null
                justEvaluated = true
                recallIndex = -1
            }.onFailure { e ->
                runError = errorMessage(e)
            }
            scheduleSave()
        }
    }

    fun resetMemory() {
        memory.variables.clear()
        memory.matrices.clear()
        memory.lists.clear()
        memory.ans = Value.of(0)
        memory.changed()
    }

    fun clearHistory() {
        history.clear()
        scheduleSave()
    }

    fun removeHistory(entry: HistoryEntry) {
        history.remove(entry)
        scheduleSave()
    }

    // ---- shared evaluation helpers ----------------------------------------------------------

    /** Evaluates a row for an editor cell (lists, matrices, coefficients) without touching Ans. */
    fun evaluateCell(row: MathRow): Result<Value> = runCatching {
        val ans = memory.ans
        try {
            Calculator.evaluate(row, memory, settings).value
        } finally {
            memory.ans = ans
        }
    }

    fun setGraphFunction(index: Int, f: GraphFunction) {
        graphFunctions[index] = f
        scheduleSave()
    }

    fun updateViewWindow(w: ViewWindow) {
        viewWindow = w
        scheduleSave()
    }

    fun updateTableRange(r: TableRange) {
        tableRange = r
        scheduleSave()
    }

    fun updateStatSettings(s: StatSettings) {
        statSettings = s
        scheduleSave()
    }

    // ---- persistence -------------------------------------------------------------------------

    fun scheduleSave() {
        if (!loaded) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            val state = snapshot()
            withContext(Dispatchers.IO) {
                runCatching {
                    val tmp = File(file.parentFile, "state.json.tmp")
                    tmp.writeText(json.encodeToString(SavedState.serializer(), state))
                    tmp.renameTo(file)
                }
            }
        }
    }

    private fun snapshot() = SavedState(
        settings = settings,
        prefs = prefs,
        variables = memory.variables.mapValues { Codec.encode(it.value) },
        matrices = memory.matrices.entries.associate { "${it.key}" to Codec.encode(it.value) },
        lists = memory.lists.entries.associate { "${it.key}" to Codec.encode(it.value) },
        ans = Codec.encode(memory.ans),
        history = history.takeLast(MAX_HISTORY).map {
            HistoryDto(Codec.encode(it.input), Codec.encode(it.value), it.style.name, it.decimal)
        },
        graphs = graphFunctions.map {
            GraphDto(it.type.name, Codec.encode(it.expr), Codec.encode(it.expr2), it.colorIndex, it.enabled)
        },
        viewWindow = viewWindow,
        table = tableRange,
        stat = statSettings,
        simUnknowns = simUnknowns,
        simCoefficients = simCoefficients.map { Codec.encode(it) },
        polyDegree = polyDegree,
        polyCoefficients = polyCoefficients.map { Codec.encode(it) },
        solverEquation = Codec.encode(solverEquation),
        integrand = Codec.encode(integrand),
        integralLower = Codec.encode(integralLower),
        integralUpper = Codec.encode(integralUpper),
        integralDefinite = integralDefinite,
        derivFunction = Codec.encode(derivFunction),
        derivOrder = derivOrder,
        derivAt = Codec.encode(derivAt),
        derivAtEnabled = derivAtEnabled,
    )

    private fun load() {
        val state = runCatching {
            if (file.exists()) json.decodeFromString(SavedState.serializer(), file.readText()) else null
        }.getOrNull()
        if (state != null) {
            settings = state.settings
            prefs = state.prefs
            state.variables.forEach { (k, v) -> Codec.decode(v)?.let { memory.variables[k] = it } }
            state.matrices.forEach { (k, v) -> (Codec.decode(v) as? Value.Mat)?.let { memory.matrices[k.first()] = it } }
            state.lists.forEach { (k, v) -> (Codec.decode(v) as? Value.Lst)?.let { memory.lists[k.toInt()] = it } }
            state.ans?.let { Codec.decode(it) }?.let { memory.ans = it }
            state.history.forEach { h ->
                val v = Codec.decode(h.value) ?: return@forEach
                val style = runCatching { Formatter.Style.valueOf(h.style) }.getOrDefault(Formatter.Style.AUTO)
                history += HistoryEntry(Codec.decode(h.input), v, style, h.decimal)
            }
            state.graphs.take(GRAPH_SLOTS).forEachIndexed { i, g ->
                val type = runCatching { GraphType.valueOf(g.type) }.getOrDefault(GraphType.Y)
                graphFunctions[i] = GraphFunction(type, Codec.decode(g.expr), Codec.decode(g.expr2), g.color, g.enabled)
            }
            viewWindow = state.viewWindow
            tableRange = state.table
            statSettings = state.stat
            simUnknowns = state.simUnknowns.coerceIn(2, 6)
            state.simCoefficients.take(simCoefficients.size).forEachIndexed { i, v -> Codec.decode(v)?.let { simCoefficients[i] = it } }
            polyDegree = state.polyDegree.coerceIn(2, 6)
            state.polyCoefficients.take(polyCoefficients.size).forEachIndexed { i, v -> Codec.decode(v)?.let { polyCoefficients[i] = it } }
            solverEquation = Codec.decode(state.solverEquation)
            integrand = Codec.decode(state.integrand)
            integralLower = Codec.decode(state.integralLower)
            integralUpper = Codec.decode(state.integralUpper)
            integralDefinite = state.integralDefinite
            derivFunction = Codec.decode(state.derivFunction)
            derivOrder = state.derivOrder.coerceIn(1, 3)
            derivAt = Codec.decode(state.derivAt)
            derivAtEnabled = state.derivAtEnabled
        }
        if (solverEquation.isEmpty) {
            // A friendly starting equation for the Solver: X² − 2 = 0.
            solverEquation = MathRow(
                listOf(
                    TokenNode(Tokens.letter('X')),
                    com.l0gic0l.casiocalc.math.editor.Template.create(
                        com.l0gic0l.casiocalc.math.editor.TemplateKind.POWER,
                        listOf(TokenNode(Tokens.digit(2))),
                    ),
                    TokenNode(Tokens.SUB), TokenNode(Tokens.digit(2)), TokenNode(Tokens.EQ), TokenNode(Tokens.digit(0)),
                ),
            )
        }
        loaded = true
    }

    companion object {
        const val GRAPH_SLOTS = 10
        const val GRAPH_COLORS = 6
        const val MAX_HISTORY = 60
        const val SOLVE_SECONDS = 8L
        private const val PREVIEW_LIMIT_MS = 250L

        fun errorMessage(e: Throwable): String = when (e) {
            is CalcException -> e.kind.message
            is ArithmeticException -> ErrorKind.MATH.message
            is StackOverflowError -> ErrorKind.STACK.message
            else -> ErrorKind.MATH.message
        }
    }
}
