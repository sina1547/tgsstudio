package ir.sina.tgsstudio.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.renderer.SceneBuilder
import java.io.ByteArrayOutputStream

/** First-frame preview as PNG bytes, drawn with the same code as the canvas. */
fun renderThumbnail(doc: LottieDocument, px: Int = 256): ByteArray {
    val bmp = ImageBitmap(px, px)
    val scene = SceneBuilder(doc).build(doc.inPoint)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(px.toFloat(), px.toFloat())) {
        scale(px.toFloat() / doc.width.coerceAtLeast(1), Offset.Zero) { drawScene(scene) }
    }
    val out = ByteArrayOutputStream()
    bmp.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
