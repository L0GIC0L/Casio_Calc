package com.l0gic0l.casiocalc.math.editor

enum class TokenKind {
    DIGIT, POINT, EXP10,
    BINARY,      // + − × ÷ P C ∠ and or xor
    RELATION,    // = ≠ > < ≥ ≤
    PREFIX,      // (−) not
    POSTFIX,     // ! ° ʳ ᵍ ▸DMS …
    DMS,         // the °′″ entry mark
    LPAREN, RPAREN, COMMA, LBRACE, RBRACE,
    VARIABLE, CONSTANT, FUNCTION, REFERENCE, STORE,
}

/**
 * One key-press worth of input. [display] is what the natural display draws; function tokens
 * carry their opening parenthesis like on the calculator ("sin(").
 */
class Tok(val id: String, val display: String, val kind: TokenKind) {
    /** Name of the function for FUNCTION tokens, otherwise the id. */
    val name: String get() = id
    override fun toString() = display
}

object Tokens {
    private val all = LinkedHashMap<String, Tok>()

    private fun t(id: String, display: String, kind: TokenKind): Tok {
        val tok = Tok(id, display, kind)
        all[id] = tok
        return tok
    }

    val digits = (0..9).map { t("$it", "$it", TokenKind.DIGIT) }
    val POINT = t(".", ".", TokenKind.POINT)
    val EXP10 = t("E", "E", TokenKind.EXP10)

    val ADD = t("+", "+", TokenKind.BINARY)
    val SUB = t("-", "−", TokenKind.BINARY)
    val MUL = t("*", "×", TokenKind.BINARY)
    val DIV = t("/", "÷", TokenKind.BINARY)
    val NPR = t("nPr", "P", TokenKind.BINARY)
    val NCR = t("nCr", "C", TokenKind.BINARY)
    val ANGLE = t("angle", "∠", TokenKind.BINARY)
    val AND = t("and", " and ", TokenKind.BINARY)
    val OR = t("or", " or ", TokenKind.BINARY)
    val XOR = t("xor", " xor ", TokenKind.BINARY)

    val EQ = t("=", "=", TokenKind.RELATION)
    val NE = t("!=", "≠", TokenKind.RELATION)
    val GT = t(">", ">", TokenKind.RELATION)
    val LT = t("<", "<", TokenKind.RELATION)
    val GE = t(">=", "≥", TokenKind.RELATION)
    val LE = t("<=", "≤", TokenKind.RELATION)

    val NEG = t("neg", "−", TokenKind.PREFIX)
    val NOT = t("not", "not ", TokenKind.PREFIX)

    val FACT = t("!", "!", TokenKind.POSTFIX)
    val DEG = t("deg", "°", TokenKind.POSTFIX)
    val RAD = t("rad", "ʳ", TokenKind.POSTFIX)
    val GRA = t("gra", "ᵍ", TokenKind.POSTFIX)
    val PERCENT = t("%", "%", TokenKind.POSTFIX)
    val TO_DMS = t("toDMS", "▸DMS", TokenKind.POSTFIX)
    val TO_RECT = t("toRect", "▸a+bi", TokenKind.POSTFIX)
    val TO_POLAR = t("toPolar", "▸r∠θ", TokenKind.POSTFIX)
    val TO_DEC = t("toDec", "▸Dec", TokenKind.POSTFIX)
    val DMS = t("dms", "°", TokenKind.DMS)

    val LPAREN = t("(", "(", TokenKind.LPAREN)
    val RPAREN = t(")", ")", TokenKind.RPAREN)
    val COMMA = t(",", ",", TokenKind.COMMA)
    val LBRACE = t("{", "{", TokenKind.LBRACE)
    val RBRACE = t("}", "}", TokenKind.RBRACE)
    val STORE = t("store", "→", TokenKind.STORE)

