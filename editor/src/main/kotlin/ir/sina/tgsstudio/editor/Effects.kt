package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.DocIndex
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.LayerType
import ir.sina.tgsstudio.document.ShapeKind
import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonValue
import ir.sina.tgsstudio.document.json.deepCopy
import ir.sina.tgsstudio.renderer.PropEval
import ir.sina.tgsstudio.renderer.Rgba
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class EffectWork { TRANSFORM, OPACITY, TRIM, GLOW, UNSUPPORTED }

/** The ready-made effects. [id] is stable (used in saved presets); the English [title] is what layers created by an effect are named after. */
enum class EffectKind(val id: String, val title: String, val work: EffectWork, val periodic: Boolean = false, val directional: Boolean = false) {
    FADE_IN("fade_in", "Fade In", EffectWork.OPACITY),
    FADE_OUT("fade_out", "Fade Out", EffectWork.OPACITY),
    ZOOM_IN("zoom_in", "Zoom In", EffectWork.TRANSFORM),
    ZOOM_OUT("zoom_out", "Zoom Out", EffectWork.TRANSFORM),
    SHAKE("shake", "Shake", EffectWork.TRANSFORM, periodic = true),
    BOUNCE("bounce", "Bounce", EffectWork.TRANSFORM),
    PULSE("pulse", "Pulse", EffectWork.TRANSFORM, periodic = true),
    ROTATE("rotate", "Rotation", EffectWork.TRANSFORM, periodic = true, directional = true),
    SCALE("scale", "Scale", EffectWork.TRANSFORM),
    SLIDE_IN("slide_in", "Slide In", EffectWork.TRANSFORM, directional = true),
    SLIDE_OUT("slide_out", "Slide Out", EffectWork.TRANSFORM, directional = true),
    OPACITY("opacity", "Opacity", EffectWork.OPACITY, periodic = true),
    FLOAT("float", "Float", EffectWork.TRANSFORM, periodic = true),
    DRAW_ON("draw_on", "Draw On", EffectWork.TRIM),
    DRAW_OFF("draw_off", "Draw Off", EffectWork.TRIM),
    GLOW("glow", "Glow", EffectWork.GLOW, periodic = true),
    /** Gaussian blur is an After Effects layer effect, which Telegram's TGS format forbids. Listed so users learn why it is missing. */
    BLUR("blur", "Blur", EffectWork.UNSUPPORTED);

    val supported: Boolean get() = work != EffectWork.UNSUPPORTED

    /** Sensible starting values for a document running at [fps]. */
    fun defaults(fps: Double, start: Double = 0.0): EffectParams {
        fun sec(s: Double) = Math.rint(s * fps)
        return when (this) {
            FADE_IN, FADE_OUT -> EffectParams(start, sec(0.5))
            ZOOM_IN, ZOOM_OUT, SCALE, SLIDE_IN, SLIDE_OUT -> EffectParams(start, sec(0.5))
            SHAKE -> EffectParams(start, sec(0.6))
            BOUNCE -> EffectParams(start, sec(1.0))
            PULSE -> EffectParams(start, sec(0.8), loop = true)
            ROTATE -> EffectParams(start, sec(1.5), loop = true, direction = 1)
            OPACITY -> EffectParams(start, sec(1.0), loop = true)
            FLOAT -> EffectParams(start, sec(2.0), loop = true)
            DRAW_ON, DRAW_OFF -> EffectParams(start, sec(1.0))
            GLOW -> EffectParams(start, sec(1.5), loop = false)
            BLUR -> EffectParams(start, sec(1.0))
        }
    }

    companion object { fun byId(id: String) = entries.firstOrNull { it.id == id } }
}

/**
 * [durationFrames] is one cycle; [strength] 100 = the default intensity (0..300); [loop] repeats periodic effects until the
 * end of the timeline; [direction]: 0 left, 1 right, 2 up, 3 down (slides) or 0 anticlockwise / 1 clockwise (rotation).
 */
data class EffectParams(val startFrame: Double = 0.0, val durationFrames: Double = 30.0, val strength: Double = 100.0, val loop: Boolean = false, val direction: Int = 0)

enum class EffectNote { BLUR_UNSUPPORTED, SHORTENED_TO_TIMELINE, NOT_ENOUGH_TIME, NO_STROKE, NOT_A_SHAPE_LAYER, GLOW_ADDS_LAYERS, NO_TARGET }

