package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj

enum class TrimAnim {
    /** End 0% → 100%: the path draws itself from its start. */
    DRAW_ON,
    /** End 100% → 0%: the path withdraws back towards its start. */
    DRAW_OFF,
    /** Start 0% → 100%: the path is wiped away from its start. */
    ERASE_FROM_START,
}

/**
 * Standard Lottie Trim Paths (`tm`): Start, End and Offset are ordinary animatable properties, so they can be keyed,
 * eased and edited like any other. The path itself is never touched, so it stays editable with the node tools.
 */
object TrimOps {
    private val PAINTS = setOf("fl", "st", "gf", "gs")

    fun find(group: JsonObj): JsonObj? = group.arr("it")?.items?.firstOrNull { it is JsonObj && it.string("ty") == "tm" } as? JsonObj

    /** The trim of [group], created if missing: right below the group's paths and above its fill/stroke, where Lottie applies it. */
    fun ensure(group: JsonObj): JsonObj? {
        find(group)?.let { return it }
        val items = group.arr("it") ?: return null
        val firstPaint = items.items.indexOfFirst { it is JsonObj && it.string("ty") in PAINTS }
        val tr = items.items.indexOfFirst { it is JsonObj && it.string("ty") == "tr" }
        val at = if (firstPaint >= 0) firstPaint else if (tr >= 0) tr else items.items.size
        val trim = obj(
            "ty" to str("tm"), "s" to staticProp(0.0), "e" to staticProp(100.0), "o" to staticProp(0.0),
            "m" to int(1), "nm" to str("Trim Paths 1"),
        )
        items.items.add(at, trim)
        return trim
    }

    fun remove(group: JsonObj) { group.arr("it")?.items?.removeAll { it is JsonObj && it.string("ty") == "tm" } }

    /** Plays [mode] from [startFrame] over [frames] frames (local composition frames). Existing keyframes of the properties involved are replaced. */
    fun animate(trim: JsonObj, mode: TrimAnim, startFrame: Double, frames: Double, easing: Easing = Easing.EASE_IN_OUT) {
        require(frames >= 1.0) { "duration must be at least one frame" }
        val s = trim.obj("s") ?: staticProp(0.0).also { trim["s"] = it }
        val e = trim.obj("e") ?: staticProp(100.0).also { trim["e"] = it }
        val end = startFrame + frames
        when (mode) {
            TrimAnim.DRAW_ON -> { constant(s, 0.0); between(e, startFrame, 0.0, end, 100.0, easing) }
            TrimAnim.DRAW_OFF -> { constant(s, 0.0); between(e, startFrame, 100.0, end, 0.0, easing) }
            TrimAnim.ERASE_FROM_START -> { constant(e, 100.0); between(s, startFrame, 0.0, end, 100.0, easing) }
        }
    }

    private fun constant(prop: JsonObj, v: Double) { prop["k"] = jn(v); prop.putInt("a", 0) }

    private fun between(prop: JsonObj, f0: Double, v0: Double, f1: Double, v1: Double, easing: Easing) {
        constant(prop, v0)
        KeyframeOps.addAt(prop, f0)
        PropWrite.setValues(prop, f1, doubleArrayOf(v1))
        KeyframeOps.setEasing(prop, 0, easing)
    }

    /** Groups a trim should apply to for a layer: every top-level group. For a group (or a shape in one) just that group. */
    fun groupsOfLayer(layer: JsonObj): List<JsonObj> = layer.arr("shapes")?.items.orEmpty().filterIsInstance<JsonObj>().filter { it.string("ty") == "gr" }

}
