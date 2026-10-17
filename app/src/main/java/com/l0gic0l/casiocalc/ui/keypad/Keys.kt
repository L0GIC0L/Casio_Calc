package com.l0gic0l.casiocalc.ui.keypad

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.Tok
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens

enum class Cmd {
    SHIFT, ALPHA, ALPHA_LOCK,
    LEFT, RIGHT, UP, DOWN, HOME, END,
    DEL, AC, EXE, EXE_DECIMAL,
    OPTN, VARS, SETUP, SD,
    MAT_PICK, LIST_PICK, MAT_TEMPLATE,
}

/** What a key press does. */
sealed interface KeyAction {
    data class Insert(val toks: List<Tok>) : KeyAction {
        constructor(vararg t: Tok) : this(t.toList())
    }

    data class InsertTemplate(val kind: TemplateKind, val prefill: List<List<Tok>> = emptyList()) : KeyAction

    /** Several actions in order, e.g. e followed by an empty exponent. */
    data class Sequence(val actions: List<KeyAction>) : KeyAction

    /** The X,θ,T key: X in most places, θ for polar graphs, T for parametric ones. */
    data object XVar : KeyAction

    data class Command(val cmd: Cmd) : KeyAction
}

sealed interface KeyLabel {
    data class Text(val text: String) : KeyLabel
    data class Math(val row: MathRow) : KeyLabel

    /** An icon, optionally with a short caption under it; [description] is read by TalkBack. */
    data class Icon(val image: ImageVector, val description: String, val caption: String? = null) : KeyLabel
}

enum class KeyStyle { FUNCTION, NUMBER, OPERATOR, DELETE, CLEAR, EXE, SHIFT, ALPHA, NAV }

data class KeySpec(
    val label: KeyLabel,
    val action: KeyAction,
    val style: KeyStyle = KeyStyle.FUNCTION,
    val shiftLabel: String? = null,
    val shift: KeyAction? = null,
    val alphaLabel: String? = null,
    val alpha: KeyAction? = null,
    /** Main label of the shift function when it needs a picture (shown while SHIFT is active). */
    val shiftMath: MathRow? = null,
)

/** The calculator keyboard, modelled on the CG50 but reflowed for a phone. */
object Keys {
    private fun text(s: String) = KeyLabel.Text(s)
    private fun ins(vararg t: Tok) = KeyAction.Insert(*t)
    private fun tpl(kind: TemplateKind, vararg prefill: List<Tok>) = KeyAction.InsertTemplate(kind, prefill.toList())
    private fun cmd(c: Cmd) = KeyAction.Command(c)
    private fun letter(c: Char) = ins(Tokens.letter(c))

    private fun placeholderRow(kind: TemplateKind, vararg prefill: List<Tok>) =
        MathRow(listOf(Template.create(kind, *prefill.map { l -> l.map { TokenNode(it) } }.toTypedArray())))

    val fractionLabel = placeholderRow(TemplateKind.FRACTION)
    private val sqrtLabel = placeholderRow(TemplateKind.SQRT)
    private val powerLabel = MathRow(listOf(TokenNode(Tokens.letter('X')), Template.create(TemplateKind.POWER)))

    val e = KeyAction.Sequence(listOf(ins(Tokens.E), tpl(TemplateKind.POWER)))
    val tenPow = KeyAction.Sequence(listOf(ins(Tokens.TEN), tpl(TemplateKind.POWER)))
    val square = tpl(TemplateKind.POWER, listOf(Tokens.digit(2)))
    val inverse = tpl(TemplateKind.POWER, listOf(Tokens.NEG, Tokens.digit(1)))

