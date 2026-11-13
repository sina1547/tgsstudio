package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonNum
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.document.json.deepCopy
import ir.sina.tgsstudio.renderer.PropEval
import kotlin.math.abs

enum class Interp { LINEAR, HOLD, BEZIER }

/** Cubic-bezier easing of the segment that starts at a keyframe: out handle (ox,oy), in handle (ix,iy). */
data class Easing(val ox: Double, val oy: Double, val ix: Double, val iy: Double) {
    companion object {
        val EASE_IN_OUT = Easing(0.333, 0.0, 0.667, 1.0)
        val EASE_IN = Easing(0.42, 0.0, 1.0, 1.0)
        val EASE_OUT = Easing(0.0, 0.0, 0.58, 1.0)
    }
}

object KeyframeOps {
    private const val SAME = 0.5

    fun list(prop: JsonObj): List<JsonObj> = PropWrite.keyframesOf(prop) ?: emptyList()
    fun times(prop: JsonObj): List<Double> = list(prop).map { it.double("t") ?: 0.0 }

    /** Adds a keyframe holding the property's current value. Returns its index. */
    fun addAt(prop: JsonObj, frame: Double, isPath: Boolean = false): Int {
        val kfs = PropWrite.keyframesOf(prop)
        if (kfs != null) {
            val ex = kfs.indexOfFirst { abs((it.double("t") ?: 0.0) - frame) < SAME }
            if (ex >= 0) return ex
            val value: JsonValue = if (isPath) PropEval.path(prop, frame)?.let { JsonArr(mutableListOf<JsonValue>(bezierJson(it))) } ?: return -1
                else jarr(PropEval.values(prop, frame) ?: return -1)
            PropWrite.insertKeyframe(kfs, frame, value)
            PropWrite.normalize(kfs)
            return kfs.indexOfFirst { abs((it.double("t") ?: 0.0) - frame) < SAME }
        }
        val s: JsonValue = if (isPath) JsonArr(mutableListOf<JsonValue>(bezierJson(PropEval.path(prop, frame) ?: return -1)))
            else jarr(PropEval.values(prop, frame) ?: return -1)
        val kf = JsonObj(); kf["t"] = jn(frame); kf["s"] = s
        prop["k"] = JsonArr(mutableListOf<JsonValue>(kf)); prop.putInt("a", 1)
        return 0
    }

    /** Removes a keyframe; removing the last one turns the property back into a static value. */
    fun remove(prop: JsonObj, index: Int) {
        val kfs = PropWrite.keyframesOf(prop) ?: return
        if (index !in kfs.indices) return
        if (kfs.size == 1) {
            val s = kfs[0]["s"]
            prop["k"] = when {
                s is JsonArr && s.items.firstOrNull() is JsonObj -> s.items[0].deepCopy()
                s is JsonArr && s.items.size == 1 -> s.items[0]
                else -> s?.deepCopy() ?: JsonNum(0)
            }
            prop.putInt("a", 0)
            return
        }
        kfs.removeAt(index)
        PropWrite.normalize(kfs)
    }

    /** Moves a keyframe in time. Returns its new index (unchanged if the target frame is taken). */
    fun move(prop: JsonObj, index: Int, newFrame: Double): Int {
        val kfs = PropWrite.keyframesOf(prop) ?: return index
        if (index !in kfs.indices) return index
        if (kfs.indices.any { it != index && abs((kfs[it].double("t") ?: 0.0) - newFrame) < SAME }) return index
        val kf = kfs[index]
        kf["t"] = jn(newFrame)
        PropWrite.normalize(kfs)
        return kfs.indexOf(kf)
    }

    fun setValues(prop: JsonObj, index: Int, values: DoubleArray) {
        val kfs = PropWrite.keyframesOf(prop) ?: return
        if (index !in kfs.indices) return
        kfs[index]["s"] = jarr(values)
        PropWrite.normalize(kfs)
    }

    fun interpolation(prop: JsonObj, index: Int): Interp {
        val kf = list(prop).getOrNull(index) ?: return Interp.LINEAR
        return when {
            kf.bool("h") == true -> Interp.HOLD
            kf.has("i") && kf.has("o") && easing(prop, index) != Easing(0.0, 0.0, 1.0, 1.0) -> Interp.BEZIER
            else -> Interp.LINEAR
        }
    }

    fun easing(prop: JsonObj, index: Int): Easing? {
        val kf = list(prop).getOrNull(index) ?: return null
        val o = kf.obj("o") ?: return null; val i = kf.obj("i") ?: return null
        fun first(v: JsonValue?): Double? = when (v) { is JsonNum -> v.value.toDouble(); is JsonArr -> (v.items.firstOrNull() as? JsonNum)?.value?.toDouble(); else -> null }
        return Easing(first(o["x"]) ?: return null, first(o["y"]) ?: return null, first(i["x"]) ?: return null, first(i["y"]) ?: return null)
    }

    fun setInterpolation(prop: JsonObj, index: Int, interp: Interp) {
        val kf = list(prop).getOrNull(index) ?: return
        when (interp) {
            Interp.LINEAR -> { kf.remove("h"); kf.remove("i"); kf.remove("o") }
            Interp.HOLD -> { kf.putInt("h", 1); kf.remove("i"); kf.remove("o") }
            Interp.BEZIER -> { kf.remove("h"); setEasing(prop, index, easing(prop, index)?.takeIf { it != Easing(0.0, 0.0, 1.0, 1.0) } ?: Easing.EASE_IN_OUT) }
        }
    }

    fun setEasing(prop: JsonObj, index: Int, e: Easing) {
        val kf = list(prop).getOrNull(index) ?: return
        fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
        kf.remove("h")
        kf["o"] = obj("x" to jarr(doubleArrayOf(clamp01(e.ox))), "y" to jarr(doubleArrayOf(e.oy.coerceIn(-1.0, 2.0))))
        kf["i"] = obj("x" to jarr(doubleArrayOf(clamp01(e.ix))), "y" to jarr(doubleArrayOf(e.iy.coerceIn(-1.0, 2.0))))
    }

    fun copy(prop: JsonObj, index: Int): JsonObj? = list(prop).getOrNull(index)?.deepCopy() as? JsonObj

    /** Pastes a copied keyframe at [frame] (replacing a keyframe already there). */
    fun paste(prop: JsonObj, copied: JsonObj, frame: Double): Boolean {
        val kfs = PropWrite.keyframesOf(prop)
        val kf = copied.deepCopy() as JsonObj
        kf["t"] = jn(frame)
        if (kfs == null) {
            // static target: only paste into a property of the same shape
            prop["k"] = JsonArr(mutableListOf<JsonValue>(kf)); prop.putInt("a", 1)
            return true
        }
        val ex = kfs.indexOfFirst { abs((it.double("t") ?: 0.0) - frame) < SAME }
        if (ex >= 0) kfs[ex] = kf else kfs.add(kf)
        PropWrite.normalize(kfs)
        return true
    }
}
