package ir.sina.tgsstudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ir.sina.tgsstudio.editor.Easing

/** Interactive cubic-Bezier graph editor. Handle coordinates are normalized to the unit square. */
@Composable
fun GraphEditorDialog(easing: Easing, onChange: (Easing) -> Unit, onDismiss: () -> Unit) {
    val latestEasing = rememberUpdatedState(easing)
    val latestChange = rememberUpdatedState(onChange)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Panel,
        title = { Text(tr("Bezier graph editor", "ویرایشگر نمودار بزیه"), color = StudioColors.TextPrimary) },
        text = {
            Canvas(
                Modifier.fillMaxWidth().height(220.dp).pointerInput(Unit) {
                    var selected = -1
                    detectDragGestures(
                        onDragStart = { point ->
                            val e = latestEasing.value
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val p1 = Offset(e.ox.toFloat() * w, (1f - e.oy.toFloat()) * h)
                            val p2 = Offset(e.ix.toFloat() * w, (1f - e.iy.toFloat()) * h)
                            selected = if ((point - p1).getDistance() <= (point - p2).getDistance()) 0 else 1
                        },
                        onDragEnd = { selected = -1 },
                        onDragCancel = { selected = -1 },
                        onDrag = { change, _ ->
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val x = (change.position.x / w).coerceIn(0f, 1f).toDouble()
                            val y = (1f - change.position.y / h).coerceIn(0f, 1f).toDouble()
                            val e = latestEasing.value
                            latestChange.value(if (selected == 0) e.copy(ox = x, oy = y) else e.copy(ix = x, iy = y))
                            change.consume()
                        },
                    )
                },
            ) {
                val w = size.width
                val h = size.height
                for (i in 0..4) {
                    val x = w * i / 4f
                    val y = h * i / 4f
                    drawLine(StudioColors.Divider, Offset(x, 0f), Offset(x, h), 1f)
                    drawLine(StudioColors.Divider, Offset(0f, y), Offset(w, y), 1f)
                }
                val e = easing
                val p0 = Offset(0f, h)
                val p1 = Offset(e.ox.toFloat() * w, (1f - e.oy.toFloat()) * h)
                val p2 = Offset(e.ix.toFloat() * w, (1f - e.iy.toFloat()) * h)
                val p3 = Offset(w, 0f)
                drawLine(StudioColors.TextMuted, p0, p1, 2f)
                drawLine(StudioColors.TextMuted, p3, p2, 2f)
                val curve = Path().apply { moveTo(p0.x, p0.y); cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y) }
                drawPath(curve, StudioColors.Accent, style = Stroke(width = 4f))
                drawCircle(StudioColors.Violet, 8f, p1)
                drawCircle(StudioColors.Green, 8f, p2)
                drawCircle(Color.White, 5f, p0)
                drawCircle(Color.White, 5f, p3)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Done", "تأیید"), color = StudioColors.Accent) } },
    )
}
