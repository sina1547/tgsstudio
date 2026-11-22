package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.json.deepCopy
import kotlin.test.*

class HistoryTest {
    private fun setup() = docOf("[${layerJson(1, "a", ks())}]").let { it to History(it.root) }

    @Test fun undoRedoRestoresExactState() {
        val (d, h) = setup()
        val before = d.root.deepCopy()
        h.perform("rename") { LayerOps.rename(idx(d), LayerRef(null, 0), "b") }
        val after = d.root.deepCopy()
        assertEquals("b", d.layers[0].name); assertTrue(h.canUndo); assertFalse(h.canRedo)
        assertTrue(h.undo()); assertEquals(before, d.root); assertTrue(h.canRedo)
        assertTrue(h.redo()); assertEquals(after, d.root)
    }

    @Test fun gestureIsOneStep() {
        val (d, h) = setup()
        h.begin("drag")
        repeat(20) { TransformOps.translateDoc(idx(d), LayerTarget(LayerRef(null, 0)), 0.0, 1.0, 0.0) }
        h.commit()
        near(20.0, TransformOps.read(idx(d), LayerTarget(LayerRef(null, 0)), 0.0)!!.x)
        h.undo()
        near(0.0, TransformOps.read(idx(d), LayerTarget(LayerRef(null, 0)), 0.0)!!.x)
        assertFalse(h.canUndo)
    }

    @Test fun failedEditIsRolledBack() {
        val (d, h) = setup()
        val before = d.root.deepCopy()
        assertFailsWith<IllegalStateException> { h.perform("boom") { LayerOps.rename(idx(d), LayerRef(null, 0), "x"); error("fail") } }
        assertEquals(before, d.root); assertFalse(h.canUndo)
    }

    @Test fun noOpGestureLeavesNoStep() {
        val (_, h) = setup()
        h.begin("nothing"); h.commit()
        assertFalse(h.canUndo)
    }

    @Test fun newEditClearsRedoAndLimitIsEnforced() {
        val d = docOf("[${layerJson(1, "a", ks())}]"); val h = History(d.root, limit = 3)
        repeat(5) { i -> h.perform("e$i") { LayerOps.rename(idx(d), LayerRef(null, 0), "n$i") } }
        var undone = 0; while (h.undo()) undone++
        assertEquals(3, undone)
        h.perform("x") { LayerOps.rename(idx(d), LayerRef(null, 0), "z") }
        assertFalse(h.canRedo)
    }

    @Test fun rootIdentityIsKept() {
        val (d, h) = setup(); val root = d.root
        h.perform("r") { LayerOps.rename(idx(d), LayerRef(null, 0), "q") }; h.undo()
        assertSame(root, d.root)
    }
}
