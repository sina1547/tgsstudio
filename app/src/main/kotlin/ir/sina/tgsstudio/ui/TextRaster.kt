package ir.sina.tgsstudio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import ir.sina.tgsstudio.editor.Mask
import java.nio.ByteBuffer

/**
 * Renders a line of text with the system font into a black/white mask. Android's text engine does the
 * shaping, so Persian/Arabic letters join and run right to left correctly; [ir.sina.tgsstudio.editor.Skeleton]
 * then reduces the ink to a single hand-drawn style centerline.
 */
object TextRaster {
    fun mask(text: String, sizePx: Float = 220f, bold: Boolean = false): Mask? {
        if (text.isBlank()) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx; color = 0xFF000000.toInt(); typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val b = Rect(); paint.getTextBounds(text, 0, text.length, b)
        val pad = 10
        val w = (b.width() + pad * 2).coerceIn(8, 4096); val h = (b.height() + pad * 2).coerceIn(8, 2048)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        Canvas(bmp).drawText(text, (pad - b.left).toFloat(), (pad - b.top).toFloat(), paint)
        val buf = ByteBuffer.allocate(bmp.byteCount); bmp.copyPixelsToBuffer(buf)
        val bytes = buf.array()
        val stride = bmp.rowBytes
        val ink = BooleanArray(w * h) { i -> (bytes[(i / w) * stride + (i % w)].toInt() and 0xFF) > 127 }
        bmp.recycle()
        return Mask(w, h, ink)
    }
}
