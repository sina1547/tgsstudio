package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.PropEval
import kotlin.test.*

/**
 * rlottie (and so Telegram's players) drop any keyframe except the last that has no easing handles `i`/`o`
 * (or `h:1`), and break when the last one has them. These tests pin that structure for every editing path.
 */
class KeyframeInvariantTest {
    private fun prop(json: String) = JsonParser.parse(json) as JsonObj
    private fun assertValid(p: JsonObj, what: String) {
        val kfs = PropWrite.keyframesOf(p) ?: return
        val v = PropWrite.violations(kfs)
        assertTrue(v.isEmpty(), "$what: ${v.map { it.kind to it.index }} in ${p}")
    }

    @Test fun keyframesCreatedFromScratchAreValid() {
        val p = prop("""{"a":0,"k":[10,20]}""")
        KeyframeOps.addAt(p, 0.0)
        assertValid(p, "single keyframe")
        PropWrite.setValues(p, 60.0, doubleArrayOf(50.0, 60.0))
        assertValid(p, "after auto-key")
        val kfs = KeyframeOps.list(p)
        assertTrue(kfs[0].has("i") && kfs[0].has("o")); assertFalse(kfs[1].has("i") || kfs[1].has("o"))
        near(30.0, PropEval.values(p, 30.0)!![0], 1e-6)                   // diagonal handles = still linear
    }

    @Test fun everyInterpolationChoiceKeepsTheStructureValid() {
        val p = prop("""{"a":1,"k":[{"t":0,"s":[0]},{"t":30,"s":[10]},{"t":60,"s":[0]}]}""")
        PropWrite.normalize(PropWrite.keyframesOf(p)!!)
        for (i in 0..2) for (m in Interp.entries) { KeyframeOps.setInterpolation(p, i, m); assertValid(p, "set $m on $i") }
        KeyframeOps.setEasing(p, 2, Easing.EASE_IN_OUT); assertValid(p, "easing on the last keyframe")
        assertFalse(KeyframeOps.list(p).last().has("o"), "last keyframe must stay free of handles")
        KeyframeOps.setEasing(p, 0, Easing(0.2, 0.0, 0.9, 1.0)); assertValid(p, "custom easing")
        assertEquals(Interp.BEZIER, KeyframeOps.interpolation(p, 0))
        KeyframeOps.setInterpolation(p, 0, Interp.LINEAR)
        assertEquals(Interp.LINEAR, KeyframeOps.interpolation(p, 0)); assertTrue(KeyframeOps.list(p)[0].has("i"))   // explicit, not removed
        KeyframeOps.setInterpolation(p, 1, Interp.HOLD)
        assertEquals(Interp.HOLD, KeyframeOps.interpolation(p, 1))
        near(10.0, PropEval.number(p, 59.0, -1.0), 1e-6)                    // held at 10 until the next keyframe
    }

    @Test fun hasNoSegmentAfterTheLastKeyframe() {
        val p = prop("""{"a":1,"k":[{"t":0,"s":[0]},{"t":30,"s":[10]}]}""")
        assertTrue(KeyframeOps.hasOutgoingSegment(p, 0)); assertFalse(KeyframeOps.hasOutgoingSegment(p, 1))
    }

    @Test fun invalidFilesAreRepairedWithoutChangingValues() {
        val broken = prop("""{"a":1,"k":[{"t":0,"s":[0]},{"t":20,"s":[50]},{"t":40,"s":[100],"i":{"x":[0.8],"y":[0.8]},"o":{"x":[0.2],"y":[0.2]}}]}""")
        assertEquals(3, PropWrite.violations(PropWrite.keyframesOf(broken)!!).size)       // two without handles + the last one with handles
        assertEquals(1, PropWrite.normalizeTree(broken))
        assertValid(broken, "repaired")
        near(25.0, PropEval.number(broken, 10.0, -1.0), 1e-6); near(75.0, PropEval.number(broken, 30.0, -1.0), 1e-6)
        assertEquals(0, PropWrite.normalizeTree(broken))                                  // idempotent
        val malformed = prop("""{"a":1,"k":[{"t":0,"s":[0],"i":{"x":"a","y":[1]},"o":5},{"t":10,"s":[9]}]}""")
        PropWrite.normalizeTree(malformed); assertValid(malformed, "malformed handles")
    }

