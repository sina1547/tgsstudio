package ir.sina.tgsstudio.renderer

import ir.sina.tgsstudio.document.Layer
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.LayerType
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.Precomp
import ir.sina.tgsstudio.document.ShapeItem
import ir.sina.tgsstudio.document.ShapeKind
import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import kotlin.math.hypot

/**
 * Turns a [LottieDocument] into a [Scene] for one frame. Pure Kotlin, no drawing: the app maps the
 * scene onto a Canvas. Features that are not rendered yet are reported in [Scene.unsupported]
 * instead of being silently dropped.
 */
class SceneBuilder(private val doc: LottieDocument) {

    private val unsupported = linkedSetOf<String>()
    private var assets: Map<String?, Precomp> = emptyMap()

    fun build(frame: Double): Scene {
        unsupported.clear()
        assets = doc.precomps.associateBy { it.id }
        val layers = buildLayers(doc.layers, frame, depth = 0, scope = null)
        return Scene(doc.width, doc.height, layers, unsupported.toSet())
    }

    // ---- layers ------------------------------------------------------------------------------

    private fun buildLayers(layers: List<Layer>, frame: Double, depth: Int, scope: String?): List<SceneLayer> {
        if (depth > MAX_PRECOMP_DEPTH) { unsupported += "deeply nested precomps"; return emptyList() }
        fun matrixOf(l: Layer) = Transforms.layerFull(layers, l, frame)

        val out = ArrayList<SceneLayer>()
        // Bottom layer first, so later entries paint on top.
        for (idx in layers.indices.reversed()) {
            val l = layers[idx]
            if (l.raw.int("td") == 1 || l.hidden || frame < l.inPoint || frame >= l.outPoint) continue
            try {
                val built = buildLayer(l, matrixOf(l), frame, depth, LayerRef(scope, idx))
                var matte: Matte? = null
                val tt = l.raw.int("tt")
                if (tt != null && tt != 0) {
                    val src = layers.getOrNull(idx - 1)
                    if (src != null && src.raw.int("td") == 1) {
                        if (tt == 1 || tt == 2) {
                            val srcLayer = buildLayer(src, matrixOf(src), frame, depth, LayerRef(scope, idx - 1))
                            matte = Matte(inverted = tt == 2, layer = srcLayer)
                        } else unsupported += "luma matte"
                    }
                }
                out += if (matte != null) SceneLayer(built.ref, built.name, built.matrix, built.opacity, built.items, built.children, built.masks, matte) else built
            } catch (e: RuntimeException) {
                unsupported += "malformed layer \"${l.name}\""
            }
        }
        return out
    }

    private fun buildLayer(l: Layer, matrix: Affine, frame: Double, depth: Int, ref: LayerRef): SceneLayer {
        val ks = l.raw.obj("ks")
        val opacity = (PropEval.number(ks?.obj("o"), frame, 100.0) / 100.0).coerceIn(0.0, 1.0)
        if ((l.raw.int("bm") ?: 0) != 0) unsupported += "blend modes"
        if (l.raw.arr("ef")?.items?.isNotEmpty() == true) unsupported += "layer effects"
        if (l.raw.bool("ao") == true) unsupported += "auto-orient"

        val items = ArrayList<PaintItem>()
        val children = ArrayList<SceneLayer>()
        when (l.type) {
            LayerType.SHAPE -> shapeContent(l.shapes, frame, Affine.IDENTITY, 1.0, items, emptyList())
            LayerType.SOLID -> solidItem(l)?.let(items::add)
            LayerType.PRECOMP -> {
                val asset = assets[l.refId]
                if (asset == null) unsupported += "missing precomp \"${l.refId}\""
                else {
                    if (l.raw.has("tm")) unsupported += "time remap"
                    val sr = l.raw.double("sr")?.takeIf { it != 0.0 } ?: 1.0
                    children += buildLayers(asset.layers, (frame - l.startTime) / sr, depth + 1, l.refId)
                }
            }
            LayerType.NULL -> Unit
            LayerType.IMAGE -> unsupported += "image layers"
            LayerType.TEXT -> unsupported += "text layers"
            else -> unsupported += "layer type ${l.typeCode}"
        }
        return SceneLayer(ref, l.name, matrix, opacity, items, children, masksOf(l, frame), null)
    }

