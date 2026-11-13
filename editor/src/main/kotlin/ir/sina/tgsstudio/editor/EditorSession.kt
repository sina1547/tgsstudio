package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.DocIndex
import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.ShapeKind
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

    fun deleteSelection(): Boolean {
        if (selection.isEmpty()) return false
        // delete deepest/last first so earlier refs stay valid
        val targets = selection
        edit("Delete") {
            targets.filterIsInstance<ShapeTarget>().sortedWith(compareBy({ it.ref.layer.scope ?: "" }, { it.ref.layer.index }, { it.ref.path.size }, { it.ref.path.lastOrNull() ?: 0 })).asReversed()
                .forEach { ShapeOps.delete(index, it.ref) }
            targets.filterIsInstance<LayerTarget>().sortedWith(compareBy({ it.layer.scope ?: "" }, { it.layer.index })).asReversed().forEach { LayerOps.delete(index, it.layer) }
        }
        select(emptyList()); return true
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
        addLayer("Star", cx, cy, color, Creator.group("Star", Creator.starItem(points, radius, radius * 0.45), Creator.fill(color)))
    fun addPolygon(cx: Double, cy: Double, radius: Double, points: Int = 6, color: Rgba = Creator.DEFAULT_FILL) =
        addLayer("Polygon", cx, cy, color, Creator.group("Polygon", Creator.polygonItem(points, radius), Creator.fill(color)))

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

    // ---- keyframes ---------------------------------------------------------------------------------

    fun animatedProps(t: Target): List<PropEntry> = AnimatedProps.of(index, t)

    fun addKeyframe(p: PropEntry, scope: String? = null) = edit("Add keyframe") { KeyframeOps.addAt(p.prop, Spaces.localFrame(index, scope, frame), p.kind == PropKind.PATH) }
    fun removeKeyframe(p: PropEntry, i: Int) = edit("Delete keyframe") { KeyframeOps.remove(p.prop, i) }
    fun moveKeyframe(p: PropEntry, i: Int, toFrame: Double) = edit("Move keyframe") { KeyframeOps.move(p.prop, i, toFrame.coerceIn(doc.inPoint, doc.outPoint)) }
    fun setKeyframeValue(p: PropEntry, i: Int, v: DoubleArray) = edit("Keyframe value") { KeyframeOps.setValues(p.prop, i, v) }
    fun setInterpolation(p: PropEntry, i: Int, m: Interp) = edit("Interpolation") { KeyframeOps.setInterpolation(p.prop, i, m) }
    fun setEasing(p: PropEntry, i: Int, e: Easing) = edit("Easing") { KeyframeOps.setEasing(p.prop, i, e) }
    fun pasteKeyframe(p: PropEntry, kf: ir.sina.tgsstudio.document.json.JsonObj) = edit("Paste keyframe") { KeyframeOps.paste(p.prop, kf, frame) }
    fun valueAt(p: PropEntry): DoubleArray? = PropEval.values(p.prop, frame)

    fun setFrameRate(fps: Double) = edit("Frame rate") { doc.frameRate = fps }
}

private typealias JsonObjOrNull = ir.sina.tgsstudio.document.json.JsonObj?
