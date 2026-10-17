package com.l0gic0l.casiocalc.math.parse

import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.num.Real

/** Parsed expression tree. */
sealed class Expr {
    data class Num(val value: Real) : Expr()
    data class Var(val name: String) : Expr()
    data class Const(val id: String) : Expr()
    data class Ref(val id: String) : Expr()
    data class Unary(val op: String, val e: Expr) : Expr()
    data class Binary(val op: String, val l: Expr, val r: Expr) : Expr()
    data class Postfix(val op: String, val e: Expr) : Expr()
    data class Call(val name: String, val args: List<Expr>) : Expr()
    data class Tpl(val kind: TemplateKind, val slots: List<Expr?>, val variable: String? = null) : Expr()
    data class ListLit(val items: List<Expr>) : Expr()
    data class MatLit(val rows: Int, val cols: Int, val cells: List<Expr>) : Expr()
    data class Store(val e: Expr, val target: String) : Expr()

    /** A display directive such as ▸DMS applied to the whole result. */
    data class Format(val e: Expr, val directive: String) : Expr()

    /** Visits this tree depth-first. */
    fun walk(f: (Expr) -> Unit) {
        f(this)
        when (this) {
            is Unary -> e.walk(f)
            is Binary -> { l.walk(f); r.walk(f) }
            is Postfix -> e.walk(f)
            is Call -> args.forEach { it.walk(f) }
            is Tpl -> slots.forEach { it?.walk(f) }
            is ListLit -> items.forEach { it.walk(f) }
            is MatLit -> cells.forEach { it.walk(f) }
            is Store -> e.walk(f)
            is Format -> e.walk(f)
            else -> {}
        }
    }

    fun any(pred: (Expr) -> Boolean): Boolean {
        var found = false
        walk { if (!found && pred(it)) found = true }
        return found
    }
}
