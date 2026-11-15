package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonNum
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.document.json.deepCopy
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.PropEval
import kotlin.math.abs

/**
 * Writes values into Lottie animatable properties. Static properties are set directly;
 * animated ones get a keyframe at the frame (updating it if one is already there), like auto-key in Alight Motion.
 */
object PropWrite {
    private const val SAME_FRAME = 0.5

    fun keyframesOf(prop: JsonObj): MutableList<JsonObj>? {
        val k = prop["k"] as? JsonArr ?: return null
        if (k.items.isEmpty() || !k.items.all { it is JsonObj && it.has("t") }) return null
        @Suppress("UNCHECKED_CAST") return k.items as MutableList<JsonObj> // JsonValue list holding JsonObj
    }

    fun isAnimated(prop: JsonObj) = keyframesOf(prop) != null

    fun setValues(prop: JsonObj, frame: Double, values: DoubleArray) {
        val kfs = keyframesOf(prop)
        if (kfs == null) {
            val old = prop["k"]
            val merged = mergeTail(oldNumbers(old), values)
            prop["k"] = if (old is JsonNum && merged.size == 1) jn(merged[0]) else jarr(merged)
            prop.putInt("a", 0)
            return
        }
        val existing = kfs.indexOfFirst { abs((it.double("t") ?: 0.0) - frame) < SAME_FRAME }
        if (existing >= 0) {
            kfs[existing]["s"] = jarr(mergeTail(oldNumbers(kfs[existing]["s"]), values))
        } else {
            insertKeyframe(kfs, frame, jarr(mergeTail(PropEval.values(prop, frame) ?: values, values)))
        }
        normalize(kfs)
    }

    fun setPath(prop: JsonObj, frame: Double, b: Bezier) {
        val kfs = keyframesOf(prop)
        if (kfs == null) { prop["k"] = bezierJson(b); prop.putInt("a", 0); return }
        val existing = kfs.indexOfFirst { abs((it.double("t") ?: 0.0) - frame) < SAME_FRAME }
        val value = JsonArr(mutableListOf<JsonValue>(bezierJson(b)))
        if (existing >= 0) kfs[existing]["s"] = value else insertKeyframe(kfs, frame, value)
        normalize(kfs)
    }

    /** Inserts a keyframe at [frame] with start value [s], inheriting easing from the segment it lands in. */
    fun insertKeyframe(kfs: MutableList<JsonObj>, frame: Double, s: JsonValue): JsonObj {
        val prev = kfs.lastOrNull { (it.double("t") ?: 0.0) < frame }
        val kf = JsonObj()
        kf["t"] = jn(frame)
        kf["s"] = s
        if (prev != null) {
            prev["i"]?.let { kf["i"] = it.deepCopy() }
            prev["o"]?.let { kf["o"] = it.deepCopy() }
            if (prev.has("to")) {
                // the motion path no longer fits the split segment: fall back to straight segments
                val zero = { (prev["to"] as? JsonArr)?.let { a -> jarr(DoubleArray(a.items.size)) } ?: jv(0.0, 0.0) }
                prev["to"] = zero(); prev["ti"] = zero(); kf["to"] = zero(); kf["ti"] = zero()
            }
            if (prev.bool("h") == true) kf.putInt("h", 1)
        }
        val pos = kfs.indexOfFirst { (it.double("t") ?: 0.0) > frame }.let { if (it < 0) kfs.size else it }
        kfs.add(pos, kf)
        return kf
    }

