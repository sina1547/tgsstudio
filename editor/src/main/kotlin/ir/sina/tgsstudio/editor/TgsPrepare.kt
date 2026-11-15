package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.document.json.deepCopy
import ir.sina.tgsstudio.renderer.PropEval

/** What [TgsPrepare.repair] changed. */
data class RepairReport(val keyframeListsRepaired: Int, val starsConverted: Int) {
    val any: Boolean get() = keyframeListsRepaired > 0 || starsConverted > 0
}

/**
 * Makes a document safe to write as TGS without changing how it looks: repairs keyframe structure and turns
 * static star/polygon items (`sr`, on Telegram's unsupported list) into ordinary paths. Works on a copy, so the
 * open project is never modified by exporting.
 */
object TgsPrepare {
    fun repaired(doc: LottieDocument): Pair<LottieDocument, RepairReport> {
        val copy = LottieDocument(doc.root.deepCopy() as JsonObj)
        return copy to repair(copy)
    }

    fun repair(doc: LottieDocument): RepairReport {
        val keyframes = PropWrite.normalizeTree(doc.root)
        val stars = convertStaticStars(doc.root, doc.inPoint)
        return RepairReport(keyframes, stars)
    }

    private val STAR_PROPS = listOf("p", "pt", "or", "ir", "r", "os", "is")

    /** Only stars that are fully static and have no rounding are converted; the rest stay and are reported by the validator. */
    private fun convertStaticStars(v: JsonValue, frame: Double): Int {
        var n = 0
        when (v) {
            is JsonObj -> v.fields.values.forEach { n += convertStaticStars(it, frame) }
            is JsonArr -> {
                for (i in v.items.indices) {
                    val item = v.items[i] as? JsonObj ?: continue
                    if (item.string("ty") == "sr" && isStatic(item) && !rounded(item, frame) && PathEdit.convertToPath(v, i, frame) != null) n++
                }
                v.items.forEach { n += convertStaticStars(it, frame) }
            }
            else -> Unit
        }
        return n
    }

    private fun isStatic(star: JsonObj) = STAR_PROPS.none { k -> star.obj(k)?.let { PropWrite.isAnimated(it) } == true }
    private fun rounded(star: JsonObj, frame: Double) =
        PropEval.number(star.obj("os"), frame, 0.0) != 0.0 || PropEval.number(star.obj("is"), frame, 0.0) != 0.0
}
