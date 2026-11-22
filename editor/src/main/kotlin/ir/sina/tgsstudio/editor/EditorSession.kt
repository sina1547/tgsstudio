package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.DocIndex
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.ShapeKind
import ir.sina.tgsstudio.document.json.JsonArr
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.deepCopy
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.PropEval
import ir.sina.tgsstudio.renderer.Rgba
import ir.sina.tgsstudio.renderer.Scene
import ir.sina.tgsstudio.renderer.SceneBuilder

/**
 * The editing engine behind the UI: owns the document, undo history and selection. It has no Android or UI
 * dependency, so everything the editor does can be tested on the JVM. Every public mutator is one undo step
 * (or part of an open gesture).
 */
/** Timing of a write-on (Trim Paths) animation, in document frames. */
data class WriteOn(val startFrame: Double, val frames: Double, val easing: Easing = Easing.EASE_IN_OUT)

data class LayerTiming(val inPoint: Double, val outPoint: Double, val startTime: Double, val stretch: Double)

class EditorSession(val doc: LottieDocument) {
    val history = History(doc.root)
    val index: DocIndex get() = DocIndex(doc)
    private val builder = SceneBuilder(doc)

    var frame: Double = doc.inPoint
    var selection: List<Target> = emptyList()
        private set
    /** Layers the user locked in the panel. Session-only (Lottie has no lock flag): keyed by scope and `ind`. */
    val locked = HashSet<Pair<String?, Int>>()

    /** Bumped on any change of document, selection or frame; UI observes it. */
    var revision: Int = 0
        private set
    private fun touch() { revision++ }

    fun scene(at: Double = frame): Scene = builder.build(at)

    // ---- selection ---------------------------------------------------------------------------------

    fun select(targets: List<Target>) { selection = targets.filter { isLive(it) }; touch() }
    fun clearSelection() = select(emptyList())
    fun isLocked(ref: LayerRef) = index.layer(ref)?.index?.let { (ref.scope to it) in locked } ?: false
    fun toggleLock(ref: LayerRef) { val k = index.layer(ref)?.index?.let { ref.scope to it } ?: return; if (!locked.add(k)) locked.remove(k); touch() }

    private fun isLive(t: Target) = Targets.raw(index, t) != null

    /** Tap on the canvas: first tap selects the layer, a tap on an already selected layer drills into the shape group. */
    fun tap(x: Double, y: Double, toleranceDoc: Double, additive: Boolean) {
        val hit = ir.sina.tgsstudio.editor.Hit.at(scene(), x, y, toleranceDoc)?.takeUnless { isLocked(it.layer) }
        if (hit == null) { if (!additive) clearSelection(); return }
        val layerT = LayerTarget(hit.layer)
        val shapeT = if (hit.shapePath.isNotEmpty()) ShapeTarget(ShapeRef(hit.layer, hit.shapePath)) else null
        val next: Target = if (shapeT != null && selection.any { it == layerT || (it is ShapeTarget && it.layer == hit.layer) }) shapeT else layerT
        if (additive) select(if (next in selection) selection - next else selection + next) else select(listOf(next))
    }

    /** Document-space box around the selection, or null. */
    fun selectionBox(): Box? {
        val sc = scene()
        var box: Box? = null
        for (t in selection) {
            val b = when (t) { is LayerTarget -> Hit.bounds(sc, t.layer); is ShapeTarget -> Hit.bounds(sc, t.layer, t.ref.path) } ?: continue
            box = box?.union(b) ?: b
        }
        return box
    }

    /** Align selected objects to the composition bounds using their rendered document-space bounds. */
    fun alignSelection(horizontal: String? = null, vertical: String? = null): Boolean {
        if (horizontal == null && vertical == null) return false
        val sc = scene()
        val items = movable().mapNotNull { target ->
            val bounds = when (target) {
                is LayerTarget -> Hit.bounds(sc, target.layer)
                is ShapeTarget -> Hit.bounds(sc, target.layer, target.ref.path)
            } ?: return@mapNotNull null
            target to bounds
        }
        if (items.isEmpty()) return false
        edit("Align selection") {
            items.forEach { (target, bounds) ->
                val dx = when (horizontal) {
                    "left" -> -bounds.minX
                    "center" -> doc.width / 2.0 - bounds.centerX
                    "right" -> doc.width - bounds.maxX
                    else -> 0.0
                }
                val dy = when (vertical) {
                    "top" -> -bounds.minY
                    "middle" -> doc.height / 2.0 - bounds.centerY
                    "bottom" -> doc.height - bounds.maxY
                    else -> 0.0
                }
                TransformOps.translateDoc(index, target, frame, dx, dy)
            }
        }
        return true
    }

