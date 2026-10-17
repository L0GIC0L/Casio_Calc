package com.l0gic0l.casiocalc.math.layout

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.TokenKind
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import kotlin.math.max
import kotlin.math.min

/**
 * Lays out a [MathRow] as natural display: fractions stacked over a bar, radicals drawn around
 * their contents, superscripts raised, brackets stretched to fit. Sizes are fractions of the font
 * size so the result scales with it.
 */
class LayoutEngine(private val measure: TextWidth) {

    private companion object {
        const val ASC = 0.74f        // ink ascent of digits and capitals
        const val DESC = 0.06f
        const val DEEP_DESC = 0.22f  // brackets, commas, descenders
        const val AXIS = 0.28f       // height of the fraction bar / minus sign above the baseline
        const val SCRIPT = 0.7f
        val deepGlyphs = "()[]{},;gjpqy|"
    }

    /** A laid-out box; coordinates are relative to its left edge and baseline (negative y is up). */
    private class Box(var width: Float = 0f, var ascent: Float = 0f, var descent: Float = 0f) {
        val ops = ArrayList<DrawOp>()
        val stops = ArrayList<CursorStop>()

        fun place(child: Box, dx: Float, dy: Float) {
            child.ops.mapTo(ops) { it.moved(dx, dy) }
            child.stops.mapTo(stops) { it.moved(dx, dy) }
            ascent = max(ascent, child.ascent - dy)
            descent = max(descent, child.descent + dy)
        }
    }

    private var baseSize = 0f

    fun layout(row: MathRow, size: Float): MathLayout {
        baseSize = size
        val box = layoutRow(row, size)
        val pad = size * 0.12f
        val dx = pad
        val dy = box.ascent + pad
        return MathLayout(
            width = box.width + 2 * pad,
            height = box.ascent + box.descent + 2 * pad,
            baseline = dy,
            ops = box.ops.map { it.moved(dx, dy) },
            stops = box.stops.map { it.moved(dx, dy) },
        )
    }

    private fun scriptSize(s: Float) = max(s * SCRIPT, baseSize * 0.45f)

    private val superscripts = mapOf('⁻' to '−', '¹' to '1', '²' to '2', '³' to '3')
    private val subscripts = mapOf('ₙ' to 'n', '₊' to '+', '₁' to '1')

    /** Text where super/subscript characters (sin⁻¹, χ², aₙ) are drawn as real script runs. */
    private fun richText(text: String, s: Float, role: Role): Box {
        fun kind(c: Char) = if (c in superscripts) 1 else if (c in subscripts) -1 else 0
        if (text.all { kind(it) == 0 }) return textBox(text, s, role)
        val box = Box(0f, s * ASC, s * DESC)
        var i = 0
        while (i < text.length) {
            val k = kind(text[i])
            val start = i
            while (i < text.length && kind(text[i]) == k) i++
            val run = text.substring(start, i)
            if (k != 0) {
                val map = if (k == 1) superscripts else subscripts
                val part = textBox(run.map { map.getValue(it) }.joinToString(""), scriptSize(s), role)
                box.place(part, box.width, if (k == 1) -s * 0.42f else s * 0.22f)
                box.width += part.width
            } else {
                val part = textBox(run, s, role)
                box.place(part, box.width, 0f)
                box.width += part.width
            }
        }
        return box
    }

    private fun textBox(text: String, s: Float, role: Role): Box {
        val w = measure.width(text, s, role)
        val deep = text.any { it in deepGlyphs }
        val box = Box(w, s * ASC, s * (if (deep) DEEP_DESC else DESC))
        box.ops += DrawOp.Text(text, 0f, 0f, s, role)
        return box
    }

    // ---- rows -----------------------------------------------------------------------------

    /** One element of a row before ops are emitted; delimiters may still be stretched. */
    private class Item(val x: Float, val dy: Float, val box: Box?, val delim: Char? = null, val width: Float, val size: Float) {
        var top = 0f      // delimiter extents, relative to the row baseline
        var bottom = 0f
        var stretched = false
        val ascent get() = if (box != null) box.ascent - dy else -top
        val descent get() = if (box != null) box.descent + dy else bottom
    }

