package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.DocIndex
import ir.sina.tgsstudio.document.Layer
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.PropEval
import ir.sina.tgsstudio.renderer.Transforms
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * What a parenting operation did. [keptPosition] layers did not move on screen; [movedLayers] had to keep their
 * local values instead (animated transform, skew, or a parent that squashes them non-uniformly), so they jump
 * to the new parent's space like in After Effects.
 */
data class ParentResult(val linked: Int, val keptPosition: Int, val movedLayers: List<LayerRef>, val refused: OpResult.Reason? = null)

/**
 * Real Lottie layer parenting: the child's `parent` field points at the parent's `ind`, so moving, rotating or
 * scaling the parent moves the child in every renderer, Telegram's included (opacity is not inherited, as in Lottie).
 */
object ParentOps {
    private fun layers(idx: DocIndex, scope: String?): List<Layer> =
        idx.layerArray(scope)?.items.orEmpty().filterIsInstance<JsonObj>().map(::Layer)

    fun parentOf(idx: DocIndex, ref: LayerRef): LayerRef? {
        val ls = layers(idx, ref.scope)
        val p = ls.getOrNull(ref.index)?.parentIndex ?: return null
        val at = ls.indexOfFirst { it.index == p }
        return if (at >= 0) LayerRef(ref.scope, at) else null
    }

    fun childrenOf(idx: DocIndex, ref: LayerRef): List<LayerRef> {
        val ls = layers(idx, ref.scope)
        val ind = ls.getOrNull(ref.index)?.index ?: return emptyList()
        return ls.indices.filter { ls[it].parentIndex == ind && it != ref.index }.map { LayerRef(ref.scope, it) }
    }

    /** All layers below [ref] in the hierarchy (children, grandchildren, ...). */
    fun descendants(idx: DocIndex, ref: LayerRef): Set<LayerRef> {
        val out = LinkedHashSet<LayerRef>()
        val stack = ArrayDeque(childrenOf(idx, ref))
        while (stack.isNotEmpty()) { val c = stack.removeLast(); if (out.add(c)) stack.addAll(childrenOf(idx, c)) }
        return out
    }

    /** Layers [children] may legally be parented to: same composition, not themselves, not their own descendants. */
    fun candidates(idx: DocIndex, children: List<LayerRef>): List<LayerRef> {
        val scope = (children.firstOrNull() ?: return emptyList()).scope   // null = main composition, so no elvis on scope itself
        val banned = HashSet<LayerRef>(children)
        children.forEach { banned += descendants(idx, it) }
        return layers(idx, scope).indices.map { LayerRef(scope, it) }.filter { it !in banned }
    }

    /** Why linking [children] to [parent] is not allowed, or null when it is. */
    fun validate(idx: DocIndex, children: List<LayerRef>, parent: LayerRef?): OpResult.Reason? {
        if (children.isEmpty() || children.any { idx.layerRaw(it) == null }) return OpResult.Reason.NOT_FOUND
        if (children.any { it.scope != children.first().scope } || (parent != null && parent.scope != children.first().scope)) return OpResult.Reason.INVALID
        if (parent != null) {
            if (idx.layerRaw(parent)?.int("ind") == null) return OpResult.Reason.INVALID
            if (parent in children || children.any { parent in descendants(idx, it) }) return OpResult.Reason.WOULD_NEST_INTO_SELF
        }
        return null
    }

    fun setParent(idx: DocIndex, children: List<LayerRef>, parent: LayerRef?, docFrame: Double, keepPosition: Boolean = true): ParentResult {
        validate(idx, children, parent)?.let { return ParentResult(0, 0, emptyList(), it) }
        val scope = children.first().scope
        val lf = Spaces.localFrame(idx, scope, docFrame)
        var linked = 0; var kept = 0; val moved = ArrayList<LayerRef>()
        for (child in children) {
            val ls = layers(idx, scope)
            val childLayer = ls.getOrNull(child.index) ?: continue
            val worldBefore = Transforms.layerFull(ls, childLayer, lf)
            val r = LayerOps.setParent(idx, child, parent)
            if (r !is OpResult.Ok) return ParentResult(linked, kept, moved, (r as OpResult.Refused).reason)
            linked++
            if (!keepPosition) continue
            val fresh = layers(idx, scope)
            val newParentFull = parent?.let { p -> fresh.getOrNull(p.index)?.let { Transforms.layerFull(fresh, it, lf) } } ?: Affine.IDENTITY
            val inv = newParentFull.inverse()
            if (inv == null || !preserve(idx, child, inv * worldBefore, lf)) moved += child else kept++
        }
        return ParentResult(linked, kept, moved)
    }

