package ir.sina.tgsstudio.document

import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue

/**
 * Editable view over a Lottie JSON tree.
 *
 * The raw [root] tree is the single source of truth. Every model class ([Layer], [ShapeItem], ...)
 * is a thin wrapper over a [JsonObj], so anything the editor does not understand stays in the tree
 * and is written back untouched.
 */
class LottieDocument(val root: JsonObj) {
    init {
        if (root.arr("layers") == null) throw DocumentException("Not a Lottie animation: missing 'layers' array")
    }

    var version: String?
        get() = root.string("v")
        set(v) { if (v != null) root.putString("v", v) else root.remove("v") }
    var name: String?
        get() = root.string("nm")
        set(v) { if (v != null) root.putString("nm", v) else root.remove("nm") }
    var frameRate: Double
        get() = root.double("fr") ?: 30.0
        set(v) = root.putDouble("fr", v)
    var inPoint: Double
        get() = root.double("ip") ?: 0.0
        set(v) = root.putDouble("ip", v)
    var outPoint: Double
        get() = root.double("op") ?: 0.0
        set(v) = root.putDouble("op", v)
    val width: Int get() = root.int("w") ?: 0
    val height: Int get() = root.int("h") ?: 0

    val durationFrames: Double get() = outPoint - inPoint
    val durationSeconds: Double get() = if (frameRate > 0) durationFrames / frameRate else 0.0

    val layers: List<Layer> get() = layersOf(root.arr("layers"))

    /** Precomposition assets (assets that own a 'layers' array). Image assets are kept in the raw tree. */
    val precomps: List<Precomp>
        get() = root.arr("assets")?.items.orEmpty().filterIsInstance<JsonObj>()
            .filter { it.arr("layers") != null }.map(::Precomp)

    fun layerTree(): List<LayerNode> = LayerNode.build(layers)

    fun stats(): DocumentStats {
        var layerCount = 0; var shapes = 0; var keyframes = 0; var unknownLayers = 0
        fun visitShapes(items: List<ShapeItem>) {
            for (s in items) {
                shapes++
                s.properties().forEach { keyframes += it.keyframeCount }
                visitShapes(s.children)
            }
        }
        fun visitLayers(ls: List<Layer>) {
            for (l in ls) {
                layerCount++
                if (l.type == LayerType.UNKNOWN) unknownLayers++
                l.transform?.all()?.forEach { keyframes += it.keyframeCount }
                visitShapes(l.shapes)
            }
        }
        visitLayers(layers)
        precomps.forEach { visitLayers(it.layers) }
        return DocumentStats(layerCount, shapes, keyframes, precomps.size, unknownLayers)
    }

    companion object {
        internal fun layersOf(arr: JsonArr?): List<Layer> =
            arr?.items.orEmpty().filterIsInstance<JsonObj>().map(::Layer)
    }
}

data class DocumentStats(
    val layers: Int,
    val shapeItems: Int,
    val keyframes: Int,
    val precomps: Int,
    val unknownLayerTypes: Int,
)

class Precomp(val raw: JsonObj) {
    val id: String? get() = raw.string("id")
    val layers: List<Layer> get() = LottieDocument.layersOf(raw.arr("layers"))
}

enum class LayerType(val code: Int) {
    PRECOMP(0), SOLID(1), IMAGE(2), NULL(3), SHAPE(4), TEXT(5), AUDIO(6), ADJUSTMENT(12), UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int?) = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

class Layer(val raw: JsonObj) {
    val type: LayerType get() = LayerType.fromCode(raw.int("ty"))
    val typeCode: Int? get() = raw.int("ty")
    /** Layer number used by other layers' 'parent' field. */
    val index: Int? get() = raw.int("ind")
    val parentIndex: Int? get() = raw.int("parent")
    var name: String
        get() = raw.string("nm") ?: ""
        set(v) = raw.putString("nm", v)
    var hidden: Boolean
        get() = raw.bool("hd") ?: false
        set(v) = raw.putBool("hd", v)
    val inPoint: Double get() = raw.double("ip") ?: 0.0
    val outPoint: Double get() = raw.double("op") ?: 0.0
    val startTime: Double get() = raw.double("st") ?: 0.0
    /** For precomp layers: id of the referenced asset. */
    val refId: String? get() = raw.string("refId")
    val transform: TransformProps? get() = raw.obj("ks")?.let(::TransformProps)
    val shapes: List<ShapeItem>
        get() = raw.arr("shapes")?.items.orEmpty().filterIsInstance<JsonObj>().map(::ShapeItem)
}

