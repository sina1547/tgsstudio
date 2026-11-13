package ir.sina.tgsstudio.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.data.ProjectInfo
import ir.sina.tgsstudio.data.ProjectStore
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Logo(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF1E7BFF), Color(0xFF7C4DDB))), size = this.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.26f))
        val p = Path().apply { moveTo(s * 0.24f, s * 0.70f); cubicTo(s * 0.32f, s * 0.28f, s * 0.62f, s * 0.28f, s * 0.78f, s * 0.52f) }
        drawPath(p, Color.White, style = Stroke(width = s * 0.07f, cap = StrokeCap.Round))
        drawCircle(Color.White, s * 0.06f, Offset(s * 0.24f, s * 0.70f)); drawCircle(Color.White, s * 0.06f, Offset(s * 0.78f, s * 0.52f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    store: ProjectStore, refresh: Int, setting: String,
    onOpen: (ProjectInfo) -> Unit, onNew: (String) -> Unit, onImport: () -> Unit,
    onRename: (ProjectInfo, String) -> Unit, onDelete: (ProjectInfo) -> Unit, onLanguage: (String) -> Unit,
) {
    val projects = remember(refresh) { store.list() }
    var showNew by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleting by remember { mutableStateOf<ProjectInfo?>(null) }
    val fmtDate = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()) }

    LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        Modifier.fillMaxSize().background(StudioColors.Background).systemBarsPadding(),
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Logo(46.dp)
                    Column(Modifier.weight(1f)) {
                        Text("SINA TGS Studio", color = StudioColors.TextPrimary, fontSize = 22.sp)
                        Text(tr("Telegram sticker motion editor", "ویرایشگر انیمیشن استیکر تلگرام"), color = StudioColors.TextMuted, fontSize = 13.sp)
                    }
                    IconBtn(Ic.Settings, { showSettings = true })
                    IconBtn(Ic.Info, { showAbout = true })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton(tr("New Project", "پروژهٔ جدید"), { showNew = true }, Modifier.weight(1f), primary = true, ic = Ic.Plus)
                    SmallButton(tr("Open TGS", "باز کردن TGS"), onImport, Modifier.weight(1f), ic = Ic.Folder)
                }
                Text(tr("Recent Projects", "پروژه‌های اخیر"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                if (projects.isEmpty()) Text(tr("No projects yet. Create one or open a .tgs file.", "هنوز پروژه‌ای نیست. یکی بسازید یا یک فایل .tgs باز کنید."), color = StudioColors.TextMuted, fontSize = 13.sp)
            }
        }
        items(projects, key = { it.id }) { p ->
            val thumb = remember(p.id, p.modified) { store.thumbnail(p.id)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(StudioColors.Panel).combinedClickable(onClick = { onOpen(p) }, onLongClick = { menuFor = p.id }),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(StudioColors.CheckerA), contentAlignment = Alignment.Center) {
                    if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) else Icon(Ic.Shape, StudioColors.TextMuted, 36.dp)
                    Box(Modifier.align(Alignment.TopEnd)) {
                        DropdownMenu(menuFor == p.id, { menuFor = null }, containerColor = StudioColors.PanelRaised) {
                            DropdownMenuItem(text = { Text(tr("Rename", "تغییر نام"), color = StudioColors.TextPrimary) }, onClick = { menuFor = null; renaming = p })
                            DropdownMenuItem(text = { Text(tr("Delete", "حذف"), color = StudioColors.Danger) }, onClick = { menuFor = null; deleting = p })
                        }
                    }
                }
                Column(Modifier.padding(10.dp)) {
                    Text(p.name, color = StudioColors.TextPrimary, fontSize = 14.sp, maxLines = 1)
                    Text(fmtDate.format(Date(p.modified)), color = StudioColors.TextMuted, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }

    if (showNew) NameDialog(tr("New project", "پروژهٔ جدید"), tr("Untitled", "بدون‌نام"), { n -> showNew = false; onNew(n) }, { showNew = false })
    renaming?.let { p -> NameDialog(tr("Rename", "تغییر نام"), p.name, { n -> onRename(p, n); renaming = null }, { renaming = null }) }
    deleting?.let { p -> ConfirmDialog(tr("Delete project?", "پروژه حذف شود؟"), tr("“${p.name}” will be removed from this phone.", "«${p.name}» از این گوشی پاک می‌شود."), tr("Delete", "حذف"), { onDelete(p); deleting = null }, { deleting = null }) }
    if (showSettings) SettingsDialog(setting, onLanguage) { showSettings = false }
    if (showAbout) AboutDialog { showAbout = false }
}