    private fun solidItem(l: Layer): PaintItem? {
        val hex = l.raw.string("sc")?.removePrefix("#") ?: return null
        val rgb = hex.toLongOrNull(16) ?: return null
        val w = l.raw.double("sw") ?: return null
        val h = l.raw.double("sh") ?: return null
        val color = Rgba(((rgb shr 16) and 255) / 255.0, ((rgb shr 8) and 255) / 255.0, (rgb and 255) / 255.0, 1.0)
        val c = Shapes.rect(w / 2, h / 2, w, h, 0.0)
        return PaintItem(listOf(c), Affine.IDENTITY, Paint(color, null, 1.0), null, false)
    }

    private fun masksOf(l: Layer, frame: Double): List<SceneMask> {
        val arr = l.raw.arr("masksProperties") ?: return emptyList()
        val out = ArrayList<SceneMask>()
        for (m in arr.items.filterIsInstance<JsonObj>()) {
            when (m.string("mode") ?: "a") {
                "n" -> continue
                "a" -> {
                    val b = PropEval.path(m.obj("pt"), frame) ?: continue
                    out += SceneMask(listOf(b.toContour()), m.bool("inv") ?: false)
                }
                else -> unsupported += "mask modes other than Add"
            }
        }
        return out
    }

    private fun transformOf(ks: JsonObj?, frame: Double) = Transforms.of(ks, frame)

    // ---- shapes ------------------------------------------------------------------------------

    /**
     * Lottie lists a group's items top to bottom. A fill/stroke applies to every path above it in the
     * same group (after any Trim Paths above it); the item listed first paints last (on top).
     */
    private fun shapeContent(items: List<ShapeItem>, frame: Double, parent: Affine, parentOpacity: Double, out: MutableList<PaintItem>, prefix: List<Int>) {
        val tr = items.firstOrNull { it.kind == ShapeKind.TRANSFORM }
        val m = parent * (tr?.let { transformOf(it.raw, frame) } ?: Affine.IDENTITY)
        val groupOpacity = parentOpacity * (tr?.let { PropEval.number(it.raw.obj("o"), frame, 100.0) / 100.0 } ?: 1.0)

        var paths: List<Contour> = emptyList()
        val topToBottom = ArrayList<List<PaintItem>>()

        for ((idx, item) in items.withIndex()) {
            if (item.hidden) continue
            val raw = item.raw
            when (item.kind) {
                ShapeKind.TRANSFORM -> Unit
                ShapeKind.GROUP -> {
                    val sub = ArrayList<PaintItem>()
                    shapeContent(item.children, frame, m, groupOpacity, sub, prefix + idx)
                    topToBottom += sub
                }
                ShapeKind.PATH -> PropEval.path(raw.obj("ks"), frame)?.let { paths = paths + it.toContour() }
                ShapeKind.RECT -> {
                    val p = PropEval.values(raw.obj("p"), frame); val s = PropEval.values(raw.obj("s"), frame)
                    if (s != null && s.size >= 2) {
                        paths = paths + Shapes.rect(p?.getOrNull(0) ?: 0.0, p?.getOrNull(1) ?: 0.0, s[0], s[1], PropEval.number(raw.obj("r"), frame, 0.0))
                    }
                }
                ShapeKind.ELLIPSE -> {
                    val p = PropEval.values(raw.obj("p"), frame); val s = PropEval.values(raw.obj("s"), frame)
                    if (s != null && s.size >= 2) paths = paths + Shapes.ellipse(p?.getOrNull(0) ?: 0.0, p?.getOrNull(1) ?: 0.0, s[0], s[1])
                }
                ShapeKind.STAR -> {
                    val p = PropEval.values(raw.obj("p"), frame)
                    val polygon = raw.int("sy") == 2
                    if (raw.has("is") && PropEval.number(raw.obj("is"), frame, 0.0) != 0.0 || PropEval.number(raw.obj("os"), frame, 0.0) != 0.0) {
                        unsupported += "star/polygon roundness"
                    }
                    paths = paths + Shapes.star(
                        p?.getOrNull(0) ?: 0.0, p?.getOrNull(1) ?: 0.0,
                        PropEval.number(raw.obj("pt"), frame, 5.0).toInt(),
                        PropEval.number(raw.obj("or"), frame, 50.0),
                        if (polygon) null else PropEval.number(raw.obj("ir"), frame, 25.0),
                        PropEval.number(raw.obj("r"), frame, 0.0),
                    )
                }
                ShapeKind.TRIM -> {
                    paths = Trim.apply(
                        paths,
                        PropEval.number(raw.obj("s"), frame, 0.0), PropEval.number(raw.obj("e"), frame, 100.0),
                        PropEval.number(raw.obj("o"), frame, 0.0), (raw.int("m") ?: 1) == 1,
                    )
                }
                ShapeKind.FILL, ShapeKind.GRADIENT_FILL -> {
                    val paint = paintOf(raw, item.kind == ShapeKind.GRADIENT_FILL, frame, groupOpacity)
                    if (paint != null && paths.isNotEmpty()) {
                        topToBottom += listOf(PaintItem(paths, m, paint, null, (raw.int("r") ?: 1) == 2, prefix + idx))
                    }
                }
                ShapeKind.STROKE, ShapeKind.GRADIENT_STROKE -> {
                    val paint = paintOf(raw, item.kind == ShapeKind.GRADIENT_STROKE, frame, groupOpacity)
                    if (paint != null && paths.isNotEmpty()) {
                        topToBottom += listOf(PaintItem(paths, m, paint, strokeOf(raw, frame), false, prefix + idx))
                    }
                }
                ShapeKind.ROUNDED_CORNERS -> unsupported += "rounded corners modifier"
                ShapeKind.REPEATER -> unsupported += "repeater"
                ShapeKind.MERGE -> unsupported += "merge paths"
                ShapeKind.UNKNOWN -> unsupported += "shape type \"${item.typeCode}\""
            }
        }
        for (entry in topToBottom.asReversed()) out += entry
    }