/** Layer hierarchy built from the 'parent' links. Array order is kept among siblings. */
class LayerNode(val layer: Layer, val children: MutableList<LayerNode> = mutableListOf()) {
    companion object {
        fun build(layers: List<Layer>): List<LayerNode> {
            val nodes = layers.map { LayerNode(it) }
            val byIndex = HashMap<Int, LayerNode>()
            nodes.forEach { n -> n.layer.index?.let { byIndex.putIfAbsent(it, n) } }
            val roots = mutableListOf<LayerNode>()
            val attached = HashSet<LayerNode>()
            for (n in nodes) {
                val p = n.layer.parentIndex?.let(byIndex::get)
                if (p == null || p === n) roots += n else { p.children += n; attached += n }
            }
            // A parent cycle leaves nodes unreachable from any root; surface them as roots so nothing is hidden.
            val reachable = HashSet<LayerNode>()
            fun walk(n: LayerNode) { if (reachable.add(n)) n.children.forEach(::walk) }
            roots.forEach(::walk)
            for (n in nodes) if (n !in reachable) { roots += n; n.layer.parentIndex?.let(byIndex::get)?.children?.remove(n); walk(n) }
            return roots
        }
    }
}

enum class ShapeKind(val code: String) {
    GROUP("gr"), PATH("sh"), RECT("rc"), ELLIPSE("el"), STAR("sr"),
    FILL("fl"), STROKE("st"), GRADIENT_FILL("gf"), GRADIENT_STROKE("gs"),
    TRANSFORM("tr"), TRIM("tm"), ROUNDED_CORNERS("rd"), REPEATER("rp"), MERGE("mm"), UNKNOWN("");

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code.isNotEmpty() && it.code == code } ?: UNKNOWN
    }
}

class ShapeItem(val raw: JsonObj) {
    val typeCode: String? get() = raw.string("ty")
    val kind: ShapeKind get() = ShapeKind.fromCode(typeCode)
    var name: String
        get() = raw.string("nm") ?: ""
        set(v) = raw.putString("nm", v)
    var hidden: Boolean
        get() = raw.bool("hd") ?: false
        set(v) = raw.putBool("hd", v)
    /** Children of a group ('it' array); empty for every other kind. */
    val children: List<ShapeItem>
        get() = raw.arr("it")?.items.orEmpty().filterIsInstance<JsonObj>().map(::ShapeItem)

    /** Every animatable property directly on this item (p, s, r, o, c, w, ks, ...). */
    fun properties(): List<AnimatableProp> = ANIMATABLE_KEYS.mapNotNull { k -> raw.obj(k)?.takeIf { it.has("k") }?.let(::AnimatableProp) }

    private companion object {
        val ANIMATABLE_KEYS = listOf("p", "s", "r", "o", "c", "w", "ks", "d", "sk", "sa", "a", "is", "or", "ir", "pt", "rd", "e")
    }
}

/** Lottie 'ks' layer transform block. */
class TransformProps(val raw: JsonObj) {
    val anchor get() = prop("a")
    val position get() = prop("p")
    val scale get() = prop("s")
    val rotation get() = prop("r")
    val opacity get() = prop("o")
    private fun prop(k: String) = raw.obj(k)?.let(::AnimatableProp)
    fun all(): List<AnimatableProp> = listOfNotNull(anchor, position, scale, rotation, opacity)
}

/** Lottie animatable property: `{ "a": 0|1, "k": value | [keyframes] }`. */
class AnimatableProp(val raw: JsonObj) {
    val keyframes: List<JsonObj>
        get() = (raw["k"] as? JsonArr)?.items.orEmpty().let { items ->
            if (items.isNotEmpty() && items.all { it is JsonObj && it.has("t") }) items.map { it as JsonObj } else emptyList()
        }
    val isAnimated: Boolean get() = keyframes.isNotEmpty()
    val keyframeCount: Int get() = keyframes.size
    /** Static value when not animated (number, array, or path object); null when animated. */
    val staticValue: JsonValue? get() = if (isAnimated) null else raw["k"]
}