class EffectResult(val targets: List<LayerTarget>, val notes: Set<EffectNote>, val applied: Int)

/** A saved, named parameter set for an effect (the user's own presets). */
data class EffectPreset(val name: String, val kind: EffectKind, val params: EffectParams)

object EffectPresets {
    fun toJson(list: List<EffectPreset>): String = ir.sina.tgsstudio.document.json.JsonWriter.write(JsonArr(list.mapTo(mutableListOf<JsonValue>()) { p ->
        obj("name" to str(p.name), "kind" to str(p.kind.id), "duration" to jn(p.params.durationFrames), "strength" to jn(p.params.strength),
            "loop" to ir.sina.tgsstudio.document.json.JsonBool(p.params.loop), "direction" to int(p.params.direction))
    }))

    fun fromJson(text: String): List<EffectPreset> = runCatching {
        (ir.sina.tgsstudio.document.json.JsonParser.parse(text) as JsonArr).items.mapNotNull { v ->
            val o = v as? JsonObj ?: return@mapNotNull null
            val kind = EffectKind.byId(o.string("kind") ?: return@mapNotNull null) ?: return@mapNotNull null
            EffectPreset(o.string("name") ?: kind.title, kind, EffectParams(0.0, o.double("duration") ?: 30.0, o.double("strength") ?: 100.0, o.bool("loop") ?: false, o.int("direction") ?: 0))
        }
    }.getOrDefault(emptyList())
}

/**
 * Applies effects as real, editable Lottie animation, never as a hidden recipe:
 *  - move/scale/rotate effects animate a Null parent ("Fx: …") that rests at the identity transform and pivots on the layer's
 *    centre, so any number of them stack by nesting and each can be removed or re-timed in the Timeline;
 *  - opacity effects keyframe the layer's own opacity, composed with whatever animation it already has;
 *  - Draw On/Off use Trim Paths; Glow adds translucent stroked copies. Blur is refused (TGS forbids layer effects).
 * Everything is ordinary keyframes with valid easing handles, so the exported file passes the Telegram checks.
 */
object EffectEngine {
    const val FX_PREFIX = "Fx: "

    private val easeOut = Easing.EASE_OUT
    private val easeIn = Easing.EASE_IN
    private val easeInOut = Easing.EASE_IN_OUT

    private class Key(val frame: Double, val v: DoubleArray, val ease: Easing = easeInOut)

    fun apply(s: EditorSession, targets: List<LayerTarget>, kind: EffectKind, p: EffectParams): EffectResult {
        if (!kind.supported) return EffectResult(targets, setOf(EffectNote.BLUR_UNSUPPORTED), 0)
        if (targets.isEmpty()) return EffectResult(targets, setOf(EffectNote.NO_TARGET), 0)
        val notes = LinkedHashSet<EffectNote>()
        val timeline = timelineFor(s.doc.inPoint, s.doc.outPoint, p, kind, notes) ?: return EffectResult(targets, notes + EffectNote.NOT_ENOUGH_TIME, 0)
        val finished = ArrayList<LayerRef>(); var applied = 0
        // process bottom-most first, so a layer or null inserted for one target never shifts a target still to do
        for (t in targets.sortedByDescending { it.layer.index }) {
            val ref = t.layer
            var newRef: LayerRef = ref; var inserted = 0
            val ok = when (kind.work) {
                EffectWork.TRANSFORM -> { newRef = wrapWithNull(s, ref, kind, p, timeline, notes) ?: ref; inserted = if (newRef != ref) 1 else 0; true }
                EffectWork.OPACITY -> { opacity(s, ref, kind, p, timeline); true }
                EffectWork.TRIM -> trim(s, ref, kind, timeline, notes)
                EffectWork.GLOW -> glow(s, ref, p, timeline, notes).also { if (it) inserted = 2 }
                EffectWork.UNSUPPORTED -> false
            }
            if (ok) applied++
            // everything already finished sits above this layer's old position in the array, so it moved down by what was inserted
            if (inserted > 0) for (i in finished.indices) if (finished[i].scope == ref.scope && finished[i].index > ref.index) finished[i] = LayerRef(ref.scope, finished[i].index + inserted)
            finished += newRef
        }
        val out = finished.map { LayerTarget(it) }
        return EffectResult(out, notes, applied)
    }

