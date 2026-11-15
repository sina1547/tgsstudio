package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.LayerRef
import ir.sina.tgsstudio.document.ShapeRef
import ir.sina.tgsstudio.document.json.deepCopy
import ir.sina.tgsstudio.renderer.SceneBuilder
import kotlin.test.*

class TgsPrepareTest {
    @Test fun starAndPolygonToolsCreateStandardPaths() {
        val s = EditorSession(Creator.blankDocument("t"))
        s.addStar(256.0, 256.0, 100.0); s.addPolygon(256.0, 256.0, 100.0)
        val types = s.doc.layers.flatMap { l -> l.shapes.flatMap { g -> g.children.map { it.typeCode } } }
        assertTrue("sr" !in types, types.toString()); assertEquals(2, types.count { it == "sh" })
        // and they still behave like any path: editable in the node editor
        s.select(listOf(ShapeTarget(ShapeRef(LayerRef(null, 0), listOf(0)))))
        assertNotNull(s.pathShape()); assertEquals(6, s.pathBezier(s.pathShape()!!)!!.count)   // the default polygon is a hexagon
    }

    @Test fun staticStarsAreConvertedOnExportWithoutChangingTheDrawing() {
        val doc = docOf("[${layerJson(1, "L", ks(pos = "[256,256,0]"), groupJson("""{"ty":"sr","sy":1,"p":${p("[0,0]")},"r":${p("0")},"pt":${p("5")},"or":${p("80")},"ir":${p("35")},"os":${p("0")},"is":${p("0")}}""", fillJson, trJson))}]")
        val before = SceneBuilder(doc).build(0.0).layers.single().items.single().contours.single().length
        val original = doc.root.deepCopy()
        val (copy, report) = TgsPrepare.repaired(doc)
        assertEquals(1, report.starsConverted); assertEquals(original, doc.root, "exporting must not modify the open project")
        assertEquals("sh", copy.layers.single().shapes.single().children.first().typeCode)
        near(before, SceneBuilder(copy).build(0.0).layers.single().items.single().contours.single().length, 1e-2)   // coordinates are stored to 3 decimals
    }

    @Test fun animatedOrRoundedStarsAreLeftAloneForTheValidatorToReport() {
        val anim = """{"a":1,"k":[{"t":0,"s":[60],"i":{"x":[0.8],"y":[0.8]},"o":{"x":[0.2],"y":[0.2]}},{"t":30,"s":[90]}]}"""
        val animated = docOf("[${layerJson(1, "L", ks(), groupJson("""{"ty":"sr","sy":1,"p":${p("[0,0]")},"r":${p("0")},"pt":${p("5")},"or":$anim,"ir":${p("30")},"os":${p("0")},"is":${p("0")}}""", fillJson, trJson))}]")
        assertEquals(0, TgsPrepare.repaired(animated).second.starsConverted)
        val rounded = docOf("[${layerJson(1, "L", ks(), groupJson("""{"ty":"sr","sy":1,"p":${p("[0,0]")},"r":${p("0")},"pt":${p("5")},"or":${p("80")},"ir":${p("30")},"os":${p("40")},"is":${p("0")}}""", fillJson, trJson))}]")
        assertEquals(0, TgsPrepare.repaired(rounded).second.starsConverted)
    }

    @Test fun keyframeRepairIsReportedAndLeavesValidFilesUntouched() {
        val bad = docOf("""[{"ind":1,"ty":3,"nm":"n","ks":{"o":${p("100")},"r":${p("0")},"p":{"a":1,"k":[{"t":0,"s":[0,0,0]},{"t":60,"s":[100,0,0]}]},"a":${p("[0,0,0]")},"s":${p("[100,100,100]")}},"ip":0,"op":180}]""")
        assertEquals(1, TgsPrepare.repaired(bad).second.keyframeListsRepaired)
        val s = EditorSession(Creator.blankDocument("t")); s.addRect(256.0, 256.0, 50.0, 50.0)
        assertFalse(TgsPrepare.repaired(s.doc).second.any)
    }
}
