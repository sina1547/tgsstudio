package ir.sina.tgsstudio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import ir.sina.tgsstudio.editor.Mask
import java.nio.ByteBuffer
import kotlin.math.ceil

/**
 * Renders text with Android's shaping/layout engine before vector conversion.
 * Supports imported TTF/OTF faces, mixed RTL/LTR text and explicit line breaks.
 * TGS has no live-text primitive, so text is converted to editable vector strokes.
 */
object TextRaster {
    fun mask(
        text: String,
        sizePx: Float = 220f,
        bold: Boolean = false,
        typeface: Typeface = Typeface.DEFAULT,
        lineSpacingMultiplier: Float = 1f,
    ): Mask? {
        if (text.isBlank()) return null
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx.coerceIn(12f, 600f)
            color = android.graphics.Color.BLACK
            this.typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val measuredWidth = text.split('\n').maxOfOrNull { paint.measureText(it) } ?: 0f
        val pad = 12
        val layoutWidth = ceil(measuredWidth).toInt().coerceIn(8, 4096)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setLineSpacing(0f, lineSpacingMultiplier.coerceIn(0.8f, 2.5f))
            .build()
        val w = (layoutWidth + pad * 2).coerceIn(8, 4096)
        val h = (layout.height + pad * 2).coerceIn(8, 2048)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bmp)
        canvas.save()
        canvas.translate(pad.toFloat(), pad.toFloat())
        layout.draw(canvas)
        canvas.restore()
        val buf = ByteBuffer.allocate(bmp.byteCount)
        bmp.copyPixelsToBuffer(buf)
        val bytes = buf.array()
        val stride = bmp.rowBytes
        val ink = BooleanArray(w * h) { i ->
            (bytes[(i / w) * stride + (i % w)].toInt() and 0xFF) > 127
        }
        bmp.recycle()
        return Mask(w, h, ink)
    }
}
