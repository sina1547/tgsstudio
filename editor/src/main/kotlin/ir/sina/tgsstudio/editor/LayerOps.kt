package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.DocIndex
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.document.json.deepCopy

/** Result of an edit that can be refused, with a reason the UI can show. */
sealed interface OpResult {
    data class Ok(val ref: Any? = null) : OpResult
    data class Refused(val reason: Reason) : OpResult
    enum class Reason { NOT_FOUND, GROUP_HAS_TRANSFORM, WOULD_NEST_INTO_SELF, LAST_ITEM, INVALID }
}

object LayerOps {
    fun rename(idx: DocIndex, ref: LayerRef, name: String) { idx.layerRaw(ref)?.putString("nm", name) }
    fun setHidden(idx: DocIndex, ref: LayerRef, hidden: Boolean) {
        val l = idx.layerRaw(ref) ?: return
        if (hidden) l.putBool("hd", true) else l.remove("hd")
    }

    fun delete(idx: DocIndex, ref: LayerRef): OpResult {
        val arr = idx.layerArray(ref.scope) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val l = arr.items.getOrNull(ref.index) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val ind = l.int("ind")
        // a matte source takes its target's matte with it
        if (l.int("td") == 1) (arr.items.getOrNull(ref.index + 1) as? JsonObj)?.let { it.remove("tt") }
        arr.items.removeAt(ref.index)
        if (ind != null) arr.items.filterIsInstance<JsonObj>().forEach { if (it.int("parent") == ind) it.remove("parent") }
        return OpResult.Ok()
    }

    /** Duplicates a layer just above itself and returns the copy's ref. */
    fun duplicate(idx: DocIndex, ref: LayerRef): OpResult {
        val arr = idx.layerArray(ref.scope) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val src = arr.items.getOrNull(ref.index) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val copy = src.deepCopy() as JsonObj
        copy.putInt("ind", Creator.nextIndex(arr))
        copy.remove("td"); copy.remove("tt")
        copy.putString("nm", (src.string("nm") ?: "Layer") + " copy")
        arr.items.add(ref.index, copy)
        return OpResult.Ok(LayerRef(ref.scope, ref.index))
    }

    /** Moves a layer to [newIndex] in its composition (0 = top). Returns its new ref. */
    fun reorder(idx: DocIndex, ref: LayerRef, newIndex: Int): OpResult {
        val arr = idx.layerArray(ref.scope) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        if (ref.index !in arr.items.indices) return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val to = newIndex.coerceIn(0, arr.items.size - 1)
        val l = arr.items.removeAt(ref.index)
        arr.items.add(to, l)
        return OpResult.Ok(LayerRef(ref.scope, to))
    }

    /** Sets (or clears with null) a layer's parent layer. Refuses cycles. */
    fun setParent(idx: DocIndex, ref: LayerRef, parent: LayerRef?): OpResult {
        val arr = idx.layerArray(ref.scope) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val l = arr.items.getOrNull(ref.index) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        if (parent == null) { l.remove("parent"); return OpResult.Ok() }
        if (parent.scope != ref.scope) return OpResult.Refused(OpResult.Reason.INVALID)
        val p = arr.items.getOrNull(parent.index) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val pInd = p.int("ind") ?: return OpResult.Refused(OpResult.Reason.INVALID)
        val byInd = arr.items.filterIsInstance<JsonObj>().associateBy { it.int("ind") }
        var cur: JsonObj? = p
        var hops = 0
        while (cur != null && hops++ < 100) {
            if (cur === l) return OpResult.Refused(OpResult.Reason.WOULD_NEST_INTO_SELF)
            cur = cur.int("parent")?.let(byInd::get)
        }
        l.putInt("parent", pInd)
        return OpResult.Ok()
    }
}

object ShapeOps {
    private fun parentArray(idx: DocIndex, ref: ShapeRef): JsonArr? = idx.shapeArray(ref)

    fun rename(idx: DocIndex, ref: ShapeRef, name: String) { idx.shapeRaw(ref)?.putString("nm", name) }
    fun setHidden(idx: DocIndex, ref: ShapeRef, hidden: Boolean) {
        val s = idx.shapeRaw(ref) ?: return
        if (hidden) s.putBool("hd", true) else s.remove("hd")
    }

    fun delete(idx: DocIndex, ref: ShapeRef): OpResult {
        val arr = parentArray(idx, ref) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val i = ref.path.last()
        if (i !in arr.items.indices) return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        arr.items.removeAt(i)
        return OpResult.Ok()
    }

    fun duplicate(idx: DocIndex, ref: ShapeRef): OpResult {
        val arr = parentArray(idx, ref) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val i = ref.path.last()
        val src = arr.items.getOrNull(i) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        arr.items.add(i, src.deepCopy())
        return OpResult.Ok(ref) // the copy sits at the same index, above the original
    }

    /** Moves an item up (-1) or down (+1) among its siblings; a group's transform stays last. */
    fun move(idx: DocIndex, ref: ShapeRef, delta: Int): OpResult {
        val arr = parentArray(idx, ref) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val i = ref.path.last()
        val limit = arr.items.indexOfLast { !(it is JsonObj && it.string("ty") == "tr") }
        val j = (i + delta).coerceIn(0, maxOf(limit, 0))
        if (i !in arr.items.indices) return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val item = arr.items.removeAt(i)
        arr.items.add(j, item)
        return OpResult.Ok(ShapeRef(ref.layer, ref.path.dropLast(1) + j))
    }

