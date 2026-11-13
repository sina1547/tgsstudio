package ir.sina.tgsstudio.renderer

import kotlin.math.hypot
import kotlin.math.min

/** One cubic Bézier segment in absolute coordinates. Straight lines use control points equal to the ends. */
class Cubic(
    val x0: Double, val y0: Double, val x1: Double, val y1: Double,
    val x2: Double, val y2: Double, val x3: Double, val y3: Double,
) {
    fun x(t: Double): Double { val u = 1 - t; return u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3 }
    fun y(t: Double): Double { val u = 1 - t; return u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3 }

    /** Cumulative arc length at [STEPS]+1 evenly spaced parameter values. */
    private val table: DoubleArray by lazy {
        val t = DoubleArray(STEPS + 1)
        var px = x0; var py = y0
        for (i in 1..STEPS) {
            val u = i.toDouble() / STEPS
            val nx = x(u); val ny = y(u)
            t[i] = t[i - 1] + hypot(nx - px, ny - py)
            px = nx; py = ny
        }
        t
    }
    val length: Double get() = table[STEPS]

    /** Parameter whose arc length from the start is [dist] (clamped). */
    fun tAtLength(dist: Double): Double {
        val total = length
        if (total <= 0.0) return 0.0
        if (dist <= 0.0) return 0.0
        if (dist >= total) return 1.0
        var i = 1
        while (i < STEPS && table[i] < dist) i++
        val span = table[i] - table[i - 1]
        val frac = if (span > 0) (dist - table[i - 1]) / span else 0.0
        return (i - 1 + frac) / STEPS
    }

    fun split(t: Double): Pair<Cubic, Cubic> {
        fun l(a: Double, b: Double) = a + (b - a) * t
        val ax = l(x0, x1); val ay = l(y0, y1)
        val bx = l(x1, x2); val by = l(y1, y2)
        val cx = l(x2, x3); val cy = l(y2, y3)
        val dx = l(ax, bx); val dy = l(ay, by)
        val ex = l(bx, cx); val ey = l(by, cy)
        val fx = l(dx, ex); val fy = l(dy, ey)
        return Cubic(x0, y0, ax, ay, dx, dy, fx, fy) to Cubic(fx, fy, ex, ey, cx, cy, x3, y3)
    }

    /** Sub-segment between arc distances [from] and [to]. */
    fun slice(from: Double, to: Double): Cubic {
        val t0 = tAtLength(from); val t1 = tAtLength(to)
        if (t1 <= t0) return Cubic(x(t0), y(t0), x(t0), y(t0), x(t0), y(t0), x(t0), y(t0))
        val right = if (t0 > 0.0) split(t0).second else this
        val localT = if (t0 >= 1.0) 1.0 else (t1 - t0) / (1 - t0)
        return if (localT < 1.0) right.split(localT).first else right
    }

    /** Polyline approximation as x,y pairs including both end points. */
    fun flatten(steps: Int = 12): DoubleArray {
        val out = DoubleArray((steps + 1) * 2)
        for (i in 0..steps) { val t = i.toDouble() / steps; out[2 * i] = x(t); out[2 * i + 1] = y(t) }
        return out
    }

    fun transformed(m: Affine) = Cubic(
        m.mapX(x0, y0), m.mapY(x0, y0), m.mapX(x1, y1), m.mapY(x1, y1),
        m.mapX(x2, y2), m.mapY(x2, y2), m.mapX(x3, y3), m.mapY(x3, y3),
    )

    private companion object { const val STEPS = 24 }
}

class Contour(val segments: List<Cubic>, val closed: Boolean) {
    val length: Double by lazy { segments.sumOf { it.length } }
    fun transformed(m: Affine) = Contour(segments.map { it.transformed(m) }, closed)
}

/** Raw Lottie path: vertices plus relative in/out tangents. */
class Bezier(val closed: Boolean, val v: DoubleArray, val inT: DoubleArray, val outT: DoubleArray) {
    val count: Int get() = v.size / 2

