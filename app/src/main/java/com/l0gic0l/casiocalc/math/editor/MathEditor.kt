package com.l0gic0l.casiocalc.math.editor

/**
 * Cursor-based editing of a [MathRow] tree, the model behind the natural display.
 * The cursor sits between nodes: before node [index] of [row].
 */
class MathEditor(root: MathRow = MathRow()) {
    var root: MathRow = root
        private set
    var row: MathRow = root
        private set
    var index: Int = 0
        private set

    val isEmpty get() = root.isEmpty

    fun setCursor(row: MathRow, index: Int) {
        this.row = row
        this.index = index.coerceIn(0, row.size)
    }

    fun clear() {
        root = MathRow()
        row = root
        index = 0
    }

    /** Replaces the content with a copy of [content] and puts the cursor at the end. */
    fun load(content: MathRow) {
        root = content.copy()
        row = root
        index = root.size
    }

    fun moveToEnd() {
        row = root
        index = root.size
    }

    fun moveToStart() {
        row = root
        index = 0
    }

    fun insert(tok: Tok) {
        row.insert(index, TokenNode(tok))
        index++
    }

    fun insertAll(toks: List<Tok>) = toks.forEach { insert(it) }

    /**
     * Inserts a template. A fraction takes the operand left of the cursor as its numerator when
     * there is one. [prefill] fills slots in order; the cursor lands in the first empty slot, or after
     * the template when every slot is filled.
     */
    fun insertTemplate(kind: TemplateKind, vararg prefill: List<Tok>) {
        val template = Template.create(kind, *prefill.map { list -> list.map { TokenNode(it) } }.toTypedArray())
        if (kind == TemplateKind.FRACTION && prefill.isEmpty()) {
            val start = operandStart(row, index)
            if (start < index) {
                val taken = (start until index).map { row.removeAt(start) }
                taken.forEach { template.slots[0].add(it) }
                index = start
            }
        }
        row.insert(index, template)
        index++
        val firstEmpty = template.slots.indexOfFirst { it.isEmpty }
        if (firstEmpty >= 0) setCursor(template.slots[firstEmpty], 0)
    }

    fun insertMatrix(rows: Int, cols: Int) {
        val template = Template.matrix(rows, cols)
        row.insert(index, template)
        index++
        setCursor(template.slots[0], 0)
    }

    /** Inserts a copy of [content] at the cursor. */
    fun insertRow(content: MathRow) {
        content.copy().nodes.toList().forEach {
            row.insert(index, it)
            index++
        }
    }

    fun left() {
        if (index > 0) {
            val prev = row.nodes[index - 1]
            if (prev is Template && prev.slots.isNotEmpty()) {
                val slot = prev.slots.last()
                setCursor(slot, slot.size)
            } else {
                index--
            }
            return
        }
        val owner = row.owner ?: run {
            // Wrap around like the calculator does.
            index = row.size
            return
        }
        val s = row.slotIndex
        if (s > 0) {
            val prev = owner.slots[s - 1]
            setCursor(prev, prev.size)
        } else {
            exitBefore(owner)
        }
    }

    fun right() {
        if (index < row.size) {
            val next = row.nodes[index]
            if (next is Template && next.slots.isNotEmpty()) {
                setCursor(next.slots.first(), 0)
            } else {
                index++
            }
            return
        }
        val owner = row.owner ?: run {
            index = 0
            return
        }
        val s = row.slotIndex
        if (s < owner.slots.size - 1) {
            setCursor(owner.slots[s + 1], 0)
        } else {
            exitAfter(owner)
        }
    }

    /** Moves into the slot above; returns false when there is none (callers may use ▲ for history). */
    fun up(): Boolean = vertical { t, s -> t.slotAbove(s) }

    fun down(): Boolean = vertical { t, s -> t.slotBelow(s) }

    private inline fun vertical(target: (Template, Int) -> Int): Boolean {
        var r: MathRow = row
        while (true) {
            val owner = r.owner ?: return false
            val t = target(owner, r.slotIndex)
            if (t >= 0) {
                val dest = owner.slots[t]
                val newIndex = if (r === row) index.coerceAtMost(dest.size) else dest.size
                setCursor(dest, newIndex)
                return true
            }
            r = owner.parent ?: return false
        }
    }

