package ir.sina.tgsstudio.document

import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayerTreeTest {
    private fun doc(layers: String) =
        LottieDocument(JsonParser.parse("""{"fr":30,"ip":0,"op":30,"w":8,"h":8,"layers":$layers}""") as JsonObj)

    @Test fun buildsParentChildTree() {
        val d = doc("""[{"ind":1,"ty":3,"nm":"a"},{"ind":2,"ty":4,"nm":"b","parent":1},{"ind":3,"ty":4,"nm":"c","parent":1},{"ind":4,"ty":4,"nm":"d"}]""")
        val t = d.layerTree()
        assertEquals(listOf("a", "d"), t.map { it.layer.name })
        assertEquals(listOf("b", "c"), t[0].children.map { it.layer.name })
    }

    @Test fun missingParentBecomesRoot() {
        val t = doc("""[{"ind":1,"ty":4,"parent":99}]""").layerTree()
        assertEquals(1, t.size)
    }

    @Test fun parentCyclesDoNotHideLayers() {
        val d = doc("""[{"ind":1,"ty":4,"nm":"a","parent":2},{"ind":2,"ty":4,"nm":"b","parent":1},{"ind":3,"ty":4,"nm":"self","parent":3}]""")
        fun count(n: LayerNode): Int = 1 + n.children.sumOf(::count)
        assertEquals(3, d.layerTree().sumOf(::count))
    }

    @Test fun rejectsNonLottie() {
        assertFailsWith<DocumentException> { LottieDocument(JsonParser.parse("{}") as JsonObj) }
    }
}
