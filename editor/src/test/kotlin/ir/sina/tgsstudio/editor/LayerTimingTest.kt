package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LayerTimingTest {
    @Test fun layerTimingIsUndoableAndKeepsPositiveDuration() {
        val doc = docOf("[${layerJson(1, "Shape", ks())}]")
        val session = EditorSession(doc)
        val ref = LayerRef(null, 0)
        val before = assertNotNull(session.layerTiming(ref))
        session.setLayerTiming(ref, before.copy(inPoint = 12.0, outPoint = 80.0, startTime = 5.0, stretch = 1.5))
        val after = assertNotNull(session.layerTiming(ref))
        assertEquals(12.0, after.inPoint)
        assertEquals(80.0, after.outPoint)
        assertEquals(5.0, after.startTime)
        assertEquals(1.5, after.stretch)

        session.setLayerTiming(ref, after.copy(inPoint = 90.0, outPoint = 20.0))
        val clamped = assertNotNull(session.layerTiming(ref))
        assertEquals(90.0, clamped.inPoint)
        assertEquals(91.0, clamped.outPoint)

        session.undo()
        val restored = assertNotNull(session.layerTiming(ref))
        assertEquals(12.0, restored.inPoint)
        assertEquals(80.0, restored.outPoint)
    }
}
