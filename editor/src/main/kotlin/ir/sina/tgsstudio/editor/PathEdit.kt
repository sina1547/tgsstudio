package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.PropEval
import ir.sina.tgsstudio.renderer.Shapes

/** Reads and writes the path of a `sh` shape item, keyframe-aware. */
object PathEdit {
    fun isPath(shape: JsonObj) = shape.string("ty") == "sh"

    fun read(shape: JsonObj, frame: Double): Bezier? =
        if (isPath(shape)) PropEval.path(shape.obj("ks"), frame) else null

    /** Writes the path at [frame] (updates or adds a keyframe when the path is animated). */
    fun write(shape: JsonObj, frame: Double, b: Bezier) {
        val ks = shape.obj("ks") ?: staticPathProp(b).also { shape["ks"] = it }
        PropWrite.setPath(ks, frame, b)
    }

    /** Applies a vertex-count-changing edit to every keyframe so animated paths stay interpolatable. */
    fun structural(shape: JsonObj, frame: Double, edit: (Bezier) -> Bezier?): Boolean {
        val ks = shape.obj("ks") ?: return false
        val kfs = PropWrite.keyframesOf(ks)
        if (kfs == null) {
            val b = PropEval.path(ks, frame) ?: return false
            val nb = edit(b) ?: return false
            ks["k"] = bezierJson(nb); return true
        }
        // validate on every keyframe first so a failure leaves the document untouched
        val updated = ArrayList<Pair<JsonObj, Bezier>>()
        for (kf in kfs) {
            val s = ((kf["s"] as? JsonArr)?.items?.firstOrNull() as? JsonObj) ?: return false
            val b = PropEval.path(JsonObj().also { it["k"] = s }, 0.0) ?: return false
            updated += kf to (edit(b) ?: return false)
        }
        for ((kf, nb) in updated) {
            kf["s"] = JsonArr(mutableListOf<JsonValue>(bezierJson(nb)))
            kf.remove("e")
        }
        PropWrite.normalize(kfs)
        return true
    }

    /**
     * Turns a rectangle/ellipse/star item into an editable `sh` path (replacing it in place). An animated
     * source is baked at [frame]. Returns the new item or null when the item is not convertible.
     */
    fun convertToPath(items: JsonArr, index: Int, frame: Double): JsonObj? {
        val raw = items.items.getOrNull(index) as? JsonObj ?: return null
        if (isPath(raw)) return raw
        val p = PropEval.values(raw.obj("p"), frame)
        val s = PropEval.values(raw.obj("s"), frame)
        val cx = p?.getOrNull(0) ?: 0.0; val cy = p?.getOrNull(1) ?: 0.0
        val contour = when (raw.string("ty")) {
            "rc" -> s?.let { Shapes.rect(cx, cy, it[0], it[1], PropEval.number(raw.obj("r"), frame, 0.0)) }
            "el" -> s?.let { Shapes.ellipse(cx, cy, it[0], it[1]) }
            "sr" -> Shapes.star(
                cx, cy, PropEval.number(raw.obj("pt"), frame, 5.0).toInt(), PropEval.number(raw.obj("or"), frame, 50.0),
                if (raw.int("sy") == 2) null else PropEval.number(raw.obj("ir"), frame, 25.0), PropEval.number(raw.obj("r"), frame, 0.0),
            )
            else -> null
        } ?: return null
        val b = PathOps.fromContour(contour) ?: return null
        val path = Creator.pathItem(b, raw.string("nm") ?: "Path 1")
        items.items[index] = path
        return path
    }
}