    private fun layoutRow(row: MathRow, s: Float): Box {
        val out = Box()
        if (row.isEmpty) {
            val w = s * 0.62f
            out.width = w + s * 0.08f
            out.ascent = s * ASC
            out.descent = s * DESC
            out.ops += DrawOp.Placeholder(s * 0.04f, -s * ASC, w, s * (ASC + DESC))
            out.stops += CursorStop(row, 0, s * 0.16f, 0f, s)
            return out
        }
        val items = ArrayList<Item>()
        val opens = ArrayList<Item>()
        var x = 0f
        var dmsCount = 0
        val stopX = FloatArray(row.size + 1)

        fun addBox(box: Box, dy: Float = 0f) {
            items += Item(x, dy, box, width = box.width, size = s)
            x += box.width
        }

        fun addDelim(c: Char): Item {
            val w = measure.width(c.toString(), s, Role.NORMAL) + s * 0.04f
            val item = Item(x, 0f, null, c, w, s)
            item.top = -s * ASC
            item.bottom = s * DEEP_DESC
            items += item
            x += w
            return item
        }

        fun stretch(open: Item, close: Item?) {
            val from = items.indexOf(open) + 1
            val to = if (close == null) items.size else items.indexOf(close)
            var asc = s * ASC
            var desc = s * DEEP_DESC
            for (k in from until to) {
                asc = max(asc, items[k].ascent)
                desc = max(desc, items[k].descent)
            }
            if (asc > s * 0.95f || desc > s * 0.34f) {
                val top = -(asc + s * 0.08f)
                val bottom = desc + s * 0.08f
                for (d in listOfNotNull(open, close)) {
                    d.top = top; d.bottom = bottom; d.stretched = true
                }
            }
        }

        for ((i, node) in row.nodes.withIndex()) {
            stopX[i] = x
            when (node) {
                is TokenNode -> {
                    val tok = node.tok
                    if (tok.kind != TokenKind.DIGIT && tok.kind != TokenKind.POINT && tok.kind != TokenKind.DMS) dmsCount = 0
                    when (tok.kind) {
                        TokenKind.FUNCTION -> {
                            addBox(richText(tok.display.dropLast(1), s, Role.FUNCTION))
                            opens += addDelim('(')
                        }
                        TokenKind.LPAREN -> opens += addDelim('(')
                        TokenKind.LBRACE -> opens += addDelim('{')
                        TokenKind.RPAREN, TokenKind.RBRACE -> {
                            val close = addDelim(if (tok.kind == TokenKind.RPAREN) ')' else '}')
                            if (opens.isNotEmpty()) stretch(opens.removeAt(opens.size - 1), close)
                        }
                        TokenKind.BINARY, TokenKind.RELATION, TokenKind.STORE -> {
                            val unary = tok === Tokens.SUB && isUnaryPosition(row.nodes, i)
                            if (unary || tok === Tokens.ANGLE || tok === Tokens.NPR || tok === Tokens.NCR) {
                                addBox(textBox(tok.display, s, Role.OPERATOR))
                            } else {
                                val pad = s * 0.16f
                                x += pad
                                addBox(textBox(tok.display.trim(), s, Role.OPERATOR))
                                x += pad
                            }
                        }
                        TokenKind.COMMA -> {
                            addBox(textBox(",", s, Role.OPERATOR))
                            x += s * 0.14f
                        }
                        TokenKind.DMS -> {
                            val mark = when (dmsCount++) {
                                0 -> "°"; 1 -> "′"; else -> "″"
                            }
                            addBox(textBox(mark, s, Role.NORMAL))
                        }
                        TokenKind.EXP10 -> addBox(textBox("E", s * 0.85f, Role.FUNCTION))
                        TokenKind.POSTFIX -> when (tok) {
                            Tokens.RAD, Tokens.GRA -> {
                                val mark = textBox(if (tok === Tokens.RAD) "r" else "g", scriptSize(s), Role.NORMAL)
                                addBox(mark, -s * 0.42f)
                            }
                            else -> addBox(textBox(tok.display, s, if (tok.id.startsWith("to")) Role.FUNCTION else Role.NORMAL))
                        }
                        TokenKind.REFERENCE, TokenKind.CONSTANT ->
                            addBox(textBox(tok.display, s, if (tok.display.length > 1 && tok !== Tokens.TEN) Role.FUNCTION else Role.NORMAL))
                        TokenKind.PREFIX -> addBox(textBox(tok.display, s, if (tok === Tokens.NEG) Role.NORMAL else Role.OPERATOR))
                        else -> addBox(richText(tok.display, s, Role.NORMAL))
                    }
                }
                is Template -> {
                    if (node.kind == TemplateKind.POWER) {
                        val prev = items.lastOrNull()
                        val prevAsc = prev?.ascent ?: (s * ASC)
                        val exp = layoutRow(node.slots[0], scriptSize(s))
                        val shift = max(s * 0.42f, prevAsc - s * 0.32f)
                        addBox(exp, -shift)
                        x += s * 0.03f
                    } else {
                        addBox(layoutTemplate(node, s))
                    }
                }
            }
        }
        stopX[row.size] = x
        for (open in opens) stretch(open, null)

        out.width = x
        out.ascent = s * ASC
        out.descent = s * DESC
        for (item in items) {
            if (item.box != null) {
                out.place(item.box, item.x, item.dy)
            } else {
                val c = item.delim!!
                if (item.stretched) {
                    out.ops += DrawOp.Delimiter(c, item.x, item.top, item.bottom, item.width, max(1f, s * 0.06f))
                } else {
                    out.ops += DrawOp.Text(c.toString(), item.x, 0f, s, Role.NORMAL)
                }
                out.ascent = max(out.ascent, -item.top)
                out.descent = max(out.descent, item.bottom)
            }
        }
        for (i in 0..row.size) out.stops += CursorStop(row, i, stopX[i], 0f, s)
        return out
    }

