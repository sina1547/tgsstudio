package ir.sina.tgsstudio.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import ir.sina.tgsstudio.data.AppPrefs
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.editor.EditorSession
import ir.sina.tgsstudio.renderer.Bezier

enum class Tool { SELECT, HAND, PEN, NODE, RECT, ELLIPSE, STAR, POLYGON }
enum class Workspace { DESIGN, ANIMATION, ADVANCED }
enum class PanelTab { LAYERS, PROPERTIES, TIMELINE, ADD }

/**
 * UI state of one open project. The document itself lives in [session] (pure Kotlin); this class only
 * holds what the screen needs: tool, panels, playback, viewport. Call [changed] after touching the session.
 */
class EditorState(val projectId: String, var projectName: String, val session: EditorSession, private val prefs: AppPrefs) {
    /** Bumped on every document/selection change so composables re-read the session. */
    var tick by mutableIntStateOf(0)
        private set
    fun changed() { tick++ }
    inline fun <T> act(block: EditorSession.() -> T): T { val r = session.block(); changed(); return r }

    var tool by mutableStateOf(Tool.SELECT)
    var workspace by mutableStateOf(runCatching { Workspace.valueOf(prefs.workspace.uppercase()) }.getOrDefault(Workspace.DESIGN))
    var panel by mutableStateOf<PanelTab?>(if (prefs.panelOpen) runCatching { PanelTab.valueOf(prefs.openPanel.uppercase()) }.getOrNull() else null)
    var panelHeightDp by mutableFloatStateOf(prefs.panelHeightDp)
    var previewMode by mutableStateOf(false)
    var multiSelect by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    /** True when the UI language is Persian (set by the screen), for messages produced outside composition. */
    var isFa: Boolean = false

    // playback
    var frame by mutableDoubleStateOf(session.doc.inPoint)
        private set
    var playing by mutableStateOf(false)
    var loop by mutableStateOf(true)
    fun seek(f: Double) {
        val lo = session.doc.inPoint; val hi = (session.doc.outPoint - 1).coerceAtLeast(lo)
        frame = f.coerceIn(lo, hi); session.frame = frame; changed()
    }

    // viewport: screen = offset + scale * doc
    var viewScale by mutableFloatStateOf(1f)
    var viewOffset by mutableStateOf(Offset.Zero)
    var viewFitted by mutableStateOf(false)
    fun toDoc(p: Offset) = Offset((p.x - viewOffset.x) / viewScale, (p.y - viewOffset.y) / viewScale)
    fun toScreen(x: Double, y: Double) = Offset(viewOffset.x + viewScale * x.toFloat(), viewOffset.y + viewScale * y.toFloat())
    fun fit(w: Float, h: Float, margin: Float = 0.92f) {
        if (w <= 0f || h <= 0f) return
        val s = minOf(w / session.doc.width, h / session.doc.height) * margin
        viewScale = s
        viewOffset = Offset((w - session.doc.width * s) / 2f, (h - session.doc.height * s) / 2f)
        viewFitted = true
    }
    fun zoomPan(centroid: Offset, zoom: Float, pan: Offset) {
        val ns = (viewScale * zoom).coerceIn(0.1f, 40f)
        val k = ns / viewScale
        viewOffset = Offset(centroid.x - (centroid.x - viewOffset.x) * k + pan.x, centroid.y - (centroid.y - viewOffset.y) * k + pan.y)
        viewScale = ns
    }
    var fitSize by mutableStateOf(Offset.Zero)

    // pen tool and shape dragging
    var penPath by mutableStateOf<Bezier?>(null)
    var dragRect by mutableStateOf<Pair<Offset, Offset>?>(null)

    // node editor
    var nodeIndex by mutableIntStateOf(-1)

    // timeline
    var selectedKey by mutableStateOf<Pair<Int, Int>?>(null)   // (row, keyframe index)
    var copiedKeyframe by mutableStateOf<JsonObj?>(null)
    var timelinePxPerFrame by mutableFloatStateOf(6f)

    // saving
    private var savedRevision = session.history.revision
    val dirty: Boolean get() = tick >= 0 && session.history.revision != savedRevision
    fun markSaved() { savedRevision = session.history.revision; changed() }

    fun updatePanel(p: PanelTab?) { panel = p; if (p != null) prefs.openPanel = p.name.lowercase(); prefs.panelOpen = p != null }
    fun savePanelHeight() { prefs.panelHeightDp = panelHeightDp }
    fun setWorkspaceMode(w: Workspace) {
        workspace = w; prefs.workspace = w.name.lowercase()
        when (w) { Workspace.DESIGN -> updatePanel(PanelTab.LAYERS); Workspace.ANIMATION -> updatePanel(PanelTab.TIMELINE); Workspace.ADVANCED -> updatePanel(panel ?: PanelTab.LAYERS) }
    }
}
