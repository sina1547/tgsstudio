package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.json.deepCopy
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.Rgba
import ir.sina.tgsstudio.renderer.SceneBuilder
import ir.sina.tgsstudio.renderer.Transforms
import kotlin.test.*

class EffectsTest {
    private fun session(): Pair<EditorSession, LayerTarget> {
        val s = EditorSession(Creator.blankDocument("t")); val t = s.addRect(256.0, 256.0, 100.0, 60.0, Rgba(1.0, 1.0, 1.0, 1.0))!!
        s.rename(t, "box"); return s to t
    }
    private fun EditorSession.box(): LayerRef = LayerRef(null, doc.layers.indexOfFirst { it.name == "box" })
    private fun EditorSession.bounds(f: Double) = Hit.bounds(scene(f), box())
    private fun EditorSession.alpha(f: Double) = scene(f).layers.first { it.ref == box() }.opacity
    private fun clean(s: EditorSession) = assertEquals(0, TgsPrepare.repaired(s.doc).second.keyframeListsRepaired, "every keyframe list must already be valid")
    private fun apply(s: EditorSession, k: EffectKind, p: EffectParams = k.defaults(60.0)): EffectResult { s.select(listOf(LayerTarget(s.box()))); return s.applyEffect(k, p) }
    private fun nullsOf(s: EditorSession) = s.effectNullsOf(s.box())

    @Test fun everySupportedEffectProducesValidKeyframesAndEntranceEffectsEndAtRest() {
        val returnsToRest = setOf(EffectKind.ZOOM_IN, EffectKind.SHAKE, EffectKind.BOUNCE, EffectKind.PULSE, EffectKind.ROTATE, EffectKind.SLIDE_IN, EffectKind.FLOAT, EffectKind.FADE_IN, EffectKind.OPACITY, EffectKind.GLOW)
        for (k in EffectKind.entries.filter { it.supported && it.work != EffectWork.TRIM }) {
            val (s, _) = session(); val rest = s.world("box", 0.0)
            val r = apply(s, k)
            assertEquals(1, r.applied, k.id); clean(s)
            assertEquals("box", s.doc.layers[s.selection.single().layer.index].name, "selection must follow the layer for ${k.id}")
            if (k in returnsToRest) { same(rest, s.world("box", 179.0), 1e-6); near(1.0, s.alpha(179.0), 1e-6, k.id) }
            else { val end = s.bounds(179.0)!!; assertTrue(end.width != 100.0 || end.centerX != 256.0 || s.alpha(179.0) < 1.0, "${k.id} should leave the layer changed") }
        }
    }
    private fun EditorSession.world(name: String, f: Double): Affine { val ls = doc.layers; return Transforms.layerFull(ls, ls.first { it.name == name }, f) }
    private fun same(a: Affine, b: Affine, eps: Double) { near(a.e, b.e, eps); near(a.f, b.f, eps); near(a.a, b.a, eps); near(a.d, b.d, eps) }

    @Test fun zoomInGrowsFromTheCentreAndEndsAtTheRestSize() {
        val (s, _) = session(); apply(s, EffectKind.ZOOM_IN, EffectParams(0.0, 30.0))
        val a = s.bounds(0.0)!!; val z = s.bounds(30.0)!!
        near(20.0, a.width, 0.1); near(256.0, a.centerX, 0.1); near(256.0, a.centerY, 0.1)
        near(100.0, z.width, 0.1); near(60.0, z.height, 0.1); near(256.0, z.centerX, 0.1)
        assertTrue(s.bounds(10.0)!!.width in 20.0..100.0)
        assertEquals(1, nullsOf(s).size); assertEquals("Fx: Zoom In", s.doc.layers[nullsOf(s).single().index].name)
    }

    @Test fun zoomOutShrinksAndSlidesMoveTheRightWay() {
        val (s, _) = session(); apply(s, EffectKind.ZOOM_OUT, EffectParams(0.0, 30.0)); near(100.0, s.bounds(0.0)!!.width, 0.1); near(20.0, s.bounds(30.0)!!.width, 0.1)
        for ((dir, ex, ey) in listOf(Triple(0, -200.0, 0.0), Triple(1, 200.0, 0.0), Triple(2, 0.0, -200.0), Triple(3, 0.0, 200.0))) {
            val (s2, _) = session(); apply(s2, EffectKind.SLIDE_IN, EffectParams(0.0, 30.0, direction = dir))
            near(256 + ex, s2.bounds(0.0)!!.centerX, 0.1, "dir $dir x"); near(256 + ey, s2.bounds(0.0)!!.centerY, 0.1, "dir $dir y")
            near(256.0, s2.bounds(30.0)!!.centerX, 0.1); near(256.0, s2.bounds(30.0)!!.centerY, 0.1)
        }
        val (s3, _) = session(); apply(s3, EffectKind.SLIDE_OUT, EffectParams(0.0, 30.0, direction = 1)); near(256.0, s3.bounds(0.0)!!.centerX, 0.1); near(456.0, s3.bounds(30.0)!!.centerX, 0.1)
    }

