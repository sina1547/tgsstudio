package ir.sina.tgsstudio.export

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import ir.sina.tgsstudio.editor.*
import ir.sina.tgsstudio.editor.Target
import ir.sina.tgsstudio.parser.TgsCodec
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.Rgba
import java.io.File
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Writes real TGS files built through the editor's own API (exactly what a user's taps produce) plus the expectations
 * `tools/rlottie_check.py` verifies by rendering them with rlottie, the renderer family Telegram's players are based on.
 * The checks are analytic (positions, opacity, rotation, trim lengths) and a cross-render comparison against this
 * project's own renderer (via SVG), so a divergence in either direction is caught.
 */
class RlottieFixtureTest {
    private val dir = File(System.getProperty("rlottie.fixtures") ?: "build/rlottie-fixtures").also { it.mkdirs() }

    private fun fresh() = EditorSession(Creator.blankDocument("fixture"))

    // ---- expectation helpers ------------------------------------------------------------------------------------
    private fun centroid(frame: Int, x: Double, y: Double, tol: Double = 2.5) = """{"type":"centroid","frame":$frame,"x":$x,"y":$y,"tol":$tol}"""
    private fun alphaMax(frame: Int, v: Double, tol: Double = 4.0) = """{"type":"alpha_max","frame":$frame,"value":$v,"tol":$tol}"""
    private fun bbox(frame: Int, w: Double, h: Double, tol: Double = 4.0) = """{"type":"bbox","frame":$frame,"w":$w,"h":$h,"tol":$tol}"""
    private fun ink(frame: Int, px: Double, relTol: Double) = """{"type":"ink","frame":$frame,"value":$px,"rel_tol":$relTol}"""
    private fun inkRatio(frame: Int, ref: Int, v: Double, tol: Double) = """{"type":"ink_ratio","frame":$frame,"ref":$ref,"value":$v,"tol":$tol}"""
    private fun moved(a: Int, b: Int, d: Double) = """{"type":"moved","frame_a":$a,"frame_b":$b,"min_dist":$d}"""

    private fun write(name: String, doc: LottieDocument, checks: List<String>, iouFrames: List<Int> = emptyList(), minIou: Double = 0.9) {
        File(dir, "$name.tgs").writeBytes(Exporter.tgs(doc))
        iouFrames.forEach { f -> File(dir, "$name.f$f.svg").writeText(Exporter.svgFrame(TgsPrepare.repaired(doc).first, f.toDouble())) }
        File(dir, "$name.expect.json").writeText("""{"checks":[${checks.joinToString(",")}],"iou_frames":$iouFrames,"min_iou":$minIou}""")
    }

    private fun EditorSession.key(t: Target, key: String, depth: Int = 0): PropEntry = animatedProps(t).first { it.key == key && it.depth == depth }

    /** Eased progress of cubic-bezier(x1,y1,x2,y2) at x = p, for expected values. */
    private fun ease(p: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        fun b(u: Double, a: Double, c: Double): Double { val w = 1 - u; return 3 * w * w * u * a + 3 * w * u * u * c + u * u * u }
        var lo = 0.0; var hi = 1.0
        repeat(50) { val m = (lo + hi) / 2; if (b(m, x1, x2) < p) lo = m else hi = m }
        return b((lo + hi) / 2, y1, y2)
    }

    // ---- fixtures ------------------------------------------------------------------------------------------------

