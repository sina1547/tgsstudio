package ir.sina.tgsstudio.renderer

/** Trim Paths. Percentages are 0..100, [offsetDeg] is the 0..360 offset; [simultaneous] = Lottie mode 1. */
object Trim {
    fun apply(contours: List<Contour>, startPct: Double, endPct: Double, offsetDeg: Double, simultaneous: Boolean): List<Contour> {
        var s = (startPct / 100.0).coerceIn(0.0, 1.0)
        var e = (endPct / 100.0).coerceIn(0.0, 1.0)
        if (s > e) { val t = s; s = e; e = t }
        val off = offsetDeg / 360.0
        if (e - s <= 0.0) return emptyList()
        if (e - s >= 1.0 && off % 1.0 == 0.0) return contours

        // Fractions of the length, shifted by the offset and wrapped into [0, 1].
        val span = e - s
        val a = ((s + off) % 1.0 + 1.0) % 1.0
        val ranges = if (a + span <= 1.0) listOf(a to a + span) else listOf(a to 1.0, 0.0 to a + span - 1.0)

        val real = contours.filter { it.segments.isNotEmpty() }
        return if (simultaneous) {
            real.flatMap { c -> ranges.mapNotNull { (f, t) -> slice(c, f * c.length, t * c.length) } }
        } else {
            val total = real.sumOf { it.length }
            if (total <= 0.0) return emptyList()
            val out = ArrayList<Contour>()
            for ((f, t) in ranges) {
                val from = f * total; val to = t * total
                var base = 0.0
                for (c in real) {
                    val lo = maxOf(from, base); val hi = minOf(to, base + c.length)
                    if (hi > lo) slice(c, lo - base, hi - base)?.let(out::add)
                    base += c.length
                }
            }
            out
        }
    }

    /** Open sub-path of [c] between arc distances [from] and [to]. */
    private fun slice(c: Contour, from: Double, to: Double): Contour? {
        if (to - from <= 0.0) return null
        val segs = ArrayList<Cubic>()
        var base = 0.0
        for (seg in c.segments) {
            val len = seg.length
            val lo = maxOf(from, base); val hi = minOf(to, base + len)
            if (hi > lo) segs += if (lo <= base && hi >= base + len) seg else seg.slice(lo - base, hi - base)
            base += len
            if (base >= to) break
        }
        return if (segs.isEmpty()) null else Contour(segs, false)
    }
}