    private fun paintOf(raw: JsonObj, gradient: Boolean, frame: Double, groupOpacity: Double): Paint? {
        val opacity = (PropEval.number(raw.obj("o"), frame, 100.0) / 100.0).coerceIn(0.0, 1.0) * groupOpacity
        if (!gradient) {
            val c = PropEval.values(raw.obj("c"), frame) ?: return null
            if (c.size < 3) return null
            return Paint(Rgba(c[0], c[1], c[2], c.getOrElse(3) { 1.0 }), null, opacity)
        }
        val g = raw.obj("g") ?: return null
        val count = g.int("p") ?: return null
        val vals = PropEval.values(g.obj("k"), frame) ?: return null
        if (vals.size < count * 4) return null
        val alphaStops = ArrayList<Pair<Double, Double>>()
        var i = count * 4
        while (i + 1 < vals.size) { alphaStops += vals[i] to vals[i + 1]; i += 2 }
        fun alphaAt(pos: Double): Double {
            if (alphaStops.isEmpty()) return 1.0
            if (pos <= alphaStops.first().first) return alphaStops.first().second
            for (k in 1 until alphaStops.size) {
                val (p1, a1) = alphaStops[k]; val (p0, a0) = alphaStops[k - 1]
                if (pos <= p1) return if (p1 > p0) a0 + (a1 - a0) * (pos - p0) / (p1 - p0) else a1
            }
            return alphaStops.last().second
        }
        val stops = List(count) { k -> val pos = vals[k * 4]; GradientStop(pos, Rgba(vals[k * 4 + 1], vals[k * 4 + 2], vals[k * 4 + 3], alphaAt(pos))) }
        val s = PropEval.values(raw.obj("s"), frame) ?: return null
        val e = PropEval.values(raw.obj("e"), frame) ?: return null
        if (s.size < 2 || e.size < 2) return null
        return Paint(null, Gradient((raw.int("t") ?: 1) == 2, s[0], s[1], e[0], e[1], stops), opacity)
    }

    private fun strokeOf(raw: JsonObj, frame: Double): StrokeStyle {
        val cap = when (raw.int("lc")) { 2 -> Cap.ROUND; 3 -> Cap.SQUARE; else -> Cap.BUTT }
        val join = when (raw.int("lj")) { 2 -> Join.ROUND; 3 -> Join.BEVEL; else -> Join.MITER }
        var dashes: DoubleArray? = null
        var phase = 0.0
        val d = raw.arr("d")
        if (d != null) {
            val lens = ArrayList<Double>()
            for (e in d.items.filterIsInstance<JsonObj>()) {
                val v = PropEval.number(e.obj("v"), frame, 0.0)
                when (e.string("n")) { "o" -> phase = v; "d", "g" -> lens += v }
            }
            if (lens.isNotEmpty()) {
                if (lens.size % 2 == 1) lens += lens.toList()
                if (lens.sum() > 0.0) dashes = lens.toDoubleArray()
            }
        }
        return StrokeStyle(PropEval.number(raw.obj("w"), frame, 1.0), cap, join, raw.double("ml") ?: 4.0, dashes, phase)
    }

    private companion object { const val MAX_PRECOMP_DEPTH = 8 }
}