    /** Distribute three or more selected objects with equal visual gaps along one axis. */
    fun distributeSelectionHorizontally(): Boolean = distributeSelection(horizontal = true)
    fun distributeSelectionVertically(): Boolean = distributeSelection(horizontal = false)

    private fun distributeSelection(horizontal: Boolean): Boolean {
        val sc = scene()
        val items = movable().mapNotNull { target ->
            val bounds = when (target) {
                is LayerTarget -> Hit.bounds(sc, target.layer)
                is ShapeTarget -> Hit.bounds(sc, target.layer, target.ref.path)
            } ?: return@mapNotNull null
            target to bounds
        }
        if (items.size < 3) return false
        val ordered = if (horizontal) items.sortedBy { it.second.centerX } else items.sortedBy { it.second.centerY }
        val first = ordered.first().second
        val last = ordered.last().second
        val span = if (horizontal) last.maxX - first.minX else last.maxY - first.minY
        val occupied = ordered.sumOf { if (horizontal) it.second.width else it.second.height }
        val gap = (span - occupied).coerceAtLeast(0.0) / (ordered.size - 1)
        var cursor = if (horizontal) first.minX else first.minY
        edit(if (horizontal) "Distribute horizontally" else "Distribute vertically") {
            ordered.forEachIndexed { i, (target, bounds) ->
                val start = if (horizontal) bounds.minX else bounds.minY
                val delta = if (i == 0) 0.0 else cursor - start
                if (horizontal) TransformOps.translateDoc(index, target, frame, delta, 0.0)
                else TransformOps.translateDoc(index, target, frame, 0.0, delta)
                cursor += (if (horizontal) bounds.width else bounds.height) + gap
            }
        }
        return true
    }

    // ---- gestures (one undo step each) -------------------------------------------------------------

    fun beginGesture(label: String) = history.begin(label)
    fun endGesture() { history.commit(); touch() }
    fun cancelGesture() { history.cancel(); touch() }

    private fun movable() = selection.filter { !isLocked(it.layer) }

    fun dragBy(dx: Double, dy: Double) { movable().forEach { TransformOps.translateDoc(index, it, frame, dx, dy) }; touch() }
    fun scaleBy(qx: Double, qy: Double, factor: Double) { movable().forEach { TransformOps.scaleAboutDoc(index, it, frame, qx, qy, factor) }; touch() }
    fun rotateBy(qx: Double, qy: Double, deg: Double) { movable().forEach { TransformOps.rotateAboutDoc(index, it, frame, qx, qy, deg) }; touch() }

    // ---- generic undoable edit ---------------------------------------------------------------------

    fun <T> edit(label: String, block: () -> T): T { val r = history.perform(label, block); touch(); return r }

    fun undo() { if (history.undo()) { revalidate(); touch() } }
    fun redo() { if (history.redo()) { revalidate(); touch() } }
    private fun revalidate() { selection = selection.filter { isLive(it) } }

    // ---- properties --------------------------------------------------------------------------------

    private fun lf(t: Target) = Spaces.localFrame(index, t.layer.scope, frame)

    fun transformOf(t: Target) = TransformOps.read(index, t, lf(t))
    fun setPosition(t: Target, x: Double, y: Double) = edit("Position") { TransformOps.setPosition(index, t, lf(t), x, y) }
    fun setScale(t: Target, sx: Double, sy: Double) = edit("Scale") { TransformOps.setScale(index, t, lf(t), sx, sy) }
    fun setRotation(t: Target, deg: Double) = edit("Rotation") { TransformOps.setRotation(index, t, lf(t), deg) }
    fun setAnchor(t: Target, x: Double, y: Double) = edit("Anchor") { TransformOps.setAnchor(index, t, lf(t), x, y) }
    fun setOpacity(t: Target, percent: Double) = edit("Opacity") { TransformOps.setOpacity(index, t, lf(t), percent) }

    fun fillColor(t: Target) = StyleOps.fillColor(index, t, lf(t))
    fun strokeColor(t: Target) = StyleOps.strokeColor(index, t, lf(t))
    fun strokeWidth(t: Target) = StyleOps.strokeWidth(index, t, lf(t))
    fun gradientOf(t: Target): GradientValues? = StyleOps.gradient(index, t, lf(t))

