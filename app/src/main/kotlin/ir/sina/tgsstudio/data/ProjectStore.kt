package ir.sina.tgsstudio.data

import android.content.Context
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.parser.TgsCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ProjectInfo(val id: String, val name: String, val modified: Long)

/** Projects live in private storage as a TGS document, thumbnail, and index entry. */
class ProjectStore(context: Context) {
    private val dir = File(context.filesDir, "projects").apply { mkdirs() }
    private val index = File(dir, "index.json")

    fun list(): List<ProjectInfo> = runCatching {
        val arr = JSONArray(index.readText())
        List(arr.length()) { i -> arr.getJSONObject(i).let { ProjectInfo(it.getString("id"), it.getString("name"), it.getLong("modified")) } }
            .filter { File(dir, "${it.id}.tgs").exists() }
            .sortedByDescending { it.modified }
    }.getOrDefault(emptyList())

    private fun writeIndex(items: List<ProjectInfo>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("modified", it.modified)) }
        index.writeText(arr.toString())
    }

    private fun documentFile(id: String) = File(dir, "$id.tgs")
    private fun snapshotFile(id: String) = File(dir, "$id.tgs.bak")

    /**
     * Preserve the last readable document before replacing it. A corrupt/incomplete current
     * file is never promoted to the recovery snapshot.
     */
    private fun preserveLastGood(target: File) {
        if (!target.isFile) return
        val previous = runCatching { target.readBytes() }.getOrNull() ?: return
        if (runCatching { TgsCodec.read(previous) }.isFailure) return
        val snapshot = File(dir, "${target.name}.bak")
        val tmp = File(dir, "${snapshot.name}.tmp")
        runCatching {
            tmp.writeBytes(previous)
            if (!tmp.renameTo(snapshot)) {
                snapshot.writeBytes(tmp.readBytes())
                tmp.delete()
            }
        }
    }

    /** Replace a project file via a sibling temporary file. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.writeBytes(tmp.readBytes())
            tmp.delete()
        }
    }

    fun create(doc: LottieDocument, name: String): ProjectInfo {
        val info = ProjectInfo(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        writeAtomically(documentFile(info.id), TgsCodec.writeTgs(doc))
        writeIndex(list() + info)
        return info
    }

    /** Save a new version while retaining the last known-good TGS snapshot for recovery. */
    fun save(id: String, doc: LottieDocument, thumbnailPng: ByteArray?) {
        val target = documentFile(id)
        preserveLastGood(target)
        writeAtomically(target, TgsCodec.writeTgs(doc))
        thumbnailPng?.let { File(dir, "$id.png").writeBytes(it) }
        writeIndex(list().map { if (it.id == id) it.copy(modified = System.currentTimeMillis()) else it })
    }

    /**
     * Load the current project. If it is missing or invalid, recover the previous validated
     * snapshot and restore it to the primary path before returning it.
     */
    fun load(id: String): LottieDocument {
        val target = documentFile(id)
        val current = runCatching { TgsCodec.read(target.readBytes()) }
        current.getOrNull()?.let { return it }

        val backup = snapshotFile(id)
        val backupBytes = runCatching { backup.takeIf { it.isFile }?.readBytes() }.getOrNull()
        if (backupBytes != null) {
            val recovered = runCatching { TgsCodec.read(backupBytes) }
            recovered.getOrNull()?.let { document ->
                runCatching { writeAtomically(target, backupBytes) }
                return document
            }
        }
        throw current.exceptionOrNull()
            ?: IllegalStateException("Project $id has no valid current or recovery snapshot")
    }

    /** True when a validated previous version is available for this project. */
    fun hasRecoverySnapshot(id: String): Boolean {
        val backup = snapshotFile(id)
        return backup.isFile && runCatching { TgsCodec.read(backup.readBytes()) }.isSuccess
    }

    fun thumbnail(id: String): File? = File(dir, "$id.png").takeIf { it.exists() }

    fun rename(id: String, name: String) = writeIndex(list().map { if (it.id == id) it.copy(name = name) else it })

    fun delete(id: String) {
        documentFile(id).delete()
        snapshotFile(id).delete()
        File(dir, "$id.png").delete()
        writeIndex(list().filter { it.id != id })
    }

    fun info(id: String): ProjectInfo? = list().firstOrNull { it.id == id }
}