    @Test fun movement() {
        run { // linear
            val s = fresh(); val t = s.addRect(256.0, 256.0, 100.0, 100.0)!!
            s.frame = 0.0; s.addKeyframe(s.key(t, "p")); s.frame = 90.0; s.setPosition(t, 356.0, 256.0)
            write("move_linear", s.doc, listOf(centroid(0, 256.0, 256.0), centroid(45, 306.0, 256.0), centroid(90, 356.0, 256.0), centroid(150, 356.0, 256.0)), listOf(0, 30, 60, 90))
        }
        run { // ease in/out on the first keyframe
            val s = fresh(); val t = s.addRect(256.0, 256.0, 100.0, 100.0)!!
            s.frame = 0.0; val p = s.key(t, "p"); s.addKeyframe(p); s.frame = 90.0; s.setPosition(t, 356.0, 256.0)
            s.setInterpolation(p, 0, Interp.BEZIER)
            val x = { f: Int -> 256.0 + 100.0 * ease(f / 90.0, 0.333, 0.0, 0.667, 1.0) }
            write("move_ease", s.doc, listOf(centroid(0, 256.0, 256.0), centroid(22, x(22), 256.0), centroid(45, x(45), 256.0), centroid(68, x(68), 256.0), centroid(90, 356.0, 256.0)), listOf(0, 22, 45, 68, 90))
        }
        run { // hold
            val s = fresh(); val t = s.addRect(256.0, 256.0, 100.0, 100.0)!!
            s.frame = 0.0; val p = s.key(t, "p"); s.addKeyframe(p); s.frame = 90.0; s.setPosition(t, 356.0, 256.0)
            s.setInterpolation(p, 0, Interp.HOLD)
            write("move_hold", s.doc, listOf(centroid(45, 256.0, 256.0), centroid(89, 256.0, 256.0), centroid(90, 356.0, 256.0)), listOf(0, 45, 89, 90))
        }
        run { // three keyframes, mixed interpolation
            val s = fresh(); val t = s.addRect(256.0, 256.0, 100.0, 100.0)!!
            s.frame = 0.0; val p = s.key(t, "p"); s.addKeyframe(p)
            s.frame = 60.0; s.setPosition(t, 356.0, 256.0); s.frame = 120.0; s.setPosition(t, 356.0, 356.0)
            s.setInterpolation(p, 0, Interp.BEZIER)
            val x30 = 256.0 + 100.0 * ease(0.5, 0.333, 0.0, 0.667, 1.0)
            write("move_mixed", s.doc, listOf(centroid(0, 256.0, 256.0), centroid(30, x30, 256.0), centroid(60, 356.0, 256.0), centroid(90, 356.0, 306.0), centroid(120, 356.0, 356.0)), listOf(0, 30, 60, 90, 120))
        }
        run { // the flow that used to break: ease on the last keyframe, ease on the first, then key a later frame
            val s = fresh(); val t = s.addRect(256.0, 256.0, 100.0, 100.0)!!
            s.frame = 0.0; val p = s.key(t, "p"); s.addKeyframe(p); s.frame = 90.0; s.setPosition(t, 356.0, 256.0)
            s.setInterpolation(p, 1, Interp.BEZIER)
            s.setInterpolation(p, 0, Interp.BEZIER)
            s.frame = 150.0; s.setPosition(t, 300.0, 300.0)
            write("move_ease_flow", s.doc, listOf(centroid(0, 256.0, 256.0), centroid(90, 356.0, 256.0), centroid(120, 328.0, 278.0), centroid(150, 300.0, 300.0)), listOf(0, 45, 90, 120, 150))
        }
    }