    fun addGradientFill(t: Target): Boolean {
        var added = false
        edit("Add gradient fill") { group(t)?.let { added = StyleOps.ensureGradient(it) != null } }
        return added
    }

    fun setGradient(t: Target, value: GradientValues) = edit("Gradient fill") {
        StyleOps.firstOf(index, t, ShapeKind.GRADIENT_FILL)?.let { StyleOps.setGradient(it, lf(t), value) }
    }

    fun removeGradientFill(t: Target) = edit("Remove gradient fill") {
        group(t)?.let { StyleOps.remove(it, "gf") }
    }

    private fun group(t: Target): JsonObjOrNull = when (t) {
        is ShapeTarget -> index.shapeRaw(t.ref)?.takeIf { it.string("ty") == "gr" }
        is LayerTarget -> index.layerRaw(t.layer)?.arr("shapes")?.items?.firstOrNull { it is ir.sina.tgsstudio.document.json.JsonObj && it.string("ty") == "gr" } as? ir.sina.tgsstudio.document.json.JsonObj
    }

    fun setFillColor(t: Target, c: Rgba) = edit("Fill color") {
        val item = StyleOps.firstOf(index, t, ShapeKind.FILL) ?: group(t)?.let { StyleOps.ensureFill(it, c) }
        item?.let { StyleOps.setColor(it, lf(t), c) }
    }
    fun setStrokeColor(t: Target, c: Rgba) = edit("Stroke color") {
        val item = StyleOps.firstOf(index, t, ShapeKind.STROKE) ?: group(t)?.let { StyleOps.ensureStroke(it, c) }
        item?.let { StyleOps.setColor(it, lf(t), c) }
    }
    fun setStrokeWidth(t: Target, w: Double) = edit("Stroke width") {
        val item = StyleOps.firstOf(index, t, ShapeKind.STROKE) ?: group(t)?.let { StyleOps.ensureStroke(it) }
        item?.let { StyleOps.setWidth(it, lf(t), w) }
    }
    fun removeStroke(t: Target) = edit("Remove stroke") { group(t)?.let { StyleOps.remove(it, "st") } }
    fun removeFill(t: Target) = edit("Remove fill") { group(t)?.let { StyleOps.remove(it, "fl") } }

    // ---- layers & shapes ---------------------------------------------------------------------------

    fun rename(t: Target, name: String) = edit("Rename") { when (t) { is LayerTarget -> LayerOps.rename(index, t.layer, name); is ShapeTarget -> ShapeOps.rename(index, t.ref, name) } }
    fun setHidden(t: Target, hidden: Boolean) = edit("Visibility") { when (t) { is LayerTarget -> LayerOps.setHidden(index, t.layer, hidden); is ShapeTarget -> ShapeOps.setHidden(index, t.ref, hidden) } }

    fun layerTiming(ref: LayerRef): LayerTiming? {
        val raw = index.layerRaw(ref) ?: return null
        return LayerTiming(raw.double("ip") ?: doc.inPoint, raw.double("op") ?: doc.outPoint, raw.double("st") ?: 0.0, (raw.double("sr") ?: 1.0).coerceAtLeast(0.01))
    }

    fun setLayerTiming(ref: LayerRef, value: LayerTiming) = edit("Layer timing") {
        val raw = index.layerRaw(ref) ?: return@edit
        val start = value.inPoint.coerceAtLeast(0.0)
        val end = value.outPoint.coerceAtLeast(start + 1.0)
        raw.putDouble("ip", start)
        raw.putDouble("op", end)
        raw.putDouble("st", value.startTime)
        raw.putDouble("sr", value.stretch.coerceIn(0.01, 100.0))
    }


    /**
     * Split a layer at the playhead while preserving the animation phase of the second segment.
     */
    fun splitLayer(ref: LayerRef, atFrame: Double = frame): LayerRef? {
        val layers = index.layerArray(ref.scope) ?: return null
        val source = layers.items.getOrNull(ref.index) as? JsonObj ?: return null
        val start = source.double("ip") ?: doc.inPoint
        val end = source.double("op") ?: doc.outPoint
        val split = atFrame.coerceIn(start + 1.0, end - 1.0)
        if (split <= start || split >= end) return null
        val copy = source.deepCopy() as JsonObj
        edit("Split layer") {
            source.putDouble("op", split)
            copy.putDouble("ip", split)
            copy.putDouble("op", end)
            // Keep the original start time and stretch so the duplicated segment continues the same animation phase.
            copy.putInt("ind", Creator.nextIndex(layers))
            copy.putString("nm", (source.string("nm") ?: "Layer") + " - 2")
            layers.items.add(ref.index, copy)
        }
        val result = LayerRef(ref.scope, ref.index)
        select(listOf(LayerTarget(result)))
        return result
    }

