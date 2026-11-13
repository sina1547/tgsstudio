package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.Contour
import ir.sina.tgsstudio.renderer.Cubic
import kotlin.math.abs
import kotlin.math.hypot

/** Pure edits on Lottie paths (vertices + relative in/out tangents). All functions return a new [Bezier]. */
object PathOps {
    enum class HitType { VERTEX, IN_HANDLE, OUT_HANDLE, SEGMENT }
    class Hit(val type: HitType, val index: Int, val t: Double = 0.0)

    fun moveVertex(b: Bezier, i: Int, x: Double, y: Double): Bezier {
        val v = b.v.copyOf(); v[2 * i] = x; v[2 * i + 1] = y   // relative handles travel with the vertex
        return Bezier(b.closed, v, b.inT, b.outT)
    }

    fun translateAll(b: Bezier, dx: Double, dy: Double): Bezier =
        Bezier(b.closed, DoubleArray(b.v.size) { b.v[it] + if (it % 2 == 0) dx else dy }, b.inT, b.outT)

    /** Moves a handle to absolute position (hx, hy). A smooth vertex keeps its opposite handle collinear. */
    fun moveHandle(b: Bezier, i: Int, out: Boolean, hx: Double, hy: Double): Bezier {
        val smooth = isSmooth(b, i)
        val ins = b.inT.copyOf(); val outs = b.outT.copyOf()
        val rx = hx - b.v[2 * i]; val ry = hy - b.v[2 * i + 1]
        val mine = if (out) outs else ins; val other = if (out) ins else outs
        mine[2 * i] = rx; mine[2 * i + 1] = ry
        if (smooth) {
            val len = hypot(rx, ry)
            val oLen = hypot(other[2 * i], other[2 * i + 1])
            if (len > 1e-9) { other[2 * i] = -rx / len * oLen; other[2 * i + 1] = -ry / len * oLen }
        }
        return Bezier(b.closed, b.v, ins, outs)
    }

    fun isSmooth(b: Bezier, i: Int): Boolean {
        val ix = b.inT[2 * i]; val iy = b.inT[2 * i + 1]; val ox = b.outT[2 * i]; val oy = b.outT[2 * i + 1]
        val li = hypot(ix, iy); val lo = hypot(ox, oy)
        if (li < 1e-9 || lo < 1e-9) return false
        return abs(ix * oy - iy * ox) / (li * lo) < 0.02 && ix * ox + iy * oy < 0
    }

    fun toCorner(b: Bezier, i: Int): Bezier {
        val ins = b.inT.copyOf(); val outs = b.outT.copyOf()
        ins[2 * i] = 0.0; ins[2 * i + 1] = 0.0; outs[2 * i] = 0.0; outs[2 * i + 1] = 0.0
        return Bezier(b.closed, b.v, ins, outs)
    }

    /** Smooth handles aligned with the neighbours, one third of the distance to each. */
    fun toSmooth(b: Bezier, i: Int): Bezier {
        val n = b.count
        val hasPrev = b.closed || i > 0; val hasNext = b.closed || i < n - 1
        if (!hasPrev && !hasNext) return b
        val p = (i - 1 + n) % n; val nx = (i + 1) % n
        val vx = b.v[2 * i]; val vy = b.v[2 * i + 1]
        val px = if (hasPrev) b.v[2 * p] else vx; val py = if (hasPrev) b.v[2 * p + 1] else vy
        val nxx = if (hasNext) b.v[2 * nx] else vx; val nxy = if (hasNext) b.v[2 * nx + 1] else vy
        var dx = nxx - px; var dy = nxy - py
        val len = hypot(dx, dy)
        if (len < 1e-9) return b
        dx /= len; dy /= len
        val ins = b.inT.copyOf(); val outs = b.outT.copyOf()
        val dOut = if (hasNext) hypot(nxx - vx, nxy - vy) / 3 else 0.0
        val dIn = if (hasPrev) hypot(vx - px, vy - py) / 3 else 0.0
        outs[2 * i] = dx * dOut; outs[2 * i + 1] = dy * dOut
        ins[2 * i] = -dx * dIn; ins[2 * i + 1] = -dy * dIn
        return Bezier(b.closed, b.v, ins, outs)
    }

    fun setClosed(b: Bezier, closed: Boolean) = Bezier(closed, b.v, b.inT, b.outT)

    private fun cubicOf(b: Bezier, seg: Int): Cubic {
        val n = b.count; val k = seg; val j = (seg + 1) % n
        return Cubic(
            b.v[2 * k], b.v[2 * k + 1], b.v[2 * k] + b.outT[2 * k], b.v[2 * k + 1] + b.outT[2 * k + 1],
            b.v[2 * j] + b.inT[2 * j], b.v[2 * j + 1] + b.inT[2 * j + 1], b.v[2 * j], b.v[2 * j + 1],
        )
    }

    fun segmentCount(b: Bezier) = if (b.count < 2) 0 else if (b.closed) b.count else b.count - 1

