package ir.sina.tgsstudio.export

import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.Cap
import ir.sina.tgsstudio.renderer.Contour
import ir.sina.tgsstudio.renderer.Gradient
import ir.sina.tgsstudio.renderer.Join
import ir.sina.tgsstudio.renderer.PaintItem
import ir.sina.tgsstudio.renderer.Rgba
import ir.sina.tgsstudio.renderer.Scene
import ir.sina.tgsstudio.renderer.SceneLayer
import java.util.Locale

/** Renders one frame of a [Scene] as a standalone SVG (vector elements only; alpha mattes are not exported). */
object SceneSvg {
    fun toSvg(scene: Scene): String {
        val defs = StringBuilder()
        val body = StringBuilder()
        var uid = 0
        fun num(v: Double): String = if (v == Math.rint(v) && Math.abs(v) < 1e9) v.toLong().toString() else String.format(Locale.ROOT, "%.3f", v).trimEnd('0').trimEnd('.')
        fun rgb(c: Rgba) = String.format(Locale.ROOT, "#%02x%02x%02x", (c.r.coerceIn(0.0, 1.0) * 255).toInt(), (c.g.coerceIn(0.0, 1.0) * 255).toInt(), (c.b.coerceIn(0.0, 1.0) * 255).toInt())
        fun matrix(m: Affine) = "matrix(${num(m.a)} ${num(m.b)} ${num(m.c)} ${num(m.d)} ${num(m.e)} ${num(m.f)})"
        fun pathData(cs: List<Contour>): String {
            val sb = StringBuilder()
            for (c in cs) {
                val first = c.segments.firstOrNull() ?: continue
                sb.append('M').append(num(first.x0)).append(' ').append(num(first.y0))
                for (s in c.segments) sb.append('C').append(num(s.x1)).append(' ').append(num(s.y1)).append(' ')
                    .append(num(s.x2)).append(' ').append(num(s.y2)).append(' ').append(num(s.x3)).append(' ').append(num(s.y3))
                if (c.closed) sb.append('Z')
            }
            return sb.toString()
        }
        fun gradient(g: Gradient): String {
            val id = "g${uid++}"
            val stops = g.stops.joinToString("") {
                "<stop offset=\"${num(it.position)}\" stop-color=\"${rgb(it.color)}\" stop-opacity=\"${num(it.color.a)}\"/>"
            }
            defs.append(
                if (g.radial) "<radialGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" cx=\"${num(g.x0)}\" cy=\"${num(g.y0)}\" r=\"${num(Math.hypot(g.x1 - g.x0, g.y1 - g.y0))}\">$stops</radialGradient>"
                else "<linearGradient id=\"$id\" gradientUnits=\"userSpaceOnUse\" x1=\"${num(g.x0)}\" y1=\"${num(g.y0)}\" x2=\"${num(g.x1)}\" y2=\"${num(g.y1)}\">$stops</linearGradient>",
            )
            return id
        }
        fun item(it: PaintItem) {
            val d = pathData(it.contours)
            if (d.isEmpty()) return
            val paintAttr = it.paint.color?.let { c -> rgb(c) } ?: "url(#${gradient(it.paint.gradient!!)})"
            val alpha = (it.paint.color?.a ?: 1.0) * it.paint.opacity
            val st = it.stroke
            val attrs = if (st == null) {
                "fill=\"$paintAttr\" fill-opacity=\"${num(alpha)}\"" + if (it.evenOdd) " fill-rule=\"evenodd\"" else ""
            } else {
                val cap = when (st.cap) { Cap.BUTT -> "butt"; Cap.ROUND -> "round"; Cap.SQUARE -> "square" }
                val join = when (st.join) { Join.MITER -> "miter"; Join.ROUND -> "round"; Join.BEVEL -> "bevel" }
                "fill=\"none\" stroke=\"$paintAttr\" stroke-opacity=\"${num(alpha)}\" stroke-width=\"${num(st.width)}\" stroke-linecap=\"$cap\" stroke-linejoin=\"$join\" stroke-miterlimit=\"${num(st.miterLimit)}\"" +
                    (st.dashes?.let { ds -> " stroke-dasharray=\"${ds.joinToString(" ") { v -> num(v) }}\" stroke-dashoffset=\"${num(st.dashPhase)}\"" } ?: "")
            }
            body.append("<path d=\"$d\" transform=\"${matrix(it.matrix)}\" $attrs/>\n")
        }
        fun layer(l: SceneLayer) {
            val clip = if (l.masks.isNotEmpty()) {
                val id = "c${uid++}"
                defs.append("<clipPath id=\"$id\"><path d=\"${pathData(l.masks.flatMap { it.contours })}\" transform=\"${matrix(l.matrix)}\"/></clipPath>")
                " clip-path=\"url(#$id)\""
            } else ""
            body.append("<g${if (clip.isNotEmpty()) clip else ""}>\n<g transform=\"${matrix(l.matrix)}\"${if (l.opacity < 1.0) " opacity=\"${num(l.opacity)}\"" else ""}>\n")
            l.items.forEach(::item)
            l.children.forEach(::layer)
            body.append("</g>\n</g>\n")
        }
        scene.layers.forEach(::layer)
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${scene.width}\" height=\"${scene.height}\" viewBox=\"0 0 ${scene.width} ${scene.height}\">\n" +
            (if (defs.isNotEmpty()) "<defs>$defs</defs>\n" else "") + body + "</svg>\n"
    }
}
