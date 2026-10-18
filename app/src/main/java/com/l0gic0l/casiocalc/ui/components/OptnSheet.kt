package com.l0gic0l.casiocalc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.ui.keypad.Cmd
import com.l0gic0l.casiocalc.ui.keypad.KeyAction

data class OptnItem(val label: String, val action: KeyAction, val hint: String? = null)
data class OptnCategory(val title: String, val items: List<OptnItem>)

/** The OPTN function catalogue, grouped like the calculator's option menus. */
object OptnCatalog {
    private fun fn(id: String, hint: String? = null) = OptnItem(Tokens.fn(id).display, KeyAction.Insert(Tokens.fn(id)), hint)
    private fun tok(label: String, t: Tok, hint: String? = null) = OptnItem(label, KeyAction.Insert(t), hint)
    private fun tpl(label: String, kind: TemplateKind, vararg prefill: List<Tok>, hint: String? = null) =
        OptnItem(label, KeyAction.InsertTemplate(kind, prefill.toList()), hint)

    val categories = listOf(
        OptnCategory(
            "CALC",
            listOf(
                tpl("d/dx", TemplateKind.DERIV, hint = "derivative at a point"),
                tpl("d²/dx²", TemplateKind.DERIV2, hint = "second derivative"),
                tpl("∫dx", TemplateKind.INTEGRAL, hint = "definite integral"),
                tpl("Σ", TemplateKind.SUM, listOf(Tokens.letter('X')), hint = "sum over X"),
                tpl("Π", TemplateKind.PRODUCT, listOf(Tokens.letter('X')), hint = "product over X"),
                fn("Solve", "Solve(f(X), guess)"),
                fn("FMin", "FMin(f(X), a, b) → {x, y}"),
                fn("FMax", "FMax(f(X), a, b) → {x, y}"),
                tpl("logₐb", TemplateKind.LOG_BASE),
            ),
        ),
        OptnCategory(
            "NUM",
            listOf(
                tpl("Abs |x|", TemplateKind.ABS),
                fn("Int", "integer part"),
                fn("Frac", "fractional part"),
                fn("Intg", "greatest integer ≤ x"),
                fn("Rnd", "round to display"),
                fn("RndFix", "RndFix(x, digits)"),
                fn("GCD", "GCD(a, b)"),
                fn("LCM", "LCM(a, b)"),
                fn("MOD", "MOD(a, b)"),
                tpl("ˣ√", TemplateKind.ROOT),
                tpl("∛", TemplateKind.ROOT, listOf(Tokens.digit(3))),
                tok("%", Tokens.PERCENT),
            ),
        ),
        OptnCategory(
            "PROB",
            listOf(
                tok("x!", Tokens.FACT),
                tok("nPr", Tokens.NPR, "permutations"),
                tok("nCr", Tokens.NCR, "combinations"),
                tok("Ran#", Tokens.RAN, "random 0–1"),
                fn("RanInt", "RanInt#(a, b[, n])"),
            ),
        ),
        OptnCategory(
            "HYP",
            listOf(fn("sinh"), fn("cosh"), fn("tanh"), fn("asinh"), fn("acosh"), fn("atanh")),
        ),
        OptnCategory(
            "ANGLE",
            listOf(
                tok("°", Tokens.DEG, "degrees"),
                tok("r", Tokens.RAD, "radians"),
                tok("g", Tokens.GRA, "grads"),
                tok("°′″", Tokens.DMS, "deg-min-sec entry"),
                tok("▸DMS", Tokens.TO_DMS, "show as d°m′s″"),
                fn("Pol", "Pol(x, y) → {r, θ}"),
                fn("Rec", "Rec(r, θ) → {x, y}"),
            ),
        ),
        OptnCategory(
            "CPLX",
            listOf(
                tok("i", Tokens.I),
                tpl("Abs |z|", TemplateKind.ABS),
                fn("Arg"),
                fn("Conjg"),
                fn("ReP"),
                fn("ImP"),
                tok("∠", Tokens.ANGLE, "polar entry r∠θ"),
                tok("▸r∠θ", Tokens.TO_POLAR),
                tok("▸a+bi", Tokens.TO_RECT),
            ),
        ),
        OptnCategory(
            "MAT",
            listOf(
                OptnItem("Mat", KeyAction.Command(Cmd.MAT_PICK), "Mat A–Z"),
                OptnItem("[▦]", KeyAction.Command(Cmd.MAT_TEMPLATE), "new matrix"),
                fn("Det"),
                fn("Trn", "transpose"),
                fn("Identity", "Identity(n)"),
                fn("Dim"),
                fn("Ref", "row echelon"),
                fn("Rref", "reduced row echelon"),
                fn("Augment", "Augment(A, B)"),
                OptnItem("x⁻¹", KeyAction.InsertTemplate(TemplateKind.POWER, listOf(listOf(Tokens.NEG, Tokens.digit(1)))), "inverse"),
            ),
        ),
        OptnCategory(
            "LIST",
            listOf(
                OptnItem("List", KeyAction.Command(Cmd.LIST_PICK), "List 1–26"),
                tok("{", Tokens.LBRACE),
                tok("}", Tokens.RBRACE),
                fn("Sum"), fn("Prod"), fn("Mean", "Mean(list[, freq])"), fn("Median"),
                fn("Min"), fn("Max"), fn("Dim"), fn("Cuml", "cumulative sums"),
                fn("Seq", "Seq(f, var, start, end[, step])"),
                fn("SortA"), fn("SortD"), fn("Augment"), fn("Fill", "Fill(value, n)"),
            ),
        ),
        OptnCategory(
            "DIST",
            listOf(
                fn("NormPD", "NormPD(x[, σ, μ])"),
                fn("NormCD", "NormCD(lower, upper[, σ, μ])"),
                fn("InvNormCD", "InvNormCD(area[, σ, μ])"),
                fn("BinomialPD", "BinomialPD(x, n, p)"),
                fn("BinomialCD", "BinomialCD(x, n, p)"),
                fn("PoissonPD", "PoissonPD(x, μ)"),
                fn("PoissonCD", "PoissonCD(x, μ)"),
                fn("GeoPD", "GeoPD(x, p)"),
                fn("GeoCD", "GeoCD(x, p)"),
                fn("tPD", "tPD(x, df)"),
                fn("tCD", "tCD(lower, upper, df)"),
                fn("ChiPD", "χ²PD(x, df)"),
                fn("ChiCD", "χ²CD(lower, upper, df)"),
                fn("FPD", "FPD(x, n, d)"),
                fn("FCD", "FCD(lower, upper, n, d)"),
            ),
        ),
        OptnCategory(
            "LOGIC",
            listOf(
                tok("=", Tokens.EQ), tok("≠", Tokens.NE), tok(">", Tokens.GT), tok("<", Tokens.LT),
                tok("≥", Tokens.GE), tok("≤", Tokens.LE),
                tok("and", Tokens.AND), tok("or", Tokens.OR), tok("xor", Tokens.XOR), tok("not", Tokens.NOT),
            ),
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptnSheet(onDismiss: () -> Unit, onPick: (KeyAction) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column {
            Text("OPTN", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp))
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                OptnCatalog.categories.forEachIndexed { i, c ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(c.title) })
                }
            }
            val items = OptnCatalog.categories[tab].items
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items(items) { item ->
                    Surface(
                        onClick = {
                            onPick(item.action)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                            Text(item.label, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            if (item.hint != null) {
                                Text(
                                    item.hint,
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