    @Test fun otherProperties() {
        run { // fade in (a failed opacity property makes the layer invisible in rlottie)
            val s = fresh(); val t = s.addRect(256.0, 256.0, 100.0, 100.0, Rgba(1.0, 1.0, 1.0, 1.0))!!
            s.frame = 0.0; val o = s.key(t, "o"); s.setOpacity(t, 0.0); s.addKeyframe(o); s.frame = 90.0; s.setOpacity(t, 100.0)
            write("fade_in", s.doc, listOf(alphaMax(0, 0.0, 3.0), alphaMax(45, 127.5), alphaMax(90, 255.0, 2.0)), listOf(45, 90))
        }
        run { // rotation
            val s = fresh(); val t = s.addRect(256.0, 256.0, 200.0, 100.0)!!
            s.frame = 0.0; s.addKeyframe(s.key(t, "r")); s.frame = 90.0; s.setRotation(t, 90.0)
            write("rotate", s.doc, listOf(bbox(0, 200.0, 100.0), bbox(45, 212.1, 212.1, 5.0), bbox(90, 100.0, 200.0)), listOf(0, 30, 45, 90))
        }
        run { // scale
            val s = fresh(); val t = s.addRect(256.0, 256.0, 200.0, 200.0)!!
            s.frame = 0.0; s.addKeyframe(s.key(t, "s")); s.frame = 90.0; s.setScale(t, 50.0, 50.0)
            write("scale", s.doc, listOf(inkRatio(90, 0, 0.25, 0.03), centroid(90, 256.0, 256.0)), listOf(0, 45, 90))
        }
        run { // path morph: a vertex of a filled triangle moves, doubling its area
            val s = fresh()
            val b = Bezier(true, doubleArrayOf(0.0, 0.0, 100.0, 0.0, 100.0, 100.0), DoubleArray(6), DoubleArray(6))
            val t = s.addFreeform(b)!!
            val g = ShapeTarget(ShapeRef(t.layer, listOf(0)))
            val ref = ShapeRef(t.layer, listOf(0, 0))
            s.frame = 0.0; s.edit("k") { KeyframeOps.addAt(s.index.shapeRaw(ref)!!.obj("ks")!!, 0.0, isPath = true) }
            s.frame = 90.0; val cur = s.pathBezier(ref)!!; s.writePath(ref, PathOps.moveVertex(cur, 2, cur.v[4], cur.v[5] + 100.0))
            write("path_morph", s.doc, listOf(inkRatio(90, 0, 2.0, 0.12)), listOf(0, 45, 90))
            check(g.layer == t.layer)
        }
    }

    @Test fun starsAndPolygonsArePlainPaths() {
        for ((name, make) in listOf<Pair<String, (EditorSession) -> Unit>>("star" to { s -> s.addStar(256.0, 256.0, 100.0) }, "polygon" to { s -> s.addPolygon(256.0, 256.0, 100.0) })) {
            val s = fresh(); make(s)
            write("static_$name", s.doc, listOf(centroid(0, 256.0, 256.0, 6.0), ink(0, if (name == "star") 17000.0 else 26000.0, 0.25)), listOf(0))
        }
    }

    @Test fun pensAndText() {
        val s = fresh()
        val open = Bezier(false, doubleArrayOf(100.0, 300.0, 250.0, 150.0, 400.0, 300.0), doubleArrayOf(0.0, 0.0, -40.0, 0.0, 0.0, 0.0), doubleArrayOf(40.0, 0.0, 40.0, 0.0, 0.0, 0.0))
        val t = s.addFreeform(open, filled = false, strokeWidth = 12.0)!!
        s.frame = 0.0; s.addKeyframe(s.key(t, "p")); s.frame = 90.0; s.setPosition(t, 336.0, 256.0)
        write("pen_open_move", s.doc, listOf(moved(0, 90, 70.0), moved(0, 45, 30.0)), listOf(0, 45, 90), 0.85)
    }