    /**
     * Creates an Add-mode mask from a direct child path in the same shape layer.
     * Restricting the source to a top-level path avoids silently losing nested group transforms.
     */
    fun addMaskFromPath(target: LayerRef, source: ShapeRef, inverted: Boolean = false): Boolean {
        if (source.layer != target || source.path.size != 1) return false
        val shape = index.shapeRaw(source)?.takeIf { it.string("ty") == "sh" } ?: return false
        val path = PathEdit.read(shape, Spaces.localFrame(index, target.scope, frame)) ?: return false
        var added = false
        edit("Create mask") {
            val layer = index.layerRaw(target) ?: return@edit
            val masks = layer.arr("masksProperties") ?: JsonArr().also { layer["masksProperties"] = it }
            val mask = obj(
                "mode" to str("a"),
                "pt" to staticPathProp(path),
                "o" to staticProp(100.0),
                "x" to staticProp(0.0),
                "nm" to str("Mask ${masks.items.size + 1}"),
            ).also { it.putBool("inv", inverted) }
            masks.items.add(mask)
            added = true
        }
        return added
    }

    /** Inversion flags for masks on a layer. Mask geometry stays in the original Lottie document tree. */
    fun maskInversions(ref: LayerRef): List<Boolean> =
        index.layerRaw(ref)?.arr("masksProperties")?.items?.filterIsInstance<JsonObj>()?.map { it.bool("inv") ?: false }.orEmpty()

    fun setMaskInverted(ref: LayerRef, maskIndex: Int, inverted: Boolean) = edit("Invert mask") {
        val mask = index.layerRaw(ref)?.arr("masksProperties")?.items?.getOrNull(maskIndex) as? JsonObj ?: return@edit
        mask.putBool("inv", inverted)
    }

    fun removeMask(ref: LayerRef, maskIndex: Int) = edit("Remove mask") {
        val masks = index.layerRaw(ref)?.arr("masksProperties") ?: return@edit
        if (maskIndex in masks.items.indices) masks.items.removeAt(maskIndex)
    }

    fun deleteSelection(): Boolean {
        if (selection.isEmpty()) return false
        // delete deepest/last first so earlier refs stay valid
        val targets = selection
        edit("Delete") {
            targets.filterIsInstance<ShapeTarget>().sortedWith(compareBy({ it.ref.layer.scope ?: "" }, { it.ref.layer.index }, { it.ref.path.size }, { it.ref.path.lastOrNull() ?: 0 })).asReversed()
                .forEach { ShapeOps.delete(index, it.ref) }
            targets.filterIsInstance<LayerTarget>().sortedWith(compareBy({ it.layer.scope ?: "" }, { it.layer.index })).asReversed().forEach { ParentOps.releaseChildren(index, it.layer, frame); LayerOps.delete(index, it.layer) }
        }
        select(emptyList()); return true
    }

    /** Replaces vector artwork and keeps the destination layer's own properties. */
    fun replaceLayerContents(target: LayerRef, source: LayerRef): OpResult {
        val result = edit("Replace layer contents") { LayerOps.replaceContents(index, target, source) }
        if (result is OpResult.Ok) select(listOf(LayerTarget(target)))
        return result
    }

    fun duplicateSelection() {
        val t = selection.singleOrNull() ?: return
        var next: Target? = null
        edit("Duplicate") {
            val r = when (t) { is LayerTarget -> LayerOps.duplicate(index, t.layer); is ShapeTarget -> ShapeOps.duplicate(index, t.ref) }
            (r as? OpResult.Ok)?.ref?.let { ref -> next = when (ref) { is LayerRef -> LayerTarget(ref); is ShapeRef -> ShapeTarget(ref); else -> null } }
        }
        select(listOfNotNull(next ?: t))
    }

    fun move(t: Target, delta: Int): Target {
        var out: Target = t
        edit("Reorder") {
            val r = when (t) { is LayerTarget -> LayerOps.reorder(index, t.layer, t.layer.index + delta); is ShapeTarget -> ShapeOps.move(index, t.ref, delta) }
            (r as? OpResult.Ok)?.ref?.let { ref -> out = when (ref) { is LayerRef -> LayerTarget(ref); is ShapeRef -> ShapeTarget(ref); else -> t } }
        }
        select(listOf(out)); return out
    }

