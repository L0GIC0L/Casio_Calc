package com.l0gic0l.casiocalc.math.eval

import com.l0gic0l.casiocalc.math.num.Value
import java.util.concurrent.ConcurrentHashMap

/** Calculator memory: letter variables, Ans, Mat A–Z and List 1–26. */
open class Memory {
    // Concurrent maps: calculations run off the main thread while the UI reads memory.
    val variables = ConcurrentHashMap<String, Value>()
    val matrices = ConcurrentHashMap<Char, Value.Mat>()
    val lists = ConcurrentHashMap<Int, Value.Lst>()

    @Volatile
    var ans: Value = Value.of(0)

    open fun getVar(name: String): Value = variables[name] ?: Value.of(0)
    open fun setVar(name: String, v: Value) {
        variables[name] = v
        changed()
    }

    open fun getMatrix(c: Char): Value.Mat? = matrices[c]
    open fun setMatrix(c: Char, m: Value.Mat?) {
        if (m == null) matrices.remove(c) else matrices[c] = m
        changed()
    }

    open fun getList(n: Int): Value.Lst? = lists[n]
    open fun setList(n: Int, l: Value.Lst?) {
        if (l == null || l.items.isEmpty()) lists.remove(n) else lists[n] = l
        changed()
    }

    /** Hook for observers such as persistence. */
    open fun changed() {}

    companion object {
        val variableNames: List<String> = ('A'..'Z').map { "$it" } + listOf("theta", "r")
    }
}