    @Test fun parenting() {
        run { // a Null at (256,256) rotates 0 -> 90 and carries a child that sits at (356,256): the child orbits the null
            val s = fresh(); val kid = s.addRect(356.0, 256.0, 80.0, 50.0)!!
            val nullRef = s.createNullParent(listOf(kid.layer))!!
            // createNullParent centres the null on the child itself; move the pivot to (256,256) the way a user would: re-create at the child's offset
            val nt = LayerTarget(nullRef)
            s.edit("pivot") {
                val ks = s.index.layerRaw(nullRef)!!.obj("ks")!!
                ks["p"] = staticProp(256.0, 256.0, 0.0); ks["a"] = staticProp(256.0, 256.0, 0.0)
            }
            s.frame = 0.0; s.addKeyframe(s.key(nt, "r")); s.frame = 90.0; s.setRotation(nt, 90.0)
            val r = 100.0
            write("parent_null_orbit", s.doc, listOf(
                centroid(0, 356.0, 256.0), centroid(45, 256.0 + r * Math.cos(PI / 4), 256.0 + r * Math.sin(PI / 4), 3.0), centroid(90, 256.0, 356.0), bbox(90, 50.0, 80.0)), listOf(0, 30, 45, 60, 90))
        }
        run { // a visible parent that moves, scales and turns; the child keeps its own look but follows
            val s = fresh()
            val kid0 = s.addRect(350.0, 300.0, 60.0, 40.0)!!; s.rename(kid0, "kid"); s.setRotation(kid0, 15.0)
            val par0 = s.addRect(200.0, 200.0, 100.0, 70.0)!!; s.rename(par0, "par"); s.setScale(par0, 130.0, 130.0); s.setRotation(par0, 20.0)
            fun ref(n: String) = LayerRef(null, s.doc.layers.indexOfFirst { it.name == n })
            val r = s.setParent(listOf(ref("kid")), ref("par"))
            check(r.keptPosition == 1) { "expected position to be kept: $r" }
            val parent = LayerTarget(ref("par"))
            s.frame = 0.0
            for (k in listOf("p", "s", "r")) s.addKeyframe(s.key(parent, k))
            s.frame = 90.0; s.setPosition(parent, 300.0, 250.0); s.setScale(parent, 80.0, 80.0); s.setRotation(parent, 110.0)
            write("parent_follows", s.doc, listOf(moved(0, 90, 40.0)), listOf(0, 30, 60, 90), 0.9)
        }
    }

    @Test fun effects() {
        fun fx(name: String, kind: EffectKind, p: EffectParams, frames: List<Int>, checks: (EditorSession) -> List<String> = { emptyList() }, minIou: Double = 0.9) {
            val s = fresh(); val t = s.addRect(256.0, 256.0, 100.0, 60.0, Rgba(1.0, 1.0, 1.0, 1.0))!!
            s.select(listOf(t)); s.applyEffect(kind, p)
            write("fx_$name", s.doc, checks(s), frames, minIou)
        }
        fx("zoom_in", EffectKind.ZOOM_IN, EffectParams(0.0, 30.0), listOf(0, 10, 20, 30), { listOf(bbox(0, 20.0, 12.0, 3.0), bbox(30, 100.0, 60.0), centroid(0, 256.0, 256.0)) })
        fx("zoom_out", EffectKind.ZOOM_OUT, EffectParams(0.0, 30.0), listOf(0, 15, 30), { listOf(bbox(0, 100.0, 60.0), bbox(30, 20.0, 12.0, 3.0)) })
        fx("slide_in", EffectKind.SLIDE_IN, EffectParams(0.0, 30.0, direction = 0), listOf(0, 10, 20, 30), { listOf(centroid(0, 56.0, 256.0), centroid(30, 256.0, 256.0)) })
        fx("shake", EffectKind.SHAKE, EffectParams(0.0, 40.0), listOf(0, 7, 14, 21, 28, 35, 40), { listOf(centroid(40, 256.0, 256.0, 1.5)) })
        fx("bounce", EffectKind.BOUNCE, EffectParams(0.0, 60.0), listOf(0, 12, 24, 40, 52, 60), { listOf(centroid(0, 256.0, 136.0), centroid(24, 256.0, 256.0, 3.0), centroid(60, 256.0, 256.0)) })
        fx("pulse", EffectKind.PULSE, EffectParams(0.0, 40.0, loop = true), listOf(0, 10, 20, 30, 60), { listOf(bbox(20, 115.0, 69.0, 3.0), bbox(40, 100.0, 60.0)) })
        fx("rotate", EffectKind.ROTATE, EffectParams(0.0, 40.0, direction = 1, loop = true), listOf(0, 5, 10, 15, 25, 40), { listOf(bbox(10, 60.0, 100.0, 4.0), bbox(40, 100.0, 60.0)) })
        fx("float", EffectKind.FLOAT, EffectParams(0.0, 60.0, loop = true), listOf(0, 15, 30, 45, 60), { listOf(centroid(15, 256.0, 246.0, 2.0), centroid(45, 256.0, 266.0, 2.0)) })
        fx("scale", EffectKind.SCALE, EffectParams(0.0, 30.0), listOf(0, 15, 30), { listOf(bbox(30, 150.0, 90.0, 3.0)) })
        fx("fade_in", EffectKind.FADE_IN, EffectParams(10.0, 30.0), listOf(0, 10, 25, 40, 60), { listOf(alphaMax(0, 0.0, 2.0), alphaMax(40, 255.0, 2.0)) })
        fx("fade_out", EffectKind.FADE_OUT, EffectParams(10.0, 30.0), listOf(0, 10, 25, 40, 60), { listOf(alphaMax(0, 255.0, 2.0), alphaMax(40, 0.0, 2.0)) })
        fx("opacity", EffectKind.OPACITY, EffectParams(0.0, 40.0, loop = true), listOf(0, 10, 20, 30, 40), { listOf(alphaMax(20, 76.5, 4.0), alphaMax(40, 255.0, 2.0)) })
        run { // everything stacked: the same layer zooms in, spins once and fades in
            val s = fresh(); val t = s.addRect(256.0, 256.0, 100.0, 60.0, Rgba(1.0, 1.0, 1.0, 1.0))!!; s.select(listOf(t))
            s.applyEffect(EffectKind.ZOOM_IN, EffectParams(0.0, 40.0)); s.applyEffect(EffectKind.ROTATE, EffectParams(0.0, 40.0, direction = 1)); s.applyEffect(EffectKind.FADE_IN, EffectParams(0.0, 20.0))
            write("fx_stacked", s.doc, listOf(alphaMax(0, 0.0, 2.0), alphaMax(30, 255.0, 2.0), bbox(40, 100.0, 60.0)), listOf(5, 10, 15, 20, 30, 40), 0.88)
        }
        run { // glow and draw-on
            val s = fresh(); val t = s.addEllipse(256.0, 256.0, 160.0, 160.0, Rgba(0.2, 0.6, 1.0, 1.0))!!; s.select(listOf(t))
            s.applyEffect(EffectKind.GLOW, EffectParams(0.0, 60.0, 100.0, loop = true))
            write("fx_glow", s.doc, listOf(ink(0, 20000.0, 0.5)), listOf(0, 20, 30, 45), 0.9)
        }
        run {
            val s = fresh(); val t = s.addStroke(List(60) { (120.0 + it * 4) to (256.0 + 60 * Math.sin(it / 8.0)) }, Rgba(1.0, 1.0, 1.0, 1.0), 12.0)!!; s.select(listOf(t))
            s.applyEffect(EffectKind.DRAW_ON, EffectParams(0.0, 60.0))
            write("fx_draw_on", s.doc, listOf(ink(0, 0.0, 0.0001), inkRatio(30, 70, 0.5, 0.08)), listOf(0, 15, 30, 45, 60), 0.85)
        }
    }

