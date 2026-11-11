package ir.sina.tgsstudio.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.sina.tgsstudio.document.DocumentException
import ir.sina.tgsstudio.document.DocumentStats
import ir.sina.tgsstudio.document.LayerNode
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.parser.TgsCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Stage 1 state holder: open a TGS and expose its parsed document. Editing arrives in later stages. */
class OpenFileViewModel : ViewModel() {

    enum class Failure { UNREADABLE, INVALID, TOO_LARGE }

    sealed interface State {
        data object Empty : State
        data object Loading : State
        data class Loaded(
            val fileName: String,
            val fileSizeBytes: Long,
            val document: LottieDocument,
            val tree: List<LayerNode>,
            val stats: DocumentStats,
        ) : State
        data class Failed(val reason: Failure, val detail: String?) : State
    }

    var state: State by mutableStateOf(State.Empty)
        private set

    fun open(resolver: ContentResolver, uri: Uri) {
        state = State.Loading
        viewModelScope.launch {
            state = withContext(Dispatchers.Default) { load(resolver, uri) }
        }
    }

    private fun load(resolver: ContentResolver, uri: Uri): State {
        return try {
            // TgsCodec.read(InputStream) is size-bounded, so a hostile file can't exhaust memory.
            val doc = resolver.openInputStream(uri)?.use { TgsCodec.read(it) }
                ?: return State.Failed(Failure.UNREADABLE, null)
            val (name, size) = nameAndSize(resolver, uri)
            State.Loaded(name, size, doc, doc.layerTree(), doc.stats())
        } catch (e: DocumentException) {
            val tooLarge = e.message?.contains("too large") == true
            State.Failed(if (tooLarge) Failure.TOO_LARGE else Failure.INVALID, e.message)
        } catch (e: IOException) {
            State.Failed(Failure.UNREADABLE, e.message)
        } catch (e: SecurityException) {
            State.Failed(Failure.UNREADABLE, e.message)
        } catch (e: OutOfMemoryError) {
            State.Failed(Failure.TOO_LARGE, null)
        }
    }

    private fun nameAndSize(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val name = c.getString(0)
                val size = if (c.isNull(1)) 0L else c.getLong(1)
                if (name != null) return name to size
            }
        }
        return uri.lastPathSegment.orEmpty() to 0L
    }
}
