package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.json.deepCopy
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.Transforms
import kotlin.test.*

class ParentTest {
    private fun session() = EditorSession(Creator.blankDocument("t"))
    /** Adds a rectangle layer and returns its (current) ref; new layers go on top, so refs of older ones shift. */
    private fun EditorSession.rect(name: String, x: Double, y: Double, rot: Double = 0.0, scale: Double = 100.0): LayerRef {
        val t = addRect(x, y, 80.0, 50.0)!!
        rename(t, name); setRotation(t, rot); setScale(t, scale, scale)
        return t.layer
    }
    private fun EditorSession.ref(name: String) = LayerRef(null, doc.layers.indexOfFirst { it.name == name }.also { check(it >= 0) { "no layer $name" } })
    private fun EditorSession.world(name: String, f: Double = 0.0): Affine { val ls = doc.layers; return Transforms.layerFull(ls, ls.first { it.name == name }, f) }
    private fun same(a: Affine, b: Affine, eps: Double = 5e-3) { near(a.a, b.a, 1e-4); near(a.b, b.b, 1e-4); near(a.c, b.c, 1e-4); near(a.d, b.d, 1e-4); near(a.e, b.e, eps); near(a.f, b.f, eps) }

    @Test fun parentingKeepsTheChildWhereItWas() {
        val s = session(); s.rect("child", 350.0, 300.0, 10.0, 80.0); s.rect("parent", 200.0, 200.0, 30.0, 150.0)
        s.setAnchor(LayerTarget(s.ref("child")), 12.0, -7.0)                       // a non-zero anchor must be respected too
        val before = s.world("child")
        val r = s.setParent(listOf(s.ref("child")), s.ref("parent"))
        assertEquals(1, r.linked); assertEquals(1, r.keptPosition); assertTrue(r.movedLayers.isEmpty())
        same(before, s.world("child"))
        assertEquals(s.ref("parent"), s.parentOf(s.ref("child"))); assertEquals(listOf(s.ref("child")), s.childrenOf(s.ref("parent")))
    }

    @Test fun movingRotatingAndScalingTheParentCarriesTheChild() {
        val s = session(); s.rect("child", 350.0, 300.0); s.rect("parent", 200.0, 200.0, 20.0)
        s.setParent(listOf(s.ref("child")), s.ref("parent"))
        val childBefore = s.world("child"); val parentBefore = s.world("parent")
        val p = LayerTarget(s.ref("parent"))
        TransformOps.translateDoc(s.index, p, 0.0, 40.0, -15.0)
        TransformOps.rotateAboutDoc(s.index, p, 0.0, 200.0, 200.0, 35.0)
        TransformOps.scaleAboutDoc(s.index, p, 0.0, 200.0, 200.0, 1.5)
        val delta = s.world("parent") * parentBefore.inverse()!!                      // what happened to the parent in doc space
        same(delta * childBefore, s.world("child"), 1e-2)                              // the child moved rigidly with it
    }

    @Test fun parentAnimationIsInheritedAtEveryFrame() {
        val s = session(); s.rect("child", 350.0, 256.0); s.rect("parent", 256.0, 256.0)
        s.setParent(listOf(s.ref("child")), s.ref("parent"))
        val pt = LayerTarget(s.ref("parent"))
        s.frame = 0.0; s.addKeyframe(s.animatedProps(pt).first { it.key == "r" && it.depth == 0 }); s.frame = 90.0; s.setRotation(pt, 90.0)
        val c = s.world("child", 90.0)
        near(256.0, c.mapX(0.0, 0.0), 0.05); near(350.0, c.mapY(0.0, 0.0), 0.05)       // (350,256) turned 90° about (256,256) -> (256,350)
    }

