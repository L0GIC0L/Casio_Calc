package com.l0gic0l.casiocalc.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.ui.theme.ThemeMode
import kotlinx.serialization.Serializable

enum class GraphType(val label: String, val variable: String) {
    Y("Y=", "X"), R("r=", "θ"), PARAM("Parm", "T"), X("X=", "Y"),
}

/** One graph function slot (Y1, Y2 …). Immutable; edits replace the slot. */
data class GraphFunction(
    val type: GraphType = GraphType.Y,
    val expr: MathRow = MathRow(),
    /** Second expression (Yt) for parametric graphs. */
    val expr2: MathRow = MathRow(),
    val colorIndex: Int = 0,
    val enabled: Boolean = true,
) {
    val isEmpty get() = expr.isEmpty && (type != GraphType.PARAM || expr2.isEmpty)
}

@Serializable
data class ViewWindow(
    val xmin: Double = -6.3,
    val xmax: Double = 6.3,
    val xscl: Double = 1.0,
    val ymin: Double = -6.3,
    val ymax: Double = 6.3,
    val yscl: Double = 1.0,
    val tmin: Double = 0.0,
    val tmax: Double = 2 * Math.PI,
    val tstep: Double = Math.PI / 60,
)

@Serializable
data class TableRange(val start: Double = 1.0, val end: Double = 5.0, val step: Double = 1.0)

@Serializable
data class StatSettings(
    val xList: Int = 1,
    val yList: Int = 2,
    /** 0 means every value counts once. */
    val freqList: Int = 0,
)

/** Size of the Run-Matrix display text. */
enum class TextScale(val label: String, val factor: Float) { SMALL("Small", 0.85f), MEDIUM("Medium", 1f), LARGE("Large", 1.2f) }

/** Corners of the keypad's keys. */
enum class KeyShape(val label: String) { ROUNDED("Rounded"), PILL("Pill"), SQUARE("Square") }

/** Gap between keys. */
enum class KeySpacing(val label: String, val dp: Float) { TIGHT("Tight", 3f), NORMAL("Normal", 6f), ROOMY("Roomy", 10f) }

/** Height of the keypad relative to the default. */
enum class KeypadSize(val label: String, val factor: Float) { COMPACT("Compact", 0.86f), NORMAL("Normal", 1f), TALL("Tall", 1.12f) }

/** Theme colour when wallpaper colours are off: the default blue palette turned round the colour wheel. */
enum class AccentColor(val label: String, val hueShift: Float) {
    BLUE("Blue", 0f), TEAL("Teal", -48f), GREEN("Green", -92f), ORANGE("Orange", 168f), ROSE("Rose", 118f), PURPLE("Purple", 50f),
}

/** The screen the app opens on. */
enum class StartScreen(val label: String, val route: String) { MENU("Main menu", "menu"), RUN("Run-Matrix", "run") }

/** Width of graph lines. */
enum class LineWeight(val label: String, val dp: Float) { THIN("Thin", 1.5f), NORMAL("Normal", 2.5f), THICK("Thick", 4f) }

@Serializable
data class UiPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Black backgrounds in the dark theme. */
    val pureBlack: Boolean = false,
    /** Drifting particles on the main menu. (The name is kept so saved settings carry over.) */
    val particles: Boolean = true,
    /**
     * A burst of sparks when a Run-Matrix answer appears. Null in settings saved before this had a
     * switch of its own, when [particles] covered both; see [answerSparkles].
     */
    val resultParticles: Boolean? = null,
    /** Turns off particles and decorative motion. */
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    /** Key clicks, which also need the system Touch sounds setting on. */
    val keySound: Boolean = true,
    /** Show each key's SHIFT function in small print above the key. */
    val keyHints: Boolean = true,
    val displaySize: TextScale = TextScale.MEDIUM,
    val keepScreenOn: Boolean = false,
    val graphGrid: Boolean = true,
    val graphAxes: Boolean = true,
    val graphLabels: Boolean = true,
    val graphLine: LineWeight = LineWeight.NORMAL,
    val keyShape: KeyShape = KeyShape.ROUNDED,
    val keySpacing: KeySpacing = KeySpacing.NORMAL,
    val keyLabels: TextScale = TextScale.MEDIUM,
    val keypadSize: KeypadSize = KeypadSize.NORMAL,
    val accent: AccentColor = AccentColor.BLUE,
    val startScreen: StartScreen = StartScreen.MENU,
    /** Show the value of a simple calculation under the Run-Matrix input while typing. */
    val livePreview: Boolean = true,
) {
    val answerSparkles get() = resultParticles ?: particles
    val showHomeParticles get() = particles && !reduceMotion
    val showResultParticles get() = answerSparkles && !reduceMotion
}

/** A finished Run-Matrix calculation. S⇔D flips [decimal]. */
class HistoryEntry(val input: MathRow, val value: Value, val style: Formatter.Style, decimal: Boolean = false) {
    /** Stable key for lists. */
    val id: Long = nextId.getAndIncrement()
    var decimal by mutableStateOf(decimal)

    /** True until the entry has been shown once, so only new answers animate in. */
    var fresh = false

    private companion object {
        val nextId = java.util.concurrent.atomic.AtomicLong()
    }
}

/** [Memory] whose changes are observable from Compose and trigger saving. */
class AppMemory(private val onChange: () -> Unit) : Memory() {
    var version by mutableIntStateOf(0)
        private set

    override fun changed() {
        version++
        onChange()
    }

    // Reading memory also reads [version], so any screen showing memory redraws after a store.
    override fun getVar(name: String): Value = version.let { super.getVar(name) }
    override fun getMatrix(c: Char): Value.Mat? = version.let { super.getMatrix(c) }
    override fun getList(n: Int): Value.Lst? = version.let { super.getList(n) }
}

@Serializable
data class HistoryDto(val input: List<NodeDto>, val value: ValueDto, val style: String = "AUTO", val decimal: Boolean = false)

@Serializable
data class GraphDto(val type: String, val expr: List<NodeDto>, val expr2: List<NodeDto> = emptyList(), val color: Int, val enabled: Boolean)

@Serializable
data class SavedState(
    val settings: Settings = Settings(),
    val prefs: UiPrefs = UiPrefs(),
    val variables: Map<String, ValueDto> = emptyMap(),
    val matrices: Map<String, ValueDto> = emptyMap(),
    val lists: Map<String, ValueDto> = emptyMap(),
    val ans: ValueDto? = null,
    val history: List<HistoryDto> = emptyList(),
    val graphs: List<GraphDto> = emptyList(),
    val viewWindow: ViewWindow = ViewWindow(),
    val table: TableRange = TableRange(),
    val stat: StatSettings = StatSettings(),
    val simUnknowns: Int = 2,
    val simCoefficients: List<ValueDto> = emptyList(),
    val polyDegree: Int = 2,
    val polyCoefficients: List<ValueDto> = emptyList(),
    val solverEquation: List<NodeDto> = emptyList(),
    val integrand: List<NodeDto> = emptyList(),
    val integralLower: List<NodeDto> = emptyList(),
    val integralUpper: List<NodeDto> = emptyList(),
    val integralDefinite: Boolean = false,
    val derivFunction: List<NodeDto> = emptyList(),
    val derivOrder: Int = 1,
    val derivAt: List<NodeDto> = emptyList(),
    val derivAtEnabled: Boolean = false,
)
