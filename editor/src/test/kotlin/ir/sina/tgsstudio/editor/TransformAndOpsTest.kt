package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.renderer.Affine
import ir.sina.tgsstudio.renderer.SceneBuilder
import kotlin.test.*

class TransformAndOpsTest {
    private val L0 = LayerRef(null, 0)

    private fun layerMatrix(d: ir.sina.tgsstudio.document.LottieDocument, ref: LayerRef, frame: Double = 0.0): Affine {
        fun find(layers: List<ir.sina.tgsstudio.renderer.SceneLayer>, outer: Affine): Affine? {
            for (l in layers) { val m = outer * l.matrix; if (l.ref == ref) return m; find(l.children, m)?.let { return it } }
            return null
        }
        return find(SceneBuilder(d).build(frame).layers, Affine.IDENTITY)!!
    }

    private fun docWithParent() = docOf(
        "[${layerJson(2, "child", ks(pos = "[30,10,0]", rot = "20", scale = "[150,150,100]"), groupJson(rectJson(), fillJson, trJson), ""","parent":1""")}," +
            """{"ind":1,"ty":3,"nm":"parent","ks":${ks(pos = "[200,100,0]", rot = "90", scale = "[200,200,100]")},"ip":0,"op":180,"st":0}]""",
    )

    @Test fun translateMovesByExactlyTheDocumentDeltaEvenUnderRotatedScaledParent() {
        val d = docWithParent()
        val before = layerMatrix(d, L0)
        TransformOps.translateDoc(idx(d), LayerTarget(L0), 0.0, 12.0, -7.0)
        val after = layerMatrix(d, L0)
        near(before.e + 12.0, after.e, 1e-6); near(before.f - 7.0, after.f, 1e-6)
        near(before.a, after.a); near(before.d, after.d)
    }

    @Test fun scaleAndRotateKeepTheirPivotFixedOnScreen() {
        val d = docWithParent()
        val (qx, qy) = 260.0 to 140.0
        val m0 = layerMatrix(d, L0); val local = m0.inverse()!!.let { it.mapX(qx, qy) to it.mapY(qx, qy) }
        TransformOps.scaleAboutDoc(idx(d), LayerTarget(L0), 0.0, qx, qy, 1.5)
        val m1 = layerMatrix(d, L0)
        near(qx, m1.mapX(local.first, local.second), 5e-3); near(qy, m1.mapY(local.first, local.second), 5e-3)
        near(m0.a * 1.5, m1.a, 1e-3)
        TransformOps.rotateAboutDoc(idx(d), LayerTarget(L0), 0.0, qx, qy, 30.0)
        val m2 = layerMatrix(d, L0)
        near(qx, m2.mapX(local.first, local.second), 5e-3); near(qy, m2.mapY(local.first, local.second), 5e-3)
    }

    @Test fun shapeGroupTransformsEditInTheirOwnSpace() {
        val d = docOf("[${layerJson(1, "L", ks(pos = "[100,100,0]", scale = "[200,200,100]"), groupJson(rectJson(), fillJson, trJson))}]")
        val t = ShapeTarget(ShapeRef(L0, listOf(0)))
        TransformOps.translateDoc(idx(d), t, 0.0, 40.0, 0.0)   // layer is 2x, so 20 units in group space
        near(20.0, TransformOps.read(idx(d), t, 0.0)!!.x, 1e-6)
        val item = SceneBuilder(d).build(0.0).layers[0].items[0]
        val sceneM = layerMatrix(d, L0) * item.matrix
        near(140.0, sceneM.mapX(0.0, 0.0), 1e-6)   // layer at x=100 plus 2 x 20
    }

    @Test fun editingAnAnimatedPropertyAddsAKeyframeAtTheCurrentFrame() {
        val anim = """{"a":1,"k":[{"t":0,"s":[0,0,0]},{"t":60,"s":[100,0,0]}]}"""
        val d = docOf("""[{"ind":1,"ty":3,"nm":"n","ks":{"o":${p("100")},"r":${p("0")},"p":$anim,"a":${p("[0,0,0]")},"s":${p("[100,100,100]")}},"ip":0,"op":180}]""")
        TransformOps.setPosition(idx(d), LayerTarget(L0), 30.0, 50.0, 25.0)
        val prop = d.layers[0].raw.obj("ks")!!.obj("p")!!
        assertEquals(listOf(0.0, 30.0, 60.0), KeyframeOps.times(prop))
        near(25.0, TransformOps.read(idx(d), LayerTarget(L0), 30.0)!!.y)
    }

    @Test fun layerOpsDeleteDuplicateReorderParent() {
        val d = docWithParent(); val i = idx(d)
        assertEquals(OpResult.Ok(LayerRef(null, 1)), LayerOps.reorder(i, L0, 1))
        assertEquals("child", d.layers[1].name)
        LayerOps.reorder(i, LayerRef(null, 1), 0)
        val dup = LayerOps.duplicate(i, L0) as OpResult.Ok
        assertEquals(3, d.layers.size); assertEquals("child copy", d.layers[0].name)
        assertEquals(3, d.layers.mapNotNull { it.index }.toSet().size)           // unique ind
        assertEquals(LayerRef(null, 0), dup.ref)
        LayerOps.delete(i, LayerRef(null, 2))                                     // delete the parent
        assertTrue(d.layers.all { it.parentIndex == null })
        // cycles are refused
        val d2 = docWithParent()
        val r = LayerOps.setParent(idx(d2), LayerRef(null, 1), LayerRef(null, 0))
        assertEquals(OpResult.Refused(OpResult.Reason.WOULD_NEST_INTO_SELF), r)
    }

    @Test fun groupUngroupAndMove() {
        val d = docOf("[${layerJson(1, "L", ks(), groupJson(rectJson(), fillJson, strokeJson, trJson))}]"); val i = idx(d)
        val g = ShapeOps.group(i, listOf(ShapeRef(L0, listOf(0, 1)), ShapeRef(L0, listOf(0, 2)))) as OpResult.Ok
        val gref = g.ref as ShapeRef
        assertEquals(listOf(0, 1), gref.path)
        assertEquals("gr", i.shapeRaw(gref)!!.string("ty"))
        assertEquals(listOf("fl", "st", "tr"), i.shape(gref)!!.children.map { it.typeCode })
        assertTrue(ShapeOps.ungroup(i, gref) is OpResult.Ok)
        assertEquals(listOf("rc", "fl", "st", "tr"), i.shape(ShapeRef(L0, listOf(0)))!!.children.map { it.typeCode })
        // a transformed group can't be ungrouped without changing the picture
        TransformOps.setPosition(i, ShapeTarget(ShapeRef(L0, listOf(0))), 0.0, 5.0, 5.0)
        // (the group at [0] is the original wrapper group: it has the position set)
        assertEquals(OpResult.Refused(OpResult.Reason.GROUP_HAS_TRANSFORM), ShapeOps.ungroup(i, ShapeRef(L0, listOf(0))))
    }

    @Test fun moveShapeBetweenGroupsAndGuardAgainstSelfNesting() {
        val inner = groupJson(rectJson(), fillJson, trJson)
        val d = docOf("[${layerJson(1, "L", ks(), groupJson(inner, trJson) + "," + groupJson(fillJson, trJson))}]"); val i = idx(d)
        // move the second top-level group's fill into the first group's inner group
        val r = ShapeOps.moveInto(i, ShapeRef(L0, listOf(1, 0)), ShapeRef(L0, listOf(0, 0)))
        assertTrue(r is OpResult.Ok, r.toString())
        assertEquals(listOf("rc", "fl", "fl", "tr"), i.shape(ShapeRef(L0, listOf(0, 0)))!!.children.map { it.typeCode })
        assertEquals(OpResult.Refused(OpResult.Reason.WOULD_NEST_INTO_SELF), ShapeOps.moveInto(i, ShapeRef(L0, listOf(0)), ShapeRef(L0, listOf(0, 0))))
        assertTrue(ShapeOps.moveOut(i, ShapeRef(L0, listOf(0, 0, 0))) is OpResult.Ok)
    }

    @Test fun styleEditing() {
        val d = docOf("[${layerJson(1, "L", ks(), groupJson(rectJson(), fillJson, trJson))}]"); val i = idx(d)
        val t = LayerTarget(L0)
        assertNull(StyleOps.strokeColor(i, t, 0.0))
        val g = i.shapeRaw(ShapeRef(L0, listOf(0)))!!
        StyleOps.setWidth(StyleOps.ensureStroke(g)!!, 0.0, 12.0)
        near(12.0, StyleOps.strokeWidth(i, t, 0.0)!!)
        StyleOps.setColor(StyleOps.firstOf(i, t, ir.sina.tgsstudio.document.ShapeKind.FILL)!!, 0.0, ir.sina.tgsstudio.renderer.Rgba(0.0, 1.0, 0.0, 1.0))
        near(1.0, StyleOps.fillColor(i, t, 0.0)!!.g)
        assertEquals("tr", i.shape(ShapeRef(L0, listOf(0)))!!.children.last().typeCode)  // new items go before the transform
    }
}
