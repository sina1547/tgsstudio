package ir.sina.tgsstudio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import ir.sina.tgsstudio.editor.Mask
import ir.sina.tgsstudio.renderer.Bezier
import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * Renders text with Android's shaping/layout engine before vector conversion.
 * Supports imported TTF/OTF faces, mixed RTL/LTR text and explicit line breaks.
 * TGS has no live-text primitive, so text is converted to editable vector strokes.
 */
object TextRaster {
    fun mask(
        text: String,
        sizePx: Float = 220f,
        bold: Boolean = false,
        typeface: Typeface = Typeface.DEFAULT,
        lineSpacingMultiplier: Float = 1f,
    ): Mask? {
        if (text.isBlank()) return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx.coerceIn(12f, 600f)
            color = android.graphics.Color.BLACK
            this.typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val measuredWidth = text.split('\n').maxOfOrNull { paint.measureText(it) } ?: 0f
        val pad = 12
        val layoutWidth = ceil(measuredWidth).toInt().coerceIn(8, 4096)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setLineSpacing(0f, lineSpacingMultiplier.coerceIn(0.8f, 2.5f))
            .build()
        val w = (layoutWidth + pad * 2).coerceIn(8, 4096)
        val h = (layout.height + pad * 2).coerceIn(8, 2048)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bmp)
        canvas.save()
        canvas.translate(pad.toFloat(), pad.toFloat())
        layout.draw(canvas)
        canvas.restore()
        val buf = ByteBuffer.allocate(bmp.byteCount)
        bmp.copyPixelsToBuffer(buf)
        val bytes = buf.array()
        val stride = bmp.rowBytes
        val ink = BooleanArray(w * h) { i ->
            (bytes[(i / w) * stride + (i % w)].toInt() and 0xFF) > 127
        }
        bmp.recycle()
        return Mask(w, h, ink)
    }

    /** Converts the rendered glyph mask to closed vector contours (outer edges and counters). */
    fun outlines(mask: Mask, tolerance: Double = 1.25): List<Bezier> {
        val w = mask.width
        val stride = w + 1
        val edges = LinkedHashMap<Long, MutableList<Long>>()
        fun key(x: Int, y: Int) = y.toLong() * stride + x
        fun edge(x1: Int, y1: Int, x2: Int, y2: Int) {
            edges.getOrPut(key(x1, y1)) { ArrayList() }.add(key(x2, y2))
        }
        for (y in 0 until mask.height) for (x in 0 until w) {
            if (!mask[x, y]) continue
            if (!mask[x, y - 1]) edge(x, y, x + 1, y)
            if (!mask[x + 1, y]) edge(x + 1, y, x + 1, y + 1)
            if (!mask[x, y + 1]) edge(x + 1, y + 1, x, y + 1)
            if (!mask[x - 1, y]) edge(x, y + 1, x, y)
        }
        val contours = ArrayList<Bezier>()
        while (edges.isNotEmpty()) {
            val start = edges.keys.first()
            var current = start
            val points = ArrayList<Pair<Double, Double>>()
            points += (current % stride).toDouble() to (current / stride).toDouble()
            var guard = edges.values.sumOf { it.size } + 2
            while (guard-- > 0) {
                val nexts = edges[current] ?: break
                if (nexts.isEmpty()) { edges.remove(current); break }
                val next = nexts.removeAt(0)
                if (nexts.isEmpty()) edges.remove(current)
                current = next
                if (current == start) break
                points += (current % stride).toDouble() to (current / stride).toDouble()
            }
            if (current != start || points.size < 4) continue
            val simplified = simplifyClosed(points, tolerance)
            if (simplified.size < 3) continue
            val vertices = DoubleArray(simplified.size * 2)
            simplified.forEachIndexed { i, p -> vertices[i * 2] = p.first; vertices[i * 2 + 1] = p.second }
            contours += Bezier(true, vertices, DoubleArray(vertices.size), DoubleArray(vertices.size))
        }
        return contours
    }

    private fun simplifyClosed(points: List<Pair<Double, Double>>, epsilon: Double): List<Pair<Double, Double>> {
        if (points.size <= 4) return points
        val origin = points.first()
        val split = points.indices.maxByOrNull { i ->
            val dx = points[i].first - origin.first
            val dy = points[i].second - origin.second
            dx * dx + dy * dy
        } ?: return points
        if (split <= 1 || split >= points.lastIndex) return points
        val a = simplifyOpen(points.subList(0, split + 1), epsilon)
        val bInput = points.subList(split, points.size) + listOf(points.first())
        val b = simplifyOpen(bInput, epsilon)
        return (a.dropLast(1) + b.dropLast(1)).distinct()
    }

    private fun simplifyOpen(points: List<Pair<Double, Double>>, epsilon: Double): List<Pair<Double, Double>> {
        if (points.size <= 2) return points
        val a = points.first()
        val b = points.last()
        val dx = b.first - a.first
        val dy = b.second - a.second
        val len2 = dx * dx + dy * dy
        var farthest = 0
        var maxDistance = 0.0
        for (i in 1 until points.lastIndex) {
            val p = points[i]
            val t = if (len2 == 0.0) 0.0 else (((p.first - a.first) * dx + (p.second - a.second) * dy) / len2).coerceIn(0.0, 1.0)
            val ex = p.first - (a.first + t * dx)
            val ey = p.second - (a.second + t * dy)
            val distance = kotlin.math.sqrt(ex * ex + ey * ey)
            if (distance > maxDistance) { maxDistance = distance; farthest = i }
        }
        if (maxDistance <= epsilon || farthest == 0) return listOf(a, b)
        return simplifyOpen(points.subList(0, farthest + 1), epsilon).dropLast(1) +
            simplifyOpen(points.subList(farthest, points.size), epsilon)
    }
}
