package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GradientFillTest {
    @Test fun createsEditsAndUndoesGradientFill() {
        val doc = docOf("[${layerJson(1, "Shape", ks(), groupJson(rectJson(), fillJson))}]")
        val session = EditorSession(doc)
        val target = LayerTarget(LayerRef(null, 0))

        assertTrue(session.addGradientFill(target))
        val initial = assertNotNull(session.gradientOf(target))
        assertEquals(false, initial.radial)
        val animatedTracks = session.animatedProps(target).map { it.key }
        assertTrue("gradient" in animatedTracks)
        assertTrue("start" in animatedTracks)
        assertTrue("end" in animatedTracks)

        session.setGradient(target, initial.copy(
            radial = true,
            startX = -40.0,
            startY = 12.0,
            endX = 70.0,
            endY = 90.0,
        ))
        val edited = assertNotNull(session.gradientOf(target))
        assertEquals(true, edited.radial)
        near(-40.0, edited.startX)
        near(12.0, edited.startY)
        near(70.0, edited.endX)
        near(90.0, edited.endY)

        session.undo()
        assertEquals(false, assertNotNull(session.gradientOf(target)).radial)
    }
}