    /** DEL: deletes left of the cursor, entering or dissolving templates as needed. */
    fun backspace() {
        if (index > 0) {
            val prev = row.nodes[index - 1]
            if (prev is Template && prev.slots.any { !it.isEmpty }) {
                // Delete through the template's last entry, like deleting x² removes the ².
                val slot = prev.slots.last { !it.isEmpty }
                setCursor(slot, slot.size)
                backspace()
                val parentRow = prev.parent
                if (prev.slots.all { it.isEmpty } && parentRow != null) {
                    val pos = parentRow.nodes.indexOf(prev)
                    parentRow.removeAt(pos)
                    setCursor(parentRow, pos)
                }
                return
            }
            row.removeAt(index - 1)
            index--
            return
        }
        val owner = row.owner ?: return
        val s = row.slotIndex
        if (s > 0) {
            // Keep deleting into the previous slot so every DEL removes something.
            val prev = owner.slots[s - 1]
            setCursor(prev, prev.size)
            backspace()
            return
        }
        if (owner.kind == TemplateKind.MATRIX) {
            if (owner.slots.all { it.isEmpty }) {
                val p = owner.parent ?: return
                val pos = p.nodes.indexOf(owner)
                p.removeAt(pos)
                setCursor(p, pos)
            } else {
                exitBefore(owner)
            }
            return
        }
        dissolve(owner)
    }

    /** Replaces a template by the concatenated contents of its slots. */
    private fun dissolve(t: Template) {
        val parentRow = t.parent ?: return
        var pos = parentRow.nodes.indexOf(t)
        parentRow.removeAt(pos)
        val cursorPos = pos
        // Keep whatever the user typed; summation variables and default prefill are dropped.
        val keep = when (t.kind) {
            TemplateKind.SUM, TemplateKind.PRODUCT, TemplateKind.INTEGRAL -> listOf(t.slots.last())
            TemplateKind.DERIV, TemplateKind.DERIV2 -> listOf(t.slots[0])
            TemplateKind.ROOT -> listOf(t.slots[1])
            else -> t.slots
        }
        for (slot in keep) {
            for (n in slot.nodes.toList()) {
                slot.nodes.remove(n)
                parentRow.insert(pos++, n)
            }
        }
        setCursor(parentRow, cursorPos)
    }

    private fun exitBefore(t: Template) {
        val p = t.parent ?: return
        setCursor(p, p.nodes.indexOf(t))
    }

    private fun exitAfter(t: Template) {
        val p = t.parent ?: return
        setCursor(p, p.nodes.indexOf(t) + 1)
    }

    /** True when the cursor is at the very end of the root row. */
    val atEnd get() = row === root && index == root.size

    companion object {
        /**
         * Start index of the operand that ends at [end] in [row]: a run of number parts, variables,
         * constants and templates, or one bracketed group.
         */
        fun operandStart(row: MathRow, end: Int): Int {
            var i = end
            while (i > 0) {
                val n = row.nodes[i - 1]
                if (n is Template) {
                    i--
                    continue
                }
                val tok = (n as TokenNode).tok
                when (tok.kind) {
                    TokenKind.DIGIT, TokenKind.POINT, TokenKind.EXP10, TokenKind.VARIABLE,
                    TokenKind.CONSTANT, TokenKind.REFERENCE, TokenKind.POSTFIX, TokenKind.DMS -> i--
                    TokenKind.RPAREN -> {
                        var depth = 0
                        var j = i - 1
                        while (j >= 0) {
                            val k = (row.nodes[j] as? TokenNode)?.tok?.kind
                            if (k == TokenKind.RPAREN) depth++
                            if (k == TokenKind.LPAREN || k == TokenKind.FUNCTION) {
                                depth--
                                if (depth == 0) break
                            }
                            j--
                        }
                        if (j < 0) return i
                        i = j
                    }
                    else -> return i
                }
            }
            return i
        }
    }
}