    private fun ringDoc(build: (EditorSession, ShapeTarget) -> Unit): LottieDocument {
        val s = fresh()
        val trim = obj("ty" to str("tm"), "s" to staticProp(0.0), "e" to staticProp(100.0), "o" to staticProp(0.0), "m" to int(1), "nm" to str("Trim Paths 1"))
        val g = Creator.group("Ring", Creator.ellipseItem(300.0, 300.0), trim, Creator.stroke(Rgba(0.1, 0.8, 0.9, 1.0), 20.0))
        s.edit("ring") { Creator.insertLayer(s.doc, null, Creator.shapeLayer(s.doc, s.doc.root.arr("layers"), "Ring", 256.0, 256.0, g)) }
        build(s, ShapeTarget(ShapeRef(LayerRef(null, 0), listOf(0))))
        return s.doc
    }

    private fun EditorSession.setTrim(g: ShapeTarget, key: String, frame: Double, v: Double) {
        val e = animatedProps(g).first { it.key == key }
        this.frame = frame; edit("trim") { PropWrite.setValues(e.prop, Spaces.localFrame(index, null, frame), doubleArrayOf(v)) }
    }
    private fun EditorSession.keyTrim(g: ShapeTarget, key: String) { val e = animatedProps(g).first { it.key == key }; edit("k") { KeyframeOps.addAt(e.prop, frame) } }

