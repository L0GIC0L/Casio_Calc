package com.l0gic0l.casiocalc.math.eval

import com.l0gic0l.casiocalc.math.CalcException
import com.l0gic0l.casiocalc.math.ErrorKind
import com.l0gic0l.casiocalc.math.argError
import com.l0gic0l.casiocalc.math.dimError
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.mathError
import com.l0gic0l.casiocalc.math.nonReal
import com.l0gic0l.casiocalc.math.num.BigRational
import com.l0gic0l.casiocalc.math.num.C
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.R
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import com.l0gic0l.casiocalc.math.num.asNum
import com.l0gic0l.casiocalc.math.numeric.Distributions
import com.l0gic0l.casiocalc.math.numeric.Numeric
import com.l0gic0l.casiocalc.math.parse.Expr
import java.math.BigInteger
import kotlin.random.Random

/**
 * Evaluates [Expr] trees against a [Memory] with the calculator [settings].
 * [complexAllowed] is false in Real mode unless the input itself uses i.
 */
class Evaluator(
    val memory: Memory,
    val settings: Settings,
    var complexAllowed: Boolean = settings.complex != ComplexMode.REAL,
    private val random: Random = Random.Default,
    private val cancelled: () -> Boolean = { false },
) {
    private val locals = HashMap<String, Value>()
    private val unit get() = settings.angle

    fun checkCancelled() {
        if (cancelled()) throw CalcException(ErrorKind.CANCELLED)
    }

    /** Binds [name] to [value] while running [block]. */
    fun <T> withLocal(name: String, value: Value, block: () -> T): T {
        val old = locals[name]
        locals[name] = value
        try {
            return block()
        } finally {
            if (old == null) locals.remove(name) else locals[name] = old
        }
    }

    fun eval(e: Expr): Value {
        val v = evalNode(e)
        checkRange(v)
        return v
    }

    /** Evaluates to a real double with the variable [name] bound to [x]; used by graphs and calculus. */
    fun evalReal(e: Expr, name: String, x: Double): Double {
        val v = withLocal(name, Value.of(Real.Approx(x))) { eval(e) }
        val z = (v as? Value.Num)?.z ?: dimError("number expected")
        if (!z.im.isZero && kotlin.math.abs(z.im.toDouble()) > 1e-12 * (1 + kotlin.math.abs(z.re.toDouble()))) {
            nonReal()
        }
        return z.re.toDouble()
    }

    private fun checkRange(v: Value) {
        when (v) {
            is Value.Num -> {
                checkReal(v.z.re); checkReal(v.z.im)
            }
            is Value.Mat -> v.cells.forEach { checkReal(it.re); checkReal(it.im) }
            is Value.Lst -> v.items.forEach { checkReal(it.re); checkReal(it.im) }
        }
    }

    private fun checkReal(r: Real) {
        when (r) {
            is Real.Approx -> if (r.v.isNaN() || kotlin.math.abs(r.v) >= 1e100) mathError("overflow")
            is Real.Exact -> {
                if (r.a.num.bitLength() - r.a.den.bitLength() > 330 || r.b.num.bitLength() - r.b.den.bitLength() > 330) {
                    if (kotlin.math.abs(r.toDouble()) >= 1e100) mathError("overflow")
                }
            }
        }
    }

    private fun evalNode(e: Expr): Value = when (e) {
        is Expr.Num -> Value.of(e.value)
        is Expr.Var -> locals[e.name] ?: memory.getVar(e.name)
        is Expr.Const -> constant(e.id)
        is Expr.Ref -> reference(e.id)
        is Expr.Unary -> unary(e.op, eval(e.e))
        is Expr.Binary -> binary(e.op, e.l, e.r)
        is Expr.Postfix -> postfix(e.op, eval(e.e))
        is Expr.Call -> call(e.name, e.args)
        is Expr.Tpl -> template(e)
        is Expr.ListLit -> Value.Lst(e.items.map { eval(it).asNum() })
        is Expr.MatLit -> Value.Mat(e.rows, e.cols, e.cells.map { eval(it).asNum() })
        is Expr.Store -> store(eval(e.e), e.target)
        is Expr.Format -> eval(e.e)
    }

    private fun constant(id: String): Value = when (id) {
        "pi" -> Value.of(Real.PI)
        "e" -> Value.of(Real.E)
        "i" -> {
            if (!complexAllowed) nonReal()
            Value.of(Complex.I)
        }
        "Ans" -> memory.ans
        "Ran#" -> Value.of(Real.Approx(random.nextDouble()))
        else -> argError(id)
    }

    private fun reference(id: String): Value = when {
        id.startsWith("Mat") -> memory.getMatrix(id[3]) ?: dimError("$id is empty")
        id.startsWith("List") -> memory.getList(id.substring(4).toInt()) ?: dimError("$id is empty")
        else -> argError(id)
    }

    fun store(v: Value, target: String): Value {
        when {
            target.startsWith("Mat") -> memory.setMatrix(target[3], v as? Value.Mat ?: dimError("matrix expected"))
            target.startsWith("List") -> memory.setList(target.substring(4).toInt(), v as? Value.Lst ?: dimError("list expected"))
            else -> {
                if (v !is Value.Num) dimError("number expected")
                memory.setVar(target, v)
            }
        }
        return v
    }

    // ---- element-wise helpers -------------------------------------------------------------

    private fun mapNum(v: Value, f: (Complex) -> Complex): Value = when (v) {
        is Value.Num -> Value.Num(f(v.z))
        is Value.Lst -> Value.Lst(v.items.map(f))
        is Value.Mat -> MatrixOps.map(v, f)
    }

    private fun zip(a: Value, b: Value, f: (Complex, Complex) -> Complex): Value = when {
        a is Value.Num && b is Value.Num -> Value.Num(f(a.z, b.z))
        a is Value.Lst && b is Value.Lst -> {
            if (a.items.size != b.items.size) dimError("list size")
            Value.Lst(a.items.indices.map { f(a.items[it], b.items[it]) })
        }
        a is Value.Lst && b is Value.Num -> Value.Lst(a.items.map { f(it, b.z) })
        a is Value.Num && b is Value.Lst -> Value.Lst(b.items.map { f(a.z, it) })
        else -> dimError("operand types")
    }

    private fun requireReal(z: Complex): Real {
        if (!z.isReal) argError("real number expected")
        return z.re
    }

    private fun realArg(v: Value): Real = requireReal(v.asNum())
    private fun doubleArg(v: Value): Double = realArg(v).toDouble()

    private fun intArg(v: Value): Long {
        val b = R.toBigInteger(realArg(v)) ?: argError("integer expected")
        if (b.bitLength() > 62) argError("integer too large")
        return b.toLong()
    }

    // ---- operators ------------------------------------------------------------------------

    private fun unary(op: String, v: Value): Value = when (op) {
        "neg" -> mapNum(v) { C.neg(it) }
        "not" -> mapNum(v) { if (it.re.isZero && it.im.isZero) Complex.ONE else Complex.ZERO }
        else -> argError(op)
    }

    private fun binary(op: String, le: Expr, re: Expr): Value {
        val l = eval(le)
        val r = eval(re)
        return when (op) {
            "+" -> if (l is Value.Mat && r is Value.Mat) MatrixOps.add(l, r) else zip(l, r, C::add)
            "-" -> if (l is Value.Mat && r is Value.Mat) MatrixOps.add(l, r, subtract = true) else zip(l, r, C::sub)
            "*", "imul" -> multiply(l, r)
            "/" -> when {
                l is Value.Mat && r is Value.Num -> MatrixOps.map(l) { C.div(it, r.z) }
                r is Value.Mat -> dimError("matrix division")
                else -> zip(l, r) { a, b -> C.div(a, b) }
            }
            "pow" -> power(l, r)
            "nPr" -> zip(l, r) { a, b -> Complex(permutation(requireReal(a), requireReal(b), false)) }
            "nCr" -> zip(l, r) { a, b -> Complex(permutation(requireReal(a), requireReal(b), true)) }
            "angle" -> zip(l, r) { a, b -> polar(requireReal(a), requireReal(b)) }
            "=", "!=", ">", "<", ">=", "<=" -> zip(l, r) { a, b -> if (compare(op, a, b)) Complex.ONE else Complex.ZERO }
            "and" -> zip(l, r) { a, b -> bool(truthy(a) && truthy(b)) }
            "or" -> zip(l, r) { a, b -> bool(truthy(a) || truthy(b)) }
            "xor" -> zip(l, r) { a, b -> bool(truthy(a) != truthy(b)) }
            else -> argError(op)
        }
    }

    private fun bool(b: Boolean) = if (b) Complex.ONE else Complex.ZERO
    private fun truthy(z: Complex) = !(z.re.isZero && z.im.isZero)

    private fun compare(op: String, a: Complex, b: Complex): Boolean {
        if (op == "=" || op == "!=") {
            val eq = R.sub(a.re, b.re).let { d -> d.isZero || (d is Real.Approx && kotlin.math.abs(d.v) < 1e-13) } &&
                R.sub(a.im, b.im).let { d -> d.isZero || (d is Real.Approx && kotlin.math.abs(d.v) < 1e-13) }
            return if (op == "=") eq else !eq
        }
        val c = R.cmp(requireReal(a), requireReal(b))
        return when (op) {
            ">" -> c > 0
            "<" -> c < 0
            ">=" -> c >= 0
            else -> c <= 0
        }
    }

    private fun multiply(l: Value, r: Value): Value = when {
        l is Value.Mat && r is Value.Mat -> MatrixOps.mul(l, r)
        l is Value.Mat && r is Value.Num -> MatrixOps.scale(l, r.z)
        l is Value.Num && r is Value.Mat -> MatrixOps.scale(r, l.z)
        else -> zip(l, r, C::mul)
    }

    fun power(l: Value, r: Value): Value {
        if (l is Value.Mat) {
            val n = intArg(r)
            return MatrixOps.pow(l, n)
        }
        return zip(l, r) { a, b -> powComplex(a, b) }
    }

    fun powComplex(a: Complex, b: Complex): Complex {
        if (a.isReal && b.isReal) {
            val p = R.pow(a.re, b.re)
            if (p != null) return Complex(p)
            if (!complexAllowed) nonReal()
            // Negative base, fractional exponent: exact √ for exponent ½ steps.
            val be = b.re as? Real.Exact
            if (be != null && be.isRational && be.a.den == BigInteger.valueOf(2)) {
                val root = C.sqrt(a)
                return powComplex(root, Complex(Real.of(be.a.num)))
            }
            return C.powGeneral(a, b)
        }
        // Integer powers of complex numbers stay exact.
        val n = if (b.isReal) R.toBigInteger(b.re) else null
        if (n != null && n.bitLength() < 16) {
            var e = n.toLong()
            val invert = e < 0
            if (invert) e = -e
            var result = Complex.ONE
            var base = a
            while (e > 0) {
                if (e and 1L == 1L) result = C.mul(result, base)
                e = e shr 1
                if (e > 0) base = C.mul(base, base)
            }
            return if (invert) C.div(Complex.ONE, result) else result
        }
        return C.powGeneral(a, b)
    }

    private fun permutation(n: Real, r: Real, choose: Boolean): Real {
        val nn = R.toBigInteger(n) ?: argError("nPr")
        val rr = R.toBigInteger(r) ?: argError("nPr")
        if (nn.signum() < 0 || rr.signum() < 0 || rr > nn) mathError("nPr")
        if (nn.bitLength() > 31) mathError("nPr")
        var result = BigInteger.ONE
        val k = rr.toInt()
        val nv = nn.toInt()
        for (i in 0 until k) result *= BigInteger.valueOf((nv - i).toLong())
        if (choose) result /= factorial(k)
        return Real.of(result)
    }

    private fun factorial(n: Int): BigInteger {
        var f = BigInteger.ONE
        for (i in 2..n) f *= BigInteger.valueOf(i.toLong())
        return f
    }

    private fun polar(r: Real, theta: Real): Complex {
        if (!complexAllowed) complexAllowed = true
        return Complex(R.mul(r, Trig.cos(theta, unit)), R.mul(r, Trig.sin(theta, unit)))
    }

    private fun postfix(op: String, v: Value): Value = when (op) {
        "!" -> mapNum(v) { z ->
            val n = R.toBigInteger(requireReal(z)) ?: mathError("x!")
            if (n.signum() < 0 || n.bitLength() > 7 || n.toInt() > 69) mathError("x!")
            Complex(Real.of(factorial(n.toInt())))
        }
        "deg" -> mapNum(v) { Complex(Trig.convert(requireReal(it), AngleUnit.DEG, unit)) }
        "rad" -> mapNum(v) { Complex(Trig.convert(requireReal(it), AngleUnit.RAD, unit)) }
        "gra" -> mapNum(v) { Complex(Trig.convert(requireReal(it), AngleUnit.GRA, unit)) }
        "%" -> mapNum(v) { C.div(it, Complex.of(100)) }
        "dmsDeg" -> v
        else -> argError(op)
    }

    // ---- templates ------------------------------------------------------------------------

    private fun template(t: Expr.Tpl): Value {
        val s = t.slots
        return when (t.kind) {
            TemplateKind.FRACTION -> binaryValues("/", eval(s[0]!!), eval(s[1]!!))
            TemplateKind.MIXED -> {
                val w = realArg(eval(s[0]!!))
                val frac = R.div(realArg(eval(s[1]!!)), realArg(eval(s[2]!!)))
                Value.of(if (w.signum() < 0) R.sub(w, frac) else R.add(w, frac))
            }
            TemplateKind.SQRT -> mapNum(eval(s[0]!!)) { sqrt(it) }
            TemplateKind.ROOT -> {
                val n = eval(s[0]!!).asNum()
                val x = eval(s[1]!!)
                val idx = requireReal(n)
                if (idx.isZero) mathError("0th root")
                mapNum(x) { powComplex(it, Complex(R.inv(idx))) }
            }
            TemplateKind.LOG_BASE -> zip(eval(s[0]!!), eval(s[1]!!)) { b, x -> logBase(b, x) }
            TemplateKind.ABS -> abs(eval(s[0]!!))
            TemplateKind.INTEGRAL -> {
                val a = doubleArg(eval(s[0]!!))
                val b = doubleArg(eval(s[1]!!))
                val body = s[2]!!
                val v = Numeric.integrate({ x -> evalReal(body, "X", x) }, a, b, check = ::checkCancelled)
                Value.of(Real.Approx(v))
            }
            TemplateKind.DERIV, TemplateKind.DERIV2 -> {
                val at = doubleArg(eval(s[1]!!))
                val body = s[0]!!
                val f = { x: Double -> evalReal(body, "X", x) }
                val d = if (t.kind == TemplateKind.DERIV) Numeric.derivative(f, at) else Numeric.secondDerivative(f, at)
                Value.of(Real.Approx(d))
            }
            TemplateKind.SUM, TemplateKind.PRODUCT -> sumOrProduct(t)
            TemplateKind.POWER, TemplateKind.MATRIX, TemplateKind.INDEF_INTEGRAL, TemplateKind.DIFF -> argError("template")
        }
    }

    private fun binaryValues(op: String, l: Value, r: Value): Value = when (op) {
        "/" -> when {
            l is Value.Mat && r is Value.Num -> MatrixOps.map(l) { C.div(it, r.z) }
            r is Value.Mat -> dimError("matrix division")
            else -> zip(l, r) { a, b -> C.div(a, b) }
        }
        else -> argError(op)
    }

    private fun sumOrProduct(t: Expr.Tpl): Value {
        val name = t.variable!!
        val start = intArg(eval(t.slots[1]!!))
        val end = intArg(eval(t.slots[2]!!))
        if (end < start) argError("Σ range")
        if (end - start > 1_000_000) argError("Σ range")
        val product = t.kind == TemplateKind.PRODUCT
        var acc: Value = Value.of(if (product) 1 else 0)
        for (k in start..end) {
            checkCancelled()
            val term = withLocal(name, Value.of(k)) { eval(t.slots[3]!!) }
            acc = if (product) multiply(acc, term) else zip(acc, term, C::add)
        }
        return acc
    }

    // ---- functions ------------------------------------------------------------------------

    fun sqrt(z: Complex): Complex {
        if (z.isReal && z.re.signum() < 0 && !complexAllowed) nonReal()
        return C.sqrt(z)
    }

    private fun abs(v: Value): Value = when (v) {
        is Value.Num -> Value.of(C.abs(v.z))
        is Value.Lst -> Value.Lst(v.items.map { Complex(C.abs(it)) })
        is Value.Mat -> MatrixOps.map(v) { Complex(C.abs(it)) }
    }

    private fun ln(z: Complex): Complex {
        if (z.isReal) {
            val s = z.re.signum()
            if (s == 0) mathError("ln 0")
            if (s > 0) {
                if (z.re == Real.ONE) return Complex.ZERO
                return Complex(Real.Approx(kotlin.math.ln(z.re.toDouble())))
            }
            if (!complexAllowed) nonReal()
        }
        return C.ln(z)
    }

    private fun log10(z: Complex): Complex {
        if (z.isReal && z.re.signum() > 0) {
            exactLog(z.re, Real.of(10))?.let { return Complex(it) }
            return Complex(Real.Approx(kotlin.math.log10(z.re.toDouble())))
        }
        val l = ln(z)
        return C.div(l, Complex(Real.Approx(kotlin.math.ln(10.0))))
    }

    /** log_b(x) as an exact rational when b^q = x for a small-denominator q. */
    private fun exactLog(x: Real, b: Real): Real? {
        if (x !is Real.Exact || b !is Real.Exact || !x.isRational || !b.isRational) return null
        if (x.signum() <= 0 || b.signum() <= 0 || b == Real.ONE) return null
        if (x == Real.ONE) return Real.ZERO
        val d = kotlin.math.ln(x.toDouble()) / kotlin.math.ln(b.toDouble())
        val q = BigRational.approximate(d, 12, 1e-9) ?: return null
        val check = R.pow(b, Real.of(q)) ?: return null
        return if (check == x) Real.of(q) else null
    }

    private fun logBase(b: Complex, x: Complex): Complex {
        if (b.isReal && x.isReal && b.re.signum() > 0 && x.re.signum() > 0) {
            if (b.re == Real.ONE) mathError("log base 1")
            exactLog(x.re, b.re)?.let { return Complex(it) }
            return Complex(Real.Approx(kotlin.math.ln(x.re.toDouble()) / kotlin.math.ln(b.re.toDouble())))
        }
        return C.div(ln(x), ln(b))
    }

    private fun trig(name: String, z: Complex): Complex {
        if (!z.isReal) {
            val zr = Complex(R.mul(z.re, Real.Approx(Trig.toRadians(1.0, unit))), R.mul(z.im, Real.Approx(Trig.toRadians(1.0, unit))))
            return when (name) {
                "sin" -> C.sinRad(zr)
                "cos" -> C.cosRad(zr)
                else -> C.div(C.sinRad(zr), C.cosRad(zr))
            }
        }
        val x = z.re
        return Complex(when (name) {
            "sin" -> Trig.sin(x, unit)
            "cos" -> Trig.cos(x, unit)
            else -> Trig.tan(x, unit)
        })
    }

    private fun inverseTrig(name: String, z: Complex): Complex {
        val x = requireReal(z)
        return Complex(when (name) {
            "asin" -> {
                if (kotlin.math.abs(x.toDouble()) > 1 + 1e-15) mathError("sin⁻¹ domain")
                Trig.asin(x, unit)
            }
            "acos" -> {
                if (kotlin.math.abs(x.toDouble()) > 1 + 1e-15) mathError("cos⁻¹ domain")
                Trig.acos(x, unit)
            }
            else -> Trig.atan(x, unit)
        })
    }

    private fun hyperbolic(name: String, z: Complex): Complex {
        val x = requireReal(z)
        if (x.isZero && name != "cosh") return Complex.ZERO
        val v = x.toDouble()
        val r = when (name) {
            "sinh" -> kotlin.math.sinh(v)
            "cosh" -> kotlin.math.cosh(v)
            "tanh" -> kotlin.math.tanh(v)
            "asinh" -> kotlin.math.asinh(v)
            "acosh" -> {
                if (v < 1) mathError("cosh⁻¹ domain")
                kotlin.math.acosh(v)
            }
            else -> {
                if (kotlin.math.abs(v) >= 1) mathError("tanh⁻¹ domain")
                kotlin.math.atanh(v)
            }
        }
        return Complex(Real.Approx(r))
    }

    private fun lst(v: Value): Value.Lst = v as? Value.Lst ?: dimError("list expected")
    private fun mat(v: Value): Value.Mat = v as? Value.Mat ?: dimError("matrix expected")
    private fun realList(v: Value) = lst(v).items.map { requireReal(it) }

    private fun call(name: String, argExprs: List<Expr>): Value {
        // Functions taking an unevaluated expression.
        when (name) {
            "Seq" -> return seq(argExprs)
            "Solve" -> return solveCall(argExprs)
            "FMin", "FMax" -> return fminmax(argExprs, name == "FMax")
        }
        val args = argExprs.map { eval(it) }
        fun arg(i: Int) = args.getOrNull(i) ?: argError("$name needs ${i + 1} arguments")
        fun argCount(vararg allowed: Int) {
            if (args.size !in allowed) argError("$name arguments")
        }
        return when (name) {
            "sin", "cos", "tan" -> { argCount(1); mapNum(arg(0)) { trig(name, it) } }
            "asin", "acos", "atan" -> { argCount(1); mapNum(arg(0)) { inverseTrig(name, it) } }
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh" -> { argCount(1); mapNum(arg(0)) { hyperbolic(name, it) } }
            "log" -> when (args.size) {
                1 -> mapNum(arg(0)) { log10(it) }
                2 -> zip(arg(0), arg(1)) { b, x -> logBase(b, x) }
                else -> argError("log arguments")
            }
            "ln" -> { argCount(1); mapNum(arg(0)) { ln(it) } }
            "Int" -> mapNum(arg(0)) { z -> Complex(intPart(requireReal(z))) }
            "Intg" -> mapNum(arg(0)) { z -> Complex(floorPart(requireReal(z))) }
            "Frac" -> mapNum(arg(0)) { z -> val x = requireReal(z); Complex(R.sub(x, intPart(x))) }
            "Rnd" -> mapNum(arg(0)) { z -> Complex(roundToDisplay(requireReal(z))) }
            "RndFix" -> {
                val n = intArg(arg(1)).toInt()
                if (n !in 0..9) argError("RndFix digits")
                mapNum(arg(0)) { z -> Complex(roundFix(requireReal(z), n)) }
            }
            "GCD", "LCM" -> {
                if (args.size == 1) {
                    val items = realList(arg(0)).map { R.toBigInteger(it) ?: argError("integer") }
                    Value.of(Real.of(items.reduce { a, b -> if (name == "GCD") a.gcd(b) else lcm(a, b) }))
                } else {
                    val items = args.map { R.toBigInteger(realArg(it)) ?: argError("integer") }
                    Value.of(Real.of(items.reduce { a, b -> if (name == "GCD") a.gcd(b) else lcm(a, b) }))
                }
            }
            "MOD" -> {
                argCount(2)
                zip(arg(0), arg(1)) { a, b ->
                    val x = R.toBigInteger(requireReal(a)) ?: argError("integer")
                    val y = R.toBigInteger(requireReal(b)) ?: argError("integer")
                    if (y.signum() == 0) mathError("MOD 0")
                    Complex(Real.of(x.mod(y.abs()).let { if (y.signum() < 0 && it.signum() != 0) it + y else it }))
                }
            }
            "RanInt" -> {
                val lo = intArg(arg(0))
                val hi = intArg(arg(1))
                if (hi < lo) argError("RanInt#")
                if (args.size >= 3) {
                    val n = intArg(arg(2)).toInt()
                    if (n !in 1..999) argError("RanInt# count")
                    Value.Lst(List(n) { Complex.of(random.nextLong(lo, hi + 1)) })
                } else Value.of(random.nextLong(lo, hi + 1))
            }
            "Pol" -> {
                argCount(2)
                val x = realArg(arg(0)); val y = realArg(arg(1))
                val r = R.sqrt(R.add(R.mul(x, x), R.mul(y, y)))
                Value.Lst(listOf(Complex(r), Complex(Trig.atan2(y, x, unit))))
            }
            "Rec" -> {
                argCount(2)
                val r = realArg(arg(0)); val t = realArg(arg(1))
                Value.Lst(listOf(Complex(R.mul(r, Trig.cos(t, unit))), Complex(R.mul(r, Trig.sin(t, unit)))))
            }
            "Arg" -> mapNum(arg(0)) { z -> Complex(Trig.atan2(z.im, z.re, unit)) }
            "Conjg" -> mapNum(arg(0)) { C.conj(it) }
            "ReP" -> mapNum(arg(0)) { Complex(it.re) }
            "ImP" -> mapNum(arg(0)) { Complex(it.im) }
            "Det" -> Value.of(MatrixOps.det(mat(arg(0))))
            "Trn" -> MatrixOps.transpose(mat(arg(0)))
            "Identity" -> MatrixOps.identity(intArg(arg(0)).toInt())
            "Ref" -> MatrixOps.ref(mat(arg(0)))
            "Rref" -> MatrixOps.rref(mat(arg(0)))
            "Dim" -> when (val v = arg(0)) {
                is Value.Mat -> Value.Lst(listOf(Complex.of(v.rows.toLong()), Complex.of(v.cols.toLong())))
                is Value.Lst -> Value.of(v.items.size.toLong())
                else -> dimError("Dim")
            }
            "Augment" -> {
                val a = arg(0); val b = arg(1)
                if (a is Value.Mat && b is Value.Mat) MatrixOps.augment(a, b)
                else Value.Lst(lst(a).items + lst(b).items)
            }
            "Fill" -> {
                val v = arg(0).asNum()
                val n = intArg(arg(1)).toInt()
                if (n !in 1..999) argError("Fill")
                Value.Lst(List(n) { v })
            }
            "Sum" -> Value.of(lst(arg(0)).items.fold(Complex.ZERO, C::add))
            "Prod" -> Value.of(lst(arg(0)).items.fold(Complex.ONE, C::mul))
            "Mean" -> {
                val items = lst(arg(0)).items
                if (items.isEmpty()) dimError("empty list")
                if (args.size >= 2) {
                    val freq = lst(arg(1)).items
                    if (freq.size != items.size) dimError("list size")
                    var s = Complex.ZERO
                    var n = Complex.ZERO
                    items.indices.forEach { s = C.add(s, C.mul(items[it], freq[it])); n = C.add(n, freq[it]) }
                    Value.of(C.div(s, n))
                } else Value.of(C.div(items.fold(Complex.ZERO, C::add), Complex.of(items.size.toLong())))
            }
            "Median" -> {
                val items = realList(arg(0)).sortedWith { a, b -> R.cmp(a, b) }
                if (items.isEmpty()) dimError("empty list")
                val n = items.size
                Value.of(if (n % 2 == 1) items[n / 2] else R.div(R.add(items[n / 2 - 1], items[n / 2]), Real.of(2)))
            }
            "Min", "Max" -> {
                val sign = if (name == "Max") 1 else -1
                if (args.size == 2) {
                    zip(arg(0), arg(1)) { a, b -> if (R.cmp(requireReal(a), requireReal(b)) * sign >= 0) a else b }
                } else {
                    val items = realList(arg(0))
                    if (items.isEmpty()) dimError("empty list")
                    Value.of(items.reduce { a, b -> if (R.cmp(a, b) * sign >= 0) a else b })
                }
            }
            "Cuml" -> {
                var acc = Complex.ZERO
                Value.Lst(lst(arg(0)).items.map { acc = C.add(acc, it); acc })
            }
            "SortA", "SortD" -> {
                val sorted = realList(arg(0)).sortedWith { a, b -> R.cmp(a, b) }
                Value.Lst((if (name == "SortD") sorted.reversed() else sorted).map { Complex(it) })
            }
            "NormPD" -> dist(args, 1, 3) { a -> Distributions.normPdf(a[0], a.getOrElse(1) { 1.0 }, a.getOrElse(2) { 0.0 }) }
            "NormCD" -> dist(args, 2, 4) { a -> Distributions.normCdf(a[0], a[1], a.getOrElse(2) { 1.0 }, a.getOrElse(3) { 0.0 }) }
            "InvNormCD" -> dist(args, 1, 3) { a -> Distributions.invNorm(a[0], a.getOrElse(1) { 1.0 }, a.getOrElse(2) { 0.0 }) }
            "BinomialPD" -> dist(args, 3, 3) { a -> Distributions.binomPdf(a[0], a[1], a[2]) }
            "BinomialCD" -> dist(args, 3, 4) { a ->
                if (a.size == 3) Distributions.binomCdf(0.0, a[0], a[1], a[2]) else Distributions.binomCdf(a[0], a[1], a[2], a[3])
            }
            "PoissonPD" -> dist(args, 2, 2) { a -> Distributions.poissonPdf(a[0], a[1]) }
            "PoissonCD" -> dist(args, 2, 3) { a ->
                if (a.size == 2) Distributions.poissonCdf(0.0, a[0], a[1]) else Distributions.poissonCdf(a[0], a[1], a[2])
            }
            "GeoPD" -> dist(args, 2, 2) { a -> Distributions.geoPdf(a[0], a[1]) }
            "GeoCD" -> dist(args, 2, 3) { a ->
                if (a.size == 2) Distributions.geoCdf(1.0, a[0], a[1]) else Distributions.geoCdf(a[0], a[1], a[2])
            }
            "tPD" -> dist(args, 2, 2) { a -> Distributions.tPdf(a[0], a[1]) }
            "tCD" -> dist(args, 3, 3) { a -> Distributions.tCdf(a[0], a[1], a[2]) }
            "ChiPD" -> dist(args, 2, 2) { a -> Distributions.chiPdf(a[0], a[1]) }
            "ChiCD" -> dist(args, 3, 3) { a -> Distributions.chiCdf(a[0], a[1], a[2]) }
            "FPD" -> dist(args, 3, 3) { a -> Distributions.fPdf(a[0], a[1], a[2]) }
            "FCD" -> dist(args, 4, 4) { a -> Distributions.fCdf(a[0], a[1], a[2], a[3]) }
            else -> argError("unknown function $name")
        }
    }

    private fun dist(args: List<Value>, min: Int, max: Int, f: (DoubleArray) -> Double): Value {
        if (args.size < min || args.size > max) argError("argument count")
        val first = args[0]
        val rest = args.drop(1).map { doubleArg(it) }
        fun compute(x: Double): Complex {
            val v = f(doubleArrayOf(x) + rest.toDoubleArray())
            if (v.isNaN()) mathError("distribution")
            return Complex(Real.Approx(v))
        }
        return when (first) {
            is Value.Lst -> Value.Lst(first.items.map { compute(requireReal(it).toDouble()) })
            else -> Value.of(compute(doubleArg(first)))
        }
    }

    private fun lcm(a: BigInteger, b: BigInteger): BigInteger =
        if (a.signum() == 0 || b.signum() == 0) BigInteger.ZERO else (a / a.gcd(b) * b).abs()

    private fun intPart(x: Real): Real = when (x) {
        is Real.Exact -> if (x.isRational) Real.of(x.a.truncate()) else Real.Approx(truncate(x.toDouble()))
        is Real.Approx -> Real.Approx(truncate(x.v))
    }

    private fun truncate(v: Double) = if (v < 0) kotlin.math.ceil(v) else kotlin.math.floor(v)

    private fun floorPart(x: Real): Real = when (x) {
        is Real.Exact -> if (x.isRational) Real.of(x.a.floor()) else Real.Approx(kotlin.math.floor(x.toDouble()))
        is Real.Approx -> Real.Approx(kotlin.math.floor(x.v))
    }

    private fun roundFix(x: Real, digits: Int): Real {
        val bd = java.math.BigDecimal(x.toDouble()).setScale(digits, java.math.RoundingMode.HALF_UP)
        return if (x is Real.Exact && x.isRational) {
            val exact = java.math.BigDecimal(x.a.num).divide(java.math.BigDecimal(x.a.den), digits, java.math.RoundingMode.HALF_UP)
            Real.of(BigRational.of(exact))
        } else Real.of(BigRational.of(bd))
    }

    private fun roundToDisplay(x: Real): Real {
        val d = settings.display
        return when (d.kind) {
            DisplayFormat.Kind.FIX -> roundFix(x, d.digits)
            DisplayFormat.Kind.SCI -> {
                val v = x.toDouble()
                if (v == 0.0) return Real.ZERO
                val bd = java.math.BigDecimal(v).round(java.math.MathContext(maxOf(d.digits, 1), java.math.RoundingMode.HALF_UP))
                Real.of(BigRational.of(bd))
            }
            else -> {
                val v = x.toDouble()
                if (v == 0.0) return Real.ZERO
                val bd = java.math.BigDecimal(v).round(java.math.MathContext(10, java.math.RoundingMode.HALF_UP))
                Real.of(BigRational.of(bd))
            }
        }
    }

    private fun seq(args: List<Expr>): Value {
        if (args.size !in 4..5) argError("Seq(expr, var, start, end[, step])")
        val name = (args[1] as? Expr.Var)?.name ?: argError("Seq variable")
        val start = realArg(eval(args[2]))
        val end = realArg(eval(args[3]))
        val step = if (args.size == 5) realArg(eval(args[4])) else Real.ONE
        if (step.isZero) argError("Seq step")
        val out = ArrayList<Complex>()
        var x = start
        while (if (step.signum() > 0) R.cmp(x, end) <= 0 else R.cmp(x, end) >= 0) {
            checkCancelled()
            out += withLocal(name, Value.of(x)) { eval(args[0]).asNum() }
            if (out.size > 999) dimError("Seq too long")
            x = R.add(x, step)
        }
        return Value.Lst(out)
    }

    /** Solve(f(X), guess[, lower, upper]) finds X with f(X) = 0, or both sides equal for an equation. */
    private fun solveCall(args: List<Expr>): Value {
        if (args.isEmpty()) argError("Solve")
        val f = equationAsZero(args[0])
        val guess = if (args.size >= 2) doubleArg(eval(args[1])) else doubleArg(memory.getVar("X"))
        val lo = if (args.size >= 3) doubleArg(eval(args[2])) else Double.NEGATIVE_INFINITY
        val hi = if (args.size >= 4) doubleArg(eval(args[3])) else Double.POSITIVE_INFINITY
        val root = Numeric.solve({ x -> evalReal(f, "X", x) }, guess, lo, hi, ::checkCancelled)
        return Value.of(Real.Approx(root))
    }

    private fun fminmax(args: List<Expr>, max: Boolean): Value {
        if (args.size != 3) argError("FMin(f, a, b)")
        val f = args[0]
        val a = doubleArg(eval(args[1]))
        val b = doubleArg(eval(args[2]))
        val g = { x: Double -> val v = evalReal(f, "X", x); if (max) -v else v }
        val x = Numeric.minimize(g, minOf(a, b), maxOf(a, b))
        return Value.Lst(listOf(Complex(Real.Approx(x)), Complex(Real.Approx(evalReal(f, "X", x)))))
    }

    companion object {
        /** Turns "lhs = rhs" into lhs − rhs; other expressions are returned unchanged. */
        fun equationAsZero(e: Expr): Expr =
            if (e is Expr.Binary && e.op == "=") Expr.Binary("-", e.l, e.r) else e
    }
}
