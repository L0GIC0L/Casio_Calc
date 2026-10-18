package com.l0gic0l.casiocalc.math.format

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.TokenNode

/** Text forms of a row: a structural one for tests and a linear one for the clipboard. */
object RowText {
    fun structural(row: MathRow): String = row.nodes.joinToString("") { n ->
        when (n) {
            is TokenNode -> n.tok.display
            is Template -> {
                val name = when (n.kind) {
                    TemplateKind.FRACTION -> "frac"
                    TemplateKind.MIXED -> "mixed"
                    TemplateKind.POWER -> "pow"
                    TemplateKind.SQRT -> "sqrt"
                    TemplateKind.ROOT -> "root"
                    TemplateKind.LOG_BASE -> "logb"
                    TemplateKind.ABS -> "abs"
                    TemplateKind.INTEGRAL -> "int"
                    TemplateKind.DERIV -> "d"
                    TemplateKind.DERIV2 -> "d2"
                    TemplateKind.SUM -> "sum"
                    TemplateKind.PRODUCT -> "prod"
                    TemplateKind.MATRIX -> "mat${n.rows}x${n.cols}"
                    TemplateKind.INDEF_INTEGRAL -> "indef"
                    TemplateKind.DIFF -> "diff"
                }
                n.slots.joinToString("|", prefix = "{$name:", postfix = "}") { structural(it) }
            }
        }
    }

    fun linear(row: MathRow): String = row.nodes.joinToString("") { n ->
        when (n) {
            is TokenNode -> n.tok.display.trim().let { if (n.tok.display.startsWith(" ")) " $it " else it }
            is Template -> {
                val s = n.slots.map { linear(it) }
                fun g(x: String) = if (x.length <= 1) x else "($x)"
                when (n.kind) {
                    TemplateKind.FRACTION -> "${g(s[0])}/${g(s[1])}"
                    TemplateKind.MIXED -> "${s[0]}+${g(s[1])}/${g(s[2])}"
                    TemplateKind.POWER -> "^${g(s[0])}"
                    TemplateKind.SQRT -> "√${g(s[0])}"
                    TemplateKind.ROOT -> "${g(s[0])}√${g(s[1])}"
                    TemplateKind.LOG_BASE -> "log_${g(s[0])}(${s[1]})"
                    TemplateKind.ABS -> "|${s[0]}|"
                    TemplateKind.INTEGRAL -> "∫(${s[2]},${s[0]},${s[1]})"
                    TemplateKind.DERIV -> "d/dx(${s[0]},${s[1]})"
                    TemplateKind.DERIV2 -> "d²/dx²(${s[0]},${s[1]})"
                    TemplateKind.SUM -> "Σ(${s[3]},${s[0]},${s[1]},${s[2]})"
                    TemplateKind.PRODUCT -> "Π(${s[3]},${s[0]},${s[1]},${s[2]})"
                    TemplateKind.MATRIX -> (0 until n.rows).joinToString("", "[", "]") { r ->
                        (0 until n.cols).joinToString(",", "[", "]") { c -> s[r * n.cols + c] }
                    }
                    TemplateKind.INDEF_INTEGRAL -> "∫(${s[0]})d${s[1].lowercase()}"
                    TemplateKind.DIFF -> "d${s[2]}/d${s[1].lowercase()}${s[2]}(${s[0]})"
                }
            }
        }
    }
}