    fun groupSelection(): OpResult {
        val refs = selection.filterIsInstance<ShapeTarget>().map { it.ref }
        if (refs.isEmpty()) return OpResult.Refused(OpResult.Reason.INVALID)
        val r = edit("Group") { ShapeOps.group(index, refs) }
        ((r as? OpResult.Ok)?.ref as? ShapeRef)?.let { select(listOf(ShapeTarget(it))) }
        return r
    }

    fun ungroupSelection(): OpResult {
        val t = selection.singleOrNull() as? ShapeTarget ?: return OpResult.Refused(OpResult.Reason.INVALID)
        val r = edit("Ungroup") { ShapeOps.ungroup(index, t.ref) }
        if (r is OpResult.Ok) select(emptyList())
        return r
    }

    fun moveShapeInto(t: ShapeTarget, group: ShapeTarget): OpResult {
        val r = edit("Move into group") { ShapeOps.moveInto(index, t.ref, group.ref) }
        if (r is OpResult.Ok) select(emptyList()); return r
    }

    fun moveShapeOut(t: ShapeTarget): OpResult { val r = edit("Move out of group") { ShapeOps.moveOut(index, t.ref) }; if (r is OpResult.Ok) select(emptyList()); return r }

    // ---- parent / child ----------------------------------------------------------------------------

    fun parentOf(ref: LayerRef): LayerRef? = ParentOps.parentOf(index, ref)
    fun childrenOf(ref: LayerRef): List<LayerRef> = ParentOps.childrenOf(index, ref)
    fun parentCandidates(children: List<LayerRef>): List<LayerRef> = ParentOps.candidates(index, children)

    /** Links [children] to [parent] (null = detach). They keep their on-screen position when possible. A refusal changes nothing. */
    fun setParent(children: List<LayerRef>, parent: LayerRef?, keepPosition: Boolean = true): ParentResult {
        ParentOps.validate(index, children, parent)?.let { return ParentResult(0, 0, emptyList(), it) }
        var result = ParentResult(0, 0, emptyList())
        edit(if (parent == null) "Detach" else "Parent") { result = ParentOps.setParent(index, children, parent, frame, keepPosition) }
        return result
    }

    fun createNullParent(children: List<LayerRef>): LayerRef? {
        var created: LayerRef? = null
        var shifted: List<LayerRef> = children
        edit("Create parent") { ParentOps.createNullParent(index, children, frame)?.let { (n, kids, _) -> created = n; shifted = kids } }
        select(shifted.map { LayerTarget(it) })
        return created
    }

    // ---- creating elements -------------------------------------------------------------------------

    private fun addLayer(name: String, cx: Double, cy: Double, color: Rgba?, vararg groups: ir.sina.tgsstudio.document.json.JsonObj): LayerTarget? {
        var ref: LayerRef? = null
        edit("Add $name") {
            val layers = doc.root.arr("layers")
            val layer = Creator.shapeLayer(doc, layers, name, cx, cy, *groups)
            ref = Creator.insertLayer(doc, null, layer)
        }
        return ref?.let { LayerTarget(it).also { t -> select(listOf(t)) } }
    }

    fun addRect(cx: Double, cy: Double, w: Double, h: Double, color: Rgba = Creator.DEFAULT_FILL, roundness: Double = 0.0) =
        addLayer("Rectangle", cx, cy, color, Creator.group("Rectangle", Creator.rectItem(w, h, roundness), Creator.fill(color)))
    fun addEllipse(cx: Double, cy: Double, w: Double, h: Double, color: Rgba = Creator.DEFAULT_FILL) =
        addLayer("Ellipse", cx, cy, color, Creator.group("Ellipse", Creator.ellipseItem(w, h), Creator.fill(color)))
    fun addStar(cx: Double, cy: Double, radius: Double, points: Int = 5, color: Rgba = Creator.DEFAULT_FILL) =
        addLayer("Star", cx, cy, color, Creator.group("Star", Creator.starPath(points, radius, radius * 0.45), Creator.fill(color)))
    fun addPolygon(cx: Double, cy: Double, radius: Double, points: Int = 6, color: Rgba = Creator.DEFAULT_FILL) =
        addLayer("Polygon", cx, cy, color, Creator.group("Polygon", Creator.polygonPath(points, radius), Creator.fill(color)))

    /** Adds a real empty vector group as a selectable shape layer. */
    fun addEmptyGroup(cx: Double = doc.width / 2.0, cy: Double = doc.height / 2.0) =
        addLayer("Empty Group", cx, cy, null, Creator.group("Empty Group"))

