package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import kotlin.test.Test
import kotlin.test.assertEquals

class MaskOpsTest {
    @Test fun createsMaskFromTopLevelPathAndUndoesIt() {
        val path = """{"ty":"sh","nm":"Mask path","ks":{"a":0,"k":{"c":true,"v":[[0,0],[100,0],[100,100],[0,100]],"i":[[0,0],[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0],[0,0]]}}}"""
        val doc = docOf("[${layerJson(1, "Masked", ks(), shapes = path)}]")
        val session = EditorSession(doc)
        val ref = LayerRef(null, 0)

        assertEquals(true, session.addMaskFromPath(ref, ir.sina.tgsstudio.document.ShapeRef(ref, listOf(0))))
        assertEquals(listOf(false), session.maskInversions(ref))
        session.undo()
        assertEquals(emptyList(), session.maskInversions(ref))
    }

    @Test fun maskInversionAndRemovalAreUndoable() {
        val masks = """ ,"masksProperties":[{"mode":"a","inv":false},{"mode":"a","inv":true}] """
        val doc = docOf("[${layerJson(1, "Masked", ks(), extra = masks)}]")
        val session = EditorSession(doc)
        val ref = LayerRef(null, 0)

        assertEquals(listOf(false, true), session.maskInversions(ref))
        session.setMaskInverted(ref, 0, true)
        assertEquals(listOf(true, true), session.maskInversions(ref))
        session.undo()
        assertEquals(listOf(false, true), session.maskInversions(ref))

        session.removeMask(ref, 1)
        assertEquals(listOf(false), session.maskInversions(ref))
        session.undo()
        assertEquals(listOf(false, true), session.maskInversions(ref))
    }
}
