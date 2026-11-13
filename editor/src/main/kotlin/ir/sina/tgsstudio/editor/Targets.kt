package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.DocIndex
import ir.sina.tgsstudio.document.Layer
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.LayerType
import ir.sina.tgsstudio.document.ShapeKind
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.PropEval
import ir.sina.tgsstudio.renderer.Rgba
import ir.sina.tgsstudio.renderer.Transforms

/** Something the user can select: a whole layer or one shape item (usually a group) inside it. */
sealed interface Target {
    val layer: LayerRef
}
data class LayerTarget(override val layer: LayerRef) : Target
data class ShapeTarget(val ref: ShapeRef) : Target { override val layer: LayerRef get() = ref.layer }

object Spaces {
    private fun layersOf(idx: DocIndex, scope: String?): List<Layer> =
        idx.layerArray(scope)?.items.orEmpty().filterIsInstance<JsonObj>().map(::Layer)

    /** Local frame of a composition: the main comp uses document frames, a precomp offsets by the referencing layer. */
    fun localFrame(idx: DocIndex, scope: String?, docFrame: Double): Double {
        if (scope == null) return docFrame
        val l = referencing(idx, scope)?.let(idx::layer) ?: return docFrame
        val sr = l.raw.double("sr")?.takeIf { it != 0.0 } ?: 1.0
        return (docFrame - l.startTime) / sr
    }

    private fun referencing(idx: DocIndex, scope: String): LayerRef? =
        idx.allLayerRefs().firstOrNull { r -> idx.layer(r)?.let { it.type == LayerType.PRECOMP && it.refId == scope } == true }

    private fun outer(idx: DocIndex, scope: String?, docFrame: Double, depth: Int = 0): Affine {
        if (scope == null || depth > 8) return Affine.IDENTITY
        val ref = referencing(idx, scope) ?: return Affine.IDENTITY
        val l = idx.layer(ref) ?: return Affine.IDENTITY
        return outer(idx, ref.scope, docFrame, depth + 1) * Transforms.layerFull(layersOf(idx, ref.scope), l, localFrame(idx, ref.scope, docFrame))
    }

    /** Matrix taking the coordinate space a target's own transform lives in (its parent space) to document space. */
    fun parentToDoc(idx: DocIndex, t: Target, docFrame: Double): Affine {
        val scope = t.layer.scope
        val lf = localFrame(idx, scope, docFrame)
        val layer = idx.layer(t.layer) ?: return Affine.IDENTITY
        val layers = layersOf(idx, scope)
        val base = outer(idx, scope, docFrame)
        return when (t) {
            is LayerTarget -> base * Transforms.layerParents(layers, layer, lf)
            is ShapeTarget -> {
                var m = base * Transforms.layerFull(layers, layer, lf)
                var arr: JsonArr? = layer.raw.arr("shapes")
                for (i in t.ref.path.dropLast(1)) {
                    val g = arr?.items?.getOrNull(i) as? JsonObj ?: break
                    val tr = g.arr("it")?.items?.firstOrNull { it is JsonObj && it.string("ty") == "tr" } as? JsonObj
                    if (tr != null) m *= Transforms.of(tr, lf)
                    arr = g.arr("it")
                }
                m
            }
        }
    }

    /** Matrix of the target's own space (parent space plus its own transform) to document space. */
    fun selfToDoc(idx: DocIndex, t: Target, docFrame: Double): Affine {
        val lf = localFrame(idx, t.layer.scope, docFrame)
        val block = Targets.transformBlock(idx, t) ?: return parentToDoc(idx, t, docFrame)
        return parentToDoc(idx, t, docFrame) * Transforms.of(block, lf)
    }
}

object Targets {
    fun raw(idx: DocIndex, t: Target): JsonObj? = when (t) {
        is LayerTarget -> idx.layerRaw(t.layer)
        is ShapeTarget -> idx.shapeRaw(t.ref)
    }

    fun name(idx: DocIndex, t: Target): String = raw(idx, t)?.string("nm") ?: ""

    /** The block holding a target's transform: a layer's `ks`, or a group's `tr` item (created on demand). */
    fun transformBlock(idx: DocIndex, t: Target, create: Boolean = false): JsonObj? {
        val r = raw(idx, t) ?: return null
        return when (t) {
            is LayerTarget -> r.obj("ks") ?: if (create) Creator.defaultKs().also { r["ks"] = it } else null
            is ShapeTarget -> {
                if (r.string("ty") != "gr") return null
                val items = r.arr("it") ?: return null
                (items.items.firstOrNull { it is JsonObj && it.string("ty") == "tr" } as? JsonObj)
                    ?: if (create) Creator.groupTransform().also { items.items.add(it) } else null
            }
        }
    }

