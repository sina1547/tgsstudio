package ir.sina.tgsstudio.renderer

import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneBuilderTest {
    private fun doc(layers: String, assets: String = "[]", extra: String = "") =
        LottieDocument(JsonParser.parse("""{"fr":60,"ip":0,"op":60,"w":512,"h":512,"assets":$assets,"layers":$layers$extra}""") as JsonObj)

    private fun p(v: String) = """{"a":0,"k":$v}"""
    private fun ks(pos: String = "[0,0]", rot: String = "0", scale: String = "[100,100]", o: String = "100") =
        """{"o":${p(o)},"r":${p(rot)},"p":${p(pos)},"a":${p("[0,0]")},"s":${p(scale)}}"""
    private val tr = """{"ty":"tr","p":${p("[0,0]")},"a":${p("[0,0]")},"s":${p("[100,100]")},"r":${p("0")},"o":${p("100")}}"""
    private val rect = """{"ty":"rc","p":${p("[0,0]")},"s":${p("[100,100]")},"r":${p("0")}}"""
    private val fill = """{"ty":"fl","c":${p("[1,0,0,1]")},"o":${p("100")}}"""
    private val stroke = """{"ty":"st","c":${p("[0,0,1,1]")},"o":${p("50")},"w":${p("4")},"lc":2,"lj":2}"""
    private fun shapeLayer(ind: Int, name: String, ks: String, shapes: String, extra: String = "") =
        """{"ind":$ind,"ty":4,"nm":"$name","ks":$ks,"ip":0,"op":60,"st":0,"shapes":[$shapes]$extra}"""
    private fun group(vararg items: String) = """{"ty":"gr","it":[${items.joinToString(",")}]}"""

    @Test fun fillIsPaintedBelowStrokeAndSeesPathsAboveIt() {
        // AE order: path above stroke above fill -> fill paints first, stroke on top
        val d = doc("[${shapeLayer(1, "L", ks(), group(rect, stroke, fill, tr))}]")
        val items = SceneBuilder(d).build(0.0).layers.single().items
        assertEquals(2, items.size)
        assertNull(items[0].stroke); assertNotNull(items[1].stroke)
        near(0.5, items[1].paint.opacity)
        assertEquals(listOf(0, 2), items[0].shapePath); assertEquals(listOf(0, 1), items[1].shapePath)
        assertEquals(1, items[0].contours.size)
    }

    @Test fun paintOnlyAppliesToPathsAboveIt() {
        val ell = """{"ty":"el","p":${p("[0,0]")},"s":${p("[10,10]")}}"""
        val d = doc("[${shapeLayer(1, "L", ks(), group(rect, fill, ell, tr))}]")
        assertEquals(1, SceneBuilder(d).build(0.0).layers.single().items.single().contours.size)
    }

    @Test fun parentChainComposesTransforms() {
        val parent = """{"ind":1,"ty":3,"nm":"P","ks":${ks(pos = "[100,0]", rot = "90")},"ip":0,"op":60}"""
        val child = shapeLayer(2, "C", ks(pos = "[10,0]"), group(rect, fill, tr), ""","parent":1""")
        val scene = SceneBuilder(doc("[$child,$parent]")).build(0.0)
        val m = scene.layers.first { it.name == "C" }.matrix
        // child point (0,0) -> +10 in x, rotated 90deg clockwise -> +10 in y, then parent offset +100 in x
        near(100.0, m.mapX(0.0, 0.0), 1e-6); near(10.0, m.mapY(0.0, 0.0), 1e-6)
    }

    @Test fun layersOutsideTheirTimeRangeAreSkipped() {
        val l = shapeLayer(1, "L", ks(), group(rect, fill, tr)).replace(""""ip":0,"op":60""", """"ip":10,"op":20""")
        val b = SceneBuilder(doc("[$l]"))
        assertEquals(0, b.build(5.0).layers.size); assertEquals(1, b.build(15.0).layers.size); assertEquals(0, b.build(20.0).layers.size)
    }

    @Test fun bottomLayerPaintsFirst() {
        val a = shapeLayer(1, "top", ks(), group(rect, fill, tr)); val b = shapeLayer(2, "bottom", ks(), group(rect, fill, tr))
        assertEquals(listOf("bottom", "top"), SceneBuilder(doc("[$a,$b]")).build(0.0).layers.map { it.name })
    }

    @Test fun precompUsesLocalTimeAndKeepsRefs() {
        val inner = """{"id":"c","layers":[${shapeLayer(1, "in", ks(pos = """[0,0]"""), group(rect, fill, tr))}]}"""
        val pre = """{"ind":1,"ty":0,"nm":"pre","refId":"c","ks":${ks()},"ip":0,"op":60,"st":10}"""
        val s = SceneBuilder(doc("[$pre]", "[$inner]")).build(5.0)   // inner time -5 is outside inner ip..op
        assertEquals(0, s.layers.single().children.size)
        val s2 = SceneBuilder(doc("[$pre]", "[$inner]")).build(20.0)
        val child = s2.layers.single().children.single()
        assertEquals("c", child.ref.scope); assertEquals(0, child.ref.index)
    }

    @Test fun animatedTransformFollowsKeyframes() {
        val anim = """{"a":1,"k":[{"t":0,"s":[0,0],"e":[100,0]},{"t":60,"s":[100,0]}]}"""
        val ksA = """{"o":${p("100")},"r":${p("0")},"p":$anim,"a":${p("[0,0]")},"s":${p("[100,100]")}}"""
        val d = doc("[${shapeLayer(1, "L", ksA, group(rect, fill, tr))}]")
        near(50.0, SceneBuilder(d).build(30.0).layers.single().matrix.e)
    }

    @Test fun reportsUnsupportedFeaturesInsteadOfDroppingSilently() {
        val rep = """{"ty":"rp","c":${p("3")}}"""
        val d = doc("[${shapeLayer(1, "L", ks(), group(rect, fill, rep, tr))}]")
        assertTrue("repeater" in SceneBuilder(d).build(0.0).unsupported)
        val text = """{"ind":2,"ty":5,"nm":"t","ks":${ks()},"ip":0,"op":60}"""
        assertTrue("text layers" in SceneBuilder(doc("[$text]")).build(0.0).unsupported)
    }

    @Test fun trimAppliesToPathsAboveIt() {
        val line = """{"ty":"sh","ks":${p("""{"c":false,"i":[[0,0],[0,0]],"o":[[0,0],[0,0]],"v":[[0,0],[100,0]]}""")}}"""
        val trim = """{"ty":"tm","s":${p("0")},"e":${p("50")},"o":${p("0")},"m":1}"""
        val d = doc("[${shapeLayer(1, "L", ks(), group(line, trim, stroke, tr))}]")
        val c = SceneBuilder(d).build(0.0).layers.single().items.single().contours.single()
        near(50.0, c.length, 0.5)
    }

    @Test fun alphaMatteIsLinked() {
        val src = shapeLayer(1, "matte", ks(), group(rect, fill, tr), ""","td":1""")
        val tgt = shapeLayer(2, "target", ks(), group(rect, fill, tr), ""","tt":1""")
        val s = SceneBuilder(doc("[$src,$tgt]")).build(0.0)
        assertEquals(1, s.layers.size); assertEquals("matte", s.layers[0].matte!!.layer.name)
    }

    @Test fun realisticFixtureBuildsAndReportsTheUnknowns() {
        val d = LottieDocument(JsonParser.parse(javaClass.getResourceAsStream("/sample.json")!!.readBytes().toString(Charsets.UTF_8)) as JsonObj)
        val s = SceneBuilder(d).build(30.0)
        assertTrue(s.layers.isNotEmpty())
        assertTrue("text layers" in s.unsupported); assertTrue(s.unsupported.any { it.startsWith("layer type 99") })
        assertTrue(s.unsupported.any { it.contains("zz_future") })
    }
}
