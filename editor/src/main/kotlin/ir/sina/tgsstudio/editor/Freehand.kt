package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.renderer.Bezier
import kotlin.math.hypot

/** Turns the raw touch points of a drawn stroke into a smooth, light vector path. */
object Freehand {
    /** [tolerance] is in document units: larger = fewer points, smoother curve. Returns null for a stroke that is just a tap. */
    fun fit(points: List<Pair<Double, Double>>, tolerance: Double = 2.0): Bezier? {
        val dedup = ArrayList<Pair<Double, Double>>(points.size)
        for (p in points) if (dedup.isEmpty() || hypot(p.first - dedup.last().first, p.second - dedup.last().second) >= 0.75) dedup += p
        if (dedup.size < 2) return null
        val simple = Skeleton.simplify(dedup, tolerance)
        // only genuine reversals stay sharp; ordinary hand wobble becomes a curve
        return Skeleton.fit(simple, loop = false, cornerDegrees = 115.0)
    }

    /** Total length of the polyline through [points]. */
    fun length(points: List<Pair<Double, Double>>): Double = points.zipWithNext().sumOf { (a, b) -> hypot(b.first - a.first, b.second - a.second) }
}