    /** A freeform path drawn in document coordinates (e.g. by the pen tool). */
    fun addFreeform(b: Bezier, color: Rgba = Creator.DEFAULT_FILL, filled: Boolean = b.closed, strokeWidth: Double = 6.0): LayerTarget? {
        if (b.count < 2) return null
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (i in 0 until b.count) { minX = minOf(minX, b.v[2 * i]); maxX = maxOf(maxX, b.v[2 * i]); minY = minOf(minY, b.v[2 * i + 1]); maxY = maxOf(maxY, b.v[2 * i + 1]) }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        val local = PathOps.translateAll(b, -cx, -cy)
        val items = mutableListOf(Creator.pathItem(local))
        if (filled) items += Creator.fill(color) else items += Creator.stroke(color, strokeWidth)
        return addLayer("Path", cx, cy, color, Creator.group("Path", *items.toTypedArray()))
    }

    /** Layer name used for strokes made with the pen, so the eraser only removes drawn strokes. */
    private val penName = "Pen stroke"

    /**
     * A freehand stroke through [points] (document coordinates) as an editable path layer: round caps, [widthPx],
     * [opacityPct]. With [writeOn] the path also gets Trim Paths so it is drawn gradually from start to end.
     */
    fun addStroke(points: List<Pair<Double, Double>>, color: Rgba, widthPx: Double, opacityPct: Double = 100.0, writeOn: WriteOn? = null): LayerTarget? {
        val own = !history.isGestureOpen                       // one stroke, one undo step (including its write-on animation)
        if (own) history.begin("Draw")
        try { return addStrokeSteps(points, color, widthPx, opacityPct, writeOn) } finally { if (own) { history.commit(); touch() } }
    }

    private fun addStrokeSteps(points: List<Pair<Double, Double>>, color: Rgba, widthPx: Double, opacityPct: Double, writeOn: WriteOn?): LayerTarget? {
        val b = Freehand.fit(points) ?: return null
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (i in 0 until b.count) { minX = minOf(minX, b.v[2 * i]); maxX = maxOf(maxX, b.v[2 * i]); minY = minOf(minY, b.v[2 * i + 1]); maxY = maxOf(maxY, b.v[2 * i + 1]) }
        val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
        val stroke = Creator.stroke(color, widthPx.coerceIn(0.5, 200.0)).also { StyleOps.setOpacity(it, 0.0, opacityPct) }
        val group = Creator.group(penName, Creator.pathItem(PathOps.translateAll(b, -cx, -cy), "Stroke"), stroke)
        val layer = addLayer(penName, cx, cy, color, group) ?: return null
        if (writeOn != null) animateTrim(layer, TrimAnim.DRAW_ON, writeOn.startFrame, writeOn.frames, writeOn.easing)
        return layer
    }

    /** Eraser: removes the topmost pen stroke under (x, y). Call inside [beginGesture]/[endGesture] while dragging. */
    fun eraseStrokeAt(x: Double, y: Double, toleranceDoc: Double): Boolean {
        val hit = Hit.at(scene(), x, y, toleranceDoc) ?: return false
        val layer = index.layer(hit.layer) ?: return false
        if (!layer.name.startsWith(penName)) return false
        ParentOps.releaseChildren(index, hit.layer, frame)
        LayerOps.delete(index, hit.layer)
        selection = emptyList(); touch()
        return true
    }

    fun addSvg(svg: String): SvgImportResult? {
        val r = SvgImport.parse(svg)
        if (r.groups.isEmpty()) return r
        val w = doc.width / 2.0; val h = doc.height / 2.0
        addLayer("SVG", w, h, null, *r.groups.toTypedArray())
        return r
    }

    /** Adds text as hand-drawn centerline strokes ([lines] extracted from a rendered mask by [Skeleton]). */
    fun addCenterlineText(lines: Centerline, scale: Double, color: Rgba, name: String = "Text"): LayerTarget? {
        val (g, _) = CenterlineShape.group(lines, scale, color, name) ?: return null
        return addLayer(name, doc.width / 2.0, doc.height / 2.0, color, g)
    }

