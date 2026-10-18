package com.l0gic0l.casiocalc.math.parse

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.TokenKind
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.num.BigRational
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.syntaxError
import java.math.BigInteger

/**
 * Turns an editor tree into an [Expr] using the CG50 priority order, lowest first:
 * → store, or/xor, and, relations, + −, × ÷, nPr nCr ∠, implicit multiplication,
 * prefix (−), powers and postfix functions, atoms. Missing closing brackets are allowed at the end
 * of a row, as on the calculator.
 */
class Parser private constructor(private val lex: List<Lexeme>) {

    private sealed class Lexeme {
        data class Number(val value: BigRational) : Lexeme()
        data class T(val tok: Tok) : Lexeme()
        data class Tp(val t: Template) : Lexeme()
    }

    private var pos = 0

    private fun peek(): Lexeme? = lex.getOrNull(pos)
    private fun peekTok(): Tok? = (peek() as? Lexeme.T)?.tok
    private fun next(): Lexeme = lex[pos++]
    private fun atEnd() = pos >= lex.size

    private fun parseStatement(): Expr {
        var e = parseOr()
        while (true) {
            val t = peekTok() ?: break
            when {
                t.kind == TokenKind.POSTFIX && t.id.startsWith("to") -> {
                    pos++
                    e = Expr.Format(e, t.id)
                }
                t === Tokens.STORE -> {
                    pos++
                    val target = (next() as? Lexeme.T)?.tok ?: syntaxError("store target")
                    if (target.kind != TokenKind.VARIABLE && target.kind != TokenKind.REFERENCE) {
                        syntaxError("store target")
                    }
                    e = Expr.Store(e, target.id)
                }
                else -> break
            }
        }
        if (!atEnd()) syntaxError("unexpected ${peek()}")
        return e
    }

    private fun parseOr(): Expr {
        var l = parseAnd()
        while (true) {
            val t = peekTok()
            if (t === Tokens.OR || t === Tokens.XOR) {
                pos++
                l = Expr.Binary(t.id, l, parseAnd())
            } else return l
        }
    }

    private fun parseAnd(): Expr {
        var l = parseRel()
        while (peekTok() === Tokens.AND) {
            pos++
            l = Expr.Binary("and", l, parseRel())
        }
        return l
    }

    private fun parseRel(): Expr {
        var l = parseAdd()
        while (peekTok()?.kind == TokenKind.RELATION) {
            val op = (next() as Lexeme.T).tok.id
            l = Expr.Binary(op, l, parseAdd())
        }
        return l
    }

    private fun parseAdd(): Expr {
        var l = parseMul()
        while (true) {
            val t = peekTok()
            if (t === Tokens.ADD || t === Tokens.SUB) {
                pos++
                l = Expr.Binary(t.id, l, parseMul())
            } else return l
        }
    }

    private fun parseMul(): Expr {
        var l = parsePerm()
        while (true) {
            val t = peekTok()
            if (t === Tokens.MUL || t === Tokens.DIV) {
                pos++
                l = Expr.Binary(t.id, l, parsePerm())
            } else return l
        }
    }

    private fun parsePerm(): Expr {
        var l = parseImplicit()
        while (true) {
            val t = peekTok()
            if (t === Tokens.NPR || t === Tokens.NCR || t === Tokens.ANGLE) {
                pos++
                l = Expr.Binary(t.id, l, parseImplicit())
            } else return l
        }
    }

    private fun parseImplicit(): Expr {
        var l = parseUnary()
        while (startsAtom()) {
            l = Expr.Binary("imul", l, parseUnaryNoMinus())
        }
        return l
    }

    private fun parseUnaryNoMinus(): Expr {
        if (peekTok() === Tokens.NOT) {
            pos++
            return Expr.Unary("not", parseUnary())
        }
        return parsePostfix()
    }

