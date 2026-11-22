package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.Contour
import ir.sina.tgsstudio.renderer.Rgba
import ir.sina.tgsstudio.renderer.Shapes
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.sqrt

class SvgImportResult(val groups: List<JsonObj>, val skipped: Set<String>)

/**
 * Imports the vector content of an SVG as native Lottie shapes. Transforms are baked into the path
 * coordinates; the result is centered on (0, 0) and scaled to fit [targetSize].
 */
object SvgImport {
    private class Style(
        val fill: Rgba? = Rgba(0.0, 0.0, 0.0, 1.0), val stroke: Rgba? = null, val strokeWidth: Double = 1.0,
        val opacity: Double = 1.0, val fillOpacity: Double = 1.0, val strokeOpacity: Double = 1.0, val evenOdd: Boolean = false,
        val cap: Int = 1, val join: Int = 1,
    )

    private class Piece(val name: String, val contours: List<Contour>, val style: Style, val scale: Double)

    fun parse(svg: String, targetSize: Double = 360.0): SvgImportResult {
        require(!svg.contains("<!DOCTYPE", ignoreCase = true) && !svg.contains("<!ENTITY", ignoreCase = true)) { "SVG with DOCTYPE/ENTITY declarations is not supported" }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(svg)))
        val root = doc.documentElement
        require(root.tagName.substringAfter(':') == "svg") { "Not an SVG file" }
        val skipped = linkedSetOf<String>()
        val gradients = HashMap<String, Rgba>()
        collectGradients(root, gradients)

        var base = Affine.IDENTITY
        root.getAttribute("viewBox").split(Regex("[ ,]+")).mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 4 }?.let { base = Affine.translate(-it[0], -it[1]) }

        val pieces = ArrayList<Piece>()
        walk(root, base, Style(), pieces, skipped, gradients, isRoot = true)

        // fit to the target box, centered on the origin
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (p in pieces) for (c in p.contours) for (s in c.segments) {
            val f = s.flatten(8)
            for (i in 0 until f.size / 2) { minX = minOf(minX, f[2 * i]); maxX = maxOf(maxX, f[2 * i]); minY = minOf(minY, f[2 * i + 1]); maxY = maxOf(maxY, f[2 * i + 1]) }
        }
        if (minX > maxX) return SvgImportResult(emptyList(), skipped)
        val size = maxOf(maxX - minX, maxY - minY).takeIf { it > 1e-9 } ?: 1.0
        val k = targetSize / size
        val fit = Affine.scale(k, k) * Affine.translate(-(minX + maxX) / 2, -(minY + maxY) / 2)

        val groups = pieces.map { p ->
            val items = ArrayList<JsonObj>()
            p.contours.forEachIndexed { i, c ->
                PathOps.fromContour(c.transformed(fit))?.let { items += Creator.pathItem(it, "Path ${i + 1}") }
            }
            val st = p.style
            p.style.fill?.let { c -> items += Creator.fill(c).also { f -> StyleOps.setOpacity(f, 0.0, st.fillOpacity * st.opacity * 100); if (st.evenOdd) f.putInt("r", 2) } }
            p.style.stroke?.let { c ->
                items += Creator.stroke(c, st.strokeWidth * p.scale * k).also { s ->
                    StyleOps.setOpacity(s, 0.0, st.strokeOpacity * st.opacity * 100); s.putInt("lc", st.cap); s.putInt("lj", st.join)
                }
            }
            Creator.group(p.name, *items.toTypedArray())
        }
        return SvgImportResult(groups.reversed(), skipped) // SVG paints later elements on top; Lottie lists the top item first
    }

    private fun collectGradients(e: Element, out: MutableMap<String, Rgba>) {
        val tag = e.tagName.substringAfter(':')
        if (tag == "linearGradient" || tag == "radialGradient") {
            val id = e.getAttribute("id")
            val stop = children(e).firstOrNull { it.tagName.substringAfter(':') == "stop" }
            if (id.isNotEmpty() && stop != null) {
                val st = styleMap(stop)
                color(st["stop-color"] ?: stop.getAttribute("stop-color").ifEmpty { "black" })?.let { out[id] = it }
            }
        }
        children(e).forEach { collectGradients(it, out) }
    }

    private fun children(e: Element): List<Element> {
        val out = ArrayList<Element>(); var n: Node? = e.firstChild
        while (n != null) { if (n is Element) out += n; n = n.nextSibling }
        return out
    }

    private fun styleMap(e: Element): Map<String, String> {
        val m = HashMap<String, String>()
        e.getAttribute("style").split(';').forEach { kv -> val i = kv.indexOf(':'); if (i > 0) m[kv.substring(0, i).trim()] = kv.substring(i + 1).trim() }
        return m
    }

    private fun walk(e: Element, matrix: Affine, parent: Style, out: MutableList<Piece>, skipped: MutableSet<String>, grads: Map<String, Rgba>, isRoot: Boolean = false) {
        val tag = e.tagName.substringAfter(':')
        if (e.getAttribute("display") == "none" || styleMap(e)["display"] == "none") return
        if (tag in IGNORED) return
        val m = matrix * parseTransform(e.getAttribute("transform"))
        val style = styleOf(e, parent, grads, skipped)
        when (tag) {
            "svg", "g", "a", "switch" -> children(e).forEach { walk(it, m, style, out, skipped, grads) }
            "path" -> add(e, SvgPath.parse(e.getAttribute("d")), m, style, out)
            "rect" -> {
                val x = num(e, "x"); val y = num(e, "y"); val w = num(e, "width"); val h = num(e, "height")
                val rx = e.getAttribute("rx").toDoubleOrNull() ?: e.getAttribute("ry").toDoubleOrNull() ?: 0.0
                if (w > 0 && h > 0) add(e, listOf(Shapes.rect(x + w / 2, y + h / 2, w, h, rx)), m, style, out)
            }
            "circle" -> { val r = num(e, "r"); if (r > 0) add(e, listOf(Shapes.ellipse(num(e, "cx"), num(e, "cy"), 2 * r, 2 * r)), m, style, out) }
            "ellipse" -> { val rx = num(e, "rx"); val ry = num(e, "ry"); if (rx > 0 && ry > 0) add(e, listOf(Shapes.ellipse(num(e, "cx"), num(e, "cy"), 2 * rx, 2 * ry)), m, style, out) }
            "line" -> add(e, SvgPath.parse("M${num(e, "x1")} ${num(e, "y1")}L${num(e, "x2")} ${num(e, "y2")}"), m, style, out)
            "polyline", "polygon" -> {
                val pts = e.getAttribute("points").trim()
                if (pts.isNotEmpty()) add(e, SvgPath.parse("M$pts" + if (tag == "polygon") "Z" else ""), m, style, out)
            }
            else -> skipped += "<$tag>"
        }
    }

    private fun add(e: Element, contours: List<Contour>, m: Affine, style: Style, out: MutableList<Piece>) {
        if (contours.isEmpty()) return
        val scale = sqrt(Math.abs(m.determinant))
        val name = e.getAttribute("id").ifEmpty { e.tagName.substringAfter(':').replaceFirstChar { it.uppercase() } }
        out += Piece(name, contours.map { it.transformed(m) }, style, scale)
    }

    private fun num(e: Element, a: String) = e.getAttribute(a).trim().removeSuffix("px").toDoubleOrNull() ?: 0.0

    private fun styleOf(e: Element, p: Style, grads: Map<String, Rgba>, skipped: MutableSet<String>): Style {
        val sm = styleMap(e)
        fun attr(n: String): String? = sm[n] ?: e.getAttribute(n).takeIf { it.isNotEmpty() }
        fun paint(v: String?, inherited: Rgba?): Rgba? {
            if (v == null || v == "inherit") return inherited
            if (v.startsWith("url(")) {
                skipped += "gradients (first stop color used)"
                return grads[v.substringAfter("#").substringBefore(")").trim().trim('\'', '"')] ?: inherited
            }
            return color(v)
        }
        return Style(
            fill = paint(attr("fill"), p.fill), stroke = paint(attr("stroke"), p.stroke),
            strokeWidth = attr("stroke-width")?.removeSuffix("px")?.toDoubleOrNull() ?: p.strokeWidth,
            opacity = p.opacity * (attr("opacity")?.toDoubleOrNull() ?: 1.0),
            fillOpacity = attr("fill-opacity")?.toDoubleOrNull() ?: p.fillOpacity,
            strokeOpacity = attr("stroke-opacity")?.toDoubleOrNull() ?: p.strokeOpacity,
            evenOdd = attr("fill-rule")?.let { it == "evenodd" } ?: p.evenOdd,
            cap = when (attr("stroke-linecap")) { "round" -> 2; "square" -> 3; "butt" -> 1; else -> p.cap },
            join = when (attr("stroke-linejoin")) { "round" -> 2; "bevel" -> 3; "miter" -> 1; else -> p.join },
        )
    }

    fun parseTransform(t: String): Affine {
        var m = Affine.IDENTITY
        for (match in Regex("""(\w+)\s*\(([^)]*)\)""").findAll(t)) {
            val a = match.groupValues[2].split(Regex("[ ,]+")).filter { it.isNotEmpty() }.map { it.toDouble() }
            m *= when (match.groupValues[1]) {
                "matrix" -> if (a.size == 6) Affine(a[0], a[1], a[2], a[3], a[4], a[5]) else Affine.IDENTITY
                "translate" -> Affine.translate(a.getOrElse(0) { 0.0 }, a.getOrElse(1) { 0.0 })
                "scale" -> Affine.scale(a.getOrElse(0) { 1.0 }, a.getOrElse(1) { a.getOrElse(0) { 1.0 } })
                "rotate" -> if (a.size >= 3) Affine.translate(a[1], a[2]) * Affine.rotate(a[0]) * Affine.translate(-a[1], -a[2]) else Affine.rotate(a.getOrElse(0) { 0.0 })
                "skewX" -> Affine(c = Math.tan(Math.toRadians(a.getOrElse(0) { 0.0 })))
                "skewY" -> Affine(b = Math.tan(Math.toRadians(a.getOrElse(0) { 0.0 })))
                else -> Affine.IDENTITY
            }
        }
        return m
    }

    fun color(v: String): Rgba? {
        val s = v.trim().lowercase()
        if (s == "none" || s == "transparent") return null
        if (s == "currentcolor") return Rgba(0.0, 0.0, 0.0, 1.0)
        NAMED[s]?.let { return it }
        if (s.startsWith("#")) {
            val h = s.substring(1)
            val full = when (h.length) { 3, 4 -> h.map { "$it$it" }.joinToString(""); 6, 8 -> h; else -> return null }
            val n = full.chunked(2).map { it.toIntOrNull(16) ?: return null }
            return Rgba(n[0] / 255.0, n[1] / 255.0, n[2] / 255.0, n.getOrNull(3)?.div(255.0) ?: 1.0)
        }
        if (s.startsWith("rgb")) {
            val parts = s.substringAfter("(").substringBefore(")").split(Regex("[ ,/]+")).filter { it.isNotEmpty() }
            if (parts.size < 3) return null
            fun ch(x: String) = if (x.endsWith("%")) x.dropLast(1).toDouble() / 100 else x.toDouble() / 255
            return Rgba(ch(parts[0]).coerceIn(0.0, 1.0), ch(parts[1]).coerceIn(0.0, 1.0), ch(parts[2]).coerceIn(0.0, 1.0), parts.getOrNull(3)?.let { if (it.endsWith("%")) it.dropLast(1).toDouble() / 100 else it.toDouble() } ?: 1.0)
        }
        return null
    }

    private val IGNORED = setOf("defs", "title", "desc", "metadata", "style", "linearGradient", "radialGradient", "clipPath", "mask", "filter", "symbol", "marker", "pattern", "script")
    private val NAMED = mapOf(
        "black" to Rgba(0.0, 0.0, 0.0, 1.0), "white" to Rgba(1.0, 1.0, 1.0, 1.0), "red" to Rgba(1.0, 0.0, 0.0, 1.0),
        "green" to Rgba(0.0, 0.502, 0.0, 1.0), "lime" to Rgba(0.0, 1.0, 0.0, 1.0), "blue" to Rgba(0.0, 0.0, 1.0, 1.0),
        "yellow" to Rgba(1.0, 1.0, 0.0, 1.0), "orange" to Rgba(1.0, 0.647, 0.0, 1.0), "purple" to Rgba(0.502, 0.0, 0.502, 1.0),
        "gray" to Rgba(0.502, 0.502, 0.502, 1.0), "grey" to Rgba(0.502, 0.502, 0.502, 1.0), "pink" to Rgba(1.0, 0.753, 0.796, 1.0),
        "cyan" to Rgba(0.0, 1.0, 1.0, 1.0), "magenta" to Rgba(1.0, 0.0, 1.0, 1.0), "brown" to Rgba(0.647, 0.165, 0.165, 1.0),
    )
}