    private fun isUnaryPosition(nodes: List<Node>, i: Int): Boolean {
        if (i == 0) return true
        val prev = nodes[i - 1] as? TokenNode ?: return false
        return when (prev.tok.kind) {
            TokenKind.BINARY, TokenKind.RELATION, TokenKind.LPAREN, TokenKind.FUNCTION, TokenKind.COMMA,
            TokenKind.PREFIX, TokenKind.STORE, TokenKind.LBRACE -> true
            else -> false
        }
    }

    // ---- templates ------------------------------------------------------------------------

    private fun layoutTemplate(t: Template, s: Float): Box = when (t.kind) {
        TemplateKind.FRACTION -> fraction(layoutRow(t.slots[0], s), layoutRow(t.slots[1], s), s)
        TemplateKind.MIXED -> {
            val box = Box()
            val whole = layoutRow(t.slots[0], s)
            box.place(whole, 0f, 0f)
            val frac = fraction(layoutRow(t.slots[1], s), layoutRow(t.slots[2], s), s)
            box.place(frac, whole.width + s * 0.04f, 0f)
            box.width = whole.width + s * 0.04f + frac.width
            box
        }
        TemplateKind.SQRT -> radical(null, layoutRow(t.slots[0], s), s)
        TemplateKind.ROOT -> radical(layoutRow(t.slots[0], scriptSize(s)), layoutRow(t.slots[1], s), s)
        TemplateKind.ABS -> {
            val c = layoutRow(t.slots[0], s)
            delimited(c, '|', '|', s, force = true)
        }
        TemplateKind.LOG_BASE -> {
            val box = Box()
            val name = textBox("log", s, Role.FUNCTION)
            box.place(name, 0f, 0f)
            val base = layoutRow(t.slots[0], scriptSize(s))
            val baseDy = s * 0.3f
            box.place(base, name.width + s * 0.02f, baseDy)
            val arg = delimited(layoutRow(t.slots[1], s), '(', ')', s)
            val ax = name.width + s * 0.02f + base.width + s * 0.04f
            box.place(arg, ax, 0f)
            box.width = ax + arg.width
            box
        }
        TemplateKind.INTEGRAL -> integral(t, s)
        TemplateKind.DERIV, TemplateKind.DERIV2 -> derivative(t, s)
        TemplateKind.SUM, TemplateKind.PRODUCT -> bigOperator(t, s)
        TemplateKind.MATRIX -> matrix(t, s)
        TemplateKind.POWER -> layoutRow(t.slots[0], scriptSize(s))
    }

    private fun fraction(n: Box, d: Box, s: Float): Box {
        val pad = s * 0.1f
        val margin = s * 0.08f
        val barW = max(n.width, d.width) + 2 * pad
        val axis = s * AXIS
        val gap = s * 0.1f
        val stroke = max(1f, s * 0.055f)
        val box = Box(barW + 2 * margin, 0f, 0f)
        val nBase = -axis - gap - stroke / 2 - n.descent
        val dBase = -axis + gap + stroke / 2 + d.ascent
        box.place(n, margin + (barW - n.width) / 2, nBase)
        box.place(d, margin + (barW - d.width) / 2, dBase)
        box.ops += DrawOp.Line(margin, -axis, margin + barW, -axis, stroke)
        return box
    }

