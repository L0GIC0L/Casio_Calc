package com.l0gic0l.casiocalc.math

import com.l0gic0l.casiocalc.math.TestRows.row
import com.l0gic0l.casiocalc.math.eval.AngleUnit
import com.l0gic0l.casiocalc.math.eval.Memory
import com.l0gic0l.casiocalc.math.eval.Settings
import com.l0gic0l.casiocalc.math.graph.FastCompiler
import com.l0gic0l.casiocalc.math.graph.GraphSolve
import com.l0gic0l.casiocalc.math.graph.RealFn
import com.l0gic0l.casiocalc.math.parse.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphSolveTest {
    private val rad = Settings(angle = AngleUnit.RAD)

    private fun fn(s: String, settings: Settings = rad): RealFn {
        val e = Parser.parse(row(s))
        return FastCompiler(Calculator.evaluator(e, Memory(), settings), "X").compile(e)
    }

    @Test fun compiledMatchesExact() {
        val f = fn("{frac:1|2}X²-sin(X)+{sqrt:X}+{root:3|X-8}")
        val x = 2.5
        val expected = 0.5 * x * x - kotlin.math.sin(x) + kotlin.math.sqrt(x) + Math.cbrt(x - 8)
        assertEquals(expected, f.eval(x), 1e-12)
        assertTrue(fn("{sqrt:X}").eval(-1.0).isNaN())
        // Σ falls back to the exact evaluator.
        assertEquals(55.0 * 2, fn("{sum:K|1|10|K}X").eval(2.0), 1e-12)
        assertEquals(1.0, fn("sin(X)", Settings()).eval(90.0), 1e-12)
    }

    @Test fun rootsExtremaIntersections() {
        val r = GraphSolve.roots(fn("X²-2"), -5.0, 5.0)
        assertEquals(2, r.size)
        assertEquals(-Math.sqrt(2.0), r[0], 1e-12)
        val touching = GraphSolve.roots(fn("(X-1)²"), -5.0, 5.0)
        assertEquals(1, touching.size)
        assertEquals(1.0, touching[0], 1e-6)
        val tanRoots = GraphSolve.roots(fn("tan(X)"), -2.0, 2.0)
        assertEquals(listOf(0.0), tanRoots.map { Math.round(it * 1e9) / 1e9 })
        val max = GraphSolve.extrema(fn("~X²+4X"), -10.0, 10.0, maximum = true)
        assertEquals(2.0, max.single().first, 1e-7)
        assertEquals(4.0, max.single().second, 1e-12)
        val isect = GraphSolve.intersections(fn("X"), fn("X²"), -3.0, 3.0)
        assertEquals(listOf(0.0, 1.0), isect.map { Math.round(it * 1e9) / 1e9 })
    }
}