    val letters = ('A'..'Z').map { t("$it", "$it", TokenKind.VARIABLE) }
    val THETA = t("theta", "θ", TokenKind.VARIABLE)
    val R_VAR = t("r", "r", TokenKind.VARIABLE)
    /** Graph/table variable shown as X but distinct from nothing: it is simply variable X. */
    val X get() = letters['X' - 'A']
    val T get() = letters['T' - 'A']

    /** Recursion-mode variables: n, aₙ and bₙ. */
    val SEQ_N = t("seqN", "n", TokenKind.VARIABLE)
    val SEQ_A = t("seqA", "aₙ", TokenKind.VARIABLE)
    val SEQ_B = t("seqB", "bₙ", TokenKind.VARIABLE)

    val PI = t("pi", "π", TokenKind.CONSTANT)
    val E = t("e", "e", TokenKind.CONSTANT)
    val I = t("i", "i", TokenKind.CONSTANT)
    val ANS = t("Ans", "Ans", TokenKind.CONSTANT)
    val TEN = t("ten", "10", TokenKind.CONSTANT)
    val RAN = t("Ran#", "Ran#", TokenKind.CONSTANT)

    val matrices = ('A'..'Z').map { t("Mat$it", "Mat $it", TokenKind.REFERENCE) }
    val lists = (1..26).map { t("List$it", "List $it", TokenKind.REFERENCE) }

    /** Function tokens; each draws with its opening parenthesis. */
    private val functionDisplays = linkedMapOf(
        "sin" to "sin(", "cos" to "cos(", "tan" to "tan(",
        "asin" to "sin⁻¹(", "acos" to "cos⁻¹(", "atan" to "tan⁻¹(",
        "sinh" to "sinh(", "cosh" to "cosh(", "tanh" to "tanh(",
        "asinh" to "sinh⁻¹(", "acosh" to "cosh⁻¹(", "atanh" to "tanh⁻¹(",
        "log" to "log(", "ln" to "ln(",
        "Int" to "Int(", "Frac" to "Frac(", "Intg" to "Intg(", "Rnd" to "Rnd(", "RndFix" to "RndFix(",
        "GCD" to "GCD(", "LCM" to "LCM(", "MOD" to "MOD(",
        "RanInt" to "RanInt#(", "Pol" to "Pol(", "Rec" to "Rec(",
        "Arg" to "Arg(", "Conjg" to "Conjg(", "ReP" to "ReP(", "ImP" to "ImP(",
        "Det" to "Det(", "Trn" to "Trn(", "Identity" to "Identity(", "Dim" to "Dim(",
        "Ref" to "Ref(", "Rref" to "Rref(", "Augment" to "Augment(", "Fill" to "Fill(",
        "Sum" to "Sum(", "Prod" to "Prod(", "Mean" to "Mean(", "Median" to "Median(",
        "Min" to "Min(", "Max" to "Max(", "Cuml" to "Cuml(", "Seq" to "Seq(",
        "SortA" to "SortA(", "SortD" to "SortD(",
        "NormPD" to "NormPD(", "NormCD" to "NormCD(", "InvNormCD" to "InvNormCD(",
        "BinomialPD" to "BinomialPD(", "BinomialCD" to "BinomialCD(",
        "PoissonPD" to "PoissonPD(", "PoissonCD" to "PoissonCD(",
        "GeoPD" to "GeoPD(", "GeoCD" to "GeoCD(",
        "tPD" to "tPD(", "tCD" to "tCD(", "ChiPD" to "χ²PD(", "ChiCD" to "χ²CD(",
        "FPD" to "FPD(", "FCD" to "FCD(",
        "Solve" to "Solve(", "FMin" to "FMin(", "FMax" to "FMax(",
    )

    val functions: Map<String, Tok> = functionDisplays.mapValues { (id, d) -> t(id, d, TokenKind.FUNCTION) }

    fun fn(id: String) = functions[id] ?: error("unknown function $id")
    fun digit(d: Int) = digits[d]
    fun letter(c: Char) = letters[c - 'A']
    fun matrix(c: Char) = matrices[c - 'A']
    fun list(n: Int) = lists[n - 1]

    fun byId(id: String): Tok? = all[id]
}
