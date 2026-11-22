package ir.sina.tgsstudio.export

import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.editor.RepairReport
import ir.sina.tgsstudio.editor.TgsPrepare
import ir.sina.tgsstudio.parser.TgsCodec
import ir.sina.tgsstudio.renderer.SceneBuilder

/**
 * Output formats. All work offline and in memory. TGS and Lottie JSON are written from a repaired copy of the
 * document (see [TgsPrepare]) so what leaves the app has the keyframe structure Telegram's players need.
 */
object Exporter {
    fun repairReport(doc: LottieDocument): RepairReport = TgsPrepare.repaired(doc).second

    fun tgs(doc: LottieDocument): ByteArray = TgsCodec.writeTgs(TgsPrepare.repaired(doc).first)
    fun lottieJson(doc: LottieDocument): String = TgsCodec.toJson(TgsPrepare.repaired(doc).first)
    fun svgFrame(doc: LottieDocument, frame: Double): String = SceneSvg.toSvg(SceneBuilder(doc).build(frame))

    /** Compressed size in bytes, for the size meter. */
    fun tgsSize(doc: LottieDocument): Int = tgs(doc).size
}
