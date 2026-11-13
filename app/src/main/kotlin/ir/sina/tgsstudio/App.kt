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
import ir.sina.tgsstudio.parser.TgsCodec
import ir.sina.tgsstudio.ui.EditorScreen
import ir.sina.tgsstudio.ui.EditorState
import ir.sina.tgsstudio.ui.GalleryScreen
import ir.sina.tgsstudio.ui.Lang
import ir.sina.tgsstudio.ui.WithLanguage

@Composable
fun StudioApp() {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val store = remember { ProjectStore(context) }
    var setting by remember { mutableStateOf(prefs.languageSetting) }
    val lang = remember(setting) { prefs.language() }
    var editor by remember { mutableStateOf<EditorState?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    fun toast(fa: String, en: String) = Toast.makeText(context, if (lang == Lang.FA) fa else en, Toast.LENGTH_LONG).show()

    fun open(info: ProjectInfo) {
        runCatching { store.load(info.id) }
            .onSuccess { doc -> editor = EditorState(info.id, info.name, EditorSession(doc), prefs) }
            .onFailure { toast("پروژه باز نشد", "Couldn't open this project") }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val doc = context.contentResolver.openInputStream(uri)?.use { TgsCodec.read(it) } ?: error("no stream")
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?.substringBeforeLast('.') ?: "Imported"
            doc.name = name
            open(store.create(doc, name))
        } catch (e: DocumentException) {
            toast("این فایل TGS یا Lottie معتبر نیست: ${e.message}", "Not a valid TGS/Lottie file: ${e.message}")
        } catch (e: Exception) {
            toast("فایل خوانده نشد", "Couldn't read the file")
        }
    }

    WithLanguage(lang) {
        val ed = editor
        if (ed == null) {
            GalleryScreen(
                store, refresh, setting,
                onOpen = ::open,
                onNew = { name -> val doc = Creator.blankDocument(name); open(store.create(doc, name)) },
                onImport = { importer.launch(arrayOf("*/*")) },
                onRename = { p, n -> store.rename(p.id, n); refresh++ },
                onDelete = { p -> store.delete(p.id); refresh++ },
                onLanguage = { s -> prefs.languageSetting = s; setting = s },
            )
        } else {
            EditorScreen(ed, store, lang) { editor = null; refresh++ }
        }
    }
}
