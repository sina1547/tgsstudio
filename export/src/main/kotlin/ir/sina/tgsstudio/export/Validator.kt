package ir.sina.tgsstudio.export

import ir.sina.tgsstudio.document.Layer
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonStr
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.parser.TgsCodec
import ir.sina.tgsstudio.renderer.SceneBuilder
import kotlin.math.abs

enum class Severity { ERROR, WARNING }

/**
 * One finding. [code] is a stable id the UI translates; [detail] carries values or names.
 * Errors break Telegram's published TGS limits; warnings are features Telegram is known to reject or ignore.
 */
data class Issue(val severity: Severity, val code: String, val detail: String = "")

object Validator {
    const val MAX_BYTES = 64 * 1024
    const val MAX_SECONDS = 3.0
    const val SIZE = 512

    fun validate(doc: LottieDocument): List<Issue> {
        val out = ArrayList<Issue>()
        fun error(code: String, detail: String = "") { out += Issue(Severity.ERROR, code, detail) }
        fun warn(code: String, detail: String = "") { out += Issue(Severity.WARNING, code, detail) }

        // canvas, timing
        if (doc.width != SIZE || doc.height != SIZE) error("canvas_size", "${doc.width}×${doc.height}")
        if (doc.outPoint <= doc.inPoint) error("empty_range", "${doc.inPoint}–${doc.outPoint}")
        if (doc.frameRate != 30.0 && doc.frameRate != 60.0) error("frame_rate", doc.frameRate.toString())
        if (doc.frameRate > 0 && doc.durationSeconds > MAX_SECONDS + 1e-6) error("duration", "%.2f".format(java.util.Locale.ROOT, doc.durationSeconds))

        // structure
        for ((scope, layers) in compositions(doc)) {
            val tag = scope ?: "main"
            val seen = HashSet<Int>()
            val byInd = layers.mapNotNull { l -> l.index?.let { it to l } }.toMap()
            for (l in layers) {
                val ind = l.index
                if (ind == null) { warn("layer_without_index", "$tag/${l.name}"); continue }
                if (!seen.add(ind)) error("duplicate_layer_index", "$tag/$ind")
                l.parentIndex?.let { p -> if (p !in byInd) error("missing_parent", "$tag/${l.name}") }
                if (hasParentCycle(l, byInd)) error("parent_cycle", "$tag/${l.name}")
                if (l.inPoint > l.outPoint) error("layer_range", "$tag/${l.name}")
                if (l.raw.has("refId") && doc.precomps.none { it.id == l.refId }) error("missing_precomp", "${l.refId}")
            }
        }
        checkKeyframes(doc.root, "", out)

        // features Telegram does not support
        val scene = SceneBuilder(doc)
        for (f in scene.build(doc.inPoint).unsupported) warn("unsupported", f)
        scanFeatures(doc.root).forEach { warn("unsupported", it) }

        // everything should stay inside the canvas
        runCatching {
            val frames = listOf(doc.inPoint, (doc.inPoint + doc.outPoint) / 2, doc.outPoint - 1)
            val worst = frames.mapNotNull { f -> sceneBounds(scene.build(f)) }
            if (worst.any { (minX, minY, maxX, maxY) -> minX < -SIZE * 0.05 || minY < -SIZE * 0.05 || maxX > SIZE * 1.05 || maxY > SIZE * 1.05 }) warn("outside_canvas")
        }

        // compressed size last (it is the slowest check)
        val bytes = TgsCodec.writeTgs(doc).size
        if (bytes > MAX_BYTES) error("file_size", "%.1f KB".format(java.util.Locale.ROOT, bytes / 1024.0))
        return out.sortedBy { it.severity }
    }

    fun hasErrors(issues: List<Issue>) = issues.any { it.severity == Severity.ERROR }

    private fun compositions(doc: LottieDocument): List<Pair<String?, List<Layer>>> =
        listOf<Pair<String?, List<Layer>>>(null to doc.layers) + doc.precomps.map { it.id to it.layers }

    private fun hasParentCycle(l: Layer, byInd: Map<Int, Layer>): Boolean {
        var cur: Layer? = l; var hops = 0
        while (cur != null && hops++ < 1000) {
            val p = cur.parentIndex ?: return false
            if (p == l.index) return true
            cur = byInd[p]
        }
        return hops >= 1000
    }

    /** Keyframes must have a start value and ascending times. */
    private fun checkKeyframes(v: JsonValue, path: String, out: MutableList<Issue>) {
        when (v) {
            is JsonObj -> {
                val k = v["k"]
                if (k is JsonArr && k.items.isNotEmpty() && k.items.all { it is JsonObj && it.has("t") }) {
                    var last = -Double.MAX_VALUE
                    for (kf in k.items) {
                        kf as JsonObj
                        val t = kf.double("t") ?: Double.NaN
                        if (!t.isFinite()) out += Issue(Severity.ERROR, "bad_keyframe", path)
                        else if (t < last) { out += Issue(Severity.ERROR, "keyframes_unsorted", path); break }
                        else last = t
                        if (kf["s"] == null && kf !== k.items.last()) out += Issue(Severity.ERROR, "bad_keyframe", path)
                    }
                } else v.fields.forEach { (key, child) -> checkKeyframes(child, "$path/$key", out) }
            }
            is JsonArr -> v.items.forEachIndexed { i, c -> checkKeyframes(c, "$path[$i]", out) }
            else -> Unit
        }
    }

    /** Features the renderer might skip silently that Telegram's player does not support. */
    private fun scanFeatures(root: JsonObj): Set<String> {
        val found = linkedSetOf<String>()
        fun walk(v: JsonValue) {
            when (v) {
                is JsonObj -> {
                    if (v["x"] is JsonStr && v.has("k")) found += "expressions"
                    if (v.arr("ef")?.items?.isNotEmpty() == true) found += "layer effects"
                    if (v.arr("masksProperties")?.items?.isNotEmpty() == true) found += "masks"
                    if (v.string("ty") == "mm") found += "merge paths"
                    if (v.string("ty") == "rp") found += "repeater"
                    if (v.has("tt")) found += "track mattes"
                    if (v.has("tm") && v.int("ty") == 0) found += "time remap"
                    if (v.int("ty") == 2 && v.has("refId")) found += "images"
                    if (v.int("ty") == 5) found += "text layers"
                    v.fields.values.forEach(::walk)
                }
                is JsonArr -> v.items.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return found
    }

    private fun sceneBounds(scene: ir.sina.tgsstudio.renderer.Scene): List<Double>? {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        fun visit(layers: List<ir.sina.tgsstudio.renderer.SceneLayer>, outer: ir.sina.tgsstudio.renderer.Affine) {
            for (l in layers) {
                val m = outer * l.matrix
                for (item in l.items) {
                    val im = m * item.matrix
                    for (c in item.contours) for (s in c.segments) {
                        val f = s.flatten(6)
                        for (i in 0 until f.size / 2) { val x = im.mapX(f[2 * i], f[2 * i + 1]); val y = im.mapY(f[2 * i], f[2 * i + 1]); minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y) }
                    }
                }
                visit(l.children, m)
            }
        }
        visit(scene.layers, ir.sina.tgsstudio.renderer.Affine.IDENTITY)
        return if (minX > maxX) null else listOf(minX, minY, maxX, maxY)
    }
}

private operator fun List<Double>.component1() = this[0]
private operator fun List<Double>.component2() = this[1]
private operator fun List<Double>.component3() = this[2]
private operator fun List<Double>.component4() = this[3]
