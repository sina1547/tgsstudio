package ir.sina.tgsstudio.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.data.ProjectInfo
import ir.sina.tgsstudio.data.ProjectStore
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Logo(size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF1E7BFF), Color(0xFF7C4DDB))), size = this.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.26f))
        val p = androidx.compose.ui.graphics.Path().apply { moveTo(s * 0.24f, s * 0.70f); cubicTo(s * 0.32f, s * 0.28f, s * 0.62f, s * 0.28f, s * 0.78f, s * 0.52f) }
        drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawCircle(Color.White, s * 0.06f, androidx.compose.ui.geometry.Offset(s * 0.24f, s * 0.70f))
        drawCircle(Color.White, s * 0.06f, androidx.compose.ui.geometry.Offset(s * 0.78f, s * 0.52f))
    }
}

@Composable
fun GalleryScreen(
    store: ProjectStore, refresh: Int, setting: String,
    onOpen: (ProjectInfo) -> Unit, onNew: (String, Int, Int, Double, String) -> Unit, onImport: () -> Unit,
    onRename: (ProjectInfo, String) -> Unit, onDelete: (ProjectInfo) -> Unit, onLanguage: (String) -> Unit,
    themeSetting: String, accent: String, onThemeChange: (String) -> Unit, onAccentChange: (String) -> Unit,
) {
    val projects = remember(refresh) { store.list() }
    var showNew by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectInfo?>(null) }
    var deleting by remember { mutableStateOf<ProjectInfo?>(null) }

    HomeWorkspace(
        projects = projects,
        store = store,
        onOpen = onOpen,
        onNew = { showNew = true },
        onImport = onImport,
        onSettings = { showSettings = true },
        onAbout = { showAbout = true },
    )

    if (showNew) NewProjectDialog({ showNew = false }) { n, w, h, fps, bg -> showNew = false; onNew(n, w, h, fps, bg) }
    renaming?.let { p -> NameDialog(tr("Rename", "تغییر نام"), p.name, { n -> onRename(p, n); renaming = null }, { renaming = null }) }
    deleting?.let { p -> ConfirmDialog(tr("Delete project?", "پروژه حذف شود؟"), "“" + p.name + "” " + tr("will be removed from this phone.", "از این گوشی پاک می‌شود."), tr("Delete", "حذف"), { onDelete(p); deleting = null }, { deleting = null }) }
    if (showSettings) SettingsDialog(setting, onLanguage, themeSetting, onThemeChange, accent, onAccentChange) { showSettings = false }
    if (showAbout) AboutDialog { showAbout = false }
}