    /** Wraps sibling items (same parent array) into a new group placed where the first one was. */
    fun group(idx: DocIndex, refs: List<ShapeRef>): OpResult {
        if (refs.isEmpty()) return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val parent = refs.first().path.dropLast(1)
        if (refs.any { it.path.dropLast(1) != parent || it.layer != refs.first().layer }) return OpResult.Refused(OpResult.Reason.INVALID)
        val arr = parentArray(idx, refs.first()) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val indices = refs.map { it.path.last() }.sorted()
        val items = indices.map { arr.items.getOrNull(it) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND) }
        if (items.any { it.string("ty") == "tr" }) return OpResult.Refused(OpResult.Reason.INVALID)
        indices.asReversed().forEach { arr.items.removeAt(it) }
        val g = Creator.group("Group", *items.toTypedArray())
        val at = indices.first().coerceAtMost(arr.items.size)
        arr.items.add(at, g)
        return OpResult.Ok(ShapeRef(refs.first().layer, parent + at))
    }

    /** Replaces a group by its children. Refused unless the group's transform is the identity. */
    fun ungroup(idx: DocIndex, ref: ShapeRef): OpResult {
        val arr = parentArray(idx, ref) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val i = ref.path.last()
        val g = arr.items.getOrNull(i) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        if (g.string("ty") != "gr") return OpResult.Refused(OpResult.Reason.INVALID)
        val children = g.arr("it")?.items.orEmpty()
        val tr = children.firstOrNull { it is JsonObj && it.string("ty") == "tr" } as? JsonObj
        if (tr != null && !isIdentity(tr)) return OpResult.Refused(OpResult.Reason.GROUP_HAS_TRANSFORM)
        val kept = children.filterNot { it is JsonObj && it.string("ty") == "tr" }
        arr.items.removeAt(i)
        arr.items.addAll(i, kept)
        return OpResult.Ok(ShapeRef(ref.layer, ref.path.dropLast(1) + i))
    }

    private fun isIdentity(tr: JsonObj): Boolean {
        fun staticVals(key: String, vararg expect: Double): Boolean {
            val p = tr.obj(key) ?: return true
            if (PropWrite.isAnimated(p)) return false
            val v = ir.sina.tgsstudio.renderer.PropEval.values(p, 0.0) ?: return true
            return expect.indices.all { (v.getOrNull(it) ?: expect[it]) == expect[it] }
        }
        return staticVals("p", 0.0, 0.0) && staticVals("a", 0.0, 0.0) && staticVals("s", 100.0, 100.0) &&
            staticVals("r", 0.0) && staticVals("o", 100.0) && staticVals("sk", 0.0)
    }

    /** Moves [ref] into group [target] (before the group's transform). Refuses moving a group into itself. */
    fun moveInto(idx: DocIndex, ref: ShapeRef, target: ShapeRef): OpResult {
        if (ref.layer != target.layer) return OpResult.Refused(OpResult.Reason.INVALID)
        if (target.path.size >= ref.path.size && target.path.take(ref.path.size) == ref.path) return OpResult.Refused(OpResult.Reason.WOULD_NEST_INTO_SELF)
        val dest = idx.shapeRaw(target) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        if (dest.string("ty") != "gr") return OpResult.Refused(OpResult.Reason.INVALID)
        val srcArr = parentArray(idx, ref) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val srcIndex = ref.path.last()
        val item = srcArr.items.getOrNull(srcIndex) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        if (item.string("ty") == "tr") return OpResult.Refused(OpResult.Reason.INVALID)
        srcArr.items.removeAt(srcIndex)
        val destItems = dest.arr("it") ?: JsonArr().also { dest["it"] = it }
        val trAt = destItems.items.indexOfFirst { it is JsonObj && it.string("ty") == "tr" }
        val at = if (trAt >= 0) trAt else destItems.items.size
        destItems.items.add(at, item)
        // refs after the removal shift when the target sits after the source in the same array
        val adjusted = if (target.path.size > ref.path.size - 1 && target.path.take(ref.path.size - 1) == ref.path.dropLast(1) && target.path[ref.path.size - 1] > srcIndex)
            target.path.toMutableList().also { it[ref.path.size - 1] = it[ref.path.size - 1] - 1 } else target.path
        return OpResult.Ok(ShapeRef(ref.layer, adjusted + at))
    }

    /** Lifts an item out of its group to sit just above that group. */
    fun moveOut(idx: DocIndex, ref: ShapeRef): OpResult {
        val parent = ref.parent ?: return OpResult.Refused(OpResult.Reason.INVALID)
        val srcArr = parentArray(idx, ref) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        val item = srcArr.items.getOrNull(ref.path.last()) as? JsonObj ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        if (item.string("ty") == "tr") return OpResult.Refused(OpResult.Reason.INVALID)
        val destArr = parentArray(idx, parent) ?: return OpResult.Refused(OpResult.Reason.NOT_FOUND)
        srcArr.items.removeAt(ref.path.last())
        val at = parent.path.last()
        destArr.items.add(at, item)
        return OpResult.Ok(ShapeRef(ref.layer, parent.path.dropLast(1) + at))
    }
}