    fun isGroup(idx: DocIndex, t: Target): Boolean = t is ShapeTarget && raw(idx, t)?.string("ty") == "gr"

    /** Depth-first list of style items (fill/stroke/gradients) under a target. */
    fun styleItems(idx: DocIndex, t: Target): List<JsonObj> {
        val out = ArrayList<JsonObj>()
        fun walk(items: List<JsonValue>?) {
            for (v in items.orEmpty()) {
                val o = v as? JsonObj ?: continue
                when (ShapeKind.fromCode(o.string("ty"))) {
                    ShapeKind.FILL, ShapeKind.STROKE, ShapeKind.GRADIENT_FILL, ShapeKind.GRADIENT_STROKE -> out += o
                    ShapeKind.GROUP -> walk(o.arr("it")?.items)
                    else -> Unit
                }
            }
        }
        when (val r = raw(idx, t)) {
            null -> Unit
            else -> if (t is LayerTarget) walk(r.arr("shapes")?.items) else walk(listOf(r))
        }
        return out
    }
}

data class TransformValues(
    val x: Double, val y: Double, val scaleX: Double, val scaleY: Double,
    val rotation: Double, val anchorX: Double, val anchorY: Double, val opacity: Double,
)

object TransformOps {
    fun read(idx: DocIndex, t: Target, frame: Double): TransformValues? {
        val b = Targets.transformBlock(idx, t) ?: return null
        val pos = Transforms.position(b.obj("p"), frame)
        val s = PropEval.values(b.obj("s"), frame); val a = PropEval.values(b.obj("a"), frame)
        return TransformValues(
            pos.first, pos.second, s?.getOrNull(0) ?: 100.0, s?.getOrNull(1) ?: 100.0,
            PropEval.number(b.obj("r"), frame, 0.0), a?.getOrNull(0) ?: 0.0, a?.getOrNull(1) ?: 0.0,
            PropEval.number(b.obj("o"), frame, 100.0),
        )
    }

    private fun prop(idx: DocIndex, t: Target, key: String, default: DoubleArray): JsonObj? {
        val b = Targets.transformBlock(idx, t, create = true) ?: return null
        return b.obj(key) ?: staticProp(*default).also { b[key] = it }
    }

    fun setPosition(idx: DocIndex, t: Target, frame: Double, x: Double, y: Double) {
        val b = Targets.transformBlock(idx, t, create = true) ?: return
        val p = b.obj("p")
        if (p != null && p.has("x") && p.has("y")) { // split dimensions
            p.obj("x")?.let { PropWrite.setValues(it, frame, doubleArrayOf(x)) }; p.obj("y")?.let { PropWrite.setValues(it, frame, doubleArrayOf(y)) }
        } else prop(idx, t, "p", doubleArrayOf(0.0, 0.0))?.let { PropWrite.setValues(it, frame, doubleArrayOf(x, y)) }
    }
    fun setScale(idx: DocIndex, t: Target, frame: Double, sx: Double, sy: Double) =
        prop(idx, t, "s", doubleArrayOf(100.0, 100.0))?.let { PropWrite.setValues(it, frame, doubleArrayOf(sx, sy)) }
    fun setRotation(idx: DocIndex, t: Target, frame: Double, deg: Double) =
        prop(idx, t, "r", doubleArrayOf(0.0))?.let { PropWrite.setValues(it, frame, doubleArrayOf(deg)) }
    fun setAnchor(idx: DocIndex, t: Target, frame: Double, x: Double, y: Double) =
        prop(idx, t, "a", doubleArrayOf(0.0, 0.0))?.let { PropWrite.setValues(it, frame, doubleArrayOf(x, y)) }
    fun setOpacity(idx: DocIndex, t: Target, frame: Double, percent: Double) =
        prop(idx, t, "o", doubleArrayOf(100.0))?.let { PropWrite.setValues(it, frame, doubleArrayOf(percent.coerceIn(0.0, 100.0))) }

    // ---- gesture maths: operate in document space, written in the target's parent space -------------------

    fun translateDoc(idx: DocIndex, t: Target, docFrame: Double, dx: Double, dy: Double) {
        val inv = Spaces.parentToDoc(idx, t, docFrame).inverse() ?: return
        val lf = Spaces.localFrame(idx, t.layer.scope, docFrame)
        val v = read(idx, t, lf) ?: return
        setPosition(idx, t, lf, v.x + inv.mapVectorX(dx, dy), v.y + inv.mapVectorY(dx, dy))
    }

