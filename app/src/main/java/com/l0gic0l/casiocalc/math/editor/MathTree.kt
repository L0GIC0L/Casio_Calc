package com.l0gic0l.casiocalc.math.editor

/** A horizontal run of nodes: the whole input, or one slot of a template. */
class MathRow(initial: List<Node> = emptyList()) {
    val nodes: MutableList<Node> = ArrayList()

    /** Template this row is a slot of, or null for a root row. */
    var owner: Template? = null
        internal set

    init {
        initial.forEach { add(it) }
    }

    val size get() = nodes.size
    val isEmpty get() = nodes.isEmpty()

    fun add(node: Node) = insert(nodes.size, node)

    fun insert(index: Int, node: Node) {
        node.parent = this
        nodes.add(index, node)
    }

    fun removeAt(index: Int): Node = nodes.removeAt(index).also { it.parent = null }

    fun copy(): MathRow = MathRow(nodes.map { it.copy() })

    /** Index of this row inside its owner's slot list. */
    val slotIndex: Int get() = owner?.slots?.indexOf(this) ?: -1

    override fun toString() = nodes.joinToString("")
}

sealed class Node {
    var parent: MathRow? = null
        internal set

    abstract fun copy(): Node
}

class TokenNode(val tok: Tok) : Node() {
    override fun copy() = TokenNode(tok)
    override fun toString() = tok.display
}

enum class TemplateKind(val slotCount: Int) {
    FRACTION(2),      // numerator, denominator
    MIXED(3),         // whole, numerator, denominator
    POWER(1),         // exponent (applies to the preceding node)
    SQRT(1),          // radicand
    ROOT(2),          // index, radicand
    LOG_BASE(2),      // base, argument
    ABS(1),
    INTEGRAL(3),      // lower, upper, integrand (variable X)
    DERIV(2),         // expression, point (variable X)
    DERIV2(2),
    SUM(4),           // variable, start, end, body
    PRODUCT(4),
    MATRIX(0),        // rows × cols cells
}

class Template(val kind: TemplateKind, val slots: List<MathRow>, val rows: Int = 0, val cols: Int = 0) : Node() {
    init {
        slots.forEach { it.owner = this }
    }

    override fun copy() = Template(kind, slots.map { it.copy() }, rows, cols)

    /** Slot reached by pressing ▲ from [slot], or -1. */
    fun slotAbove(slot: Int): Int = when (kind) {
        TemplateKind.FRACTION -> if (slot == 1) 0 else -1
        TemplateKind.MIXED -> if (slot == 2) 1 else -1
        TemplateKind.INTEGRAL -> if (slot == 0) 1 else -1
        TemplateKind.SUM, TemplateKind.PRODUCT -> if (slot == 0 || slot == 1) 2 else -1
        TemplateKind.ROOT -> if (slot == 1) 0 else -1
        TemplateKind.DERIV, TemplateKind.DERIV2 -> if (slot == 1) 0 else -1
        TemplateKind.LOG_BASE -> if (slot == 0) 1 else -1
        TemplateKind.MATRIX -> if (slot >= cols) slot - cols else -1
        else -> -1
    }

    /** Slot reached by pressing ▼ from [slot], or -1. */
    fun slotBelow(slot: Int): Int = when (kind) {
        TemplateKind.FRACTION -> if (slot == 0) 1 else -1
        TemplateKind.MIXED -> if (slot == 1) 2 else -1
        TemplateKind.INTEGRAL -> if (slot == 1) 0 else -1
        TemplateKind.SUM, TemplateKind.PRODUCT -> if (slot == 2) 1 else -1
        TemplateKind.ROOT -> if (slot == 0) 1 else -1
        TemplateKind.DERIV, TemplateKind.DERIV2 -> if (slot == 0) 1 else -1
        TemplateKind.LOG_BASE -> if (slot == 1) 0 else -1
        TemplateKind.MATRIX -> if (slot + cols < slots.size) slot + cols else -1
        else -> -1
    }

    override fun toString() = "${kind.name}${slots.joinToString(prefix = "[", postfix = "]") { it.toString() }}"

    companion object {
        fun create(kind: TemplateKind, vararg prefilled: List<Node>): Template {
            val count = kind.slotCount
            val slots = List(count) { i -> MathRow(prefilled.getOrNull(i) ?: emptyList()) }
            return Template(kind, slots)
        }

        fun matrix(rows: Int, cols: Int, cells: List<MathRow>? = null): Template {
            val slots = cells ?: List(rows * cols) { MathRow() }
            return Template(TemplateKind.MATRIX, slots, rows, cols)
        }
    }
}

fun tokens(vararg toks: Tok): List<Node> = toks.map { TokenNode(it) }

fun rowOf(vararg nodes: Node) = MathRow(nodes.toList())
