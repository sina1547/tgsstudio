package ir.sina.tgsstudio.data

import android.content.Context
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.parser.TgsCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ProjectInfo(val id: String, val name: String, val modified: Long)

/** Projects live in the app's private storage as `<id>.tgs` plus a PNG thumbnail and a small index file. */
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

    fun create(doc: LottieDocument, name: String): ProjectInfo {
        val info = ProjectInfo(UUID.randomUUID().toString(), name, System.currentTimeMillis())
        File(dir, "${info.id}.tgs").writeBytes(TgsCodec.writeTgs(doc))
        writeIndex(list() + info)
        return info
    }

    /** Writes the document (and thumbnail when given) atomically enough for a crash not to lose the previous version. */
    fun save(id: String, doc: LottieDocument, thumbnailPng: ByteArray?) {
        val target = File(dir, "$id.tgs")
        val tmp = File(dir, "$id.tgs.tmp")
        tmp.writeBytes(TgsCodec.writeTgs(doc))
        if (!tmp.renameTo(target)) { target.writeBytes(tmp.readBytes()); tmp.delete() }
        thumbnailPng?.let { File(dir, "$id.png").writeBytes(it) }
        writeIndex(list().map { if (it.id == id) it.copy(modified = System.currentTimeMillis()) else it })
    }

    fun load(id: String): LottieDocument = TgsCodec.read(File(dir, "$id.tgs").readBytes())

    fun thumbnail(id: String): File? = File(dir, "$id.png").takeIf { it.exists() }

    fun rename(id: String, name: String) = writeIndex(list().map { if (it.id == id) it.copy(name = name) else it })

    fun delete(id: String) {
        File(dir, "$id.tgs").delete(); File(dir, "$id.png").delete()
        writeIndex(list().filter { it.id != id })
    }

    fun info(id: String): ProjectInfo? = list().firstOrNull { it.id == id }
}