    /** Uniform scale by [factor] about document point (qx, qy). */
    fun scaleAboutDoc(idx: DocIndex, t: Target, docFrame: Double, qx: Double, qy: Double, factor: Double) {
        if (factor <= 0.0 || !factor.isFinite()) return
        val inv = Spaces.parentToDoc(idx, t, docFrame).inverse() ?: return
        val lf = Spaces.localFrame(idx, t.layer.scope, docFrame)
        val v = read(idx, t, lf) ?: return
        val px = inv.mapX(qx, qy); val py = inv.mapY(qx, qy)
        setPosition(idx, t, lf, px + factor * (v.x - px), py + factor * (v.y - py))
        setScale(idx, t, lf, v.scaleX * factor, v.scaleY * factor)
    }

    /** Rotate by [deg] (clockwise positive) about document point (qx, qy). */
    fun rotateAboutDoc(idx: DocIndex, t: Target, docFrame: Double, qx: Double, qy: Double, deg: Double) {
        val inv = Spaces.parentToDoc(idx, t, docFrame).inverse() ?: return
        val lf = Spaces.localFrame(idx, t.layer.scope, docFrame)
        val v = read(idx, t, lf) ?: return
        val px = inv.mapX(qx, qy); val py = inv.mapY(qx, qy)
        val r = Affine.rotate(deg)
        setPosition(idx, t, lf, px + r.mapVectorX(v.x - px, v.y - py), py + r.mapVectorY(v.x - px, v.y - py))
        setRotation(idx, t, lf, v.rotation + deg)
    }
}

object StyleOps {
    fun firstOf(idx: DocIndex, t: Target, vararg kinds: ShapeKind): JsonObj? =
        Targets.styleItems(idx, t).firstOrNull { ShapeKind.fromCode(it.string("ty")) in kinds }

    fun fillColor(idx: DocIndex, t: Target, frame: Double): Rgba? = colorOf(firstOf(idx, t, ShapeKind.FILL), frame)
    fun strokeColor(idx: DocIndex, t: Target, frame: Double): Rgba? = colorOf(firstOf(idx, t, ShapeKind.STROKE), frame)
    fun strokeWidth(idx: DocIndex, t: Target, frame: Double): Double? = firstOf(idx, t, ShapeKind.STROKE)?.obj("w")?.let { PropEval.number(it, frame, 1.0) }

    private fun colorOf(item: JsonObj?, frame: Double): Rgba? {
        val c = PropEval.values(item?.obj("c"), frame) ?: return null
        if (c.size < 3) return null
        return Rgba(c[0], c[1], c[2], c.getOrElse(3) { 1.0 })
    }

    fun setColor(item: JsonObj, frame: Double, c: Rgba) {
        val p = item.obj("c") ?: staticProp(0.0, 0.0, 0.0, 1.0).also { item["c"] = it }
        PropWrite.setValues(p, frame, doubleArrayOf(c.r, c.g, c.b, c.a))
    }
    fun setWidth(item: JsonObj, frame: Double, w: Double) {
        val p = item.obj("w") ?: staticProp(1.0).also { item["w"] = it }
        PropWrite.setValues(p, frame, doubleArrayOf(w.coerceAtLeast(0.0)))
    }
    fun setOpacity(item: JsonObj, frame: Double, percent: Double) {
        val p = item.obj("o") ?: staticProp(100.0).also { item["o"] = it }
        PropWrite.setValues(p, frame, doubleArrayOf(percent.coerceIn(0.0, 100.0)))
    }

    /** Adds a solid fill to a group (before its transform) if it has none. Returns the fill item. */
    fun ensureFill(group: JsonObj, color: Rgba = Rgba(0.12, 0.48, 1.0, 1.0)): JsonObj? =
        ensure(group, "fl") { Creator.fill(color) }
    fun ensureStroke(group: JsonObj, color: Rgba = Rgba(1.0, 1.0, 1.0, 1.0), width: Double = 6.0): JsonObj? =
        ensure(group, "st") { Creator.stroke(color, width) }

    private fun ensure(group: JsonObj, ty: String, make: () -> JsonObj): JsonObj? {
        val items = group.arr("it") ?: return null
        (items.items.firstOrNull { it is JsonObj && it.string("ty") == ty } as? JsonObj)?.let { return it }
        val trAt = items.items.indexOfFirst { it is JsonObj && it.string("ty") == "tr" }
        val item = make()
        items.items.add(if (trAt >= 0) trAt else items.items.size, item)
        return item
    }

    fun remove(group: JsonObj, ty: String) {
        group.arr("it")?.items?.removeAll { it is JsonObj && it.string("ty") == ty }
    }
}
