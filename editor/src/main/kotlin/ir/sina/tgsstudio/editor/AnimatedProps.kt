package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.DocIndex
import ir.sina.tgsstudio.document.ShapeKind
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue

enum class PropKind { NUMBER, VECTOR, COLOR, PATH }

/** One animatable property row for the timeline / inspector. [key] identifies the property (p, s, r, o, a, c, w, path, ...). */
class PropEntry(val key: String, val owner: String, val depth: Int, val prop: JsonObj, val kind: PropKind) {
    val isAnimated: Boolean get() = PropWrite.isAnimated(prop)
}

object AnimatedProps {
    private const val MAX_ENTRIES = 400

    fun of(idx: DocIndex, t: Target): List<PropEntry> {
        val out = ArrayList<PropEntry>()
        val raw = Targets.raw(idx, t) ?: return out
        when (t) {
            is LayerTarget -> {
                raw.obj("ks")?.let { ks -> transformProps(ks, raw.string("nm") ?: "", 0, out) }
                walk(raw.arr("shapes")?.items.orEmpty(), 1, out)
            }
            is ShapeTarget -> walk(listOf(raw), 0, out)
        }
        return out
    }

    private fun transformProps(block: JsonObj, owner: String, depth: Int, out: MutableList<PropEntry>) {
        for (k in listOf("a", "p", "s", "r", "o")) block.obj(k)?.takeIf { it.has("k") }?.let { out += PropEntry(k, owner, depth, it, if (k == "r" || k == "o") PropKind.NUMBER else PropKind.VECTOR) }
    }

    private fun walk(items: List<JsonValue>, depth: Int, out: MutableList<PropEntry>) {
        for (v in items) {
            if (out.size >= MAX_ENTRIES) return
            val o = v as? JsonObj ?: continue
            val owner = o.string("nm") ?: o.string("ty") ?: ""
            fun add(key: String, jsonKey: String, kind: PropKind) { o.obj(jsonKey)?.takeIf { it.has("k") }?.let { out += PropEntry(key, owner, depth, it, kind) } }
            when (ShapeKind.fromCode(o.string("ty"))) {
                ShapeKind.GROUP -> { o.arr("it")?.items?.firstOrNull { it is JsonObj && it.string("ty") == "tr" }?.let { transformProps(it as JsonObj, owner, depth, out) }
                    walk(o.arr("it")?.items.orEmpty().filterNot { it is JsonObj && it.string("ty") == "tr" }, depth + 1, out) }
                ShapeKind.PATH -> add("path", "ks", PropKind.PATH)
                ShapeKind.RECT -> { add("position", "p", PropKind.VECTOR); add("size", "s", PropKind.VECTOR); add("roundness", "r", PropKind.NUMBER) }
                ShapeKind.ELLIPSE -> { add("position", "p", PropKind.VECTOR); add("size", "s", PropKind.VECTOR) }
                ShapeKind.STAR -> { add("position", "p", PropKind.VECTOR); add("points", "pt", PropKind.NUMBER); add("outer", "or", PropKind.NUMBER); add("inner", "ir", PropKind.NUMBER); add("r", "r", PropKind.NUMBER) }
                ShapeKind.FILL -> { add("c", "c", PropKind.COLOR); add("o", "o", PropKind.NUMBER) }
                ShapeKind.STROKE -> { add("c", "c", PropKind.COLOR); add("w", "w", PropKind.NUMBER); add("o", "o", PropKind.NUMBER) }
                ShapeKind.TRIM -> { add("start", "s", PropKind.NUMBER); add("end", "e", PropKind.NUMBER); add("offset", "o", PropKind.NUMBER) }
                else -> Unit
            }
        }
    }
}
