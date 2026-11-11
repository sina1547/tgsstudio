package ir.sina.tgsstudio.parser

import ir.sina.tgsstudio.document.*
import ir.sina.tgsstudio.document.json.JsonParser
import kotlin.test.*

class TgsCodecTest {
    private fun res(name: String) = TgsCodecTest::class.java.getResourceAsStream("/$name")!!.readBytes()
    private fun openTgs() = TgsCodec.read(res("sample.tgs"))

    @Test fun opensRealGzipTgs() {
        val d = openTgs()
        assertEquals(512, d.width); assertEquals(512, d.height)
        assertEquals(60.0, d.frameRate); assertEquals(3.0, d.durationSeconds)
        assertEquals("نمونه TGS", d.name)
        assertEquals(5, d.layers.size)
        assertEquals(1, d.precomps.size)
    }

    @Test fun plainJsonAlsoOpens() {
        assertEquals(openTgs().root, TgsCodec.read(res("sample.json")).root)
    }

    @Test fun parsesLayerHierarchy() {
        val tree = openTgs().layerTree()
        // Null controller -> "لایه ۱" -> Precomp ; self-parented text and unknown layer are roots
        val null0 = tree.first { it.layer.name == "Null controller" }
        assertEquals(listOf("لایه ۱"), null0.children.map { it.layer.name })
        assertEquals(listOf("Precomp"), null0.children[0].children.map { it.layer.name })
        assertEquals(setOf("Null controller", "Future layer type", "Text"), tree.map { it.layer.name }.toSet())
    }

    @Test fun parsesShapeGroupsAndTypes() {
        val layer = openTgs().layers.first { it.type == LayerType.SHAPE }
        val outer = layer.shapes.single()
        assertEquals(ShapeKind.GROUP, outer.kind)
        assertEquals("گروه \"اصلی\"\n۱", outer.name)
        assertEquals(listOf(ShapeKind.GROUP, ShapeKind.FILL, ShapeKind.TRIM, ShapeKind.UNKNOWN, ShapeKind.TRANSFORM), outer.children.map { it.kind })
        val inner = outer.children[0]
        assertEquals(listOf(ShapeKind.PATH, ShapeKind.GRADIENT_FILL, ShapeKind.TRANSFORM), inner.children.map { it.kind })
    }

    @Test fun readsKeyframes() {
        val d = openTgs()
        val rot = d.layers.first { it.name == "لایه ۱" }.transform!!.rotation!!
        assertTrue(rot.isAnimated); assertEquals(2, rot.keyframeCount); assertNull(rot.staticValue)
        val scale = d.layers.first { it.name == "لایه ۱" }.transform!!.scale!!
        assertFalse(scale.isAnimated); assertNotNull(scale.staticValue)
        assertEquals(DocumentStats(layers = 6, shapeItems = 13, keyframes = 7, precomps = 1, unknownLayerTypes = 1), d.stats())
    }

    @Test fun unchangedDocumentSurvivesGzipRoundTripExactly() {
        val original = res("sample.json").toString(Charsets.UTF_8)
        val d = TgsCodec.read(res("sample.tgs"))
        val again = TgsCodec.read(TgsCodec.writeTgs(d))
        assertEquals(JsonParser.parse(original), again.root) // unknown layers, shapes, fields, markers, meta all intact
        assertTrue(TgsCodec.isGzip(TgsCodec.writeTgs(d)))
    }

    @Test fun editsLeaveUnknownDataAlone() {
        val d = openTgs()
        val layer = d.layers.first { it.name == "لایه ۱" }
        layer.name = "Renamed"
        layer.hidden = true
        d.frameRate = 30.0
        val back = TgsCodec.read(TgsCodec.writeTgs(d))
        val l2 = back.layers.first { it.index == 2 }
        assertEquals("Renamed", l2.name); assertTrue(l2.hidden)
        assertEquals(30.0, back.frameRate)
        assertEquals(true, l2.raw.bool("x_layer_extra"))
        assertEquals("me", l2.shapes.single().raw.obj("x_custom")!!.string("keep"))
        assertEquals(1, back.layers.first { it.typeCode == 99 }.raw.obj("payload")!!.string("blob")!!.length / 4)
        assertNotNull(back.root.arr("markers")); assertNotNull(back.root.obj("meta"))
    }

    @Test fun rejectsGarbage() {
        assertFailsWith<DocumentException> { TgsCodec.read(byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3)) }
        assertFailsWith<DocumentException> { TgsCodec.read("not json".toByteArray()) }
        assertFailsWith<DocumentException> { TgsCodec.read("[1,2]".toByteArray()) }
        assertFailsWith<DocumentException> { TgsCodec.read("{\"w\":1}".toByteArray()) }
    }

    @Test fun decompressionBombIsStopped() {
        val big = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(big).use { gz ->
            val chunk = ByteArray(1 shl 20) { ' '.code.toByte() }
            repeat(TgsCodec.MAX_JSON_BYTES / (1 shl 20) + 2) { gz.write(chunk) }
        }
        assertFailsWith<DocumentException> { TgsCodec.read(big.toByteArray()) }
    }

    @Test fun oversizedPlainJsonIsRejected() {
        val big = ByteArray(TgsCodec.MAX_JSON_BYTES + 1) { ' '.code.toByte() }
        val e = assertFailsWith<DocumentException> { TgsCodec.read(big) }
        assertTrue(e.message!!.contains("too large"))
    }
}
