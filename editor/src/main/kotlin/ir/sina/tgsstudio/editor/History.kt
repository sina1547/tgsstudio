package ir.sina.tgsstudio.editor

import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.deepCopy

/**
 * Undo/redo by whole-document snapshots. Documents are small (a TGS is capped at 64 KB compressed),
 * so snapshots are simpler and safer than per-operation inverses: undo can never corrupt the document.
 * Drags use [begin]/[commit] so a whole gesture becomes one step.
 */
class History(private val root: JsonObj, private val limit: Int = 100) {
    private class Entry(val label: String, val snapshot: JsonObj)

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()
    private var pending: Entry? = null

    /** Bumped on every change so observers know to re-read the document. */
    var revision: Int = 0
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label
    val isGestureOpen: Boolean get() = pending != null

    fun begin(label: String) {
        if (pending == null) pending = Entry(label, root.deepCopy() as JsonObj)
    }

    fun commit() {
        val p = pending ?: return
        pending = null
        if (p.snapshot == root) return // nothing changed
        undoStack.addLast(p)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        revision++
    }

    /** Abandons the open gesture and restores the document to how it was at [begin]. */
    fun cancel() {
        val p = pending ?: return
        pending = null
        restore(p.snapshot)
        revision++
    }

    /** Runs [block] as one undoable step. If it throws, the document is restored and the exception rethrown. */
    fun <T> perform(label: String, block: () -> T): T {
        val nested = pending != null
        if (!nested) begin(label)
        try {
            val r = block()
            if (!nested) commit() else revision++
            return r
        } catch (e: Throwable) {
            if (!nested) cancel()
            throw e
        }
    }

    fun undo(): Boolean {
        if (pending != null) commit()
        val e = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(Entry(e.label, root.deepCopy() as JsonObj))
        restore(e.snapshot)
        revision++
        return true
    }

    fun redo(): Boolean {
        if (pending != null) commit()
        val e = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(Entry(e.label, root.deepCopy() as JsonObj))
        restore(e.snapshot)
        revision++
        return true
    }

    /** Replaces the contents of [root] in place so references to the root object stay valid. */
    private fun restore(snapshot: JsonObj) {
        root.fields.clear()
        root.fields.putAll(snapshot.fields)
    }

    fun clear() { undoStack.clear(); redoStack.clear(); pending = null; revision++ }
}