    private fun radical(index: Box?, r: Box, s: Float): Box {
        val stroke = max(1f, s * 0.06f)
        val gap = s * 0.12f
        val top = -(r.ascent + gap + stroke / 2)
        val bottom = r.descent + s * 0.04f
        val h = bottom - top
        val signW = s * 0.62f
        val xo = if (index != null) max(0f, index.width - s * 0.3f) else 0f
        val tickY = bottom - min(h * 0.5f, s * 0.55f)
        val box = Box()
        val end = xo + signW + s * 0.06f + r.width + s * 0.1f
        box.ops += DrawOp.Polyline(
            floatArrayOf(
                xo, tickY + s * 0.04f,
                xo + s * 0.16f, tickY - s * 0.05f,
                xo + s * 0.36f, bottom,
                xo + signW, top,
                end, top,
            ),
            stroke,
        )
        box.place(r, xo + signW + s * 0.06f, 0f)
        if (index != null) {
            box.place(index, xo + s * 0.3f - index.width, tickY - s * 0.1f - index.descent)
        }
        box.width = end + s * 0.04f
        box.ascent = max(box.ascent, -top + stroke)
        box.descent = max(box.descent, bottom + stroke)
        return box
    }

    /** Wraps [c] in a pair of delimiters, stretched when the content is taller than text. */
    private fun delimited(c: Box, left: Char, right: Char, s: Float, force: Boolean = false): Box {
        val box = Box()
        val stroke = max(1f, s * 0.06f)
        val tall = c.ascent > s * 0.95f || c.descent > s * 0.34f
        val lw = measure.width(left.toString(), s, Role.NORMAL) + s * 0.04f
        val rw = measure.width(right.toString(), s, Role.NORMAL) + s * 0.04f
        val top = -(max(c.ascent, s * ASC) + s * 0.08f)
        val bottom = max(c.descent, s * DEEP_DESC) + s * 0.08f
        if (tall || force) {
            box.ops += DrawOp.Delimiter(left, 0f, top, bottom, lw, stroke)
            box.ops += DrawOp.Delimiter(right, lw + c.width, top, bottom, rw, stroke)
            box.ascent = -top
            box.descent = bottom
        } else {
            box.ops += DrawOp.Text(left.toString(), 0f, 0f, s, Role.NORMAL)
            box.ops += DrawOp.Text(right.toString(), lw + c.width, 0f, s, Role.NORMAL)
            box.ascent = s * ASC
            box.descent = s * DEEP_DESC
        }
        box.place(c, lw, 0f)
        box.width = lw + c.width + rw
        return box
    }

    private fun integral(t: Template, s: Float): Box {
        val ss = scriptSize(s)
        val lo = layoutRow(t.slots[0], ss)
        val hi = layoutRow(t.slots[1], ss)
        val body = layoutRow(t.slots[2], s)
        val axis = s * AXIS
        val h = max(body.ascent + body.descent + s * 0.5f, s * 1.8f)
        val top = -axis - h / 2
        val bottom = -axis + h / 2
        val signW = s * 0.5f
        val box = Box()
        box.ops += DrawOp.IntegralSign(0f, top, bottom, signW, max(1f, s * 0.065f))
        val limX = signW + s * 0.02f
        box.place(hi, limX + s * 0.06f, top + hi.ascent - s * 0.05f)
        box.place(lo, limX - s * 0.1f, bottom - lo.descent + s * 0.05f)
        val limW = max(hi.width + s * 0.06f, lo.width - s * 0.1f)
        val bx = limX + limW + s * 0.12f
        box.place(body, bx, 0f)
        val dxBox = textBox("dx", s, Role.FUNCTION)
        val dxX = bx + body.width + s * 0.14f
        box.place(dxBox, dxX, 0f)
        box.width = dxX + dxBox.width + s * 0.04f
        box.ascent = max(box.ascent, -top)
        box.descent = max(box.descent, bottom)
        return box
    }

