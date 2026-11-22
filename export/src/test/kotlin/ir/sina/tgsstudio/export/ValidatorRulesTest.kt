package ir.sina.tgsstudio.export

import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import ir.sina.tgsstudio.editor.Creator
import kotlin.test.*

class ValidatorRulesTest {
    private fun doc(layers: String, extra: String = "", fr: Int = 60) =
        LottieDocument(JsonParser.parse("""{"v":"5.5.2","fr":$fr,"ip":0,"op":60,"w":512,"h":512,"layers":$layers$extra}""") as JsonObj)

    private val ks = """{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[256,256,0]},"a":{"a":0,"k":[0,0,0]},"s":{"a":0,"k":[100,100,100]}}"""
    private fun layer(extra: String, ty: Int = 4, shapes: String = "") =
        """{"ind":1,"ty":$ty,"nm":"L","ks":$ks,"ip":0,"op":60,"st":0,"sr":1,"ao":0,"ddd":0,"bm":0${if (shapes.isNotEmpty()) ""","shapes":[$shapes]""" else ""}$extra}"""

    private fun prohibited(d: LottieDocument) = Validator.validate(d, asExported = false).filter { it.code == "prohibited" }.map { it.detail }.toSet()

    @Test fun frameRateRules() {
        val l = "[${layer("")}]"
        assertTrue(Validator.validate(doc(l, fr = 60)).isEmpty())
        val thirty = Validator.validate(doc(l, fr = 30)); assertEquals(listOf("frame_rate_30" to Severity.WARNING), thirty.map { it.code to it.severity })
        val odd = Validator.validate(doc(l, fr = 24)); assertEquals(listOf("frame_rate" to Severity.ERROR), odd.map { it.code to it.severity })
    }

    @Test fun everyFeatureOnTelegramsListIsReported() {
        val cases = mapOf(
            "masks" to layer(""","masksProperties":[{"mode":"a","pt":{"a":0,"k":{"c":true,"v":[[0,0],[1,0],[1,1]],"i":[[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0]]}}}]"""),
            "layer effects" to layer(""","ef":[{"ty":5}]"""),
            "expressions" to layer("", shapes = "").replace(""""ks":$ks""", """"ks":{"o":{"a":0,"k":100,"x":"wiggle(1,1)"}}"""),
            "solids" to layer(",\"sc\":\"#ff0000\",\"sw\":10,\"sh\":10", ty = 1),
            "images" to layer(",\"refId\":\"img_0\"", ty = 2),
            "texts" to layer("", ty = 5),
            "3D layers" to layer("").replace(""""ddd":0""", """"ddd":1"""),
            "time stretching" to layer("").replace(""""sr":1""", """"sr":2"""),
            "auto-oriented layers" to layer("").replace(""""ao":0""", """"ao":1"""),
            "time remapping" to layer(",\"tm\":{\"a\":0,\"k\":0},\"refId\":\"c\"", ty = 0),
            "merge paths" to layer("", shapes = """{"ty":"gr","it":[{"ty":"mm","mm":1},{"ty":"tr"}]}"""),
            "repeaters" to layer("", shapes = """{"ty":"gr","it":[{"ty":"rp","c":{"a":0,"k":3}},{"ty":"tr"}]}"""),
            "gradient strokes" to layer("", shapes = """{"ty":"gr","it":[{"ty":"gs"},{"ty":"tr"}]}"""),
            "star shapes" to layer("", shapes = """{"ty":"gr","it":[{"ty":"sr","sy":1},{"ty":"tr"}]}"""),
        )
        for ((name, l) in cases) assertTrue(name in prohibited(doc("[$l]")), "$name not reported; got ${prohibited(doc("[$l]"))}")
    }