    @Test fun detachingAndChangingParentDoNotMoveTheChild() {
        val s = session(); s.rect("child", 350.0, 300.0, 15.0); s.rect("b", 120.0, 400.0, -40.0, 60.0); s.rect("a", 200.0, 200.0, 30.0, 150.0)
        val before = s.world("child")
        s.setParent(listOf(s.ref("child")), s.ref("a")); same(before, s.world("child"))
        s.setParent(listOf(s.ref("child")), s.ref("b")); same(before, s.world("child")); assertEquals(s.ref("b"), s.parentOf(s.ref("child")))
        s.setParent(listOf(s.ref("child")), null); same(before, s.world("child")); assertNull(s.parentOf(s.ref("child")))
    }

    @Test fun cyclesAndForeignCompositionsAreRefusedWithoutChangingAnything() {
        val s = session(); s.rect("c", 100.0, 100.0); s.rect("b", 200.0, 100.0); s.rect("a", 300.0, 100.0)
        s.setParent(listOf(s.ref("b")), s.ref("a")); s.setParent(listOf(s.ref("c")), s.ref("b"))     // a <- b <- c
        val snapshot = s.doc.root.deepCopy(); val undo = s.history.canUndo
        assertEquals(OpResult.Reason.WOULD_NEST_INTO_SELF, s.setParent(listOf(s.ref("a")), s.ref("c")).refused)   // a -> c would loop
        assertEquals(OpResult.Reason.WOULD_NEST_INTO_SELF, s.setParent(listOf(s.ref("a")), s.ref("b")).refused)
        assertEquals(OpResult.Reason.WOULD_NEST_INTO_SELF, s.setParent(listOf(s.ref("a")), s.ref("a")).refused)
        assertEquals(OpResult.Reason.INVALID, s.setParent(listOf(s.ref("a")), LayerRef("someAsset", 0)).refused)
        assertEquals(snapshot, s.doc.root); assertEquals(undo, s.history.canUndo)
        assertEquals(setOf(s.ref("c"), s.ref("b")), ParentOps.descendants(s.index, s.ref("a")))
        assertFalse(s.ref("c") in s.parentCandidates(listOf(s.ref("b"))))                            // a child can't become its own parent's parent
        assertTrue(s.ref("a") in s.parentCandidates(listOf(s.ref("c"))))
    }

    @Test fun layersThatCannotKeepTheirPositionAreLinkedAndReported() {
        val s = session(); s.rect("anim", 350.0, 300.0); s.rect("rot", 350.0, 100.0, 30.0); s.rect("parent", 200.0, 200.0)
        val at = LayerTarget(s.ref("anim"))
        s.frame = 0.0; s.addKeyframe(s.animatedProps(at).first { it.key == "p" && it.depth == 0 }); s.frame = 60.0; s.setPosition(at, 400.0, 300.0); s.frame = 0.0
        val animLocal = s.transformOf(at)!!
        val r = s.setParent(listOf(s.ref("anim")), s.ref("parent"))
        assertEquals(1, r.linked); assertEquals(listOf(s.ref("anim")), r.movedLayers)                 // animated position: AE-style, local values kept
        assertEquals(animLocal.x, s.transformOf(LayerTarget(s.ref("anim")))!!.x)
        s.setScale(LayerTarget(s.ref("parent")), 200.0, 100.0)                                         // squashing parent + rotated child = skew
        val r2 = s.setParent(listOf(s.ref("rot")), s.ref("parent"))
        assertEquals(1, r2.linked); assertEquals(listOf(s.ref("rot")), r2.movedLayers)
    }

    @Test fun severalChildrenAtOnce() {
        val s = session(); s.rect("c1", 100.0, 100.0, 5.0); s.rect("c2", 300.0, 120.0, 25.0, 70.0); s.rect("c3", 200.0, 400.0); s.rect("p", 256.0, 256.0, 40.0, 130.0)
        val before = listOf("c1", "c2", "c3").map { s.world(it) }
        val r = s.setParent(listOf("c1", "c2", "c3").map { s.ref(it) }, s.ref("p"))
        assertEquals(3, r.linked); assertEquals(3, r.keptPosition)
        listOf("c1", "c2", "c3").forEachIndexed { i, n -> same(before[i], s.world(n)) }
        assertEquals(3, s.childrenOf(s.ref("p")).size)
    }