    @Test fun shakeStaysSmallAndReturnsToRest() {
        val (s, _) = session(); apply(s, EffectKind.SHAKE, EffectParams(10.0, 40.0))
        var maxd = 0.0
        for (f in 10..50) { val b = s.bounds(f.toDouble())!!; maxd = maxOf(maxd, kotlin.math.hypot(b.centerX - 256, b.centerY - 256)) }
        assertTrue(maxd in 3.0..20.0, "max displacement $maxd"); near(256.0, s.bounds(50.0)!!.centerX, 1e-3); near(256.0, s.bounds(5.0)!!.centerX, 1e-3)
        val (s2, _) = session(); apply(s2, EffectKind.SHAKE, EffectParams(10.0, 40.0))
        for (f in 10..50) near(s.bounds(f.toDouble())!!.centerX, s2.bounds(f.toDouble())!!.centerX, 1e-6, "deterministic")
    }

    @Test fun bounceDropsFromAboveAndNeverSinksBelowRest() {
        val (s, _) = session(); apply(s, EffectKind.BOUNCE, EffectParams(0.0, 60.0))
        near(256.0 - 120.0, s.bounds(0.0)!!.centerY, 0.1); near(256.0, s.bounds(24.0)!!.centerY, 0.2); near(256.0, s.bounds(60.0)!!.centerY, 0.1)
        for (f in 0..60) assertTrue(s.bounds(f.toDouble())!!.centerY <= 256.0 + 0.2, "frame $f")
        assertTrue(s.bounds(39.0)!!.centerY < 250.0)                       // second, smaller bounce
    }

    @Test fun pulseAndFloatLoopUntilTheEndOfTheTimeline() {
        val (s, _) = session(); apply(s, EffectKind.PULSE, EffectParams(0.0, 40.0, loop = true))
        near(115.0, s.bounds(20.0)!!.width, 0.2); near(115.0, s.bounds(60.0)!!.width, 0.2); near(100.0, s.bounds(40.0)!!.width, 0.2)
        near(100.0, s.bounds(160.0)!!.width, 0.2)                          // 4 whole cycles of 40 frames fit before frame 179
        assertTrue(KeyframeOps.times(s.animatedProps(LayerTarget(nullsOf(s).single())).first { it.key == "s" && it.depth == 0 }.prop).last() <= 179.0)
        val (f, _) = session(); apply(f, EffectKind.FLOAT, EffectParams(0.0, 60.0, loop = true))
        near(246.0, f.bounds(15.0)!!.centerY, 0.1); near(266.0, f.bounds(45.0)!!.centerY, 0.1); near(256.0, f.bounds(60.0)!!.centerY, 0.1); near(256.0, f.bounds(120.0)!!.centerY, 0.1)
    }

    @Test fun rotationTurnsAboutTheCentreInTheChosenDirection() {
        val (s, _) = session(); apply(s, EffectKind.ROTATE, EffectParams(0.0, 40.0, direction = 1))
        near(100.0, s.bounds(0.0)!!.width, 0.2)
        near(60.0, s.bounds(10.0)!!.width, 0.5); near(100.0, s.bounds(10.0)!!.height, 0.5)          // quarter turn: width and height swap
        near(100.0, s.bounds(20.0)!!.width, 0.5); near(256.0, s.bounds(20.0)!!.centerX, 0.2)
        val n = LayerTarget(nullsOf(s).single()); val r = s.animatedProps(n).first { it.key == "r" && it.depth == 0 }
        near(360.0, ir.sina.tgsstudio.renderer.PropEval.number(r.prop, 40.0, 0.0), 1e-6)
        val (a, _) = session(); apply(a, EffectKind.ROTATE, EffectParams(0.0, 40.0, direction = 0))
        near(-360.0, ir.sina.tgsstudio.renderer.PropEval.number(a.animatedProps(LayerTarget(nullsOf(a).single())).first { it.key == "r" && it.depth == 0 }.prop, 40.0, 0.0), 1e-6)
    }

