package com.l0gic0l.casiocalc.math.basen

import com.l0gic0l.casiocalc.math.mathError
import com.l0gic0l.casiocalc.math.syntaxError

enum class Radix(val base: Int, val label: String, val prefix: Char) {
    DEC(10, "Dec", 'd'), HEX(16, "Hex", 'h'), BIN(2, "Bin", 'b'), OCT(8, "Oct", 'o'),
}

/**
 * 32-bit integer arithmetic of the Base-N mode. Expressions are plain text: digits of the current
 * radix (or a d/h/b/o prefix), + - * /, and, or, xor, xnor, not, Neg and brackets.
 * Results outside the 32-bit signed range raise Math ERROR, like the calculator.
 */
object BaseN {
    private sealed class T {
        data class Num(val v: Long) : T()
        data class Op(val s: String) : T()
    }

    private val words = listOf("xnor", "xor", "and", "or", "not", "Neg")

    private fun tokenize(text: String, radix: Radix): List<T> {
        val out = ArrayList<T>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == ' ' -> i++
                c in "+-*/()" || c == '×' || c == '÷' || c == '−' -> {
                    out += T.Op(when (c) { '×' -> "*"; '÷' -> "/"; '−' -> "-"; else -> c.toString() })
                    i++
                }
                words.any { text.startsWith(it, i) } -> {
                    val w = words.first { text.startsWith(it, i) }
                    out += T.Op(w)
                    i += w.length
                }
                else -> {
                    var r = radix
                    val p = Radix.entries.firstOrNull { it.prefix == c && i + 1 < text.length && digit(text[i + 1], it) >= 0 }
                    if (p != null) {
                        r = p
                        i++
                    }
                    val start = i
                    var v = 0L
                    while (i < text.length && digit(text[i], r) >= 0) {
                        v = v * r.base + digit(text[i], r)
                        if (v > 0xFFFFFFFFL) mathError("out of range")
                        i++
                    }
                    if (i == start) syntaxError("unexpected '$c'")
                    // Non-decimal literals are 32-bit two's complement patterns.
                    out += T.Num(if (r == Radix.DEC) v else v.toInt().toLong())
                }
            }
        }
        return out
    }

    private fun digit(c: Char, r: Radix): Int {
        val d = when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
        return if (d in 0 until r.base) d else -1
    }

    private class P(val t: List<T>) {
        var i = 0
        fun peek() = (t.getOrNull(i) as? T.Op)?.s
        fun op(vararg names: String): String? = peek()?.takeIf { it in names }?.also { i++ }

        fun or(): Long {
            var l = and()
            while (true) {
                when (op("or", "xor", "xnor")) {
                    "or" -> l = l or and()
                    "xor" -> l = l xor and()
                    "xnor" -> l = (l xor and()).inv()
                    else -> return l
                }
            }
        }

        fun and(): Long {
            var l = add()
            while (op("and") != null) l = l and add()
            return l
        }

        fun add(): Long {
            var l = mul()
            while (true) {
                when (op("+", "-")) {
                    "+" -> l = check(l + mul())
                    "-" -> l = check(l - mul())
                    else -> return l
                }
            }
        }

        fun mul(): Long {
            var l = unary()
            while (true) {
                when (op("*", "/")) {
                    "*" -> l = check(l * unary())
                    "/" -> {
                        val r = unary()
                        if (r == 0L) mathError("divide by zero")
                        l = check(l / r)
                    }
                    else -> return l
                }
            }
        }

        fun unary(): Long = when (op("-", "Neg", "not")) {
            "-", "Neg" -> check(-unary())
            "not" -> unary().toInt().inv().toLong()
            else -> atom()
        }

        fun atom(): Long {
            if (op("(") != null) {
                val v = or()
                op(")")
                return v
            }
            val n = t.getOrNull(i) as? T.Num ?: syntaxError("number expected")
            i++
            return n.v
        }
    }

    private fun check(v: Long): Long {
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) mathError("out of range")
        return v
    }

    fun evaluate(text: String, radix: Radix): Int {
        val tokens = tokenize(text, radix)
        if (tokens.isEmpty()) syntaxError("empty")
        val p = P(tokens)
        val v = p.or()
        if (p.i != tokens.size) syntaxError("unexpected input")
        return check(v).toInt()
    }

    /** Text of [v] in [radix]; non-decimal radixes show the 32-bit pattern. */
    fun format(v: Int, radix: Radix): String = when (radix) {
        Radix.DEC -> v.toString()
        Radix.HEX -> Integer.toHexString(v).uppercase().padStart(8, '0')
        Radix.OCT -> Integer.toOctalString(v).padStart(11, '0')
        Radix.BIN -> Integer.toBinaryString(v).padStart(32, '0').chunked(4).joinToString(" ")
    }
}
