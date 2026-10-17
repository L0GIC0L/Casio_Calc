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

@Serializable
data class UiPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val haptics: Boolean = true,
    val graphGrid: Boolean = true,
    val graphAxes: Boolean = true,
    val graphLabels: Boolean = true,
)

/** A finished Run-Matrix calculation. S⇔D flips [decimal]. */
class HistoryEntry(val input: MathRow, val value: Value, val style: Formatter.Style, decimal: Boolean = false) {
    var decimal by mutableStateOf(decimal)
}

/** [Memory] whose changes are observable from Compose and trigger saving. */
class AppMemory(private val onChange: () -> Unit) : Memory() {
    var version by mutableIntStateOf(0)
        private set

    override fun changed() {
        version++
        onChange()
    }
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
)