    @Test fun fadesAndOpacityAnimation() {
        val (s, _) = session(); apply(s, EffectKind.FADE_IN, EffectParams(20.0, 30.0))
        near(0.0, s.alpha(0.0), 1e-6); near(0.0, s.alpha(20.0), 1e-6); near(1.0, s.alpha(50.0), 1e-6); near(1.0, s.alpha(170.0), 1e-6); assertTrue(s.alpha(35.0) in 0.2..0.99)
        assertTrue(nullsOf(s).isEmpty(), "opacity effects animate the layer itself")
        val (o, _) = session(); apply(o, EffectKind.FADE_OUT, EffectParams(20.0, 30.0))
        near(1.0, o.alpha(10.0), 1e-6); near(0.0, o.alpha(50.0), 1e-6); near(0.0, o.alpha(170.0), 1e-6)
        val (b, _) = session(); apply(b, EffectKind.OPACITY, EffectParams(0.0, 40.0, loop = true))
        near(0.3, b.alpha(20.0), 0.01); near(1.0, b.alpha(40.0), 0.01); near(0.3, b.alpha(60.0), 0.01)
    }

    @Test fun fadeComposesWithAnExistingOpacityAnimation() {
        val (s, t) = session()
        s.frame = 0.0; s.addKeyframe(s.animatedProps(t).first { it.key == "o" && it.depth == 0 }); s.frame = 60.0; s.setOpacity(t, 50.0); s.frame = 0.0
        apply(s, EffectKind.FADE_OUT, EffectParams(30.0, 30.0, 100.0))
        near(1.0, s.alpha(0.0), 1e-6)                                           // untouched before the fade
        val base30 = 0.75; near(base30, s.alpha(30.0), 0.02)                    // 100 -> 50 over 60 frames = 75% at frame 30, factor 1
        near(0.0, s.alpha(60.0), 1e-6); near(0.0, s.alpha(120.0), 1e-6)
        val mid = s.alpha(45.0); assertTrue(mid in 0.05..0.5, "frame 45: base 62.5% × eased factor, got $mid")
        clean(s)
    }

    @Test fun effectsStackAndEachCanBeRemoved() {
        val (s, _) = session(); val rest = s.world("box", 0.0)
        apply(s, EffectKind.ZOOM_IN, EffectParams(0.0, 30.0)); apply(s, EffectKind.ROTATE, EffectParams(0.0, 40.0))
        apply(s, EffectKind.FADE_IN, EffectParams(0.0, 20.0))
        val nulls = nullsOf(s); assertEquals(2, nulls.size)
        assertEquals(listOf("Fx: Rotation", "Fx: Zoom In"), nulls.map { s.doc.layers[it.index].name })      // innermost first
        near(20.0, s.bounds(0.0)!!.width, 0.5, "both effects stack: zoomed to 20% with the rotation still at 0")
        near(0.0, s.alpha(0.0), 1e-6)
        clean(s)
        assertTrue(s.removeEffectNull(nulls[0]))                                // drop the rotation
        assertEquals(1, nullsOf(s).size); s.select(listOf(LayerTarget(s.box())))
        near(20.0, s.bounds(0.0)!!.width, 0.5)                                  // zoom-in still there
        assertTrue(s.removeEffectNull(nullsOf(s).single()))
        assertTrue(nullsOf(s).isEmpty()); same(rest, s.world("box", 0.0), 1e-6); same(rest, s.world("box", 90.0), 1e-6)
        assertEquals(listOf("box"), s.doc.layers.map { it.name })
    }

    @Test fun effectsWorkOnSeveralLayersAndLayersThatHaveAParent() {
        val s = EditorSession(Creator.blankDocument("t"))
        val a = s.addRect(100.0, 100.0, 40.0, 40.0)!!; s.rename(a, "a"); val b = s.addRect(300.0, 120.0, 40.0, 40.0)!!; s.rename(b, "b"); val c = s.addRect(200.0, 300.0, 40.0, 40.0)!!; s.rename(c, "c")
        val par = s.addRect(256.0, 256.0, 10.0, 10.0)!!; s.rename(par, "par"); s.setRotation(par, 30.0)
        fun ref(n: String) = LayerRef(null, s.doc.layers.indexOfFirst { it.name == n })
        s.setParent(listOf(ref("c")), ref("par"))
        val before = listOf("a", "b", "c").map { n -> val ls = s.doc.layers; Transforms.layerFull(ls, ls.first { it.name == n }, 0.0) }
        s.select(listOf("a", "b", "c").map { LayerTarget(ref(it)) })
        val r = s.applyEffect(EffectKind.SHAKE, EffectParams(0.0, 30.0))
        assertEquals(3, r.applied)
        assertEquals(listOf("a", "b", "c"), s.selection.map { s.doc.layers[it.layer.index].name }.sorted())
        listOf("a", "b", "c").forEachIndexed { i, n -> same(before[i], s.world(n, 30.0), 1e-3) }          // rest again at the end of the shake
        assertEquals(3, s.doc.layers.count { it.name.startsWith("Fx: ") })
        assertEquals(ref("par"), ParentOps.parentOf(s.index, s.effectNullsOf(ref("c")).last()))               // the null of c rides along with its old parent
        clean(s)
    }