    @Test fun ordinaryContentIsNotFlagged() {
        // AE writes "ind" and numeric-looking keys on shape items too; they must not be mistaken for layer features
        val shapes = """{"ty":"gr","nm":"g","it":[{"ty":"sh","ind":0,"ix":1,"ks":{"a":0,"k":{"c":true,"v":[[0,0],[10,0],[10,10]],"i":[[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0]]}}},{"ty":"tm","s":{"a":0,"k":0},"e":{"a":0,"k":100},"o":{"a":0,"k":0},"m":1},{"ty":"st","c":{"a":0,"k":[1,1,1,1]},"o":{"a":0,"k":100},"w":{"a":0,"k":4}},{"ty":"tr","p":{"a":0,"k":[0,0]},"a":{"a":0,"k":[0,0]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":100},"sk":{"a":0,"k":0},"sa":{"a":0,"k":0}}]}"""
        val d = doc("""[${layer("", shapes = shapes)}]""", extra = ""","markers":[{"tm":10,"cm":"x","dr":0}]""")
        assertEquals(emptySet(), prohibited(d)); assertTrue(Validator.validate(d).isEmpty(), Validator.validate(d).toString())
    }

    @Test fun keyframeStructureIsCheckedAsIsAndRepairedWhenExporting() {
        val noEasing = """{"a":1,"k":[{"t":0,"s":[0]},{"t":20,"s":[5]},{"t":40,"s":[9]}]}"""
        val d = doc("""[{"ind":1,"ty":3,"nm":"n","ks":${ks.replace(""""o":{"a":0,"k":100}""", """"o":$noEasing""")},"ip":0,"op":60,"st":0,"sr":1,"ao":0,"ddd":0}]""")
        val raw = Validator.validate(d, asExported = false)
        assertEquals(2, raw.count { it.code == "keyframe_no_easing" })
        assertTrue(raw.all { it.severity == Severity.ERROR })
        assertTrue(Validator.validate(d).none { it.code.startsWith("keyframe") }, "the exported copy is repaired")
        val lastHas = doc("""[{"ind":1,"ty":3,"nm":"n","ks":${ks.replace(""""o":{"a":0,"k":100}""", """"o":{"a":1,"k":[{"t":0,"s":[0],"i":{"x":[0.8],"y":[0.8]},"o":{"x":[0.2],"y":[0.2]}},{"t":40,"s":[9],"i":{"x":[0.8],"y":[0.8]},"o":{"x":[0.2],"y":[0.2]}}]}""")},"ip":0,"op":60,"st":0,"sr":1,"ao":0,"ddd":0}]""")
        assertEquals(listOf("keyframe_last_easing"), Validator.validate(lastHas, asExported = false).map { it.code })
        val hold = doc("""[{"ind":1,"ty":3,"nm":"n","ks":${ks.replace(""""o":{"a":0,"k":100}""", """"o":{"a":1,"k":[{"t":0,"s":[0],"h":1},{"t":40,"s":[9]}]}""")},"ip":0,"op":60,"st":0,"sr":1,"ao":0,"ddd":0}]""")
        assertTrue(Validator.validate(hold, asExported = false).isEmpty())
    }

    @Test fun exportingRepairsInsteadOfWritingInvalidKeyframes() {
        val noEasing = """{"a":1,"k":[{"t":0,"s":[0]},{"t":40,"s":[9]}]}"""
        val d = doc("""[{"ind":1,"ty":3,"nm":"n","ks":${ks.replace(""""o":{"a":0,"k":100}""", """"o":$noEasing""")},"ip":0,"op":60,"st":0,"sr":1,"ao":0,"ddd":0}]""")
        val json = Exporter.lottieJson(d)
        assertTrue(Validator.validate(ir.sina.tgsstudio.parser.TgsCodec.fromJson(json), asExported = false).none { it.code.startsWith("keyframe") })
        assertEquals(1, Exporter.repairReport(d).keyframeListsRepaired)
        assertTrue(Validator.validate(Creator.blankDocument("x")).isEmpty())
    }
}
