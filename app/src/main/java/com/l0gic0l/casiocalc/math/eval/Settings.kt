package com.l0gic0l.casiocalc.math.eval

import kotlinx.serialization.Serializable

enum class AngleUnit(val label: String) { DEG("Deg"), RAD("Rad"), GRA("Gra") }

enum class ComplexMode(val label: String) { REAL("Real"), RECT("a+bi"), POLAR("r∠θ") }

/** Norm 1/2, Fix n and Sci n as on the calculator; Eng is a separate switch. */
@Serializable
data class DisplayFormat(val kind: Kind = Kind.NORM1, val digits: Int = 0) {
    enum class Kind { NORM1, NORM2, FIX, SCI }

    val label: String
        get() = when (kind) {
            Kind.NORM1 -> "Norm1"
            Kind.NORM2 -> "Norm2"
            Kind.FIX -> "Fix$digits"
            Kind.SCI -> "Sci$digits"
        }
}

enum class FracResult(val label: String) { IMPROPER("d/c"), MIXED("ab/c") }

@Serializable
data class Settings(
    val angle: AngleUnit = AngleUnit.DEG,
    val display: DisplayFormat = DisplayFormat(),
    val eng: Boolean = false,
    /** Math output keeps fractions, surds and π; false always shows decimals (Math/Deci). */
    val exactOutput: Boolean = true,
    val fracResult: FracResult = FracResult.IMPROPER,
    val complex: ComplexMode = ComplexMode.REAL,
)