    /** Six-column function rows. */
    val functionRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec(text("SHIFT"), cmd(Cmd.SHIFT), KeyStyle.SHIFT),
            KeySpec(text("ALPHA"), cmd(Cmd.ALPHA), KeyStyle.ALPHA, shiftLabel = "A-LOCK", shift = cmd(Cmd.ALPHA_LOCK)),
            KeySpec(KeyLabel.Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left"), cmd(Cmd.LEFT), KeyStyle.NAV, shiftLabel = "|◀", shift = cmd(Cmd.HOME)),
            KeySpec(KeyLabel.Icon(Icons.Filled.KeyboardArrowUp, "Up"), cmd(Cmd.UP), KeyStyle.NAV),
            KeySpec(KeyLabel.Icon(Icons.Filled.KeyboardArrowDown, "Down"), cmd(Cmd.DOWN), KeyStyle.NAV),
            KeySpec(KeyLabel.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right"), cmd(Cmd.RIGHT), KeyStyle.NAV, shiftLabel = "▶|", shift = cmd(Cmd.END)),
        ),
        listOf(
            KeySpec(text("X,θ,T"), KeyAction.XVar, alphaLabel = "A", alpha = letter('A')),
            KeySpec(text("log"), ins(Tokens.fn("log")), shiftLabel = "10ˣ", shift = tenPow, alphaLabel = "B", alpha = letter('B')),
            KeySpec(text("ln"), ins(Tokens.fn("ln")), shiftLabel = "eˣ", shift = e, alphaLabel = "C", alpha = letter('C')),
            KeySpec(text("sin"), ins(Tokens.fn("sin")), shiftLabel = "sin⁻¹", shift = ins(Tokens.fn("asin")), alphaLabel = "D", alpha = letter('D')),
            KeySpec(text("cos"), ins(Tokens.fn("cos")), shiftLabel = "cos⁻¹", shift = ins(Tokens.fn("acos")), alphaLabel = "E", alpha = letter('E')),
            KeySpec(text("tan"), ins(Tokens.fn("tan")), shiftLabel = "tan⁻¹", shift = ins(Tokens.fn("atan")), alphaLabel = "F", alpha = letter('F')),
        ),
        listOf(
            KeySpec(KeyLabel.Math(fractionLabel), tpl(TemplateKind.FRACTION), shiftLabel = "a b/c", shift = tpl(TemplateKind.MIXED), alphaLabel = "G", alpha = letter('G'),
                shiftMath = placeholderRow(TemplateKind.MIXED)),
            KeySpec(KeyLabel.Math(sqrtLabel), tpl(TemplateKind.SQRT), shiftLabel = "ˣ√", shift = tpl(TemplateKind.ROOT), alphaLabel = "H", alpha = letter('H'),
                shiftMath = placeholderRow(TemplateKind.ROOT)),
            KeySpec(text("x²"), square, shiftLabel = "∛", shift = tpl(TemplateKind.ROOT, listOf(Tokens.digit(3))), alphaLabel = "I", alpha = letter('I'),
                shiftMath = placeholderRow(TemplateKind.ROOT, listOf(Tokens.digit(3)))),
            KeySpec(KeyLabel.Math(powerLabel), tpl(TemplateKind.POWER), shiftLabel = "logₐb", shift = tpl(TemplateKind.LOG_BASE), alphaLabel = "J", alpha = letter('J'),
                shiftMath = placeholderRow(TemplateKind.LOG_BASE)),
            KeySpec(text("("), ins(Tokens.LPAREN), shiftLabel = "{", shift = ins(Tokens.LBRACE), alphaLabel = "K", alpha = letter('K')),
            KeySpec(text(")"), ins(Tokens.RPAREN), shiftLabel = "}", shift = ins(Tokens.RBRACE), alphaLabel = "L", alpha = letter('L')),
        ),
        listOf(
            KeySpec(KeyLabel.Icon(Icons.Filled.Functions, "Options", "OPTN"), cmd(Cmd.OPTN), shiftLabel = "SET UP", shift = cmd(Cmd.SETUP)),
            KeySpec(KeyLabel.Icon(Icons.Filled.DataObject, "Variables", "VARS"), cmd(Cmd.VARS)),
            KeySpec(text("S⇔D"), cmd(Cmd.SD), shiftLabel = "°′″", shift = ins(Tokens.DMS)),
            KeySpec(text("x⁻¹"), inverse, shiftLabel = "x!", shift = ins(Tokens.FACT), alphaLabel = "M", alpha = letter('M')),
            KeySpec(text(","), ins(Tokens.COMMA), shiftLabel = "=", shift = ins(Tokens.EQ), alphaLabel = "N", alpha = letter('N')),
            KeySpec(text("→"), ins(Tokens.STORE), shiftLabel = "∠", shift = ins(Tokens.ANGLE), alphaLabel = "O", alpha = letter('O')),
        ),
    )