    @Test fun trimPathsAnimation() {
        val ringLen = 2 * PI * 150.0; val w = 20.0; val caps = PI * 100.0
        fun inkAt(e: Double) = if (e >= 1.0) ringLen * w else e * ringLen * w + caps
        run { // end 0% -> 100%: the path draws itself
            val doc = ringDoc { s, g -> s.setTrim(g, "end", 0.0, 0.0); s.keyTrim(g, "end"); s.setTrim(g, "end", 90.0, 100.0) }
            write("trim_end", doc, listOf(ink(22, inkAt(22 / 90.0), 0.10), ink(45, inkAt(0.5), 0.08), ink(68, inkAt(68 / 90.0), 0.08), ink(90, inkAt(1.0), 0.06)), listOf(22, 45, 68, 90), 0.85)
        }
        run { // start 0 -> 50 and end 50 -> 100: a half-length segment travelling round
            val doc = ringDoc { s, g ->
                s.setTrim(g, "end", 0.0, 50.0); s.keyTrim(g, "end"); s.setTrim(g, "end", 90.0, 100.0)
                s.setTrim(g, "start", 0.0, 0.0); s.keyTrim(g, "start"); s.setTrim(g, "start", 90.0, 50.0)
            }
            write("trim_start_end", doc, listOf(ink(0, inkAt(0.5), 0.08), ink(45, inkAt(0.5), 0.08), ink(80, inkAt(0.5), 0.10)), listOf(0, 20, 45, 70, 85), 0.85)
        }
        run { // offset 0 -> 360 with a 25% segment: it circles the ring at constant length
            val doc = ringDoc { s, g ->
                s.setTrim(g, "end", 0.0, 25.0)
                s.setTrim(g, "offset", 0.0, 0.0); s.keyTrim(g, "offset"); s.setTrim(g, "offset", 90.0, 360.0)
            }
            write("trim_offset", doc, listOf(ink(0, inkAt(0.25), 0.08), ink(22, inkAt(0.25), 0.08), ink(45, inkAt(0.25), 0.08), ink(67, inkAt(0.25), 0.08), moved(0, 45, 150.0)), listOf(0, 22, 45, 67, 85), 0.85)
        }
        run { // trim over an ordinary path with a hand-built keyframe structure (as an imported file might carry)
            val doc = ringDoc { s, g -> s.setTrim(g, "end", 0.0, 0.0); s.keyTrim(g, "end"); s.setTrim(g, "end", 60.0, 100.0); s.setTrim(g, "end", 120.0, 0.0) }
            write("trim_in_out", doc, listOf(ink(30, inkAt(0.5), 0.08), ink(60, inkAt(1.0), 0.06), ink(90, inkAt(0.5), 0.08)), listOf(15, 30, 60, 90, 105), 0.85)
        }
    }

    @Test fun repairedFilesAndNegativeControl() {
        val noEasing = """{"a":1,"k":[{"t":0,"s":[256,256,0]},{"t":90,"s":[356,256,0]}]}"""
        fun broken(): LottieDocument {
            val base = fresh().also { it.addRect(256.0, 256.0, 100.0, 100.0) }.doc
            val ks = base.layers[0].raw.obj("ks")!!
            ks["p"] = JsonParser.parse(noEasing)
            return base
        }
        // exported through the app's exporter: repaired, so it must move
        write("repaired_import_style", broken(), listOf(centroid(0, 256.0, 256.0), centroid(45, 306.0, 256.0), centroid(90, 356.0, 256.0)), listOf(0, 45, 90))
        // written WITHOUT the repair: documents what rlottie does with the invalid structure (informational in the checker)
        val raw = broken()
        File(dir, "negative_control.tgs").writeBytes(TgsCodec.writeTgs(raw))
        File(dir, "negative_control.expect.json").writeText("""{"characterization":true,"checks":[${centroid(45, 306.0, 256.0)}],"iou_frames":[],"min_iou":0.0}""")
        assertTrue(Validator.validate(raw, asExported = false).any { it.code == "keyframe_no_easing" })
    }
}
