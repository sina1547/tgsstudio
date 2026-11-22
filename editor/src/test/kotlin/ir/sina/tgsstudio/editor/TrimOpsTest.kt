package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.PropEval
import ir.sina.tgsstudio.renderer.Rgba
import ir.sina.tgsstudio.renderer.SceneBuilder
import kotlin.test.*

class TrimOpsTest {
    private fun strokeLayer(s: EditorSession): LayerTarget {
        val b = Bezier(false, doubleArrayOf(0.0, 0.0, 200.0, 0.0), DoubleArray(4), DoubleArray(4))
        return s.addFreeform(b, filled = false, strokeWidth = 10.0)!!
    }
    private fun types(s: EditorSession) = s.doc.layers[0].shapes[0].children.map { it.typeCode }
    private fun lengthAt(s: EditorSession, f: Double) = SceneBuilder(s.doc).build(f).layers.sumOf { l -> l.items.sumOf { i -> i.contours.sumOf { c -> c.length } } }

    @Test fun trimGoesBelowThePathsAndAboveTheStroke() {
        val s = EditorSession(Creator.blankDocument("t")); val t = strokeLayer(s)
        assertFalse(s.hasTrim(t)); assertTrue(s.addTrim(t))
        assertEquals(listOf("sh", "tm", "st", "tr"), types(s))
        assertTrue(s.addTrim(t)); assertEquals(listOf("sh", "tm", "st", "tr"), types(s), "adding twice must not duplicate")
        s.removeTrim(t); assertEquals(listOf("sh", "st", "tr"), types(s))
    }

    @Test fun drawOnDrawOffAndEraseProduceValidKeyframesAndTheRightLengths() {
        val s = EditorSession(Creator.blankDocument("t")); val t = strokeLayer(s)
        assertTrue(s.animateTrim(t, TrimAnim.DRAW_ON, 10.0, 60.0, Easing.LINEAR))
        val e = s.trimItem(t)!!.obj("e")!!
        assertEquals(listOf(10.0, 70.0), KeyframeOps.times(e)); assertTrue(PropWrite.violations(KeyframeOps.list(e)).isEmpty())
        near(0.0, lengthAt(s, 0.0), 1e-6)                                         // before the start: nothing drawn
        near(100.0, lengthAt(s, 40.0), 0.6)                                       // half way: half of the 200px path
        near(200.0, lengthAt(s, 100.0), 0.6)

        s.animateTrim(t, TrimAnim.DRAW_OFF, 0.0, 30.0, Easing.LINEAR)
        near(200.0, lengthAt(s, 0.0), 0.6); near(100.0, lengthAt(s, 15.0), 0.6); near(0.0, lengthAt(s, 40.0), 1e-6)
        assertFalse(PropWrite.isAnimated(s.trimItem(t)!!.obj("s")!!), "start must be reset when switching modes")

        s.animateTrim(t, TrimAnim.ERASE_FROM_START, 0.0, 30.0, Easing.LINEAR)
        near(200.0, lengthAt(s, 0.0), 0.6); near(100.0, lengthAt(s, 15.0), 0.6); near(0.0, lengthAt(s, 45.0), 1e-6)
        assertFalse(PropWrite.isAnimated(s.trimItem(t)!!.obj("e")!!))
    }

    @Test fun startEndOffsetCanBeKeyedLikeAnyProperty() {
        val s = EditorSession(Creator.blankDocument("t")); val t = strokeLayer(s); s.addTrim(t)
        for (k in listOf("start", "end", "offset")) {
            val e = s.animatedProps(t).first { it.key == k }
            s.frame = 0.0; s.addKeyframe(e)
            s.frame = 50.0; s.edit("v") { PropWrite.setValues(e.prop, 50.0, doubleArrayOf(if (k == "offset") 90.0 else 40.0)) }
            assertEquals(listOf(0.0, 50.0), KeyframeOps.times(e.prop), k)
            assertTrue(PropWrite.violations(KeyframeOps.list(e.prop)).isEmpty(), k)
        }
    }

    @Test fun thePathStaysEditableAfterTrimAndKeepsItsAnimation() {
        val s = EditorSession(Creator.blankDocument("t")); val t = strokeLayer(s)
        s.animateTrim(t, TrimAnim.DRAW_ON, 0.0, 30.0)
        val ref = ShapeRef(t.layer, listOf(0, 0))
        s.select(listOf(ShapeTarget(ShapeRef(t.layer, listOf(0)))))
        assertEquals(ref, s.pathShape())
        s.frame = 60.0; s.edit("move") { s.writePath(ref, PathOps.moveVertex(s.pathBezier(ref)!!, 1, 300.0, 0.0)) }
        assertTrue(s.structuralPathEdit(ref, "insert") { PathOps.insert(it, 0, 0.5) })
        near(400.0, lengthAt(s, 60.0), 1.0)                                       // trim finished: the whole edited path (-100 .. 300 in layer space) is shown
        assertTrue(PropWrite.isAnimated(s.trimItem(t)!!.obj("e")!!))
    }

    @Test fun appliesToEveryGroupOfALayerAndIsUndoable() {
        val s = EditorSession(Creator.blankDocument("t"))
        s.addSvg("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><path d="M1 1 L9 1" stroke="red" stroke-width="1" fill="none"/><path d="M1 9 L9 9" stroke="blue" stroke-width="1" fill="none"/></svg>""")
        val t = LayerTarget(LayerRef(null, 0)); val before = s.doc.root.toString()
        assertTrue(s.animateTrim(t, TrimAnim.DRAW_ON, 0.0, 20.0))
        assertEquals(2, s.doc.layers[0].shapes.count { g -> g.children.any { it.typeCode == "tm" } })
        s.undo(); assertEquals(before, s.doc.root.toString())
    }

    @Test fun trimmedOutputPassesTheValidator() {
        val s = EditorSession(Creator.blankDocument("t")); val t = strokeLayer(s); s.animateTrim(t, TrimAnim.DRAW_ON, 0.0, 60.0)
        assertEquals(emptyList(), PropWrite.run { ir.sina.tgsstudio.editor.TgsPrepare.repaired(s.doc).second.keyframeListsRepaired.let { if (it == 0) emptyList<String>() else listOf("needed repair: $it") } })
        near(100.0, PropEval.number(s.trimItem(t)!!.obj("e"), 30.0, -1.0), 0.0 + 60.0)   // inside 0..100
    }
}