    @Test fun holdKeyframesNeedNoHandles() {
        val p = prop("""{"a":1,"k":[{"t":0,"s":[1],"h":1},{"t":10,"s":[2]}]}""")
        assertTrue(PropWrite.violations(PropWrite.keyframesOf(p)!!).isEmpty())
    }

    @Test fun randomEditingNeverBreaksTheStructure() {
        val rnd = java.util.Random(2024)
        repeat(60) { round ->
            val p = prop("""{"a":0,"k":[0,0]}""")
            KeyframeOps.addAt(p, 0.0)
            repeat(40) { step ->
                val frame = rnd.nextInt(180).toDouble()
                val n = KeyframeOps.list(p).size
                when (rnd.nextInt(8)) {
                    0 -> PropWrite.setValues(p, frame, doubleArrayOf(rnd.nextDouble() * 100, rnd.nextDouble() * 100))
                    1 -> KeyframeOps.addAt(p, frame)
                    2 -> if (n > 0) KeyframeOps.remove(p, rnd.nextInt(n))
                    3 -> if (n > 0) KeyframeOps.move(p, rnd.nextInt(n), frame)
                    4 -> if (n > 0) KeyframeOps.setInterpolation(p, rnd.nextInt(n), Interp.entries[rnd.nextInt(3)])
                    5 -> if (n > 0) KeyframeOps.setEasing(p, rnd.nextInt(n), Easing(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble()))
                    6 -> if (n > 0) KeyframeOps.copy(p, rnd.nextInt(n))?.let { KeyframeOps.paste(p, it, frame) }
                    else -> if (n > 0) KeyframeOps.setValues(p, rnd.nextInt(n), doubleArrayOf(rnd.nextDouble() * 100, 5.0))
                }
                assertValid(p, "round $round step $step")
            }
        }
    }

    @Test fun pathKeyframesStayValidThroughEditsAndStructuralChanges() {
        val b = Bezier(true, doubleArrayOf(0.0, 0.0, 100.0, 0.0, 100.0, 100.0), DoubleArray(6), DoubleArray(6))
        val shape = prop("""{"ty":"sh"}""").also { it["ks"] = staticPathProp(b) }
        val ks = shape.obj("ks")!!
        KeyframeOps.addAt(ks, 0.0, isPath = true)
        PathEdit.write(shape, 30.0, PathOps.moveVertex(b, 1, 150.0, 0.0)); assertValid(ks, "path write")
        PathEdit.write(shape, 60.0, PathOps.moveVertex(b, 2, 100.0, 200.0)); assertValid(ks, "second path write")
        assertTrue(PathEdit.structural(shape, 0.0) { PathOps.insert(it, 0, 0.5) }); assertValid(ks, "insert node")
        KeyframeOps.setInterpolation(ks, 0, Interp.HOLD); assertValid(ks, "hold on path")
        assertEquals(4, PathEdit.read(shape, 45.0)!!.count)
    }

    @Test fun sessionFlowsProduceValidKeyframes() {
        val s = EditorSession(Creator.blankDocument("t"))
        val t = s.addRect(256.0, 256.0, 100.0, 100.0)!!
        s.frame = 0.0
        for (key in listOf("p", "r", "o", "s")) s.addKeyframe(s.animatedProps(t).first { it.key == key && it.depth == 0 })
        s.frame = 90.0; s.setPosition(t, 300.0, 256.0); s.setRotation(t, 45.0); s.setOpacity(t, 30.0); s.setScale(t, 50.0, 50.0)
        val pos = s.animatedProps(t).first { it.key == "p" && it.depth == 0 }
        s.setInterpolation(pos, 1, Interp.BEZIER)                  // user taps "Ease" on the last keyframe
        s.frame = 150.0; s.setPosition(t, 280.0, 300.0)            // and then keys another one
        s.setInterpolation(pos, 1, Interp.HOLD)
        assertEquals(emptyList(), PropWrite.run { val c = ArrayList<String>(); fun walk(v: ir.sina.tgsstudio.document.json.JsonValue) { when (v) { is JsonObj -> { keyframesOf(v)?.let { if (violations(it).isNotEmpty()) c += v.toString() }; v.fields.values.forEach(::walk) }; is ir.sina.tgsstudio.document.json.JsonArr -> v.items.forEach(::walk); else -> Unit } }; walk(s.doc.root); c })
    }
}