    /** Adds imported/system font glyphs as filled, editable vector contours. */
    fun addOutlinedText(contours: List<Bezier>, scale: Double, color: Rgba, name: String = "Text"): LayerTarget? {
        if (contours.isEmpty()) return null
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        contours.forEach { path ->
            for (i in 0 until path.count) {
                minX = minOf(minX, path.v[i * 2]); maxX = maxOf(maxX, path.v[i * 2])
                minY = minOf(minY, path.v[i * 2 + 1]); maxY = maxOf(maxY, path.v[i * 2 + 1])
            }
        }
        val cx = (minX + maxX) / 2.0
        val cy = (minY + maxY) / 2.0
        val items = contours.mapIndexed { index, path ->
            val vertices = DoubleArray(path.v.size) { i ->
                (path.v[i] - if (i % 2 == 0) cx else cy) * scale
            }
            Creator.pathItem(Bezier(true, vertices, DoubleArray(vertices.size), DoubleArray(vertices.size)), "Glyph contour ${index + 1}")
        } + Creator.fill(color)
        val group = Creator.group(name, *items.toTypedArray())
        return addLayer(name, doc.width / 2.0, doc.height / 2.0, color, group)
    }

    // ---- path (node) editing -----------------------------------------------------------------------

    /** The `sh` item the node editor works on for the current selection (a path, or a group's first path). */
    fun pathShape(): ShapeRef? {
        val t = selection.singleOrNull() ?: return null
        return when (t) {
            is ShapeTarget -> {
                val raw = index.shapeRaw(t.ref) ?: return null
                when (raw.string("ty")) {
                    "sh" -> t.ref
                    "gr" -> raw.arr("it")?.items?.indexOfFirst { it is ir.sina.tgsstudio.document.json.JsonObj && it.string("ty") == "sh" }?.takeIf { it >= 0 }?.let { ShapeRef(t.ref.layer, t.ref.path + it) }
                    else -> null
                }
            }
            is LayerTarget -> null
        }
    }

    /** True when the selection holds a rectangle/ellipse/star that can be converted into an editable path. */
    fun convertibleShape(): ShapeRef? {
        val t = selection.singleOrNull() as? ShapeTarget ?: return null
        val raw = index.shapeRaw(t.ref) ?: return null
        if (raw.string("ty") != "gr") return if (raw.string("ty") in setOf("rc", "el", "sr")) t.ref else null
        val i = raw.arr("it")?.items?.indexOfFirst { it is ir.sina.tgsstudio.document.json.JsonObj && it.string("ty") in setOf("rc", "el", "sr") } ?: -1
        return if (i >= 0) ShapeRef(t.ref.layer, t.ref.path + i) else null
    }

    fun convertToPath(): Boolean {
        val ref = convertibleShape() ?: return false
        edit("Convert to path") { index.shapeArray(ref)?.let { PathEdit.convertToPath(it, ref.path.last(), Spaces.localFrame(index, ref.layer.scope, frame)) } }
        return true
    }

    fun pathBezier(ref: ShapeRef): Bezier? = index.shapeRaw(ref)?.let { PathEdit.read(it, Spaces.localFrame(index, ref.layer.scope, frame)) }

    /** Matrix from the path's coordinates to document space. */
    fun pathToDoc(ref: ShapeRef): ir.sina.tgsstudio.renderer.Affine {
        val parent = ShapeTarget(ref)
        return Spaces.parentToDoc(index, parent, frame)
    }

    fun writePath(ref: ShapeRef, b: Bezier) { index.shapeRaw(ref)?.let { PathEdit.write(it, Spaces.localFrame(index, ref.layer.scope, frame), b) }; touch() }

    fun structuralPathEdit(ref: ShapeRef, label: String, f: (Bezier) -> Bezier?): Boolean {
        var ok = false
        edit(label) { ok = index.shapeRaw(ref)?.let { PathEdit.structural(it, Spaces.localFrame(index, ref.layer.scope, frame), f) } ?: false }
        return ok
    }

    // ---- effects -----------------------------------------------------------------------------------

    /** Applies [kind] to every selected layer (shape targets use their layer). The selection follows the layers, whose refs may shift. */
    fun applyEffect(kind: EffectKind, params: EffectParams): EffectResult {
        val layers = selection.map { LayerTarget(it.layer) }.distinct()
        var result = EffectResult(layers, emptySet(), 0)
        edit("Effect: ${kind.title}") { result = EffectEngine.apply(this, layers, kind, params) }
        if (result.applied > 0) select(result.targets)
        return result
    }

    fun effectNullsOf(layer: LayerRef): List<LayerRef> = EffectEngine.effectNullsOf(index, layer)

