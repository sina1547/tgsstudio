package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import kotlin.test.Test
import kotlin.test.assertEquals

class MaskOpsTest {
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
