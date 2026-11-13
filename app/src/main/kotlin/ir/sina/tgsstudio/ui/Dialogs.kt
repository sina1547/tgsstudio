package ir.sina.tgsstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.export.Exporter
import ir.sina.tgsstudio.export.Issue
import ir.sina.tgsstudio.export.Severity
import ir.sina.tgsstudio.export.Validator
import java.util.Locale

@Composable
private fun DialogTitle(text: String) = Text(text, color = StudioColors.TextPrimary)

@Composable
fun issueText(i: Issue): String = when (i.code) {
    "canvas_size" -> tr("Canvas is ${i.detail}; Telegram needs 512×512.", "اندازهٔ بوم ${i.detail} است؛ تلگرام ۵۱۲×۵۱۲ می‌خواهد.")
    "duration" -> tr("Duration is ${i.detail} s; the maximum is 3 s.", "مدت ${i.detail} ثانیه است؛ حداکثر ۳ ثانیه.")
    "frame_rate" -> tr("Frame rate is ${i.detail}; use 30 or 60 fps.", "نرخ فریم ${i.detail} است؛ ۳۰ یا ۶۰ فریم بر ثانیه استفاده کنید.")
    "file_size" -> tr("Compressed size is ${i.detail}; the limit is 64 KB.", "حجم فشرده ${i.detail} است؛ سقف ۶۴ کیلوبایت.")
    "empty_range" -> tr("The animation has no frames (${i.detail}).", "انیمیشن فریمی ندارد (${i.detail}).")
    "duplicate_layer_index" -> tr("Two layers share the same index (${i.detail}).", "دو لایه شمارهٔ یکسان دارند (${i.detail}).")
    "missing_parent" -> tr("Layer ${i.detail} points to a parent that doesn't exist.", "لایهٔ ${i.detail} به والدی اشاره می‌کند که وجود ندارد.")
    "parent_cycle" -> tr("Layer ${i.detail} is its own ancestor.", "لایهٔ ${i.detail} والدِ خودش است.")
    "layer_range" -> tr("Layer ${i.detail} ends before it starts.", "لایهٔ ${i.detail} پیش از شروع تمام می‌شود.")
    "missing_precomp" -> tr("Missing precomp ${i.detail}.", "پیش‌ترکیب ${i.detail} پیدا نشد.")
    "keyframes_unsorted" -> tr("Keyframes are out of order at ${i.detail}.", "کیفریم‌ها در ${i.detail} ترتیب ندارند.")
    "bad_keyframe" -> tr("A keyframe is invalid at ${i.detail}.", "یک کیفریم در ${i.detail} نامعتبر است.")
    "unsupported" -> tr("Telegram may reject or ignore: ${i.detail}.", "تلگرام ممکن است این را رد یا نادیده بگیرد: ${i.detail}.")
    "outside_canvas" -> tr("Some content leaves the canvas.", "بخشی از محتوا بیرون از بوم است.")
    "layer_without_index" -> tr("Layer ${i.detail} has no index.", "لایهٔ ${i.detail} شماره ندارد.")
    else -> i.code
}

enum class ExportKind(val mime: String, val ext: String) { TGS("application/octet-stream", "tgs"), JSON("application/json", "json"), SVG("image/svg+xml", "svg") }

/** Checks the document against Telegram's sticker limits, shows every problem, and lets the user export anyway. */
@Composable
fun ExportDialog(doc: LottieDocument, onExport: (ExportKind) -> Unit, onDismiss: () -> Unit) {
    val issues = remember { Validator.validate(doc) }
    val size = remember { Exporter.tgsSize(doc) }
    val errors = issues.count { it.severity == Severity.ERROR }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { DialogTitle(tr("Export", "خروجی")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("TGS size: ${String.format(Locale.US, "%.1f", size / 1024.0)} KB of 64 KB", "حجم TGS: ${String.format(Locale.US, "%.1f", size / 1024.0)} از ۶۴ کیلوبایت"), color = if (size > Validator.MAX_BYTES) StudioColors.Danger else StudioColors.TextPrimary, fontSize = 14.sp)
                if (issues.isEmpty()) Text(tr("✓ Meets Telegram's sticker limits.", "✓ با محدودیت‌های استیکر تلگرام سازگار است."), color = StudioColors.Green, fontSize = 14.sp)
                else issues.forEach { i ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dot(if (i.severity == Severity.ERROR) StudioColors.Danger else StudioColors.Warning, 9.dp)
                        Text(issueText(i), color = StudioColors.TextPrimary, fontSize = 13.sp)
                    }
                }
                if (errors > 0) Text(tr("Telegram will probably reject this file. You can still export it.", "تلگرام احتمالاً این فایل را نمی‌پذیرد. باز هم می‌توانید خروجی بگیرید."), color = StudioColors.TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    SmallButton("TGS", { onExport(ExportKind.TGS) }, primary = errors == 0, ic = Ic.Export)
                    SmallButton("Lottie JSON", { onExport(ExportKind.JSON) })
                    SmallButton(tr("SVG (frame)", "SVG (فریم)"), { onExport(ExportKind.SVG) })
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Close", "بستن"), color = StudioColors.Accent) } },
    )
}

@Composable
fun SettingsDialog(current: String, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { DialogTitle(tr("Settings", "تنظیمات")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Language", "زبان"), color = StudioColors.TextMuted, fontSize = 13.sp)
                listOf("system" to tr("System default", "پیش‌فرض سیستم"), "fa" to "فارسی", "en" to "English").forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (current == code) StudioColors.AccentSoft else StudioColors.PanelRaised)
                            .clickable { onChange(code) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(label, color = StudioColors.TextPrimary, modifier = Modifier.weight(1f)); if (current == code) Icon(Ic.Check, Color.White, 18.dp) }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Done", "تأیید"), color = StudioColors.Accent) } },
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { DialogTitle("SINA TGS Studio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Version 0.2.0", "نسخهٔ ۰٫۲٫۰"), color = StudioColors.TextMuted)
                Text(tr("An offline editor for Telegram animated stickers (TGS). Everything runs on your phone; no account or internet is needed.", "ویرایشگر آفلاین استیکرهای متحرک تلگرام (TGS). همه‌چیز روی گوشی شما اجرا می‌شود؛ حساب کاربری یا اینترنت لازم نیست."), color = StudioColors.TextPrimary, fontSize = 14.sp)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(tr("Close", "بستن"), color = StudioColors.Accent) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, extra: Pair<String, () -> Unit>? = null) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { DialogTitle(title) }, text = { Text(text, color = StudioColors.TextPrimary, fontSize = 14.sp) },
        confirmButton = { TextButton(onConfirm) { Text(confirm, color = StudioColors.Accent) } },
        dismissButton = {
            Row {
                extra?.let { (label, act) -> TextButton(act) { Text(label, color = StudioColors.Danger) } }
                TextButton(onDismiss) { Text(tr("Cancel", "انصراف"), color = StudioColors.TextMuted) }
            }
        },
    )
}

@Composable
fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = StudioColors.Panel,
        title = { DialogTitle(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton({ onDone(text.ifBlank { initial }) }) { Text(tr("OK", "تأیید"), color = StudioColors.Accent) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "انصراف"), color = StudioColors.TextMuted) } },
    )
}