    fun toContour(): Contour {
        val n = count
        if (n == 0) return Contour(emptyList(), closed)
        val segs = ArrayList<Cubic>(n)
        val last = if (closed) n else n - 1
        for (k in 0 until last) {
            val j = (k + 1) % n
            segs += Cubic(
                v[2 * k], v[2 * k + 1],
                v[2 * k] + outT[2 * k], v[2 * k + 1] + outT[2 * k + 1],
                v[2 * j] + inT[2 * j], v[2 * j + 1] + inT[2 * j + 1],
                v[2 * j], v[2 * j + 1],
            )
        }
        return Contour(segs, closed)
    }

    fun lerp(o: Bezier, t: Double): Bezier {
        if (o.v.size != v.size) return this
        fun mix(a: DoubleArray, b: DoubleArray) = DoubleArray(a.size) { a[it] + (b[it] - a[it]) * t }
        return Bezier(closed, mix(v, o.v), mix(inT, o.inT), mix(outT, o.outT))
    }
}

object Shapes {
    private const val K = 0.5522847498307936

    private fun line(x0: Double, y0: Double, x1: Double, y1: Double) = Cubic(x0, y0, x0, y0, x1, y1, x1, y1)

    /** Rectangle centered at (cx, cy). Starts at the top-right corner and runs clockwise like Lottie. */
    fun rect(cx: Double, cy: Double, w: Double, h: Double, roundness: Double): Contour {
        val hw = w / 2; val hh = h / 2
        val r = min(roundness.coerceAtLeast(0.0), min(hw, hh))
        val l = cx - hw; val rt = cx + hw; val t = cy - hh; val b = cy + hh
        if (r <= 0.0) {
            return Contour(listOf(line(rt, t, rt, b), line(rt, b, l, b), line(l, b, l, t), line(l, t, rt, t)), true)
        }
        val k = r * K
        return Contour(listOf(
            line(rt, t + r, rt, b - r),
            Cubic(rt, b - r, rt, b - r + k, rt - r + k, b, rt - r, b),
            line(rt - r, b, l + r, b),
            Cubic(l + r, b, l + r - k, b, l, b - r + k, l, b - r),
            line(l, b - r, l, t + r),
            Cubic(l, t + r, l, t + r - k, l + r - k, t, l + r, t),
            line(l + r, t, rt - r, t),
            Cubic(rt - r, t, rt - r + k, t, rt, t + r - k, rt, t + r),
        ), true)
    }

    /** Ellipse starting at the top, clockwise. */
    fun ellipse(cx: Double, cy: Double, w: Double, h: Double): Contour {
        val rx = w / 2; val ry = h / 2
        val kx = rx * K; val ky = ry * K
        return Contour(listOf(
            Cubic(cx, cy - ry, cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy),
            Cubic(cx + rx, cy, cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry),
            Cubic(cx, cy + ry, cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy),
            Cubic(cx - rx, cy, cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry),
        ), true)
    }

    /** Star ([inner] > 0) or regular polygon ([inner] == null); straight edges only. */
    fun star(cx: Double, cy: Double, points: Int, outer: Double, inner: Double?, rotationDeg: Double): Contour {
        val n = points.coerceAtLeast(3)
        val verts = if (inner != null) n * 2 else n
        val step = 2 * Math.PI / verts
        val start = Math.toRadians(rotationDeg) - Math.PI / 2
        val xs = DoubleArray(verts); val ys = DoubleArray(verts)
        for (i in 0 until verts) {
            val r = if (inner != null && i % 2 == 1) inner else outer
            val ang = start + step * i
            xs[i] = cx + r * Math.cos(ang); ys[i] = cy + r * Math.sin(ang)
        }
        return Contour(List(verts) { i -> val j = (i + 1) % verts; line(xs[i], ys[i], xs[j], ys[j]) }, true)
    }
}