    fun removeEffectNull(nullRef: LayerRef): Boolean {
        var ok = false
        edit("Remove effect") { ok = EffectEngine.removeEffectNull(this, nullRef) }
        select(emptyList())
        return ok
    }

    // ---- trim paths --------------------------------------------------------------------------------

    private fun trimGroups(t: Target): List<ir.sina.tgsstudio.document.json.JsonObj> = when (t) {
        is LayerTarget -> index.layerRaw(t.layer)?.let { TrimOps.groupsOfLayer(it) }.orEmpty()
        is ShapeTarget -> {
            val raw = index.shapeRaw(t.ref)
            val group = if (raw?.string("ty") == "gr") raw else t.ref.parent?.let { index.shapeRaw(it) }
            listOfNotNull(group)
        }
    }

    fun canTrim(t: Target): Boolean = trimGroups(t).isNotEmpty()
    fun hasTrim(t: Target): Boolean = trimGroups(t).any { TrimOps.find(it) != null }
    fun trimItem(t: Target): ir.sina.tgsstudio.document.json.JsonObj? = trimGroups(t).firstNotNullOfOrNull { TrimOps.find(it) }

    fun addTrim(t: Target): Boolean { var ok = false; edit("Add trim paths") { ok = trimGroups(t).count { TrimOps.ensure(it) != null } > 0 }; return ok }
    fun removeTrim(t: Target) = edit("Remove trim paths") { trimGroups(t).forEach { TrimOps.remove(it) } }

    /** Adds trim paths where missing and plays [mode] over [frames] frames starting at [startFrame] (document frames). */
    fun animateTrim(t: Target, mode: TrimAnim, startFrame: Double = frame, frames: Double, easing: Easing = Easing.EASE_IN_OUT): Boolean {
        var ok = false
        edit("Trim animation") {
            val lf = Spaces.localFrame(index, t.layer.scope, startFrame)
            for (g in trimGroups(t)) { val trim = TrimOps.ensure(g) ?: continue; TrimOps.animate(trim, mode, lf, frames, easing); ok = true }
        }
        return ok
    }

    // ---- keyframes ---------------------------------------------------------------------------------

    fun animatedProps(t: Target): List<PropEntry> = AnimatedProps.of(index, t)

    fun addKeyframe(p: PropEntry, scope: String? = null) = edit("Add keyframe") { KeyframeOps.addAt(p.prop, Spaces.localFrame(index, scope, frame), p.kind == PropKind.PATH) }
    fun removeKeyframe(p: PropEntry, i: Int) = edit("Delete keyframe") { KeyframeOps.remove(p.prop, i) }
    /** Deletes a set of keyframes from one property; callers can wrap multiple rows in one gesture. */
    fun removeKeyframes(p: PropEntry, indices: List<Int>) = edit("Delete keyframes") {
        indices.distinct().sortedDescending().forEach { i -> KeyframeOps.remove(p.prop, i) }
    }
    fun moveKeyframe(p: PropEntry, i: Int, toFrame: Double) = edit("Move keyframe") { KeyframeOps.move(p.prop, i, toFrame.coerceIn(doc.inPoint, doc.outPoint)) }
    fun setKeyframeValue(p: PropEntry, i: Int, v: DoubleArray) = edit("Keyframe value") { KeyframeOps.setValues(p.prop, i, v) }
    fun setInterpolation(p: PropEntry, i: Int, m: Interp) = edit("Interpolation") { KeyframeOps.setInterpolation(p.prop, i, m) }
    fun setEasing(p: PropEntry, i: Int, e: Easing) = edit("Easing") { KeyframeOps.setEasing(p.prop, i, e) }
    fun pasteKeyframe(p: PropEntry, kf: ir.sina.tgsstudio.document.json.JsonObj) = edit("Paste keyframe") { KeyframeOps.paste(p.prop, kf, frame) }
    fun valueAt(p: PropEntry): DoubleArray? = PropEval.values(p.prop, frame)

    fun setFrameRate(fps: Double) = edit("Frame rate") { doc.frameRate = fps.coerceIn(1.0, 120.0) }

    /** Changes the composition work area without deleting keyframes outside the new range. */
    fun setWorkArea(startFrame: Double, endFrame: Double) = edit("Work area") {
        val start = startFrame.coerceAtLeast(0.0)
        val end = endFrame.coerceAtLeast(start + 1.0)
        doc.inPoint = start
        doc.outPoint = end
        frame = frame.coerceIn(start, end - 1.0)
    }
}

private typealias JsonObjOrNull = ir.sina.tgsstudio.document.json.JsonObj?