    /** First frame, one-cycle length and cycle count after fitting into the timeline. */
    private class Timeline(val start: Double, val period: Double, val cycles: Int, val end: Double)

    private fun timelineFor(ip: Double, op: Double, p: EffectParams, kind: EffectKind, notes: MutableSet<EffectNote>): Timeline? {
        val last = op - 1
        val start = Math.rint(p.startFrame).coerceIn(ip, last)
        var period = max(2.0, Math.rint(p.durationFrames))
        if (start + period > last) { period = Math.rint(last - start); notes += EffectNote.SHORTENED_TO_TIMELINE }
        if (period < 2.0) return null
        val cycles = if (kind.periodic && p.loop) max(1, floor((last - start) / period).toInt()) else 1
        return Timeline(start, period, cycles, start + period * cycles)
    }

    // ---- transform effects: an identity-at-rest Null parent --------------------------------------------------

    private fun wrapWithNull(s: EditorSession, ref: LayerRef, kind: EffectKind, p: EffectParams, tl: Timeline, notes: MutableSet<EffectNote>): LayerRef? {
        val idx = s.index
        val arr = idx.layerArray(ref.scope) ?: return null
        val frame = tl.start
        // pivot: the visual centre of the layer, expressed in the space the new null will live in (its parent's)
        val box = Hit.bounds(s.scene(frame), ref)
        val parentToDoc = Spaces.parentToDoc(idx, LayerTarget(ref), s.frame)
        val inv = parentToDoc.inverse() ?: return null
        val cxDoc: Double; val cyDoc: Double
        if (box != null) { cxDoc = box.centerX; cyDoc = box.centerY } else { cxDoc = parentToDoc.e; cyDoc = parentToDoc.f }
        val px = inv.mapX(cxDoc, cyDoc); val py = inv.mapY(cxDoc, cyDoc)

        val oldParent = ParentOps.parentOf(idx, ref)
        val nullLayer = Creator.nullLayer(s.doc, arr, FX_PREFIX + kind.title)
        nullLayer.obj("ks")!!.also { it["p"] = staticProp(px, py, 0.0); it["a"] = staticProp(px, py, 0.0) }
        arr.items.add(ref.index, nullLayer)
        val nullRef = LayerRef(ref.scope, ref.index)
        val layerRef = LayerRef(ref.scope, ref.index + 1)
        oldParent?.let { op -> ParentOps.setParent(idx, listOf(nullRef), LayerRef(op.scope, if (op.index >= ref.index) op.index + 1 else op.index), s.frame, keepPosition = false) }
        ParentOps.setParent(idx, listOf(layerRef), nullRef, s.frame, keepPosition = true)

        val ks = nullLayer.obj("ks")!!
        val str = p.strength / 100.0
        when (kind) {
            EffectKind.ZOOM_IN -> scaleKeys(ks, listOf(Key(tl.start, scale(100 - 80 * min(str, 1.2)), easeOut), Key(tl.end, scale(100.0))))
            EffectKind.ZOOM_OUT -> scaleKeys(ks, listOf(Key(tl.start, scale(100.0), easeIn), Key(tl.end, scale(max(5.0, 100 - 80 * min(str, 1.2))))))
            EffectKind.SCALE -> scaleKeys(ks, listOf(Key(tl.start, scale(100.0), easeInOut), Key(tl.end, scale(max(5.0, 100 + 50 * str)))))
            EffectKind.PULSE -> scaleKeys(ks, cycles(tl) { t0, d -> listOf(Key(t0, scale(100.0)), Key(t0 + d / 2, scale(100 + 15 * str)), Key(t0 + d, scale(100.0))) })
            EffectKind.ROTATE -> {
                val sign = if (p.direction == 0) -1.0 else 1.0
                rotationKeys(ks, (0..tl.cycles).map { k -> Key(tl.start + tl.period * k, doubleArrayOf(sign * 360.0 * str * k), Easing.LINEAR) })
            }
            EffectKind.SHAKE -> positionKeys(ks, px, py, shake(tl, str, s.doc.frameRate, kind.id.hashCode() + tl.start.toInt()))
            EffectKind.BOUNCE -> {
                val h = 120.0 * str; val d = tl.period; val t0 = tl.start
                positionKeys(ks, px, py, listOf(
                    Key(t0, off(0.0, -h), easeIn), Key(t0 + 0.4 * d, off(0.0, 0.0), easeOut), Key(t0 + 0.65 * d, off(0.0, -0.35 * h), easeIn),
                    Key(t0 + 0.8 * d, off(0.0, 0.0), easeOut), Key(t0 + 0.9 * d, off(0.0, -0.12 * h), easeIn), Key(t0 + d, off(0.0, 0.0))))
            }
            EffectKind.FLOAT -> positionKeys(ks, px, py, cycles(tl) { t0, d ->
                listOf(Key(t0, off(0.0, 0.0)), Key(t0 + d * 0.25, off(0.0, -10 * str)), Key(t0 + d * 0.5, off(0.0, 0.0)), Key(t0 + d * 0.75, off(0.0, 10 * str)), Key(t0 + d, off(0.0, 0.0))) })
            EffectKind.SLIDE_IN, EffectKind.SLIDE_OUT -> {
                val dist = 200.0 * str
                val (dx, dy) = when (p.direction) { 0 -> -dist to 0.0; 1 -> dist to 0.0; 2 -> 0.0 to -dist; else -> 0.0 to dist }
                positionKeys(ks, px, py, if (kind == EffectKind.SLIDE_IN) listOf(Key(tl.start, off(dx, dy), easeOut), Key(tl.end, off(0.0, 0.0)))
                    else listOf(Key(tl.start, off(0.0, 0.0), easeIn), Key(tl.end, off(dx, dy))))
            }
            else -> Unit
        }
        return layerRef
    }

