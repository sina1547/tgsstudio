package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.renderer.Bezier
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/** A binary image (true = ink), row-major. */
class Mask(val width: Int, val height: Int, val ink: BooleanArray) {
    init { require(ink.size == width * height) }
    operator fun get(x: Int, y: Int) = x in 0 until width && y in 0 until height && ink[y * width + x]
}

class Centerline(val paths: List<Bezier>, /** Average stroke thickness of the original ink, in mask pixels. */ val thickness: Double)

/**
 * Centerline extraction for text: thins glyph ink to a one-pixel skeleton (Zhang-Suen), traces it into
 * polylines and fits smooth Bézier paths. The result is a single stroke per pen movement, like hand-drawn calligraphy,
 * rather than the glyph's outline.
 */
object Skeleton {
    fun centerline(mask: Mask, tolerance: Double = 1.4, minLength: Int = 4): Centerline {
        val skel = thin(mask)
        val lines = trace(skel, mask.width, mask.height).filter { it.size >= minLength || it.size >= 2 && false }
        val paths = lines.mapNotNull { fit(simplify(it, tolerance), isLoop(it)) }
        val ink = mask.ink.count { it }
        val skelLen = skel.count { it }.coerceAtLeast(1)
        return Centerline(paths, ink.toDouble() / skelLen)
    }

    /** Zhang-Suen thinning. */
    fun thin(mask: Mask): BooleanArray {
        val w = mask.width; val h = mask.height
        val img = mask.ink.copyOf()
        // clear the border so every pixel has a full neighbourhood
        for (x in 0 until w) { img[x] = false; img[(h - 1) * w + x] = false }
        for (y in 0 until h) { img[y * w] = false; img[y * w + w - 1] = false }
        val remove = ArrayList<Int>()
        var changed = true
        while (changed) {
            changed = false
            for (step in 0..1) {
                remove.clear()
                for (y in 1 until h - 1) for (x in 1 until w - 1) {
                    val i = y * w + x
                    if (!img[i]) continue
                    val p2 = img[i - w]; val p3 = img[i - w + 1]; val p4 = img[i + 1]; val p5 = img[i + w + 1]
                    val p6 = img[i + w]; val p7 = img[i + w - 1]; val p8 = img[i - 1]; val p9 = img[i - w - 1]
                    val n = listOf(p2, p3, p4, p5, p6, p7, p8, p9).count { it }
                    if (n < 2 || n > 6) continue
                    val seq = booleanArrayOf(p2, p3, p4, p5, p6, p7, p8, p9, p2)
                    var transitions = 0
                    for (k in 0 until 8) if (!seq[k] && seq[k + 1]) transitions++
                    if (transitions != 1) continue
                    val ok = if (step == 0) !(p2 && p4 && p6) && !(p4 && p6 && p8) else !(p2 && p4 && p8) && !(p2 && p6 && p8)
                    if (ok) remove += i
                }
                if (remove.isNotEmpty()) { changed = true; remove.forEach { img[it] = false } }
            }
        }
        return img
    }