    /** Expresses [local] as the layer's own position/rotation/scale; false (nothing written) when that isn't exactly possible. */
    private fun preserve(idx: DocIndex, ref: LayerRef, local: Affine, lf: Double): Boolean {
        val ks = idx.layerRaw(ref)?.obj("ks") ?: return false
        fun isStatic(key: String) = ks.obj(key)?.let { !PropWrite.isAnimated(it) && !(key == "p" && it.has("x")) } ?: true
        if (!(isStatic("p") && isStatic("r") && isStatic("s") && isStatic("a"))) return false
        if (PropEval.number(ks.obj("sk"), lf, 0.0) != 0.0) return false
        val sx = hypot(local.a, local.b)
        if (sx < 1e-9) return false
        val sy = (local.a * local.d - local.b * local.c) / sx
        if (abs(sy) < 1e-9) return false
        // the second column must be perpendicular to the first, otherwise the matrix contains a skew
        val expectedC = -local.b / sx * sy; val expectedD = local.a / sx * sy
        if (hypot(local.c - expectedC, local.d - expectedD) > 1e-3 * (abs(sy) + 1.0)) return false
        val rotation = Math.toDegrees(atan2(local.b, local.a))
        val anchor = PropEval.values(ks.obj("a"), lf)
        val ax = anchor?.getOrNull(0) ?: 0.0; val ay = anchor?.getOrNull(1) ?: 0.0
        val px = local.e + local.a * ax + local.c * ay
        val py = local.f + local.b * ax + local.d * ay
        val t = LayerTarget(ref)
        TransformOps.setPosition(idx, t, lf, px, py)
        TransformOps.setScale(idx, t, lf, sx * 100.0, sy * 100.0)
        TransformOps.setRotation(idx, t, lf, rotation)
        return true
    }

    /** Before a layer is deleted: its children move up to its own parent (or become top-level) without moving on screen. */
    fun releaseChildren(idx: DocIndex, ref: LayerRef, docFrame: Double): ParentResult {
        val kids = childrenOf(idx, ref)
        if (kids.isEmpty()) return ParentResult(0, 0, emptyList())
        return setParent(idx, kids, parentOf(idx, ref), docFrame, keepPosition = true)
    }

    /**
     * Adds an empty Null layer that rests exactly at the identity transform (position = anchor = the children's
     * centre) and makes it the parent of [children]. Returns the new null's ref and the children's shifted refs.
     */
    fun createNullParent(idx: DocIndex, children: List<LayerRef>, docFrame: Double, name: String = "Parent"): Triple<LayerRef, List<LayerRef>, ParentResult>? {
        val scope = (children.firstOrNull() ?: return null).scope
        val arr = idx.layerArray(scope) ?: return null
        val ls = layers(idx, scope)
        val lf = Spaces.localFrame(idx, scope, docFrame)
        var sx = 0.0; var sy = 0.0
        for (c in children) {
            val l = ls.getOrNull(c.index) ?: return null
            val parents = Transforms.layerParents(ls, l, lf)
            val p = Transforms.position(l.raw.obj("ks")?.obj("p"), lf)
            sx += parents.mapX(p.first, p.second); sy += parents.mapY(p.first, p.second)
        }
        val cx = sx / children.size; val cy = sy / children.size
        val at = children.minOf { it.index }
        val nullLayer = Creator.nullLayer(idx.doc, arr, name)
        nullLayer.obj("ks")!!.also { it["p"] = staticProp(cx, cy, 0.0); it["a"] = staticProp(cx, cy, 0.0) }
        arr.items.add(at, nullLayer)
        val shifted = children.map { if (it.index >= at) LayerRef(scope, it.index + 1) else it }
        val res = setParent(idx, shifted, LayerRef(scope, at), docFrame, keepPosition = true)
        return Triple(LayerRef(scope, at), shifted, res)
    }
}