    private fun parseUnary(): Expr {
        val t = peekTok()
        if (t === Tokens.NEG || t === Tokens.SUB) {
            pos++
            return Expr.Unary("neg", parseUnary())
        }
        if (t === Tokens.ADD) {
            pos++
            return parseUnary()
        }
        if (t === Tokens.NOT) {
            pos++
            return Expr.Unary("not", parseUnary())
        }
        return parsePostfix()
    }

    private fun startsAtom(): Boolean = when (val l = peek()) {
        null -> false
        is Lexeme.Number -> true
        is Lexeme.Tp -> l.t.kind != TemplateKind.POWER
        is Lexeme.T -> when (l.tok.kind) {
            TokenKind.VARIABLE, TokenKind.CONSTANT, TokenKind.REFERENCE, TokenKind.LPAREN,
            TokenKind.FUNCTION, TokenKind.LBRACE -> true
            else -> false
        }
    }

    private fun parsePostfix(): Expr {
        var e = parseAtom()
        while (true) {
            val l = peek()
            if (l is Lexeme.Tp && l.t.kind == TemplateKind.POWER) {
                pos++
                e = Expr.Binary("pow", e, parseRow(l.t.slots[0]))
                continue
            }
            val t = (l as? Lexeme.T)?.tok ?: break
            if (t.kind == TokenKind.POSTFIX && !t.id.startsWith("to")) {
                pos++
                e = Expr.Postfix(t.id, e)
            } else break
        }
        return e
    }

    private fun parseAtom(): Expr {
        val l = peek() ?: syntaxError("missing operand")
        when (l) {
            is Lexeme.Number -> {
                pos++
                return parseDmsTail(l.value)
            }
            is Lexeme.Tp -> {
                pos++
                return parseTemplate(l.t)
            }
            is Lexeme.T -> {
                val tok = l.tok
                when (tok.kind) {
                    TokenKind.VARIABLE -> { pos++; return Expr.Var(tok.id) }
                    TokenKind.CONSTANT -> {
                        pos++
                        return if (tok === Tokens.TEN) Expr.Num(Real.of(10)) else Expr.Const(tok.id)
                    }
                    TokenKind.REFERENCE -> { pos++; return Expr.Ref(tok.id) }
                    TokenKind.LPAREN -> {
                        pos++
                        val e = parseOr()
                        closeParen()
                        return e
                    }
                    TokenKind.FUNCTION -> {
                        pos++
                        val args = mutableListOf<Expr>()
                        if (peekTok()?.kind != TokenKind.RPAREN && !atEnd()) {
                            args += parseOr()
                            while (peekTok() === Tokens.COMMA) {
                                pos++
                                args += parseOr()
                            }
                        }
                        closeParen()
                        return Expr.Call(tok.id, args)
                    }
                    TokenKind.LBRACE -> {
                        pos++
                        val items = mutableListOf<Expr>()
                        if (peekTok()?.kind != TokenKind.RBRACE && !atEnd()) {
                            items += parseOr()
                            while (peekTok() === Tokens.COMMA) {
                                pos++
                                items += parseOr()
                            }
                        }
                        if (peekTok()?.kind == TokenKind.RBRACE) pos++
                        else if (!atEnd()) syntaxError("missing }")
                        return Expr.ListLit(items)
                    }
                    else -> syntaxError("unexpected ${tok.display}")
                }
            }
        }
    }

    private fun closeParen() {
        if (peekTok()?.kind == TokenKind.RPAREN) pos++
        else if (!atEnd() && peekTok()?.kind != TokenKind.STORE &&
            !(peekTok()?.kind == TokenKind.POSTFIX && peekTok()!!.id.startsWith("to"))
        ) syntaxError("missing )")
    }

