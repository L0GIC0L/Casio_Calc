package com.l0gic0l.casiocalc.math

/** Error categories named after the messages a CG50 shows. */
enum class ErrorKind(val message: String) {
    SYNTAX("Syntax ERROR"),
    MATH("Math ERROR"),
    NON_REAL("Non-Real ERROR"),
    DIMENSION("Dimension ERROR"),
    ARGUMENT("Argument ERROR"),
    UNDEFINED("Undefined ERROR"),
    ITERATION("Iteration ERROR"),
    CANT_SOLVE("Can't Solve"),
    RANGE("Range ERROR"),
    STACK("Stack ERROR"),
    CANCELLED("Break"),
}

class CalcException(val kind: ErrorKind, val detail: String? = null) :
    RuntimeException(if (detail == null) kind.message else "${kind.message}: $detail")

fun syntaxError(detail: String? = null): Nothing = throw CalcException(ErrorKind.SYNTAX, detail)
fun mathError(detail: String? = null): Nothing = throw CalcException(ErrorKind.MATH, detail)
fun dimError(detail: String? = null): Nothing = throw CalcException(ErrorKind.DIMENSION, detail)
fun argError(detail: String? = null): Nothing = throw CalcException(ErrorKind.ARGUMENT, detail)
fun nonReal(): Nothing = throw CalcException(ErrorKind.NON_REAL)
