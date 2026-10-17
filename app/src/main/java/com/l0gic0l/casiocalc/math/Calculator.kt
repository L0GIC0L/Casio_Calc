package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.eval.ComplexMode
import com.l0gic0l.casiocalc.math.eval.Evaluator
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.format.Formatter
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.parse.Expr
import com.l0gic0l.casiocalc.math.parse.Parser

/** A finished calculation: the value plus how it asked to be shown (▸DMS, ▸a+bi …). */
class CalcResult(val value: Value, val style: Formatter.Style)

/** Entry point used by the Run-Matrix screen: parse, evaluate, update Ans. */
object Calculator {
    fun evaluate(
        row: MathRow,
        memory: Memory,
        settings: Settings,
        cancelled: () -> Boolean = { false },
    ): CalcResult {
        var expr = Parser.parse(row)
        var style = Formatter.Style.AUTO
        if (expr is Expr.Format) {
            style = when (expr.directive) {
                "toDMS" -> Formatter.Style.DMS
                "toRect" -> Formatter.Style.RECT
                "toPolar" -> Formatter.Style.POLAR
                else -> Formatter.Style.DECIMAL
            }
            expr = expr.e
        }
        val evaluator = evaluator(expr, memory, settings, cancelled)
        val value = evaluator.eval(expr)
        memory.ans = value
        return CalcResult(value, style)
    }

    /** An evaluator for [expr]; Real mode still allows complex arithmetic when the input uses i or ∠. */
    fun evaluator(expr: Expr, memory: Memory, settings: Settings, cancelled: () -> Boolean = { false }): Evaluator {
        val usesComplex = expr.any { (it is Expr.Const && it.id == "i") || (it is Expr.Binary && it.op == "angle") }
        return Evaluator(memory, settings, settings.complex != ComplexMode.REAL || usesComplex, cancelled = cancelled)
    }
}
