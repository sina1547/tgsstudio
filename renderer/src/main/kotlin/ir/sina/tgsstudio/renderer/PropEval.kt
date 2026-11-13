package ir.sina.tgsstudio.renderer

import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonBool
import ir.sina.tgsstudio.document.json.JsonNum
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import kotlin.math.hypot
import kotlin.math.min

/** Evaluates Lottie animatable properties (`{a, k}`) at a frame: static values, keyframes, easing, spatial paths. */
object PropEval {

    fun values(prop: JsonObj?, frame: Double): DoubleArray? {
        val k = prop?.get("k") ?: return null
        val kfs = keyframes(k) ?: return numbers(k)
        return evalNumeric(kfs, frame)
    }

    fun number(prop: JsonObj?, frame: Double, default: Double): Double = values(prop, frame)?.firstOrNull() ?: default

    fun path(prop: JsonObj?, frame: Double): Bezier? {
        val k = prop?.get("k") ?: return null
        if (k is JsonObj) return bezierOf(k)
        val kfs = keyframes(k) ?: return null
        if (kfs.isEmpty()) return null
        fun startOf(kf: JsonObj) = pathValue(kf["s"])
        val first = kfs.first()
        if (frame <= t(first) || kfs.size == 1) return startOf(first)
        for (i in 0 until kfs.size - 1) {
            val a = kfs[i]; val b = kfs[i + 1]
            if (frame < t(b)) {
                val s = startOf(a) ?: return null
                if (a.bool("h") == true) return s
                val e = (pathValue(a["e"]) ?: startOf(b)) ?: return s
                val p = (frame - t(a)) / (t(b) - t(a))
                return s.lerp(e, easeAt(a, p, 0))
            }
        }
        return startOf(kfs.last()) ?: pathValue(kfs[kfs.size - 2]["e"])
    }

    // ---- numeric keyframes -------------------------------------------------------------------

    private fun evalNumeric(kfs: List<JsonObj>, frame: Double): DoubleArray? {
        if (kfs.isEmpty()) return null
        val first = kfs.first()
        if (frame <= t(first) || kfs.size == 1) return numbers(first["s"])
        for (i in 0 until kfs.size - 1) {
            val a = kfs[i]; val b = kfs[i + 1]
            if (frame < t(b)) {
                val s = numbers(a["s"]) ?: return null
                if (a.bool("h") == true) return s
                val e = numbers(a["e"]) ?: numbers(b["s"]) ?: return s
                val p = (frame - t(a)) / (t(b) - t(a))
                val to = numbers(a["to"]); val ti = numbers(a["ti"])
                if (to != null && ti != null && s.size >= 2 && e.size >= 2 && (to.any { it != 0.0 } || ti.any { it != 0.0 })) {
                    return spatial(s, e, to, ti, easeAt(a, p, 0))
                }
                val n = min(s.size, e.size)
                return DoubleArray(n) { d -> s[d] + (e[d] - s[d]) * easeAt(a, p, d) }
            }
        }
        return numbers(kfs.last()["s"]) ?: numbers(kfs[kfs.size - 2]["e"])
    }

    /** Position along the motion path, parametrised by arc length so speed follows the easing curve. */
    private fun spatial(s: DoubleArray, e: DoubleArray, to: DoubleArray, ti: DoubleArray, progress: Double): DoubleArray {
        val dims = min(s.size, e.size)
        fun at(u: Double) = DoubleArray(dims) { d ->
            val c0 = s[d] + to.getOrElse(d) { 0.0 }
            val c1 = e[d] + ti.getOrElse(d) { 0.0 }
            val w = 1 - u
            w * w * w * s[d] + 3 * w * w * u * c0 + 3 * w * u * u * c1 + u * u * u * e[d]
        }
        val n = 32
        val cum = DoubleArray(n + 1)
        var prev = at(0.0)
        for (i in 1..n) {
            val cur = at(i.toDouble() / n)
            var sq = 0.0
            for (d in 0 until dims) sq += (cur[d] - prev[d]) * (cur[d] - prev[d])
            cum[i] = cum[i - 1] + Math.sqrt(sq)
            prev = cur
        }
        val total = cum[n]
        if (total <= 0.0) return s.copyOf(dims)
        val target = progress.coerceIn(0.0, 1.0) * total
        var i = 1
        while (i < n && cum[i] < target) i++
        val span = cum[i] - cum[i - 1]
        val frac = if (span > 0) (target - cum[i - 1]) / span else 0.0
        return at((i - 1 + frac) / n)
    }

