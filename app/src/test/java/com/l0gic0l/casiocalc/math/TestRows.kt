package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens

/**
 * Builds editor rows from a compact text form for tests.
 * Templates: {frac:a|b} {sqrt:a} {pow:a} {root:n|a} {logb:b|a} {abs:a} {int:lo|hi|f}
 * {d:f|x} {sum:X|1|10|f} {mat2x2:a|b|c|d}. '~' is (−), '#' is ×10ˣ, '[' ']' are list braces.
 */
object TestRows {
    fun row(s: String): MathRow {
        val p = P(s)
        val r = p.row()
        require(p.i == s.length) { "trailing input at ${p.i} in $s" }
        return r
    }

    private class P(val s: String) {
        var i = 0

        fun row(): MathRow {
            val nodes = ArrayList<Node>()
            while (i < s.length && s[i] != '|' && s[i] != '}') nodes += item()
            return MathRow(nodes)
        }

        private fun t(tok: com.l0gic0l.casiocalc.math.editor.Tok) = TokenNode(tok)

        fun item(): List<Node> = when (val n = itemOrWord()) {
            is List<*> -> n.filterIsInstance<Node>()
            else -> listOf(n as Node)
        }

        fun itemOrWord(): Any {
            val c = s[i]
            if (c == '{') {
                i++
                val name = s.substring(i, s.indexOf(':', i))
                i += name.length + 1
                val slots = ArrayList<MathRow>()
                slots += row()
                while (s[i] == '|') {
                    i++; slots += row()
                }
                require(s[i] == '}')
                i++
                if (name.startsWith("mat")) {
                    val (r, cc) = name.removePrefix("mat").split('x').map { it.toInt() }
                    return Template.matrix(r, cc, slots)
                }
                val kind = when (name) {
                    "frac" -> TemplateKind.FRACTION
                    "mixed" -> TemplateKind.MIXED
                    "sqrt" -> TemplateKind.SQRT
                    "pow" -> TemplateKind.POWER
                    "root" -> TemplateKind.ROOT
                    "logb" -> TemplateKind.LOG_BASE
                    "abs" -> TemplateKind.ABS
                    "int" -> TemplateKind.INTEGRAL
                    "d" -> TemplateKind.DERIV
                    "d2" -> TemplateKind.DERIV2
                    "sum" -> TemplateKind.SUM
                    "prod" -> TemplateKind.PRODUCT
                    else -> error("template $name")
                }
                return Template(kind, slots)
            }
            i++
            return when (c) {
                in '0'..'9' -> t(Tokens.digit(c - '0'))
                '.' -> t(Tokens.POINT)
                '#' -> t(Tokens.EXP10)
                '+' -> t(Tokens.ADD)
                '-' -> t(Tokens.SUB)
                '*' -> t(Tokens.MUL)
                '/' -> t(Tokens.DIV)
                '~' -> t(Tokens.NEG)
                '(' -> t(Tokens.LPAREN)
                ')' -> t(Tokens.RPAREN)
                ',' -> t(Tokens.COMMA)
                '!' -> t(Tokens.FACT)
                '°' -> t(Tokens.DEG)
                'π' -> t(Tokens.PI)
                '=' -> t(Tokens.EQ)
                '<' -> t(Tokens.LT)
                '>' -> t(Tokens.GT)
                '[' -> t(Tokens.LBRACE)
                ']' -> t(Tokens.RBRACE)
                '→' -> t(Tokens.STORE)
                '∠' -> t(Tokens.ANGLE)
                '²' -> Template.create(TemplateKind.POWER, listOf(t(Tokens.digit(2))))
                'θ' -> t(Tokens.THETA)
                else -> {
                    if (!c.isLetter()) error("unexpected $c")
                    val start = i - 1
                    while (i < s.length && s[i].isLetter()) i++
                    if (s.substring(start, i) == "List") while (i < s.length && s[i].isDigit()) i++
                    val word = s.substring(start, i)
                    if (i < s.length && s[i] == '(' && Tokens.functions.containsKey(word)) {
                        i++
                        return t(Tokens.fn(word))
                    }
                    when {
                        word == "Ans" -> t(Tokens.ANS)
                        word == "i" -> t(Tokens.I)
                        word == "e" -> t(Tokens.E)
                        word == "nPr" -> t(Tokens.NPR)
                        word == "nCr" -> t(Tokens.NCR)
                        word.startsWith("Mat") && word.length == 4 -> t(Tokens.matrix(word[3]))
                        word.startsWith("List") -> t(Tokens.list(word.substring(4).toInt()))
                        word.all { it.isUpperCase() } -> word.map { t(Tokens.letter(it)) }
                        else -> error("unknown word $word")
                    }
                }
            }
        }
    }
}
