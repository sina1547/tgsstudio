package ir.sina.tgsstudio.export

import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.editor.Creator
import ir.sina.tgsstudio.editor.EditorSession
import ir.sina.tgsstudio.parser.TgsCodec
import kotlin.test.*

class ExportTest {
    private fun codes(d: LottieDocument, sev: Severity? = null) = Validator.validate(d).filter { sev == null || it.severity == sev }.map { it.code }

    @Test fun aFreshProjectIsValid() {
        val s = EditorSession(Creator.blankDocument("x")); s.addRect(256.0, 256.0, 200.0, 200.0); s.addStar(256.0, 256.0, 80.0)
        assertEquals(emptyList(), Validator.validate(s.doc))
    }

    @Test fun reportsTelegramLimits() {
        val d = Creator.blankDocument("x")
        d.root.putInt("w", 800); d.root.putInt("op", 400); d.root.putInt("fr", 24)
        val c = codes(d, Severity.ERROR)
        assertTrue("canvas_size" in c && "duration" in c && "frame_rate" in c, c.toString())
        d.root.putInt("fr", 60); d.root.putInt("op", 180); d.root.putInt("w", 512)
        assertEquals(emptyList(), codes(d))
    }

    @Test fun reportsOversizedFiles() {
        val d = Creator.blankDocument("x")
        val rnd = java.util.Random(7)
        repeat(2500) {
            val c = ir.sina.tgsstudio.renderer.Rgba(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), 1.0)
            val g = Creator.group("g", Creator.rectItem(5 + rnd.nextDouble() * 40, 5 + rnd.nextDouble() * 40, rnd.nextDouble() * 5), Creator.fill(c))
            Creator.insertLayer(d, null, Creator.shapeLayer(d, d.root.arr("layers"), "r$it", rnd.nextDouble() * 512, rnd.nextDouble() * 512, g))
        }
        val issue = assertNotNull(Validator.validate(d).firstOrNull { it.code == "file_size" })
        assertEquals(Severity.ERROR, issue.severity)
        assertTrue(Exporter.tgsSize(d) > Validator.MAX_BYTES)
    }

    @Test fun structuralProblemsAndUnsupportedFeatures() {
        val d = LottieDocument(JsonParser.parse("""{"fr":60,"ip":0,"op":60,"w":512,"h":512,"layers":[
            {"ind":1,"ty":3,"nm":"a","parent":2,"ks":{"o":{"a":0,"k":100},"p":{"a":1,"k":[{"t":10,"s":[0,0]},{"t":5,"s":[1,1]}]}},"ip":0,"op":60},
            {"ind":2,"ty":3,"nm":"b","parent":1,"ks":{},"ip":0,"op":60},
            {"ind":2,"ty":5,"nm":"t","ks":{"o":{"a":0,"k":50,"x":"wiggle(1,2)"}},"ip":0,"op":60}]}""") as JsonObj)
        val c = codes(d)
        for (code in listOf("parent_cycle", "duplicate_layer_index", "keyframes_unsorted", "unsupported")) assertTrue(code in c, "$code in $c")
        val feats = Validator.validate(d).filter { it.code == "unsupported" }.map { it.detail }
        assertTrue("expressions" in feats && "text layers" in feats, feats.toString())
    }

    @Test fun warnsWhenContentLeavesTheCanvas() {
        val s = EditorSession(Creator.blankDocument("x")); s.addRect(600.0, 256.0, 100.0, 100.0)
        assertTrue("outside_canvas" in codes(s.doc, Severity.WARNING))
    }

    @Test fun exportsOpenInTheParserAndAsSvg() {
        val s = EditorSession(Creator.blankDocument("x")); s.addEllipse(256.0, 256.0, 200.0, 100.0, ir.sina.tgsstudio.renderer.Rgba(1.0, 0.0, 0.0, 1.0))
        val tgs = Exporter.tgs(s.doc)
        assertTrue(TgsCodec.isGzip(tgs)); assertEquals(s.doc.root, TgsCodec.read(tgs).root)
        assertEquals(s.doc.root, TgsCodec.fromJson(Exporter.lottieJson(s.doc)).root)
        val svg = Exporter.svgFrame(s.doc, 0.0)
        assertTrue(svg.startsWith("<svg") && svg.contains("fill=\"#ff0000\"") && svg.contains("viewBox=\"0 0 512 512\""))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(org.xml.sax.InputSource(java.io.StringReader(svg)))  // well-formed XML
    }
}