    /** Five-column number rows. */
    val numberRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec(text("7"), ins(Tokens.digit(7)), KeyStyle.NUMBER, shiftLabel = "∫", shift = tpl(TemplateKind.INTEGRAL), alphaLabel = "P", alpha = letter('P'),
                shiftMath = placeholderRow(TemplateKind.INTEGRAL)),
            KeySpec(text("8"), ins(Tokens.digit(8)), KeyStyle.NUMBER, shiftLabel = "d/dx", shift = tpl(TemplateKind.DERIV), alphaLabel = "Q", alpha = letter('Q')),
            KeySpec(text("9"), ins(Tokens.digit(9)), KeyStyle.NUMBER, shiftLabel = "Σ", shift = tpl(TemplateKind.SUM, listOf(Tokens.letter('X'))), alphaLabel = "R", alpha = letter('R')),
            KeySpec(KeyLabel.Icon(Icons.AutoMirrored.Filled.Backspace, "Delete"), cmd(Cmd.DEL), KeyStyle.DELETE),
            KeySpec(text("AC"), cmd(Cmd.AC), KeyStyle.CLEAR),
        ),
        listOf(
            KeySpec(text("4"), ins(Tokens.digit(4)), KeyStyle.NUMBER, shiftLabel = "nCr", shift = ins(Tokens.NCR), alphaLabel = "S", alpha = letter('S')),
            KeySpec(text("5"), ins(Tokens.digit(5)), KeyStyle.NUMBER, shiftLabel = "nPr", shift = ins(Tokens.NPR), alphaLabel = "T", alpha = letter('T')),
            KeySpec(text("6"), ins(Tokens.digit(6)), KeyStyle.NUMBER, shiftLabel = "|x|", shift = tpl(TemplateKind.ABS), alphaLabel = "U", alpha = letter('U')),
            KeySpec(text("×"), ins(Tokens.MUL), KeyStyle.OPERATOR, shiftLabel = "°", shift = ins(Tokens.DEG), alphaLabel = "V", alpha = letter('V')),
            KeySpec(text("÷"), ins(Tokens.DIV), KeyStyle.OPERATOR, shiftLabel = "Ran#", shift = ins(Tokens.RAN), alphaLabel = "W", alpha = letter('W')),
        ),
        listOf(
            KeySpec(text("1"), ins(Tokens.digit(1)), KeyStyle.NUMBER, shiftLabel = "List", shift = cmd(Cmd.LIST_PICK), alphaLabel = "X", alpha = letter('X')),
            KeySpec(text("2"), ins(Tokens.digit(2)), KeyStyle.NUMBER, shiftLabel = "Mat", shift = cmd(Cmd.MAT_PICK), alphaLabel = "Y", alpha = letter('Y')),
            KeySpec(text("3"), ins(Tokens.digit(3)), KeyStyle.NUMBER, shiftLabel = "[▦]", shift = cmd(Cmd.MAT_TEMPLATE), alphaLabel = "Z", alpha = letter('Z')),
            KeySpec(text("+"), ins(Tokens.ADD), KeyStyle.OPERATOR, shiftLabel = "e", shift = ins(Tokens.E), alphaLabel = "θ", alpha = ins(Tokens.THETA)),
            KeySpec(text("−"), ins(Tokens.SUB), KeyStyle.OPERATOR, alphaLabel = "r", alpha = ins(Tokens.R_VAR)),
        ),
        listOf(
            KeySpec(text("0"), ins(Tokens.digit(0)), KeyStyle.NUMBER, shiftLabel = "i", shift = ins(Tokens.I)),
            KeySpec(text("."), ins(Tokens.POINT), KeyStyle.NUMBER),
            KeySpec(text("×10ˣ"), ins(Tokens.EXP10), KeyStyle.NUMBER, shiftLabel = "π", shift = ins(Tokens.PI)),
            KeySpec(text("(−)"), ins(Tokens.NEG), KeyStyle.NUMBER, shiftLabel = "Ans", shift = ins(Tokens.ANS)),
            KeySpec(text("="), cmd(Cmd.EXE), KeyStyle.EXE, shiftLabel = "≈", shift = cmd(Cmd.EXE_DECIMAL)),
        ),
    )

    /** Keys that, pressed on an empty line right after a result, continue from Ans like the calculator. */
    fun continuesFromAns(action: KeyAction): Boolean = when (action) {
        is KeyAction.Insert -> action.toks.firstOrNull().let {
            it === Tokens.ADD || it === Tokens.SUB || it === Tokens.MUL || it === Tokens.DIV ||
                it === Tokens.FACT || it === Tokens.STORE || it === Tokens.NPR || it === Tokens.NCR
        }
        is KeyAction.InsertTemplate -> action.kind == TemplateKind.POWER
        else -> false
    }
}
