package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.renderer.Rgba
import kotlin.test.*

class SessionTest {
    private fun session() = EditorSession(Creator.blankDocument("t"))

    @Test fun addDragUndoRedoCycle() {
        val s = session()
        val t = s.addRect(256.0, 256.0, 100.0, 100.0)!!
        assertEquals(1, s.doc.layers.size); assertEquals(listOf<Target>(t), s.selection)
        val v = s.transformOf(t)!!; near(256.0, v.x); near(100.0, v.scaleX)
        s.beginGesture("Move"); repeat(10) { s.dragBy(5.0, 0.0) }; s.endGesture()
        near(306.0, s.transformOf(t)!!.x, 0.01)
        s.undo(); near(256.0, s.transformOf(t)!!.x); s.redo(); near(306.0, s.transformOf(t)!!.x, 0.01)
        s.undo(); s.undo()                                   // undo the move, then the add
        assertEquals(0, s.doc.layers.size); assertTrue(s.selection.isEmpty(), "selection must not point at removed layers")
    }

    @Test fun tapSelectsLayerThenDrillsIntoShape() {
        val s = session()
        s.addEllipse(150.0, 150.0, 80.0, 80.0); s.addRect(350.0, 350.0, 100.0, 100.0)
        s.clearSelection()
        s.tap(150.0, 150.0, 4.0, additive = false)
        assertEquals(LayerTarget(LayerRef(null, 1)), s.selection.single())
        s.tap(150.0, 150.0, 4.0, additive = false)
        assertEquals(ShapeTarget(ShapeRef(LayerRef(null, 1), listOf(0))), s.selection.single())
        s.tap(350.0, 350.0, 4.0, additive = true); assertEquals(2, s.selection.size)
        s.tap(20.0, 20.0, 4.0, additive = false); assertTrue(s.selection.isEmpty())
        val box = (s.select(listOf(LayerTarget(LayerRef(null, 0)))).let { s.selectionBox() })!!
        near(100.0, box.width, 0.5)
    }

    @Test fun lockedLayersIgnoreTapsAndDrags() {
        val s = session(); val t = s.addRect(256.0, 256.0, 100.0, 100.0)!!
        s.toggleLock(t.layer)
        s.clearSelection(); s.tap(256.0, 256.0, 4.0, false); assertTrue(s.selection.isEmpty())
        s.select(listOf(t)); s.dragBy(50.0, 0.0); near(256.0, s.transformOf(t)!!.x)
    }

    @Test fun colorsStrokesAndDeletion() {
        val s = session(); val t = s.addRect(100.0, 100.0, 50.0, 50.0)!!
        s.setFillColor(t, Rgba(0.0, 1.0, 0.0, 1.0)); near(1.0, s.fillColor(t)!!.g)
        s.setStrokeWidth(t, 8.0); near(8.0, s.strokeWidth(t)!!)
        s.removeStroke(t); assertNull(s.strokeWidth(t))
        s.duplicateSelection(); assertEquals(2, s.doc.layers.size)
        s.deleteSelection(); assertEquals(1, s.doc.layers.size)
    }

    @Test fun nodeEditingFlowOnConvertedRectangle() {
        val s = session(); val t = s.addRect(256.0, 256.0, 100.0, 60.0)!!
        s.select(listOf(ShapeTarget(ShapeRef(t.layer, listOf(0)))))
        assertNull(s.pathShape()); assertNotNull(s.convertibleShape())
        assertTrue(s.convertToPath())
        val ref = s.pathShape()!!
        val b = s.pathBezier(ref)!!; assertEquals(4, b.count)   // four straight sides, no rounding
        assertTrue(s.structuralPathEdit(ref, "Insert") { PathOps.insert(it, 0, 0.5) })
        assertEquals(5, s.pathBezier(ref)!!.count)
        s.edit("Move node") { s.writePath(ref, PathOps.moveVertex(s.pathBezier(ref)!!, 0, 400.0, 0.0)) }
        near(400.0, s.pathBezier(ref)!!.v[0])
        s.undo(); assertNotEquals(400.0, s.pathBezier(ref)!!.v[0])
    }

    @Test fun keyframeWorkflow() {
        val s = session(); val t = s.addStar(256.0, 256.0, 60.0)!!
        s.frame = 0.0
        val pos = s.animatedProps(t).first { it.key == "p" && it.depth == 0 }
        s.addKeyframe(pos)
        s.frame = 60.0; s.setPosition(t, 400.0, 100.0)       // auto-key at frame 60
        assertEquals(listOf(0.0, 60.0), KeyframeOps.times(pos.prop))
        s.frame = 30.0; near(328.0, s.transformOf(t)!!.x, 0.01)
        s.setInterpolation(pos, 0, Interp.HOLD); near(256.0, s.transformOf(t)!!.x, 0.01)
        s.removeKeyframe(pos, 1); s.removeKeyframe(pos, 0)
        assertFalse(pos.isAnimated)
        assertTrue(s.animatedProps(t).any { it.key == "c" && it.kind == PropKind.COLOR })
    }

    @Test fun svgAndFreeformAndGrouping() {
        val s = session()
        val r = s.addSvg("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><rect width="10" height="10" fill="red"/><circle cx="5" cy="5" r="3" fill="blue"/></svg>""")!!
        assertEquals(2, r.groups.size); assertEquals(1, s.doc.layers.size)
        val path = ir.sina.tgsstudio.renderer.Bezier(false, doubleArrayOf(10.0, 10.0, 80.0, 40.0, 150.0, 20.0), DoubleArray(6), DoubleArray(6))
        assertNotNull(s.addFreeform(path, filled = false)); assertEquals(2, s.doc.layers.size)
        val ref = LayerRef(null, s.doc.layers.indexOfFirst { it.name == "SVG" })
        s.select(listOf(ShapeTarget(ShapeRef(ref, listOf(0))), ShapeTarget(ShapeRef(ref, listOf(1)))))
        assertTrue(s.groupSelection() is OpResult.Ok)
        assertEquals(1, s.index.layerRaw(ref)!!.arr("shapes")!!.items.size)
        s.undo(); assertEquals(2, s.index.layerRaw(ref)!!.arr("shapes")!!.items.size)
    }

    @Test fun everythingSurvivesTgsRoundTrip() {
        val s = session(); s.addRect(100.0, 100.0, 40.0, 40.0); s.addStar(300.0, 300.0, 50.0)
        val bytes = ir.sina.tgsstudio.parser.TgsCodec.writeTgs(s.doc)
        val back = ir.sina.tgsstudio.parser.TgsCodec.read(bytes)
        assertEquals(s.doc.root, back.root)
    }
}
