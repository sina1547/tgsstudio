package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.renderer.Bezier
import ir.sina.tgsstudio.renderer.SceneBuilder
import kotlin.test.*

class PathAndHitTest {
    private fun square() = Bezier(true, doubleArrayOf(0.0, 0.0, 100.0, 0.0, 100.0, 100.0, 0.0, 100.0), DoubleArray(8), DoubleArray(8))

    @Test fun insertKeepsTheShapeAndAddsAVertex() {
        val b = square()
        val nb = PathOps.insert(b, 0, 0.5)
        assertEquals(5, nb.count)
        near(50.0, nb.v[2]); near(0.0, nb.v[3])
        near(b.toContour().length, nb.toContour().length, 0.5)
        val curved = PathOps.toSmooth(b, 1)
        val c2 = PathOps.insert(curved, 1, 0.4)
        near(curved.toContour().length, c2.toContour().length, 0.05)    // splitting a curve doesn't change it
        // closing segment
        assertEquals(5, PathOps.insert(b, 3, 0.5).count)
    }

    @Test fun deleteMoveAndClose() {
        val b = square()
        assertEquals(3, PathOps.delete(b, 1)!!.count)
        assertNull(PathOps.delete(PathOps.delete(b, 0)!!.let { PathOps.delete(it, 0)!! }, 0))   // never below 2 vertices
        val m = PathOps.moveVertex(PathOps.toSmooth(b, 1), 1, 120.0, 10.0)
        near(120.0, m.v[2])
        assertFalse(PathOps.setClosed(b, false).closed)
        assertEquals(3, PathOps.setClosed(b, false).toContour().segments.size)
    }

    @Test fun smoothAndCornerHandles() {
        val b = PathOps.toSmooth(square(), 1)
        assertTrue(PathOps.isSmooth(b, 1))
        val moved = PathOps.moveHandle(b, 1, out = true, hx = 100.0 + 0.0, hy = 100.0 - 20.0)   // drag out handle
        assertTrue(PathOps.isSmooth(moved, 1), "opposite handle follows on a smooth vertex")
        val corner = PathOps.toCorner(moved, 1)
        assertFalse(PathOps.isSmooth(corner, 1)); near(0.0, corner.outT[2])
        // dragging one side of a corner leaves the other alone
        val one = PathOps.moveHandle(corner, 1, out = true, hx = 130.0, hy = 100.0)
        near(0.0, one.inT[2]); near(30.0, one.outT[2])
    }

    @Test fun contourRoundTrip() {
        val b = PathOps.toSmooth(PathOps.toSmooth(square(), 0), 2)
        val back = PathOps.fromContour(b.toContour())!!
        near(b.toContour().length, back.toContour().length, 1e-6)
        assertEquals(b.count, back.count)
    }

    @Test fun hitTestPriority() {
        val b = PathOps.toSmooth(square(), 1)
        assertEquals(PathOps.HitType.VERTEX, PathOps.hit(b, 100.5, 0.5, 4.0)!!.type)
        val seg = PathOps.hit(square(), 50.0, 1.0, 4.0)!!
        assertEquals(PathOps.HitType.SEGMENT, seg.type); assertEquals(0, seg.index)
        val outHandle = b.v[2] + b.outT[2]; val outY = b.v[3] + b.outT[3]
        assertEquals(PathOps.HitType.OUT_HANDLE, PathOps.hit(b, outHandle, outY, 3.0, selected = 1)!!.type)
        assertNull(PathOps.hit(b, 50.0, 50.0, 4.0))
    }

    @Test fun animatedPathStructuralEditAppliesToEveryKeyframe() {
        val d = docOf("[${layerJson(1, "L", ks(), groupJson("""{"ty":"sh","ks":${p("null")}}""", fillJson, trJson))}]")
        val shape = idx(d).shapeRaw(ShapeRef(LayerRef(null, 0), listOf(0, 0)))!!
        shape["ks"] = staticPathProp(square())
        PathEdit.write(shape, 0.0, square()); KeyframeOps.addAt(shape.obj("ks")!!, 0.0, isPath = true)
        PathEdit.write(shape, 30.0, PathOps.moveVertex(square(), 1, 150.0, 0.0))
        assertTrue(PathEdit.structural(shape, 0.0) { PathOps.insert(it, 0, 0.5) })
        assertEquals(5, PathEdit.read(shape, 0.0)!!.count); assertEquals(5, PathEdit.read(shape, 30.0)!!.count)
        assertEquals(5, PathEdit.read(shape, 15.0)!!.count)                         // still interpolatable
        assertFalse(PathEdit.structural(shape, 0.0) { null })                         // refused edit changes nothing
        assertEquals(5, PathEdit.read(shape, 0.0)!!.count)
    }

    @Test fun convertRectToEditablePath() {
        val d = docOf("[${layerJson(1, "L", ks(), groupJson(rectJson(100, 60), fillJson, trJson))}]")
        val items = idx(d).shapeRaw(ShapeRef(LayerRef(null, 0), listOf(0)))!!.arr("it")!!
        val path = PathEdit.convertToPath(items, 0, 0.0)!!
        assertEquals("sh", path.string("ty"))
        val c = PathEdit.read(path, 0.0)!!.toContour()
        near(320.0, c.length, 0.01)
        val box = Hit.bounds(SceneBuilder(d).build(0.0), LayerRef(null, 0))!!
        near(100.0, box.width, 0.01); near(60.0, box.height, 0.01)
    }

    @Test fun hitTestingFillsStrokesAndLayerOrder() {
        val top = layerJson(1, "top", ks(pos = "[100,100,0]"), groupJson(rectJson(60, 60), fillJson, trJson))
        val bottom = layerJson(2, "bottom", ks(pos = "[100,100,0]"), groupJson(rectJson(200, 200), fillJson, trJson))
        val ring = layerJson(3, "ring", ks(pos = "[400,400,0]"), groupJson("""{"ty":"el","p":${p("[0,0]")},"s":${p("[100,100]")}}""", strokeJson, trJson))
        val scene = SceneBuilder(docOf("[$top,$bottom,$ring]")).build(0.0)
        assertEquals(LayerRef(null, 0), Hit.at(scene, 100.0, 100.0, 4.0)!!.layer)       // overlap -> topmost
        assertEquals(LayerRef(null, 1), Hit.at(scene, 190.0, 190.0, 4.0)!!.layer)
        assertNull(Hit.at(scene, 400.0, 400.0, 4.0))                                      // hole of a stroke-only ring
        assertEquals(LayerRef(null, 2), Hit.at(scene, 400.0, 350.0, 4.0)!!.layer)         // on the ring
        assertNull(Hit.at(scene, 300.0, 50.0, 4.0))
        assertEquals(listOf(0), Hit.at(scene, 100.0, 100.0, 4.0)!!.shapePath)
    }
}
