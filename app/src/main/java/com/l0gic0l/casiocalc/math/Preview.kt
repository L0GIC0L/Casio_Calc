package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.parse.Expr

/**
 * Which Run-Matrix inputs get a live preview while typing: calculations that are quick and have no
 * side effects. Stores, random numbers, solvers, calculus, Σ/Π and anything on matrices or lists
 * wait for EXE, as does a bare number, whose preview would only repeat it. BinomialCD is left out
 * because it adds up one term per trial, which is slow for a large n.
 */
object Preview {
    private val functions = setOf(
        "sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "log", "ln", "Int", "Frac", "Intg", "Rnd", "RndFix", "GCD", "LCM", "MOD", "Arg", "Conjg", "ReP", "ImP",
        "NormPD", "NormCD", "InvNormCD", "BinomialPD", "PoissonPD", "PoissonCD", "GeoPD", "GeoCD",
        "tPD", "tCD", "ChiPD", "ChiCD", "FPD", "FCD",
    )

    private val templates = setOf(
        TemplateKind.FRACTION, TemplateKind.MIXED, TemplateKind.SQRT, TemplateKind.ROOT, TemplateKind.LOG_BASE, TemplateKind.ABS,
    )

    /** [ansIsNumber] is false when Ans holds a matrix or list, which would make the sum one too. */
    fun worthShowing(e: Expr, ansIsNumber: Boolean): Boolean {
        val bareNumber = e is Expr.Num || (e is Expr.Unary && e.op == "neg" && e.e is Expr.Num)
        return !bareNumber && simple(e, ansIsNumber)
    }

    private fun simple(e: Expr, ans: Boolean): Boolean = when (e) {
        is Expr.Num, is Expr.Var -> true
        is Expr.Const -> e.id != "Ran#" && (e.id != "Ans" || ans)
        is Expr.Unary -> simple(e.e, ans)
        is Expr.Binary -> simple(e.l, ans) && simple(e.r, ans)
        is Expr.Postfix -> simple(e.e, ans)
        is Expr.Call -> e.name in functions && e.args.all { simple(it, ans) }
        is Expr.Tpl -> e.kind in templates && e.slots.all { it == null || simple(it, ans) }
        else -> false
    }
}
