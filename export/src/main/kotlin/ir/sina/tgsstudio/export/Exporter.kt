package ir.sina.tgsstudio.export

import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.parser.TgsCodec
import ir.sina.tgsstudio.renderer.SceneBuilder

/** Output formats. All work offline and in memory. */
object Exporter {
    fun tgs(doc: LottieDocument): ByteArray = TgsCodec.writeTgs(doc)
    fun lottieJson(doc: LottieDocument): String = TgsCodec.toJson(doc)
    fun svgFrame(doc: LottieDocument, frame: Double): String = SceneSvg.toSvg(SceneBuilder(doc).build(frame))

    /** Compressed size in bytes, for the size meter. */
    fun tgsSize(doc: LottieDocument): Int = tgs(doc).size
}