    private fun scale(v: Double) = doubleArrayOf(v, v, 100.0)
    private fun off(dx: Double, dy: Double) = doubleArrayOf(dx, dy)

    private fun cycles(tl: Timeline, one: (Double, Double) -> List<Key>): List<Key> {
        val keys = ArrayList<Key>()
        for (k in 0 until tl.cycles) { val c = one(tl.start + tl.period * k, tl.period); keys += if (k == 0) c else c.drop(1) }
        return keys
    }

    /** Alternating jolts that fade out, back to rest at the end. Deterministic so a re-applied preset looks the same. */
    private fun shake(tl: Timeline, str: Double, fps: Double, seed: Int): List<Key> {
        val rnd = java.util.Random(seed.toLong())
        val step = max(2.0, Math.rint(fps / 15.0))
        val keys = ArrayList<Key>()
        for (k in 0 until tl.cycles) {
            val t0 = tl.start + tl.period * k
            keys += Key(t0, off(0.0, 0.0), Easing.LINEAR)
            var t = t0 + step; var i = 1
            while (t < t0 + tl.period - 0.5) {
                val decay = 1.0 - (t - t0) / tl.period
                val amp = 14.0 * str * decay
                val sign = if (i % 2 == 0) 1.0 else -1.0
                keys += Key(t, off(sign * amp * (0.6 + 0.4 * rnd.nextDouble()), (rnd.nextDouble() - 0.5) * amp * 0.8), Easing.LINEAR)
                t += step; i++
            }
        }
        keys += Key(tl.end, off(0.0, 0.0))
        return keys
    }

    private fun write(prop: JsonObj, keys: List<Key>, vec: (Key) -> DoubleArray) {
        val sorted = keys.sortedBy { it.frame }.fold(ArrayList<Key>()) { acc, k -> if (acc.isNotEmpty() && Math.abs(acc.last().frame - k.frame) < 0.5) acc[acc.size - 1] = k else acc += k; acc }
        val list = sorted.map { k -> obj("t" to jn(k.frame), "s" to jarr(vec(k))) }
        prop["k"] = JsonArr(list.toMutableList<JsonValue>()); prop.putInt("a", 1)
        val kfs = PropWrite.keyframesOf(prop)!!
        PropWrite.normalize(kfs)
        sorted.forEachIndexed { i, k -> if (i < sorted.size - 1) KeyframeOps.setEasing(prop, i, k.ease) }
    }

