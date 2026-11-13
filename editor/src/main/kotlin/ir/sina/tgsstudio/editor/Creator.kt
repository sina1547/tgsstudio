package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.Rgba

/** Builds standard Lottie JSON for new elements. */
object Creator {
    val DEFAULT_FILL = Rgba(0.12, 0.48, 1.0, 1.0)

    fun defaultKs(px: Double = 0.0, py: Double = 0.0): JsonObj = obj(
        "o" to staticProp(100.0), "r" to staticProp(0.0), "p" to staticProp(px, py, 0.0),
        "a" to staticProp(0.0, 0.0, 0.0), "s" to staticProp(100.0, 100.0, 100.0),
    )

    fun groupTransform(): JsonObj = obj(
        "ty" to str("tr"), "p" to staticProp(0.0, 0.0), "a" to staticProp(0.0, 0.0), "s" to staticProp(100.0, 100.0),
        "r" to staticProp(0.0), "o" to staticProp(100.0), "sk" to staticProp(0.0), "sa" to staticProp(0.0), "nm" to str("Transform"),
    )

    fun fill(c: Rgba): JsonObj = obj(
        "ty" to str("fl"), "c" to staticProp(c.r, c.g, c.b, c.a), "o" to staticProp(100.0), "r" to int(1), "nm" to str("Fill 1"),
    )

    fun stroke(c: Rgba, width: Double): JsonObj = obj(
        "ty" to str("st"), "c" to staticProp(c.r, c.g, c.b, c.a), "o" to staticProp(100.0), "w" to staticProp(width),
        "lc" to int(2), "lj" to int(2), "ml" to int(4), "nm" to str("Stroke 1"),
    )

    fun rectItem(w: Double, h: Double, roundness: Double = 0.0): JsonObj = obj(
        "ty" to str("rc"), "d" to int(1), "s" to staticProp(w, h), "p" to staticProp(0.0, 0.0), "r" to staticProp(roundness), "nm" to str("Rectangle Path 1"),
    )

    fun ellipseItem(w: Double, h: Double): JsonObj = obj(
        "ty" to str("el"), "d" to int(1), "s" to staticProp(w, h), "p" to staticProp(0.0, 0.0), "nm" to str("Ellipse Path 1"),
    )

    fun starItem(points: Int, outer: Double, inner: Double): JsonObj = obj(
        "ty" to str("sr"), "sy" to int(1), "d" to int(1), "pt" to staticProp(points.toDouble()), "p" to staticProp(0.0, 0.0),
        "r" to staticProp(0.0), "ir" to staticProp(inner), "is" to staticProp(0.0), "or" to staticProp(outer), "os" to staticProp(0.0), "nm" to str("Star 1"),
    )

    fun polygonItem(points: Int, radius: Double): JsonObj = obj(
        "ty" to str("sr"), "sy" to int(2), "d" to int(1), "pt" to staticProp(points.toDouble()), "p" to staticProp(0.0, 0.0),
        "r" to staticProp(0.0), "or" to staticProp(radius), "os" to staticProp(0.0), "nm" to str("Polygon 1"),
    )

    fun pathItem(b: Bezier, name: String = "Path 1"): JsonObj = obj(
        "ty" to str("sh"), "ks" to staticPathProp(b), "nm" to str(name),
    )

    /** A group: [items] followed by a fresh transform. */
    fun group(name: String, vararg items: JsonObj): JsonObj {
        val list = JsonArr(mutableListOf<JsonValue>(*items))
        list.items.add(groupTransform())
        return obj("ty" to str("gr"), "nm" to str(name), "it" to list)
    }

    fun nextIndex(layers: JsonArr?): Int =
        (layers?.items.orEmpty().filterIsInstance<JsonObj>().maxOfOrNull { it.int("ind") ?: 0 } ?: 0) + 1

    /** A new shape layer at (px, py), spanning the whole composition. Shapes inside are in layer-local coordinates. */
    fun shapeLayer(doc: LottieDocument, scopeLayers: JsonArr?, name: String, px: Double, py: Double, vararg groups: JsonObj): JsonObj = obj(
        "ddd" to int(0), "ind" to int(nextIndex(scopeLayers)), "ty" to int(4), "nm" to str(name), "sr" to int(1),
        "ks" to defaultKs(px, py), "ao" to int(0), "shapes" to JsonArr(mutableListOf<JsonValue>(*groups)),
        "ip" to jn(doc.inPoint), "op" to jn(doc.outPoint), "st" to int(0), "bm" to int(0),
    )

    fun nullLayer(doc: LottieDocument, scopeLayers: JsonArr?, name: String): JsonObj = obj(
        "ddd" to int(0), "ind" to int(nextIndex(scopeLayers)), "ty" to int(3), "nm" to str(name), "sr" to int(1),
        "ks" to defaultKs(), "ao" to int(0), "ip" to jn(doc.inPoint), "op" to jn(doc.outPoint), "st" to int(0), "bm" to int(0),
    )

    /** A blank 512×512, 60 fps, 3-second composition that already satisfies the TGS limits. */
    fun blankDocument(name: String): LottieDocument = LottieDocument(
        obj(
            "tgs" to int(1), "v" to str("5.5.2"), "fr" to int(60), "ip" to int(0), "op" to int(180), "w" to int(512), "h" to int(512),
            "nm" to str(name), "ddd" to int(0), "assets" to JsonArr(), "layers" to JsonArr(),
        ),
    )

    fun insertLayer(doc: LottieDocument, scope: String?, layer: JsonObj, at: Int = 0): LayerRef? {
        val arr = ir.sina.tgsstudio.document.DocIndex(doc).layerArray(scope) ?: return null
        val i = at.coerceIn(0, arr.items.size)
        arr.items.add(i, layer)
        return LayerRef(scope, i)
    }
}

/** Builds a stroke-only text shape from centerline paths (hand-drawn look). Coordinates are scaled into canvas units. */
object CenterlineShape {
    /**
     * [lines] are in mask pixels; [scale] maps pixels to canvas units. Paths are centered on their own bounds
     * so the layer can be positioned by its center.
     */
    fun group(lines: Centerline, scale: Double, color: Rgba, name: String = "Text"): Pair<JsonObj, Pair<Double, Double>>? {
        if (lines.paths.isEmpty()) return null
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (b in lines.paths) for (i in 0 until b.count) {
            minX = minOf(minX, b.v[2 * i]); maxX = maxOf(maxX, b.v[2 * i]); minY = minOf(minY, b.v[2 * i + 1]); maxY = maxOf(maxY, b.v[2 * i + 1])
        }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        val items = lines.paths.mapIndexed { i, b ->
            val n = b.count
            val v = DoubleArray(n * 2) { (b.v[it] - if (it % 2 == 0) cx else cy) * scale }
            Creator.pathItem(Bezier(b.closed, v, DoubleArray(n * 2) { b.inT[it] * scale }, DoubleArray(n * 2) { b.outT[it] * scale }), "Stroke ${i + 1}")
        }
        val stroke = Creator.stroke(color, (lines.thickness * scale).coerceAtLeast(1.0))
        return Creator.group(name, *items.toTypedArray(), stroke) to ((maxX - minX) * scale to (maxY - minY) * scale)
    }
}