@Composable
private fun HomeWorkspace(
    projects: List<ProjectInfo>,
    store: ProjectStore,
    onOpen: (ProjectInfo) -> Unit,
    onNew: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val filtered = remember(projects, query) {
        if (query.isBlank()) projects else projects.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    val visible = if (showAll) filtered else filtered.take(6)
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()) }

    Box(Modifier.fillMaxSize().background(StudioColors.Background).systemBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    Logo(46.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("TGS STUDIO", color = StudioColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(tr("VECTOR  •  MOTION  •  CREATIVE", "وکتور  •  موشن  •  طراحی"), color = StudioColors.Accent, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    IconBtn(Ic.Info, onAbout, size = 40.dp)
                    IconBtn(Ic.Settings, onSettings, size = 40.dp)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text(tr("Search projects...", "جستجوی پروژه‌ها..."), color = StudioColors.TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Ic.Tools, StudioColors.TextMuted, 19.dp) },
                )
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(StudioColors.PanelRaised, StudioColors.Panel, StudioColors.AccentSoft)))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("Import Center", "مرکز ورود فایل"), color = StudioColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(tr("Bring your creative files into one workspace", "فایل‌های طراحی و انیمیشن را وارد فضای کاری کنید"), color = StudioColors.TextMuted, fontSize = 11.sp)
                        }
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(StudioColors.AccentSoft), contentAlignment = Alignment.Center) {
                            Icon(Ic.Folder, StudioColors.Accent, 23.dp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SmallButton(tr("Open TGS", "باز کردن TGS"), onImport, Modifier.weight(1f).height(42.dp), primary = true, ic = Ic.Folder)
                        SmallButton(tr("New Project", "پروژه جدید"), onNew, Modifier.weight(1f).height(42.dp), ic = Ic.Plus)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr("Recent Projects", "پروژه‌های اخیر"), color = StudioColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr("Continue where you left off", "ادامه کارهای قبلی"), color = StudioColors.TextMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(if (showAll) tr("Show recent", "نمایش اخیر") else tr("See all", "همه"), color = StudioColors.Accent, fontSize = 12.sp)
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(StudioColors.Panel).padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(Ic.Folder, StudioColors.TextMuted, 34.dp)
                        Text(if (query.isBlank()) tr("No projects yet", "هنوز پروژه‌ای ندارید") else tr("No matching projects", "پروژه‌ای پیدا نشد"), color = StudioColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(if (query.isBlank()) tr("Create a project or open a TGS file to get started.", "برای شروع پروژه بسازید یا یک فایل TGS باز کنید.") else tr("Try another search term.", "عبارت دیگری را جستجو کنید."), color = StudioColors.TextMuted, fontSize = 11.sp)
                    }
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 2.dp)) {
                        items(visible, key = { it.id }) { project ->
                            val bitmap = remember(project.id, project.modified) {
                                store.thumbnail(project.id)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
                            }
                            Column(
                                Modifier.width(174.dp).clip(RoundedCornerShape(16.dp)).background(StudioColors.Panel)
                                    .clickable { onOpen(project) },
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(1.22f)
                                        .background(Brush.linearGradient(listOf(StudioColors.CheckerA, StudioColors.CheckerB))),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().padding(9.dp), contentScale = ContentScale.Fit)
                                    else Icon(Ic.Shape, StudioColors.TextMuted, 36.dp)
                                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(StudioColors.Background.copy(alpha = 0.72f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text("TGS", color = StudioColors.Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(project.name, color = StudioColors.TextPrimary, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                                    Text(dateFormat.format(Date(project.modified)), color = StudioColors.TextMuted, fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(tr("Quick Actions", "دسترسی سریع"), color = StudioColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Ic.Plus, tr("Create", "ساخت پروژه"), StudioColors.Accent, onNew, Modifier.weight(1f))
                    QuickAction(Ic.Folder, tr("Import", "ورود فایل"), StudioColors.Violet, onImport, Modifier.weight(1f))
                    QuickAction(Ic.Settings, tr("Workspace", "فضای کاری"), StudioColors.Green, onSettings, Modifier.weight(1f))
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(StudioColors.Panel.copy(alpha = 0.98f))
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomNavItem(Ic.Shape, tr("Home", "خانه"), !showAll) { showAll = false; query = "" }
            BottomNavItem(Ic.Layers, tr("Projects", "پروژه‌ها"), showAll) { showAll = true }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box {
                    Box(
                        Modifier.size(58.dp).clip(CircleShape).background(StudioColors.Accent)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Ic.Plus, Color.White, 29.dp) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = StudioColors.PanelRaised) {
                        DropdownMenuItem(
                            text = { Text(tr("New Project", "پروژه جدید"), color = StudioColors.TextPrimary) },
                            leadingIcon = { Icon(Ic.Plus, StudioColors.Accent, 20.dp) },
                            onClick = { menuOpen = false; onNew() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Open TGS", "باز کردن TGS"), color = StudioColors.TextPrimary) },
                            leadingIcon = { Icon(Ic.Folder, StudioColors.Accent, 20.dp) },
                            onClick = { menuOpen = false; onImport() },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Import Center", "مرکز ورود فایل"), color = StudioColors.TextPrimary) },
                            leadingIcon = { Icon(Ic.Export, StudioColors.Accent, 20.dp) },
                            onClick = { menuOpen = false; onImport() },
                        )
                    }
                }
            }
            BottomNavItem(Ic.Folder, tr("Import", "ورود"), false, onImport)
            BottomNavItem(Ic.Settings, tr("Settings", "تنظیمات"), false, onSettings)
        }
    }
}

@Composable
private fun QuickAction(icon: Ic, title: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(StudioColors.Panel).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, tint, 20.dp)
        }
        Text(title, color = StudioColors.TextPrimary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun BottomNavItem(icon: Ic, title: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(66.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, if (selected) StudioColors.Accent else StudioColors.TextMuted, 20.dp)
        Text(title, color = if (selected) StudioColors.Accent else StudioColors.TextMuted, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, Int, Int, Double, String) -> Unit) {
    val defaultName = tr("Untitled", "بدون‌نام")
    var name by remember { mutableStateOf(defaultName) }
    var preset by remember { mutableStateOf("9:16") }
    var width by remember { mutableStateOf("1080") }
    var height by remember { mutableStateOf("1920") }
    var fps by remember { mutableStateOf("60") }
    var background by remember { mutableStateOf("transparent") }
    val presets = listOf(
        "1:1" to (1080 to 1080), "4:3" to (1440 to 1080), "4:5" to (1080 to 1350),
        "9:16" to (1080 to 1920), "16:9" to (1920 to 1080),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Panel,
        title = { Text(tr("New composition", "کامپوزیشن جدید"), color = StudioColors.TextPrimary) },
        text = {
            Column(androidx.compose.ui.Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("Project name", "نام پروژه")) }, modifier = Modifier.fillMaxWidth())
                Text(tr("Aspect ratio", "نسبت تصویر"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    presets.take(3).forEach { (label, size) ->
                        SmallButton(label, { preset = label; width = size.first.toString(); height = size.second.toString() }, primary = preset == label)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    presets.drop(3).forEach { (label, size) ->
                        SmallButton(label, { preset = label; width = size.first.toString(); height = size.second.toString() }, primary = preset == label)
                    }
                    SmallButton("Custom", { preset = "Custom" }, primary = preset == "Custom")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(width, { width = it.filter(Char::isDigit); preset = "Custom" }, singleLine = true, label = { Text("Width") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(height, { height = it.filter(Char::isDigit); preset = "Custom" }, singleLine = true, label = { Text("Height") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(fps, { fps = it.filter { c -> c.isDigit() || c == '.' } }, singleLine = true, label = { Text(tr("Custom FPS (1–120)", "نرخ فریم دلخواه (۱ تا ۱۲۰)")) }, modifier = Modifier.fillMaxWidth())
                Text(tr("Background", "پس‌زمینه"), color = StudioColors.TextMuted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("transparent", "#000000", "#FFFFFF").forEach { color ->
                        SmallButton(if (color == "transparent") tr("Transparent", "شفاف") else color, { background = color }, primary = background == color)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("#202124", "#101820", "#1B263B").forEach { color ->
                        SmallButton(color, { background = color }, primary = background == color)
                    }
                }
                Text(tr("Transparent is the default. TGS export remains transparent; a solid background is stored as composition metadata.", "شفافیت پیش‌فرض است. خروجی TGS همچنان شفاف می‌ماند؛ پس‌زمینهٔ رنگی به‌عنوان مشخصات کامپوزیشن ذخیره می‌شود."), color = StudioColors.TextMuted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton({
                val w = width.toIntOrNull()?.coerceIn(16, 4096) ?: 1080
                val h = height.toIntOrNull()?.coerceIn(16, 4096) ?: 1920
                val rate = fps.toDoubleOrNull()?.coerceIn(1.0, 120.0) ?: 60.0
                onCreate(name.ifBlank { defaultName }, w, h, rate, background)
            }) { Text(tr("Create", "ساخت پروژه"), color = StudioColors.Accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel", "انصراف"), color = StudioColors.TextMuted) } },
    )
}