    private fun derivative(t: Template, s: Float): Box {
        val second = t.kind == TemplateKind.DERIV2
        val ddx = fraction(richText(if (second) "d²" else "d", s, Role.FUNCTION), richText(if (second) "dx²" else "dx", s, Role.FUNCTION), s)
        val body = delimited(layoutRow(t.slots[0], s), '(', ')', s)
        val box = Box()
        box.place(ddx, 0f, 0f)
        box.place(body, ddx.width, 0f)
        val barX = ddx.width + body.width + s * 0.06f
        val ss = scriptSize(s)
        val barTop = -max(body.ascent, s * ASC)
        val at = layoutRow(t.slots[1], ss)
        val label = textBox("x=", ss, Role.FUNCTION)
        val barBottom = max(body.descent, s * DEEP_DESC) + s * 0.15f
        val subBase = barBottom - at.descent
        box.ops += DrawOp.Line(barX, barTop, barX, barBottom, max(1f, s * 0.055f))
        box.place(label, barX + s * 0.08f, subBase)
        box.place(at, barX + s * 0.08f + label.width, subBase)
        box.width = barX + s * 0.08f + label.width + at.width + s * 0.04f
        return box
    }

    private fun bigOperator(t: Template, s: Float): Box {
        val ss = scriptSize(s)
        val symSize = s * 1.45f
        val symbol = if (t.kind == TemplateKind.SUM) "Σ" else "Π"
        val symW = measure.width(symbol, symSize, Role.NORMAL)
        val capH = symSize * 0.72f
        val axis = s * AXIS
        val symBase = -axis + capH / 2
        val symTop = symBase - capH

        val lower = Box()
        val varBox = layoutRow(t.slots[0], ss)
        lower.place(varBox, 0f, 0f)
        val eq = textBox("=", ss, Role.OPERATOR)
        lower.place(eq, varBox.width, 0f)
        val start = layoutRow(t.slots[1], ss)
        lower.place(start, varBox.width + eq.width, 0f)
        lower.width = varBox.width + eq.width + start.width
        val upper = layoutRow(t.slots[2], ss)

        val colW = maxOf(symW, lower.width, upper.width)
        val box = Box()
        box.ops += DrawOp.Text(symbol, (colW - symW) / 2, symBase, symSize, Role.NORMAL)
        box.ascent = -symTop
        box.descent = max(0f, symBase)
        box.place(upper, (colW - upper.width) / 2, symTop - s * 0.1f - upper.descent)
        box.place(lower, (colW - lower.width) / 2, symBase + s * 0.12f + lower.ascent)
        val body = delimited(layoutRow(t.slots[3], s), '(', ')', s)
        box.place(body, colW + s * 0.08f, 0f)
        box.width = colW + s * 0.08f + body.width
        return box
    }

    private fun matrix(t: Template, s: Float): Box {
        val cells = t.slots.map { layoutRow(it, s) }
        val colW = FloatArray(t.cols)
        val rowAsc = FloatArray(t.rows)
        val rowDesc = FloatArray(t.rows)
        for (r in 0 until t.rows) for (c in 0 until t.cols) {
            val b = cells[r * t.cols + c]
            colW[c] = max(colW[c], b.width)
            rowAsc[r] = max(rowAsc[r], b.ascent)
            rowDesc[r] = max(rowDesc[r], b.descent)
        }
        val colGap = s * 0.7f
        val rowGap = s * 0.3f
        val bracketW = s * 0.32f
        val inner = s * 0.18f
        val totalH = rowAsc.sum() + rowDesc.sum() + rowGap * (t.rows - 1)
        val axis = s * AXIS
        val top = -axis - totalH / 2
        val box = Box()
        var y = top
        for (r in 0 until t.rows) {
            val base = y + rowAsc[r]
            var x = bracketW + inner
            for (c in 0 until t.cols) {
                val b = cells[r * t.cols + c]
                box.place(b, x + (colW[c] - b.width) / 2, base)
                x += colW[c] + colGap
            }
            y = base + rowDesc[r] + rowGap
        }
        val contentW = colW.sum() + colGap * (t.cols - 1)
        val stroke = max(1f, s * 0.06f)
        val bTop = top - s * 0.1f
        val bBottom = top + totalH + s * 0.1f
        box.ops += DrawOp.Delimiter('[', 0f, bTop, bBottom, bracketW, stroke)
        val rightX = bracketW + inner * 2 + contentW
        box.ops += DrawOp.Delimiter(']', rightX, bTop, bBottom, bracketW, stroke)
        box.width = rightX + bracketW
        box.ascent = max(box.ascent, -bTop)
        box.descent = max(box.descent, bBottom)
        return box
    }
}
