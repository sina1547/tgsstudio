package ir.sina.tgsstudio.document

import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj

/**
 * Address of a layer: [scope] is null for the main composition or a precomp asset id,
 * [index] is the position in that composition's `layers` array (0 = topmost).
 */
data class LayerRef(val scope: String?, val index: Int)

/** Address of a shape item inside a layer: indices through nested group `it` arrays. */
data class ShapeRef(val layer: LayerRef, val path: List<Int>) {
    val parent: ShapeRef? get() = if (path.size <= 1) null else ShapeRef(layer, path.dropLast(1))
}

/** Resolves refs against a document. Refs go stale after structural edits, so callers re-derive them. */
class DocIndex(val doc: LottieDocument) {
    fun layerArray(scope: String?): JsonArr? =
        if (scope == null) doc.root.arr("layers")
        else doc.root.arr("assets")?.items?.filterIsInstance<JsonObj>()?.firstOrNull { it.string("id") == scope }?.arr("layers")

    fun layerRaw(ref: LayerRef): JsonObj? = layerArray(ref.scope)?.items?.getOrNull(ref.index) as? JsonObj
    fun layer(ref: LayerRef): Layer? = layerRaw(ref)?.let(::Layer)

    fun shapeArray(ref: ShapeRef): JsonArr? {
        var arr = layerRaw(ref.layer)?.arr("shapes") ?: return null
        for (i in ref.path.dropLast(1)) arr = ((arr.items.getOrNull(i) as? JsonObj)?.arr("it")) ?: return null
        return arr
    }

    fun shapeRaw(ref: ShapeRef): JsonObj? = ref.path.lastOrNull()?.let { shapeArray(ref)?.items?.getOrNull(it) as? JsonObj }
    fun shape(ref: ShapeRef): ShapeItem? = shapeRaw(ref)?.let(::ShapeItem)

    /** All layer refs of the main comp and every precomp, in document order. */
    fun allLayerRefs(): List<LayerRef> {
        val out = ArrayList<LayerRef>()
        doc.layers.indices.forEach { out += LayerRef(null, it) }
        doc.precomps.forEach { p -> p.layers.indices.forEach { out += LayerRef(p.id, it) } }
        return out
    }
}