    // ---- easing ------------------------------------------------------------------------------

    /** Eased progress for the segment starting at keyframe [kf], for dimension [dim]. */
    private fun easeAt(kf: JsonObj, p: Double, dim: Int): Double {
        val o = kf["o"] as? JsonObj; val i = kf["i"] as? JsonObj
        if (o == null || i == null) return p
        val ox = comp(o, "x", dim, 0.0); val oy = comp(o, "y", dim, 0.0)
        val ix = comp(i, "x", dim, 1.0); val iy = comp(i, "y", dim, 1.0)
        if (ox == oy && ix == iy && ox == 0.0 && ix == 1.0) return p
        return cubicBezierY(p.coerceIn(0.0, 1.0), ox, oy, ix, iy)
    }

    private fun comp(o: JsonObj, key: String, dim: Int, def: Double): Double = when (val v = o[key]) {
        is JsonNum -> v.value.toDouble()
        is JsonArr -> (v.items.getOrNull(min(dim, v.items.size - 1)) as? JsonNum)?.value?.toDouble() ?: def
        else -> def
    }

    /** CSS-style cubic-bezier(x1,y1,x2,y2): solve x(u)=p by bisection, return y(u). */
    internal fun cubicBezierY(p: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        fun bez(u: Double, a: Double, b: Double): Double { val w = 1 - u; return 3 * w * w * u * a + 3 * w * u * u * b + u * u * u }
        var lo = 0.0; var hi = 1.0
        repeat(24) { val mid = (lo + hi) / 2; if (bez(mid, x1, x2) < p) lo = mid else hi = mid }
        return bez((lo + hi) / 2, y1, y2)
    }

    // ---- parsing helpers ---------------------------------------------------------------------

    private fun t(kf: JsonObj) = kf.double("t") ?: 0.0

    /** The keyframe list if [k] is one, else null. */
    private fun keyframes(k: JsonValue): List<JsonObj>? {
        if (k !is JsonArr || k.items.isEmpty()) return null
        if (!k.items.all { it is JsonObj && it.has("t") }) return null
        return k.items.map { it as JsonObj }
    }

    private fun numbers(v: JsonValue?): DoubleArray? = when (v) {
        is JsonNum -> doubleArrayOf(v.value.toDouble())
        is JsonArr -> if (v.items.all { it is JsonNum }) DoubleArray(v.items.size) { (v.items[it] as JsonNum).value.toDouble() } else null
        else -> null
    }

    private fun pathValue(v: JsonValue?): Bezier? = when (v) {
        is JsonObj -> bezierOf(v)
        is JsonArr -> (v.items.firstOrNull() as? JsonObj)?.let(::bezierOf)
        else -> null
    }

    private fun bezierOf(o: JsonObj): Bezier? {
        fun flat(key: String): DoubleArray? {
            val arr = o.arr(key) ?: return null
            val out = DoubleArray(arr.items.size * 2)
            arr.items.forEachIndexed { i, p ->
                val pair = (p as? JsonArr)?.items ?: return null
                out[2 * i] = (pair.getOrNull(0) as? JsonNum)?.value?.toDouble() ?: return null
                out[2 * i + 1] = (pair.getOrNull(1) as? JsonNum)?.value?.toDouble() ?: return null
            }
            return out
        }
        val v = flat("v") ?: return null
        val i = flat("i") ?: DoubleArray(v.size)
        val out = flat("o") ?: DoubleArray(v.size)
        if (i.size != v.size || out.size != v.size) return null
        val closed = when (val c = o["c"]) { is JsonBool -> c.value; is JsonNum -> c.value.toDouble() != 0.0; else -> false }
        return Bezier(closed, v, i, out)
    }
}
