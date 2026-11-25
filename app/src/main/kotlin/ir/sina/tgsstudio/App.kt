package ir.sina.tgsstudio

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.sina.tgsstudio.data.AppPrefs
import ir.sina.tgsstudio.data.ProjectInfo
import ir.sina.tgsstudio.data.ProjectStore
import ir.sina.tgsstudio.document.DocumentException
import ir.sina.tgsstudio.editor.Creator
import ir.sina.tgsstudio.editor.EditorSession
import ir.sina.tgsstudio.export.Exporter
import ir.sina.tgsstudio.parser.TgsCodec
import ir.sina.tgsstudio.ui.EditorScreen
import ir.sina.tgsstudio.ui.EditorState
import ir.sina.tgsstudio.ui.GalleryScreen
import ir.sina.tgsstudio.ui.Lang
import ir.sina.tgsstudio.ui.WithLanguage

@Composable
fun StudioApp(onThemeChange: (String) -> Unit, onAccentChange: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val store = remember { ProjectStore(context) }
    var setting by remember { mutableStateOf(prefs.languageSetting) }
    val lang = remember(setting) { prefs.language() }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var pendingExport by remember { mutableStateOf<Pair<ProjectInfo, String>?>(null) }

    fun toast(fa: String, en: String) = Toast.makeText(context, if (lang == Lang.FA) fa else en, Toast.LENGTH_LONG).show()

    fun open(info: ProjectInfo) {
        runCatching { store.load(info.id) }
            .onSuccess { doc -> editor = EditorState(info.id, info.name, EditorSession(doc), prefs) }
            .onFailure { toast("پروژه باز نشد", "Couldn't open this project") }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var imported = 0
        var first: ProjectInfo? = null
        var failed = 0
        uris.forEach { uri ->
            runCatching {
                val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: "Imported.tgs"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no stream")
                if (displayName.endsWith(".tgsproj", ignoreCase = true)) {
                    store.importProjectArchive(bytes)
                } else {
                    val name = displayName.substringBeforeLast('.').takeIf { it.isNotBlank() } ?: "Imported"
                    val doc = TgsCodec.read(bytes)
                    doc.name = name
                    store.create(doc, name)
                }
            }.onSuccess { info ->
                if (first == null) first = info
                imported++
            }.onFailure { failed++ }
        }
        refresh++
        first?.let(::open)
        if (failed > 0) toast("$imported فایل وارد شد؛ $failed فایل نامعتبر بود", "$imported imported; $failed file(s) could not be opened")
        else toast("$imported فایل وارد شد", "$imported file(s) imported")
    }

    val projectExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) {
            val (project, format) = pending
            val success = runCatching {
                val bytes = when (format) {
                    "project" -> store.exportProjectArchive(project.id)
                    "tgs" -> Exporter.tgs(store.load(project.id))
                    "json" -> Exporter.lottieJson(store.load(project.id)).toByteArray(Charsets.UTF_8)
                    "svg" -> Exporter.svgFrame(store.load(project.id), 0.0).toByteArray(Charsets.UTF_8)
                    else -> error("Unsupported export format")
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no output stream")
            }.isSuccess
            if (success) toast("خروجی ذخیره شد", "Export saved")
            else toast("ذخیره خروجی ناموفق بود", "Export failed")
        }
    }

    fun exportProject(project: ProjectInfo, format: String) {
        pendingExport = project to format
        val extension = when (format) {
            "project" -> "tgsproj"
            "tgs" -> "tgs"
            "json" -> "json"
            "svg" -> "svg"
            else -> "bin"
        }
        projectExportLauncher.launch("${project.name}.$extension")
    }

    WithLanguage(lang) {
        val ed = editor
        if (ed == null) {
            GalleryScreen(
                store, refresh, setting,
                onOpen = ::open,
                onNew = { name, width, height, fps, background -> val doc = Creator.blankDocument(name, width, height, fps, background); open(store.create(doc, name)) },
                onImport = { importer.launch(arrayOf("application/json", "application/gzip", "application/octet-stream", "*/*")) },
                onRename = { p, n -> store.rename(p.id, n); refresh++ },
                onDelete = { p -> store.delete(p.id); refresh++ },
                onDeleteSelected = { selected -> store.deleteMany(selected.map { it.id }.toSet()); refresh++ },
                onDuplicate = { p -> if (store.duplicate(p.id) != null) refresh++ else toast("کپی پروژه ناموفق بود", "Couldn't duplicate project") },
                onExport = ::exportProject,
                onLanguage = { s -> prefs.languageSetting = s; setting = s },
                themeSetting = prefs.themeSetting, accent = prefs.themeAccent,
                onThemeChange = onThemeChange, onAccentChange = onAccentChange,
            )
        } else {
            EditorScreen(ed, store, lang) { editor = null; refresh++ }
        }
    }
}