    private fun scaleKeys(ks: JsonObj, keys: List<Key>) = write(ks.obj("s")!!, keys) { it.v }
    private fun rotationKeys(ks: JsonObj, keys: List<Key>) = write(ks.obj("r")!!, keys) { it.v }
    private fun positionKeys(ks: JsonObj, px: Double, py: Double, keys: List<Key>) = write(ks.obj("p")!!, keys) { doubleArrayOf(px + it.v[0], py + it.v[1], 0.0) }

    // ---- opacity effects: composed with the layer's existing opacity ------------------------------------------

    private fun opacity(s: EditorSession, ref: LayerRef, kind: EffectKind, p: EffectParams, tl: Timeline) {
        val layer = s.index.layerRaw(ref) ?: return
        val ks = layer.obj("ks") ?: Creator.defaultKs().also { layer["ks"] = it }
        val o = ks.obj("o") ?: staticProp(100.0).also { ks["o"] = it }
        val str = (p.strength / 100.0).coerceIn(0.0, 3.0)
        val dip = (0.7 * str).coerceIn(0.0, 1.0)
        val f: List<Pair<Double, Double>> = when (kind) {
            EffectKind.FADE_IN -> listOf(tl.start to 0.0, tl.end to 1.0)
            EffectKind.FADE_OUT -> listOf(tl.start to 1.0, tl.end to 0.0)
            else -> (0 until tl.cycles).flatMap { k -> val t0 = tl.start + tl.period * k; listOf(t0 to 1.0, (t0 + tl.period / 2) to (1 - dip), (t0 + tl.period) to 1.0) }.distinctBy { it.first }
        }
        composeOpacity(o, f, if (kind == EffectKind.FADE_IN) easeOut else if (kind == EffectKind.FADE_OUT) easeIn else easeInOut)
    }

    /** Sets opacity(t) = existing(t) × factor(t), where factor interpolates [factorKeys] with [ease] and is flat outside them. */
    internal fun composeOpacity(o: JsonObj, factorKeys: List<Pair<Double, Double>>, ease: Easing) {
        val first = factorKeys.first().first; val last = factorKeys.last().first
        fun factor(t: Double): Double {
            if (t <= first) return factorKeys.first().second
            if (t >= last) return factorKeys.last().second
            for (i in 0 until factorKeys.size - 1) {
                val (t0, v0) = factorKeys[i]; val (t1, v1) = factorKeys[i + 1]
                if (t <= t1) return v0 + (v1 - v0) * Easer.y((t - t0) / (t1 - t0), ease)
            }
            return factorKeys.last().second
        }
        val oldTimes = KeyframeOps.times(o)
        val stride = max(2.0, Math.rint((last - first) / 12.0))
        val times = sortedSetOf<Double>()
        times += oldTimes; factorKeys.forEach { times += it.first }
        var t = first; while (t < last) { times += t; t += stride }
        times += last
        if (oldTimes.isEmpty()) times += first
        val base = o.deepCopy() as JsonObj                                         // evaluate the original curve while writing the new one
        val keys = times.map { tt -> Key(tt, doubleArrayOf((PropEval.number(base, tt, 100.0) * factor(tt)).coerceIn(0.0, 100.0)), Easing.LINEAR) }
        write(o, keys) { it.v }
    }

    // ---- trim and glow ------------------------------------------------------------------------------------------

    private fun trim(s: EditorSession, ref: LayerRef, kind: EffectKind, tl: Timeline, notes: MutableSet<EffectNote>): Boolean {
        val layer = s.index.layer(ref) ?: return false
        if (layer.type != LayerType.SHAPE) { notes += EffectNote.NOT_A_SHAPE_LAYER; return false }
        if (Targets.styleItems(s.index, LayerTarget(ref)).none { ShapeKind.fromCode(it.string("ty")) == ShapeKind.STROKE }) notes += EffectNote.NO_STROKE
        val lf = Spaces.localFrame(s.index, ref.scope, tl.start)
        var ok = false
        for (g in TrimOps.groupsOfLayer(layer.raw)) { val trim = TrimOps.ensure(g) ?: continue; TrimOps.animate(trim, if (kind == EffectKind.DRAW_ON) TrimAnim.DRAW_ON else TrimAnim.DRAW_OFF, lf, tl.period); ok = true }
        return ok
    }