    @Test fun drawOnAndOffUseTrimPaths() {
        val s = EditorSession(Creator.blankDocument("t"))
        val t = s.addStroke(List(40) { (100.0 + it * 5) to 200.0 }, Rgba(1.0, 1.0, 1.0, 1.0), 8.0)!!
        s.select(listOf(t)); val r = s.applyEffect(EffectKind.DRAW_ON, EffectParams(0.0, 40.0)); assertEquals(1, r.applied)
        fun len(f: Double) = SceneBuilder(s.doc).build(f).layers.sumOf { l -> l.items.sumOf { i -> i.contours.sumOf { c -> c.length } } }
        near(0.0, len(0.0), 1e-6); assertTrue(len(20.0) in 60.0..140.0); near(len(100.0), len(40.0), 1.0)
        s.applyEffect(EffectKind.DRAW_OFF, EffectParams(0.0, 40.0)); near(0.0, len(60.0), 1e-6); assertTrue(len(0.0) > 150.0)
        val rect = EditorSession(Creator.blankDocument("t")); val rt = rect.addRect(256.0, 256.0, 50.0, 50.0)!!
        rect.select(listOf(rt)); assertTrue(EffectNote.NO_STROKE in rect.applyEffect(EffectKind.DRAW_ON, EffectParams(0.0, 30.0)).notes)
    }

    @Test fun glowAddsTranslucentHaloLayersBelowTheShape() {
        val (s, _) = session(); val r = apply(s, EffectKind.GLOW, EffectParams(0.0, 60.0, 100.0, loop = true))
        assertTrue(EffectNote.GLOW_ADDS_LAYERS in r.notes)
        assertEquals(listOf("box", "Fx: Glow Inner", "Fx: Glow Outer"), s.doc.layers.map { it.name })
        near(12.0, s.strokeWidth(LayerTarget(LayerRef(null, 1)))!!); near(26.0, s.strokeWidth(LayerTarget(LayerRef(null, 2)))!!)
        near(1.0, s.alpha(0.0), 1e-6)                                            // the shape itself is untouched
        val inner = s.scene(30.0).layers.first { it.name == "Fx: Glow Inner" }.opacity; assertTrue(inner < 0.6, "pulses down mid-cycle: $inner")
        clean(s)
    }

    @Test fun blurIsRefusedBecauseTgsForbidsLayerEffects() {
        val (s, _) = session(); val before = s.doc.root.deepCopy()
        val r = apply(s, EffectKind.BLUR)
        assertEquals(0, r.applied); assertTrue(EffectNote.BLUR_UNSUPPORTED in r.notes); assertEquals(before, s.doc.root)
        assertFalse(EffectKind.BLUR.supported)
    }

    @Test fun effectsLongerThanTheTimelineAreShortenedAndEachApplyIsOneUndoStep() {
        val (s, _) = session(); val before = s.doc.root.deepCopy()
        val r = apply(s, EffectKind.ZOOM_IN, EffectParams(150.0, 120.0))
        assertTrue(EffectNote.SHORTENED_TO_TIMELINE in r.notes)
        val n = LayerTarget(nullsOf(s).single()); assertTrue(KeyframeOps.times(s.animatedProps(n).first { it.key == "s" && it.depth == 0 }.prop).last() <= 179.0)
        assertTrue(EffectNote.NOT_ENOUGH_TIME in apply(session().first, EffectKind.ZOOM_IN, EffectParams(179.0, 30.0)).notes)
        s.undo(); assertEquals(before, s.doc.root)
    }

    @Test fun customPresetsSurviveASaveAndLoad() {
        val list = listOf(EffectPreset("My shake", EffectKind.SHAKE, EffectParams(0.0, 24.0, 160.0, true, 0)), EffectPreset("Spin", EffectKind.ROTATE, EffectParams(0.0, 90.0, 100.0, true, 1)))
        assertEquals(list, EffectPresets.fromJson(EffectPresets.toJson(list)))
        assertEquals(emptyList(), EffectPresets.fromJson("not json")); assertEquals(1, EffectPresets.fromJson("""[{"kind":"zoom_in"},{"kind":"nope"},5]""").size)
        assertEquals(EffectKind.entries.size, EffectKind.entries.map { it.id }.toSet().size)
    }
}