    /** Covers every skeleton pixel with polylines (pixel centers). Branches start at the pixel they leave from. */
    fun trace(skel: BooleanArray, w: Int, h: Int): List<List<Pair<Double, Double>>> {
        val visited = BooleanArray(skel.size)
        fun on(x: Int, y: Int) = x in 0 until w && y in 0 until h && skel[y * w + x]
        fun degree(x: Int, y: Int): Int { var n = 0; for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && on(x + dx, y + dy)) n++; return n }
        val order = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0, 1 to -1, 1 to 1, -1 to 1, -1 to -1)   // 4-neighbours first
        val lines = ArrayList<List<Pair<Double, Double>>>()

        fun walk(startX: Int, startY: Int, prefix: Pair<Int, Int>?) {
            val pts = ArrayList<Pair<Double, Double>>()
            prefix?.let { pts += it.first + 0.5 to it.second + 0.5 }
            var x = startX; var y = startY
            while (true) {
                visited[y * w + x] = true
                pts += x + 0.5 to y + 0.5
                var next: Pair<Int, Int>? = null
                for ((dx, dy) in order) { val nx = x + dx; val ny = y + dy; if (on(nx, ny) && !visited[ny * w + nx]) { next = nx to ny; break } }
                if (next == null) break
                x = next.first; y = next.second
            }
            lines += pts
        }

        // 1) from endpoints, 2) from pixels hanging off visited ones, 3) leftover loops
        for (y in 0 until h) for (x in 0 until w) if (on(x, y) && !visited[y * w + x] && degree(x, y) == 1) walk(x, y, null)
        var progress = true
        while (progress) {
            progress = false
            for (y in 0 until h) for (x in 0 until w) {
                if (!on(x, y) || visited[y * w + x]) continue
                var root: Pair<Int, Int>? = null
                for ((dx, dy) in order) if (on(x + dx, y + dy) && visited[(y + dy) * w + x + dx]) { root = x + dx to y + dy; break }
                if (root != null) { walk(x, y, root); progress = true }
            }
        }
        for (y in 0 until h) for (x in 0 until w) if (on(x, y) && !visited[y * w + x]) walk(x, y, null)
        // drop 1-2 pixel spurs left by staircase corners
        return lines.filter { it.size >= 3 }
    }

    private fun isLoop(p: List<Pair<Double, Double>>) = p.size > 8 && hypot(p.first().first - p.last().first, p.first().second - p.last().second) <= 1.5

    /** Douglas-Peucker. */
    fun simplify(pts: List<Pair<Double, Double>>, eps: Double): List<Pair<Double, Double>> {
        if (pts.size < 3) return pts
        val keep = BooleanArray(pts.size); keep[0] = true; keep[pts.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>(); stack.add(0 to pts.size - 1)
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var far = -1; var dMax = eps
            for (i in a + 1 until b) { val d = distToLine(pts[i], pts[a], pts[b]); if (d > dMax) { dMax = d; far = i } }
            if (far >= 0) { keep[far] = true; stack.add(a to far); stack.add(far to b) }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    private fun distToLine(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = b.first - a.first; val dy = b.second - a.second
        val len = hypot(dx, dy)
        return if (len < 1e-9) hypot(p.first - a.first, p.second - a.second) else abs(dx * (p.second - a.second) - dy * (p.first - a.first)) / len
    }

    /** Smooth Bézier through the points; sharp turns (> 50°) stay corners. */
    fun fit(pts: List<Pair<Double, Double>>, loop: Boolean): Bezier? {
        var p = pts
        if (loop && p.size > 2) p = p.dropLast(1)
        val n = p.size
        if (n < 2) return null
        val v = DoubleArray(n * 2); val ins = DoubleArray(n * 2); val outs = DoubleArray(n * 2)
        for (i in 0 until n) { v[2 * i] = p[i].first; v[2 * i + 1] = p[i].second }
        for (i in 0 until n) {
            val hasPrev = loop || i > 0; val hasNext = loop || i < n - 1
            val pr = p[(i - 1 + n) % n]; val nx = p[(i + 1) % n]; val c = p[i]
            val lenPrev = if (hasPrev) hypot(c.first - pr.first, c.second - pr.second) else 0.0
            val lenNext = if (hasNext) hypot(nx.first - c.first, nx.second - c.second) else 0.0
            if (hasPrev && hasNext) {
                val ax = c.first - pr.first; val ay = c.second - pr.second; val bx = nx.first - c.first; val by = nx.second - c.second
                val cos = ((ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by)).coerceAtLeast(1e-9)).coerceIn(-1.0, 1.0)
                if (Math.toDegrees(acos(cos)) > 50.0) continue   // corner: handles stay zero
                var tx = nx.first - pr.first; var ty = nx.second - pr.second
                val tl = hypot(tx, ty).coerceAtLeast(1e-9); tx /= tl; ty /= tl
                outs[2 * i] = tx * lenNext / 3; outs[2 * i + 1] = ty * lenNext / 3
                ins[2 * i] = -tx * lenPrev / 3; ins[2 * i + 1] = -ty * lenPrev / 3
            }
        }
        return Bezier(loop, v, ins, outs)
    }
}
