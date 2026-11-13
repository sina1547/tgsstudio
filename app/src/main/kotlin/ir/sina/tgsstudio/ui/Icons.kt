package ir.sina.tgsstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Ic {
    Back, Play, Pause, Prev, Next, Loop, Eye, EyeOff, Lock, Unlock, Undo, Redo, Save, Export, More, Preview,
    Select, Hand, Pen, Node, Rect, Ellipse, Star, Polygon, Text, Svg, Layers, Props, Timeline, Add,
    Close, Plus, Minus, Trash, Copy, Group, Ungroup, Up, Down, Check, Diamond, Folder, Shape, NullLayer, Precomp, Fit,
    Chevron, ChevronDown, Settings, Info, Path, Key,
}

/** Icons are drawn on a 24-unit grid so the whole set shares one stroke style. */
@Composable
fun Icon(ic: Ic, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        drawIcon(ic, tint, u)
    }
}

private fun DrawScope.drawIcon(ic: Ic, c: Color, u: Float) {
    val st = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float) = drawLine(c, p(x0, y0), p(x1, y1), 1.8f * u, StrokeCap.Round)
    fun path(build: Path.() -> Unit, fill: Boolean = false) = drawPath(Path().apply(build), c, style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else st)
    fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    fun Path.cubic(a: Float, b: Float, cx: Float, d: Float, e: Float, f: Float) = cubicTo(a * u, b * u, cx * u, d * u, e * u, f * u)
    fun circle(x: Float, y: Float, r: Float, fill: Boolean = false) = drawCircle(c, r * u, p(x, y), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else st)
    fun rect(x: Float, y: Float, w: Float, h: Float, fill: Boolean = false) =
        drawRect(c, p(x, y), Size(w * u, h * u), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else st)

    when (ic) {
        Ic.Back -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
        Ic.Play -> path({ m(8f, 5f); l(19f, 12f); l(8f, 19f); close() }, fill = true)
        Ic.Pause -> { rect(6f, 5f, 4f, 14f, true); rect(14f, 5f, 4f, 14f, true) }
        Ic.Prev -> { rect(5f, 5f, 2.5f, 14f, true); path({ m(19f, 5f); l(9f, 12f); l(19f, 19f); close() }, fill = true) }
        Ic.Next -> { rect(16.5f, 5f, 2.5f, 14f, true); path({ m(5f, 5f); l(15f, 12f); l(5f, 19f); close() }, fill = true) }
        Ic.Loop -> { path({ m(5f, 12f); cubic(5f, 8f, 8f, 6f, 12f, 6f); l(18f, 6f) }); path({ m(15f, 3f); l(18f, 6f); l(15f, 9f) })
            path({ m(19f, 12f); cubic(19f, 16f, 16f, 18f, 12f, 18f); l(6f, 18f) }); path({ m(9f, 15f); l(6f, 18f); l(9f, 21f) }) }
        Ic.Eye -> { path({ m(2f, 12f); cubic(5f, 6f, 9f, 5f, 12f, 5f); cubic(15f, 5f, 19f, 6f, 22f, 12f); cubic(19f, 18f, 15f, 19f, 12f, 19f); cubic(9f, 19f, 5f, 18f, 2f, 12f) }); circle(12f, 12f, 3f) }
        Ic.EyeOff -> { path({ m(2f, 12f); cubic(5f, 6f, 9f, 5f, 12f, 5f); cubic(15f, 5f, 19f, 6f, 22f, 12f); cubic(19f, 18f, 15f, 19f, 12f, 19f); cubic(9f, 19f, 5f, 18f, 2f, 12f) }); line(4f, 20f, 20f, 4f) }
        Ic.Lock -> { rect(5f, 11f, 14f, 10f); path({ m(8f, 11f); l(8f, 8f); cubic(8f, 2f, 16f, 2f, 16f, 8f); l(16f, 11f) }) }
        Ic.Unlock -> { rect(5f, 11f, 14f, 10f); path({ m(8f, 11f); l(8f, 8f); cubic(8f, 2f, 16f, 2f, 16f, 6f) }) }
        Ic.Undo -> { path({ m(9f, 6f); l(4f, 11f); l(9f, 16f) }); path({ m(4f, 11f); l(14f, 11f); cubic(19f, 11f, 20f, 18f, 14f, 18f); l(10f, 18f) }) }
        Ic.Redo -> { path({ m(15f, 6f); l(20f, 11f); l(15f, 16f) }); path({ m(20f, 11f); l(10f, 11f); cubic(5f, 11f, 4f, 18f, 10f, 18f); l(14f, 18f) }) }
        Ic.Save -> { path({ m(5f, 4f); l(16f, 4f); l(20f, 8f); l(20f, 20f); l(5f, 20f); close() }); rect(8f, 4f, 7f, 5f); rect(8f, 13f, 8f, 7f) }
        Ic.Export -> { line(12f, 15f, 12f, 4f); path({ m(8f, 8f); l(12f, 4f); l(16f, 8f) }); path({ m(5f, 13f); l(5f, 20f); l(19f, 20f); l(19f, 13f) }) }
        Ic.More -> { circle(12f, 5f, 1.6f, true); circle(12f, 12f, 1.6f, true); circle(12f, 19f, 1.6f, true) }
        Ic.Preview -> { path({ m(7f, 5f); l(19f, 12f); l(7f, 19f); close() }); }
        Ic.Select -> path({ m(6f, 3f); l(6f, 19f); l(10f, 15f); l(13f, 21f); l(15.5f, 20f); l(12.5f, 14f); l(18f, 14f); close() })
        Ic.Hand -> { path({ m(8f, 13f); l(8f, 6f); cubic(8f, 4f, 11f, 4f, 11f, 6f); l(11f, 11f); l(11f, 4f); cubic(11f, 2f, 14f, 2f, 14f, 4f); l(14f, 11f); l(14f, 6f); cubic(14f, 4.5f, 17f, 4.5f, 17f, 6f); l(17f, 14f); cubic(17f, 19f, 14f, 21f, 12f, 21f); cubic(9f, 21f, 7f, 18f, 5f, 15f); cubic(4f, 13f, 6.5f, 12f, 8f, 13f) }) }
        Ic.Pen -> { path({ m(4f, 20f); l(5f, 15f); l(16f, 4f); l(20f, 8f); l(9f, 19f); close() }); line(13f, 7f, 17f, 11f) }
        Ic.Node -> { path({ m(4f, 18f); cubic(6f, 6f, 14f, 6f, 20f, 8f) }); rect(2.5f, 16.5f, 3.5f, 3.5f, true); rect(18.5f, 6f, 3.5f, 3.5f, true) }
        Ic.Rect -> rect(4f, 6f, 16f, 12f)
        Ic.Ellipse -> circle(12f, 12f, 8f)
        Ic.Star -> path({ m(12f, 3f); l(14.6f, 9f); l(21f, 9.5f); l(16f, 13.8f); l(17.7f, 20.3f); l(12f, 16.8f); l(6.3f, 20.3f); l(8f, 13.8f); l(3f, 9.5f); l(9.4f, 9f); close() })
        Ic.Polygon -> path({ m(12f, 3f); l(20f, 9f); l(17f, 19f); l(7f, 19f); l(4f, 9f); close() })
        Ic.Text -> { line(5f, 6f, 19f, 6f); line(12f, 6f, 12f, 19f); line(9f, 19f, 15f, 19f) }
        Ic.Svg -> { rect(4f, 4f, 16f, 16f); path({ m(7f, 15f); cubic(9f, 7f, 14f, 7f, 17f, 15f) }) }
        Ic.Layers -> { path({ m(12f, 3f); l(21f, 8f); l(12f, 13f); l(3f, 8f); close() }); path({ m(3f, 12f); l(12f, 17f); l(21f, 12f) }); path({ m(3f, 16f); l(12f, 21f); l(21f, 16f) }) }
        Ic.Props -> { line(4f, 7f, 20f, 7f); line(4f, 17f, 20f, 17f); circle(9f, 7f, 2.2f); circle(15f, 17f, 2.2f) }
        Ic.Timeline -> { circle(12f, 12f, 8.5f); line(12f, 7f, 12f, 12f); line(12f, 12f, 16f, 14f) }
        Ic.Add -> { rect(4f, 4f, 16f, 16f); line(12f, 8f, 12f, 16f); line(8f, 12f, 16f, 12f) }
        Ic.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Ic.Plus -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        Ic.Minus -> line(5f, 12f, 19f, 12f)
        Ic.Trash -> { line(4f, 7f, 20f, 7f); path({ m(6f, 7f); l(7f, 20f); l(17f, 20f); l(18f, 7f) }); path({ m(9f, 7f); l(9f, 4f); l(15f, 4f); l(15f, 7f) }) }
        Ic.Copy -> { rect(8f, 8f, 12f, 12f); path({ m(16f, 8f); l(16f, 4f); l(4f, 4f); l(4f, 16f); l(8f, 16f) }) }
        Ic.Group -> { rect(3f, 3f, 9f, 9f); rect(12f, 12f, 9f, 9f) }
        Ic.Ungroup -> { rect(3f, 3f, 9f, 9f); rect(12f, 12f, 9f, 9f); line(12f, 12f, 12f, 12f) }
        Ic.Up -> { path({ m(6f, 15f); l(12f, 8f); l(18f, 15f) }) }
        Ic.Down -> { path({ m(6f, 9f); l(12f, 16f); l(18f, 9f) }) }
        Ic.Check -> path({ m(5f, 13f); l(10f, 18f); l(19f, 7f) })
        Ic.Diamond -> path({ m(12f, 3f); l(21f, 12f); l(12f, 21f); l(3f, 12f); close() }, fill = true)
        Ic.Folder -> path({ m(3f, 7f); l(10f, 7f); l(12f, 9f); l(21f, 9f); l(21f, 19f); l(3f, 19f); close() })
        Ic.Shape -> { circle(9f, 9f, 5f); rect(11f, 11f, 9f, 9f) }
        Ic.NullLayer -> { line(12f, 4f, 12f, 20f); line(4f, 12f, 20f, 12f); circle(12f, 12f, 3f) }
        Ic.Precomp -> { rect(4f, 7f, 13f, 13f); path({ m(8f, 7f); l(8f, 4f); l(20f, 4f); l(20f, 16f); l(17f, 16f) }) }
        Ic.Fit -> { path({ m(4f, 9f); l(4f, 4f); l(9f, 4f) }); path({ m(15f, 4f); l(20f, 4f); l(20f, 9f) }); path({ m(20f, 15f); l(20f, 20f); l(15f, 20f) }); path({ m(9f, 20f); l(4f, 20f); l(4f, 15f) }) }
        Ic.Chevron -> path({ m(9f, 6f); l(15f, 12f); l(9f, 18f) })
        Ic.ChevronDown -> path({ m(6f, 9f); l(12f, 15f); l(18f, 9f) })
        Ic.Settings -> { circle(12f, 12f, 3.2f); circle(12f, 12f, 8f) }
        Ic.Info -> { circle(12f, 12f, 9f); line(12f, 11f, 12f, 17f); circle(12f, 7.5f, 0.6f, true) }
        Ic.Path -> { path({ m(4f, 18f); cubic(8f, 4f, 16f, 4f, 20f, 18f) }); circle(4f, 18f, 1.8f, true); circle(20f, 18f, 1.8f, true) }
        Ic.Key -> path({ m(12f, 4f); l(19f, 12f); l(12f, 20f); l(5f, 12f); close() })
    }
}
