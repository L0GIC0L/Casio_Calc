package com.l0gic0l.casiocalc.data

import com.l0gic0l.casiocalc.math.editor.MathRow
import com.l0gic0l.casiocalc.math.editor.Node
import com.l0gic0l.casiocalc.math.editor.Template
import com.l0gic0l.casiocalc.math.editor.TemplateKind
import com.l0gic0l.casiocalc.math.editor.TokenNode
import com.l0gic0l.casiocalc.math.editor.Tokens
import com.l0gic0l.casiocalc.math.num.BigRational
import com.l0gic0l.casiocalc.math.num.Complex
import com.l0gic0l.casiocalc.math.num.Real
import com.l0gic0l.casiocalc.math.num.Value
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger

/** Serializable mirrors of editor rows and values for saving state between launches. */
@Serializable
sealed class NodeDto {
    @Serializable
    @SerialName("t")
    data class Tok(val id: String) : NodeDto()

    @Serializable
    @SerialName("p")
    data class Tpl(val kind: String, val slots: List<List<NodeDto>>, val rows: Int = 0, val cols: Int = 0) : NodeDto()
}

@Serializable
data class RealDto(val a: String = "0", val b: String = "0", val r: Long = 1, val pi: Int = 0, val approx: Double? = null)

@Serializable
data class ComplexDto(val re: RealDto, val im: RealDto = RealDto())

@Serializable
data class ValueDto(val kind: String, val items: List<ComplexDto>, val rows: Int = 0, val cols: Int = 0)

object Codec {
    fun encode(row: MathRow): List<NodeDto> = row.nodes.map { n ->
        when (n) {
            is TokenNode -> NodeDto.Tok(n.tok.id)
            is Template -> NodeDto.Tpl(n.kind.name, n.slots.map { encode(it) }, n.rows, n.cols)
        }
    }

    fun decode(nodes: List<NodeDto>): MathRow = MathRow(nodes.mapNotNull { decodeNode(it) })

    private fun decodeNode(n: NodeDto): Node? = when (n) {
        is NodeDto.Tok -> Tokens.byId(n.id)?.let { TokenNode(it) }
        is NodeDto.Tpl -> {
            val kind = runCatching { TemplateKind.valueOf(n.kind) }.getOrNull()
            kind?.let { Template(it, n.slots.map { s -> decode(s) }, n.rows, n.cols) }
        }
    }

    private fun q(text: String): BigRational {
        val parts = text.split('/')
        return if (parts.size == 2) BigRational.of(BigInteger(parts[0]), BigInteger(parts[1])) else BigRational.of(BigInteger(parts[0]))
    }

    fun encode(r: Real): RealDto = when (r) {
        is Real.Exact -> RealDto(r.a.toString(), r.b.toString(), r.r, r.pi)
        is Real.Approx -> RealDto(approx = r.v)
    }

    fun decode(d: RealDto): Real = d.approx?.let { Real.Approx(it) } ?: Real.exact(q(d.a), q(d.b), d.r, d.pi)

    fun encode(z: Complex) = ComplexDto(encode(z.re), encode(z.im))
    fun decode(d: ComplexDto) = Complex(decode(d.re), decode(d.im))

    fun encode(v: Value): ValueDto = when (v) {
        is Value.Num -> ValueDto("num", listOf(encode(v.z)))
        is Value.Lst -> ValueDto("list", v.items.map { encode(it) })
        is Value.Mat -> ValueDto("mat", v.cells.map { encode(it) }, v.rows, v.cols)
    }

    fun decode(d: ValueDto): Value? = runCatching {
        when (d.kind) {
            "num" -> Value.Num(decode(d.items.first()))
            "list" -> Value.Lst(d.items.map { decode(it) })
            "mat" -> Value.Mat(d.rows, d.cols, d.items.map { decode(it) })
            else -> null
        }
    }.getOrNull()
}