    /** Splits segment [seg] (between vertex seg and seg+1) at parameter [t], inserting a smooth vertex. */
    fun insert(b: Bezier, seg: Int, t: Double): Bezier {
        val n = b.count
        if (seg !in 0 until segmentCount(b)) return b
        val (l, r) = cubicOf(b, seg).split(t.coerceIn(0.001, 0.999))
        val at = seg + 1
        val v = ArrayList<Double>(); val ins = ArrayList<Double>(); val outs = ArrayList<Double>()
        for (k in 0 until n) {
            v += b.v[2 * k]; v += b.v[2 * k + 1]
            ins += b.inT[2 * k]; ins += b.inT[2 * k + 1]
            outs += b.outT[2 * k]; outs += b.outT[2 * k + 1]
            if (k == seg) {
                outs[2 * k] = l.x1 - l.x0; outs[2 * k + 1] = l.y1 - l.y0
                v += l.x3; v += l.y3
                ins += l.x2 - l.x3; ins += l.y2 - l.y3
                outs += r.x1 - r.x0; outs += r.y1 - r.y0
            }
        }
        // incoming handle of the vertex after the split segment now belongs to the right half
        val after = (at + 1) % (n + 1)
        ins[2 * after] = r.x2 - r.x3; ins[2 * after + 1] = r.y2 - r.y3
        return Bezier(b.closed, v.toDoubleArray(), ins.toDoubleArray(), outs.toDoubleArray())
    }

    /** Removes a vertex; null if that would leave fewer than two. */
    fun delete(b: Bezier, i: Int): Bezier? {
        val n = b.count
        if (n <= 2 || i !in 0 until n) return null
        fun without(a: DoubleArray) = DoubleArray(a.size - 2) { val k = it / 2; val src = if (k >= i) k + 1 else k; a[2 * src + it % 2] }
        return Bezier(b.closed, without(b.v), without(b.inT), without(b.outT))
    }

    fun append(b: Bezier, x: Double, y: Double, outDx: Double = 0.0, outDy: Double = 0.0): Bezier {
        // a dragged new point gets a symmetric handle pair, like a pen tool
        return Bezier(
            b.closed, b.v + doubleArrayOf(x, y), b.inT + doubleArrayOf(-outDx, -outDy), b.outT + doubleArrayOf(outDx, outDy),
        )
    }

    fun fromContour(c: Contour): Bezier? {
        val segs = c.segments
        if (segs.isEmpty()) return null
        val n = if (c.closed) segs.size else segs.size + 1
        val v = DoubleArray(n * 2); val ins = DoubleArray(n * 2); val outs = DoubleArray(n * 2)
        for ((k, s) in segs.withIndex()) {
            v[2 * k] = s.x0; v[2 * k + 1] = s.y0
            outs[2 * k] = s.x1 - s.x0; outs[2 * k + 1] = s.y1 - s.y0
            val j = (k + 1) % n
            if (c.closed || k + 1 < n) { ins[2 * j] = s.x2 - s.x3; ins[2 * j + 1] = s.y2 - s.y3 }
        }
        if (!c.closed) { val last = segs.last(); v[2 * (n - 1)] = last.x3; v[2 * (n - 1) + 1] = last.y3 }
        return Bezier(c.closed, v, ins, outs)
    }

    fun transform(b: Bezier, m: Affine): Bezier {
        val n = b.count
        val v = DoubleArray(n * 2); val ins = DoubleArray(n * 2); val outs = DoubleArray(n * 2)
        for (k in 0 until n) {
            v[2 * k] = m.mapX(b.v[2 * k], b.v[2 * k + 1]); v[2 * k + 1] = m.mapY(b.v[2 * k], b.v[2 * k + 1])
            ins[2 * k] = m.mapVectorX(b.inT[2 * k], b.inT[2 * k + 1]); ins[2 * k + 1] = m.mapVectorY(b.inT[2 * k], b.inT[2 * k + 1])
            outs[2 * k] = m.mapVectorX(b.outT[2 * k], b.outT[2 * k + 1]); outs[2 * k + 1] = m.mapVectorY(b.outT[2 * k], b.outT[2 * k + 1])
        }
        return Bezier(b.closed, v, ins, outs)
    }

    /** Hit test in the path's own coordinates (caller maps the touch point and scales [tol]). Handles beat vertices beat segments. */
    fun hit(b: Bezier, x: Double, y: Double, tol: Double, selected: Int = -1): Hit? {
        val n = b.count
        if (selected in 0 until n) { // handles of the selected vertex are only active while it is selected
            val i = selected
            if (hypot(b.v[2 * i] + b.outT[2 * i] - x, b.v[2 * i + 1] + b.outT[2 * i + 1] - y) <= tol && hypot(b.outT[2 * i], b.outT[2 * i + 1]) > 1e-6) return Hit(HitType.OUT_HANDLE, i)
            if (hypot(b.v[2 * i] + b.inT[2 * i] - x, b.v[2 * i + 1] + b.inT[2 * i + 1] - y) <= tol && hypot(b.inT[2 * i], b.inT[2 * i + 1]) > 1e-6) return Hit(HitType.IN_HANDLE, i)
        }
        var best = -1; var bestD = tol
        for (i in 0 until n) { val d = hypot(b.v[2 * i] - x, b.v[2 * i + 1] - y); if (d <= bestD) { best = i; bestD = d } }
        if (best >= 0) return Hit(HitType.VERTEX, best)
        var segBest = -1; var tBest = 0.0; var dBest = tol
        for (s in 0 until segmentCount(b)) {
            val c = cubicOf(b, s)
            val steps = 24
            for (k in 0..steps) {
                val t = k.toDouble() / steps
                val d = hypot(c.x(t) - x, c.y(t) - y)
                if (d <= dBest) { dBest = d; segBest = s; tBest = t }
            }
        }
        return if (segBest >= 0) Hit(HitType.SEGMENT, segBest, tBest) else null
    }
}
