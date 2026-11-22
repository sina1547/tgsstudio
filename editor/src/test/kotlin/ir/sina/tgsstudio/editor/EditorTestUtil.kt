package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.DocIndex
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParser
import ir.sina.tgsstudio.renderer.Affine
import kotlin.math.abs
import kotlin.test.assertTrue

fun near(expected: Double, actual: Double, eps: Double = 1e-6, msg: String = "") =
    assertTrue(abs(expected - actual) <= eps, "$msg expected $expected but was $actual")

fun nearM(e: Affine, a: Affine, eps: Double = 1e-6) {
    near(e.a, a.a, eps); near(e.b, a.b, eps); near(e.c, a.c, eps); near(e.d, a.d, eps); near(e.e, a.e, eps); near(e.f, a.f, eps)
}

fun docOf(layers: String, assets: String = "[]") =
    LottieDocument(JsonParser.parse("""{"fr":60,"ip":0,"op":180,"w":512,"h":512,"assets":$assets,"layers":$layers}""") as JsonObj)

fun p(v: String) = """{"a":0,"k":$v}"""
fun ks(pos: String = "[0,0,0]", rot: String = "0", scale: String = "[100,100,100]", o: String = "100", a: String = "[0,0,0]") =
    """{"o":${p(o)},"r":${p(rot)},"p":${p(pos)},"a":${p(a)},"s":${p(scale)}}"""
val trJson = """{"ty":"tr","p":${p("[0,0]")},"a":${p("[0,0]")},"s":${p("[100,100]")},"r":${p("0")},"o":${p("100")}}"""
fun rectJson(w: Int = 100, h: Int = 100) = """{"ty":"rc","p":${p("[0,0]")},"s":${p("[$w,$h]")},"r":${p("0")}}"""
val fillJson = """{"ty":"fl","c":${p("[1,0,0,1]")},"o":${p("100")}}"""
val strokeJson = """{"ty":"st","c":${p("[0,0,1,1]")},"o":${p("100")},"w":${p("10")}}"""
fun groupJson(vararg items: String) = """{"ty":"gr","nm":"g","it":[${items.joinToString(",")}]}"""
fun layerJson(ind: Int, name: String, ks: String, shapes: String = "", extra: String = "") =
    """{"ind":$ind,"ty":4,"nm":"$name","ks":$ks,"ip":0,"op":180,"st":0,"shapes":[$shapes]$extra}"""

fun idx(d: LottieDocument) = DocIndex(d)