    /** Handles degree-minute-second entry such as 12°34°56°. */
    private fun parseDmsTail(first: BigRational): Expr {
        if (peekTok()?.kind != TokenKind.DMS) return Expr.Num(Real.of(first))
        pos++
        var total = first
        var divisor = BigRational.of(60)
        repeat(2) {
            val n = peek() as? Lexeme.Number ?: return Expr.Postfix("dmsDeg", Expr.Num(Real.of(total)))
            pos++
            total += n.value / divisor
            divisor *= BigRational.of(60)
            if (peekTok()?.kind == TokenKind.DMS) pos++
        }
        return Expr.Postfix("dmsDeg", Expr.Num(Real.of(total)))
    }

    private fun parseTemplate(t: Template): Expr {
        val s = t.slots
        return when (t.kind) {
            TemplateKind.MATRIX -> Expr.MatLit(t.rows, t.cols, s.map { parseRow(it) })
            TemplateKind.SUM, TemplateKind.PRODUCT -> {
                val v = s[0].nodes.singleOrNull() as? TokenNode
                if (v == null || v.tok.kind != TokenKind.VARIABLE) syntaxError("Σ variable")
                Expr.Tpl(t.kind, listOf(null, parseRow(s[1]), parseRow(s[2]), parseRow(s[3])), v.tok.id)
            }
            TemplateKind.INTEGRAL, TemplateKind.DERIV, TemplateKind.DERIV2 ->
                Expr.Tpl(t.kind, s.map { parseRow(it) }, "X")
            TemplateKind.POWER -> syntaxError("power without base")
            else -> Expr.Tpl(t.kind, s.map { parseRow(it) })
        }
    }

    companion object {
        fun parse(row: MathRow): Expr = parseRow(row)

        private fun parseRow(row: MathRow): Expr {
            val lexemes = lexRow(row)
            if (lexemes.isEmpty()) syntaxError("empty")
            return Parser(lexemes).parseStatement()
        }

        private fun lexRow(row: MathRow): List<Lexeme> {
            val out = ArrayList<Lexeme>()
            val nodes = row.nodes
            var i = 0
            while (i < nodes.size) {
                val n = nodes[i]
                if (n is Template) {
                    out += Lexeme.Tp(n)
                    i++
                    continue
                }
                val tok = (n as TokenNode).tok
                if (tok.kind == TokenKind.DIGIT || tok.kind == TokenKind.POINT || tok.kind == TokenKind.EXP10) {
                    val sb = StringBuilder()
                    var sawPoint = false
                    while (i < nodes.size) {
                        val k = (nodes[i] as? TokenNode)?.tok ?: break
                        if (k.kind == TokenKind.DIGIT) sb.append(k.id)
                        else if (k.kind == TokenKind.POINT) {
                            if (sawPoint) syntaxError("two decimal points")
                            sawPoint = true
                            sb.append('.')
                        } else break
                        i++
                    }
                    var value = when {
                        sb.isEmpty() -> BigRational.ONE
                        sb.toString() == "." -> syntaxError("lone point")
                        else -> BigRational.parseDecimal(sb.toString())
                    }
                    val expTok = (nodes.getOrNull(i) as? TokenNode)?.tok
                    if (expTok?.kind == TokenKind.EXP10) {
                        i++
                        var negative = false
                        val signTok = (nodes.getOrNull(i) as? TokenNode)?.tok
                        if (signTok === Tokens.NEG || signTok === Tokens.SUB) {
                            negative = true; i++
                        } else if (signTok === Tokens.ADD) i++
                        val digits = StringBuilder()
                        while (i < nodes.size) {
                            val k = (nodes[i] as? TokenNode)?.tok ?: break
                            if (k.kind != TokenKind.DIGIT) break
                            digits.append(k.id); i++
                        }
                        if (digits.isEmpty()) syntaxError("exponent")
                        val e = digits.toString().toInt().let { if (negative) -it else it }
                        if (e > 400) syntaxError("exponent")
                        val scale = BigRational.of(BigInteger.TEN.pow(kotlin.math.abs(e)))
                        value = if (e >= 0) value * scale else value / scale
                    }
                    out += Lexeme.Number(value)
                    continue
                }
                out += Lexeme.T(tok)
                i++
            }
            return out
        }
    }
}
