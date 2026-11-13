package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonNum
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.renderer.Bezier
import kotlin.math.abs

/** Compact number for output: integral values become integers, others are rounded to 3 decimals (keeps TGS small). */
fun jn(d: Double): JsonNum {
    require(d.isFinite()) { "Non-finite value" }
    return if (d == Math.rint(d) && abs(d) < 1e12) JsonNum(d.toLong()) else JsonNum(Math.round(d * 1000.0) / 1000.0)
}

fun jv(vararg v: Double) = jarr(v)
fun jarr(v: DoubleArray) = JsonArr(v.mapTo(mutableListOf<JsonValue>()) { jn(it) })

/** Static animatable property `{a:0,k:value}`. */
fun staticProp(vararg v: Double) = JsonObj().also { it.putInt("a", 0); it["k"] = if (v.size == 1) jn(v[0]) else jarr(v) }
fun staticPathProp(b: Bezier) = JsonObj().also { it.putInt("a", 0); it["k"] = bezierJson(b) }

fun bezierJson(b: Bezier): JsonObj {
    fun pairs(a: DoubleArray) = JsonArr(MutableList<JsonValue>(a.size / 2) { jv(a[2 * it], a[2 * it + 1]) })
    val o = JsonObj()
    o.putBool("c", b.closed)
    o["i"] = pairs(b.inT); o["o"] = pairs(b.outT); o["v"] = pairs(b.v)
    return o
}

fun obj(vararg pairs: Pair<String, JsonValue>) = JsonObj().also { o -> pairs.forEach { (k, v) -> o[k] = v } }
fun str(s: String) = ir.sina.tgsstudio.document.json.JsonStr(s)
fun int(i: Int) = JsonNum(i.toLong())