    /**
     * Enforces the keyframe structure rlottie (and so Telegram's players) require, found by testing:
     * every keyframe except the last must carry easing handles `i` and `o` (or be a hold keyframe, `h:1`),
     * and the last keyframe must not carry any. Keyframes without them are silently dropped by rlottie,
     * which leaves the property at 0 (objects jump to the origin, opacity becomes 0).
     * Also sorts by time and keeps old-format `e` end values consistent.
     */
    fun normalize(kfs: MutableList<JsonObj>) {
        kfs.sortBy { it.double("t") ?: 0.0 }
        for (i in kfs.indices) {
            val kf = kfs[i]
            if (i == kfs.size - 1) { kf.remove("e"); kf.remove("i"); kf.remove("o"); kf.remove("h"); continue }
            if (kf.bool("h") == true) { kf.remove("i"); kf.remove("o") } // hold: no handles needed
            else {
                if (!validEasing(kf["i"])) kf["i"] = LINEAR_IN.deepCopy()
                if (!validEasing(kf["o"])) kf["o"] = LINEAR_OUT.deepCopy()
                kf.remove("h")
            }
            if (kf.has("e")) kf["e"] = kfs[i + 1]["s"]!!.deepCopy()
        }
    }

    /** What [normalize] would fix, for validation. Index is the keyframe number inside its list. */
    class Violation(val index: Int, val kind: Kind) { enum class Kind { NO_EASING, LAST_HAS_EASING, UNSORTED } }

    fun violations(kfs: List<JsonObj>): List<Violation> {
        val out = ArrayList<Violation>()
        for (i in kfs.indices) {
            val kf = kfs[i]
            if (i > 0 && (kf.double("t") ?: 0.0) < (kfs[i - 1].double("t") ?: 0.0)) out += Violation(i, Violation.Kind.UNSORTED)
            if (i == kfs.size - 1) { if (kf.has("i") || kf.has("o")) out += Violation(i, Violation.Kind.LAST_HAS_EASING) }
            else if (kf.bool("h") != true && !(validEasing(kf["i"]) && validEasing(kf["o"]))) out += Violation(i, Violation.Kind.NO_EASING)
        }
        return out
    }

    /** Repairs every keyframe list in a document tree in place. Returns how many lists needed repair. */
    fun normalizeTree(v: JsonValue): Int {
        var repaired = 0
        when (v) {
            is JsonObj -> {
                val kfs = keyframesOf(v)
                if (kfs != null) { if (violations(kfs).isNotEmpty()) { normalize(kfs); repaired++ } }
                v.fields.values.forEach { repaired += normalizeTree(it) }
            }
            is JsonArr -> v.items.forEach { repaired += normalizeTree(it) }
            else -> Unit
        }
        return repaired
    }

    /** `{x: n | [n], y: n | [n]}` with finite numbers. */
    private fun validEasing(v: JsonValue?): Boolean {
        val o = v as? JsonObj ?: return false
        fun ok(c: JsonValue?) = when (c) {
            is JsonNum -> c.value.toDouble().isFinite()
            is JsonArr -> c.items.isNotEmpty() && c.items.all { it is JsonNum && it.value.toDouble().isFinite() }
            else -> false
        }
        return ok(o["x"]) && ok(o["y"])
    }

    /** Easing handles on the diagonal: mathematically a straight (linear) segment, and what After Effects writes for "Linear". */
    val LINEAR_OUT: JsonObj get() = obj("x" to jarr(doubleArrayOf(0.167)), "y" to jarr(doubleArrayOf(0.167)))
    val LINEAR_IN: JsonObj get() = obj("x" to jarr(doubleArrayOf(0.833)), "y" to jarr(doubleArrayOf(0.833)))

    private fun oldNumbers(v: JsonValue?): DoubleArray = when (v) {
        is JsonNum -> doubleArrayOf(v.value.toDouble())
        is JsonArr -> v.items.mapNotNull { (it as? JsonNum)?.value?.toDouble() }.toDoubleArray()
        else -> DoubleArray(0)
    }

    /** New values replace the leading dimensions; trailing dimensions (e.g. z of a 3-D position) are kept. */
    private fun mergeTail(old: DoubleArray, new: DoubleArray): DoubleArray =
        if (old.size <= new.size) new else DoubleArray(old.size) { if (it < new.size) new[it] else old[it] }
}