    /** Glow = translucent, thicker, stroked copies of the layer beneath it (adds two layers). Follows the layer because it copies its transform. */
    private fun glow(s: EditorSession, ref: LayerRef, p: EffectParams, tl: Timeline, notes: MutableSet<EffectNote>): Boolean {
        val layer = s.index.layer(ref) ?: return false
        if (layer.type != LayerType.SHAPE) { notes += EffectNote.NOT_A_SHAPE_LAYER; return false }
        notes += EffectNote.GLOW_ADDS_LAYERS
        val arr = s.index.layerArray(ref.scope) ?: return false
        val str = (p.strength / 100.0).coerceIn(0.1, 3.0)
        val color = StyleOps.fillColor(s.index, LayerTarget(ref), s.frame) ?: StyleOps.strokeColor(s.index, LayerTarget(ref), s.frame) ?: Rgba(1.0, 1.0, 1.0, 1.0)
        val src = layer.raw
        // the tighter halo sits right under the shape, the wide faint one below it
        val copies = listOf(Triple(12.0 * str, 26.0, "Inner"), Triple(26.0 * str, 12.0, "Outer"))
        var at = ref.index + 1
        for ((width, opacityPct, label) in copies) {
            val g = src.deepCopy() as JsonObj
            g.putInt("ind", Creator.nextIndex(arr)); g.remove("td"); g.remove("tt")
            g.putString("nm", "${FX_PREFIX}Glow $label")
            for (group in TrimOps.groupsOfLayer(g)) {
                val items = group.arr("it") ?: continue
                items.items.removeAll { it is JsonObj && it.string("ty") == "st" }
                val stroke = Creator.stroke(color, width).also { StyleOps.setOpacity(it, 0.0, opacityPct) }
                val trAt = items.items.indexOfFirst { it is JsonObj && it.string("ty") == "tr" }
                items.items.add(if (trAt >= 0) trAt else items.items.size, stroke)
            }
            arr.items.add(at, g)
            at++
        }
        if (p.loop) {
            for (k in 0 until copies.size) {
                val raw = arr.items[ref.index + 1 + k] as JsonObj
                val ks = raw.obj("ks") ?: continue
                val o = ks.obj("o") ?: staticProp(100.0).also { ks["o"] = it }
                composeOpacity(o, (0 until tl.cycles).flatMap { c -> val t0 = tl.start + tl.period * c; listOf(t0 to 1.0, (t0 + tl.period / 2) to 0.4, (t0 + tl.period) to 1.0) }.distinctBy { it.first }, easeInOut)
            }
        }
        return true
    }

    // ---- removal ------------------------------------------------------------------------------------------------

    /** The effect Nulls a layer sits under, innermost first. */
    fun effectNullsOf(idx: DocIndex, ref: LayerRef): List<LayerRef> {
        val out = ArrayList<LayerRef>()
        var cur = ParentOps.parentOf(idx, ref)
        while (cur != null && out.size < 32) {
            val l = idx.layer(cur) ?: break
            if (l.type != LayerType.NULL || !l.name.startsWith(FX_PREFIX)) break
            out += cur; cur = ParentOps.parentOf(idx, cur)
        }
        return out
    }

    /** Removes an effect Null; what it carried returns to its rest state. */
    fun removeEffectNull(s: EditorSession, nullRef: LayerRef): Boolean {
        val l = s.index.layer(nullRef) ?: return false
        if (l.type != LayerType.NULL || !l.name.startsWith(FX_PREFIX)) return false
        val kids = ParentOps.childrenOf(s.index, nullRef); val up = ParentOps.parentOf(s.index, nullRef)
        if (kids.isNotEmpty()) ParentOps.setParent(s.index, kids, up, s.frame, keepPosition = false)
        LayerOps.delete(s.index, nullRef)
        return true
    }
}

/** cubic-bezier easing evaluated at progress [p] (the same maths the renderer uses). */
internal object Easer {
    fun y(p: Double, e: Easing): Double {
        if (e.isLinear) return p
        fun b(u: Double, a: Double, c: Double): Double { val w = 1 - u; return 3 * w * w * u * a + 3 * w * u * u * c + u * u * u }
        var lo = 0.0; var hi = 1.0
        repeat(30) { val m = (lo + hi) / 2; if (b(m, e.ox, e.ix) < p) lo = m else hi = m }
        return b((lo + hi) / 2, e.oy, e.iy)
    }
}