    @Test fun deletingAParentLeavesItsChildrenInPlaceAndReparentsThemUpwards() {
        val s = session(); s.rect("child", 350.0, 300.0, 12.0); s.rect("mid", 250.0, 200.0, 33.0, 120.0); s.rect("top", 150.0, 100.0, -20.0, 90.0)
        s.setParent(listOf(s.ref("child")), s.ref("mid")); s.setParent(listOf(s.ref("mid")), s.ref("top"))
        val before = s.world("child")
        s.select(listOf(LayerTarget(s.ref("mid")))); s.deleteSelection()
        same(before, s.world("child")); assertEquals(s.ref("top"), s.parentOf(s.ref("child")))
        s.select(listOf(LayerTarget(s.ref("top")))); s.deleteSelection()
        same(before, s.world("child")); assertNull(s.parentOf(s.ref("child"))); assertEquals(1, s.doc.layers.size)
    }

    @Test fun aGeneratedNullParentRestsAtIdentityAndPivotsOnTheChildrenCentre() {
        val s = session(); s.rect("a", 156.0, 256.0); s.rect("b", 356.0, 256.0)
        val before = listOf(s.world("a"), s.world("b"))
        val n = s.createNullParent(listOf(s.ref("a"), s.ref("b")))!!
        assertEquals("Parent", s.doc.layers[n.index].name)
        same(before[0], s.world("a")); same(before[1], s.world("b")); same(Affine.IDENTITY, s.world("Parent"))
        val nt = LayerTarget(s.ref("Parent")); s.frame = 0.0
        s.addKeyframe(s.animatedProps(nt).first { it.key == "r" && it.depth == 0 }); s.frame = 90.0; s.setRotation(nt, 90.0)
        val a = s.world("a", 90.0); val b = s.world("b", 90.0)                                         // centre is (256,256): a (156,256) -> (256,156), b (356,256) -> (256,356)
        near(256.0, a.e, 0.05); near(156.0, a.f, 0.05); near(256.0, b.e, 0.05); near(356.0, b.f, 0.05)
        assertEquals(2, s.childrenOf(s.ref("Parent")).size)
    }

    @Test fun parentingIsOneUndoStepAndNothingElseChanges() {
        val s = session(); s.rect("child", 350.0, 300.0, 10.0); s.rect("parent", 200.0, 200.0, 30.0)
        val before = s.doc.root.deepCopy()
        s.setParent(listOf(s.ref("child")), s.ref("parent")); assertNotEquals(before, s.doc.root)
        s.undo(); assertEquals(before, s.doc.root)
        s.redo(); assertEquals(s.ref("parent"), s.parentOf(s.ref("child")))
    }

    @Test fun parentingWorksInsideAPrecomp() {
        val d = docOf("[${layerJson(9, "pre", ks()).replace("\"ty\":4", "\"ty\":0")}]", assets = "[{\"id\":\"c\",\"layers\":[${layerJson(1, "kid", ks(pos = "[300,100,0]"), groupJson(rectJson(), fillJson, trJson))},${layerJson(2, "dad", ks(pos = "[100,100,0]", rot = "45"), groupJson(rectJson(), fillJson, trJson))}]}]")
        val s = EditorSession(d)
        val kid = LayerRef("c", 0); val dad = LayerRef("c", 1)
        val ls = d.precomps.first().layers
        val before = Transforms.layerFull(ls, ls[0], 0.0)
        val r = s.setParent(listOf(kid), dad)
        assertEquals(1, r.keptPosition)
        val ls2 = d.precomps.first().layers; same(before, Transforms.layerFull(ls2, ls2[0], 0.0))
        assertEquals(dad, s.parentOf(kid))
    }
}
