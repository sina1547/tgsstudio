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
    themeSetting: String, accent: String, onThemeChange: (String) -> Unit, onAccentChange: (String) -> Unit,
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
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(StudioColors.PanelRaised, StudioColors.Panel, StudioColors.AccentSoft)))
                        .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Logo(52.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("SINA TGS STUDIO", color = StudioColors.TextPrimary, fontSize = 19.sp)
                                Text(tr("VECTOR  ·  MOTION  ·  TGS", "وکتور  ·  موشن  ·  TGS"), color = StudioColors.Accent, fontSize = 10.sp)
                            }
                            IconBtn(Ic.Settings, { showSettings = true }, size = 38.dp)
                            IconBtn(Ic.Info, { showAbout = true }, size = 38.dp)
                        }
                        Text(
                            tr("A creative workspace for Telegram motion graphics", "فضای حرفه‌ای طراحی و ساخت موشن‌گرافیک تلگرام"),
                            color = StudioColors.TextMuted, fontSize = 12.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(tr("New Project", "پروژهٔ جدید"), { showNew = true }, Modifier.weight(1f).height(44.dp), primary = true, ic = Ic.Plus)
                            SmallButton(tr("Import TGS", "ورود فایل TGS"), onImport, Modifier.weight(1f).height(44.dp), ic = Ic.Folder)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr("Your workspace", "فضای کاری شما"), color = StudioColors.TextPrimary, fontSize = 17.sp)
                        Text(tr("Recent projects", "پروژه‌های اخیر"), color = StudioColors.TextMuted, fontSize = 11.sp)
                    }
                    Text("${projects.size}  ${tr("PROJECTS", "پروژه")}", color = StudioColors.Accent, fontSize = 10.sp)
                }
                if (projects.isEmpty()) Text(tr("No projects yet. Create one or import a .tgs file to get started.", "هنوز پروژه‌ای نیست. برای شروع پروژه بسازید یا فایل TGS وارد کنید."), color = StudioColors.TextMuted, fontSize = 13.sp)
            }
        }
        items(projects, key = { it.id }) { p ->
            val thumb = remember(p.id, p.modified) { store.thumbnail(p.id)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).background(StudioColors.Panel)
                    .combinedClickable(onClick = { onOpen(p) }, onLongClick = { menuFor = p.id }),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Brush.linearGradient(listOf(StudioColors.CheckerA, StudioColors.CheckerB))), contentAlignment = Alignment.Center) {
                    if (thumb != null) Image(thumb, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) else Icon(Ic.Shape, StudioColors.TextMuted, 36.dp)
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
    if (showSettings) SettingsDialog(setting, onLanguage, themeSetting, onThemeChange, accent, onAccentChange) { showSettings = false }
    if (showAbout) AboutDialog { showAbout = false }
}
