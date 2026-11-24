package ir.sina.tgsstudio.data

import android.content.Context
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.parser.TgsCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    fun duplicate(id: String): ProjectInfo? {
        val source = info(id) ?: return null
        val sourceFile = File(dir, "$id.tgs")
        if (!sourceFile.exists()) return null
        val copy = ProjectInfo(UUID.randomUUID().toString(), "${source.name} copy", System.currentTimeMillis())
        File(dir, "${copy.id}.tgs").writeBytes(sourceFile.readBytes())
        thumbnail(id)?.let { File(dir, "${copy.id}.png").writeBytes(it.readBytes()) }
        writeIndex(list() + copy)
        return copy
    }

    /** A portable, editable project bundle. It preserves the original document and its preview. */
    fun exportProjectArchive(id: String): ByteArray {
        val info = info(id) ?: error("Project not found")
        val document = File(dir, "$id.tgs")
        require(document.isFile) { "Project document is missing" }
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(JSONObject()
                .put("format", "tgsstudio-project")
                .put("version", 1)
                .put("name", info.name)
                .put("modified", info.modified)
                .toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("document.tgs"))
            zip.write(document.readBytes())
            zip.closeEntry()
            thumbnail(id)?.let { image ->
                zip.putNextEntry(ZipEntry("thumbnail.png"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    /** Imports only the known entries of a TGS Studio project bundle; arbitrary ZIP paths are ignored. */
    fun importProjectArchive(bytes: ByteArray): ProjectInfo {
        require(bytes.size <= 64 * 1024 * 1024) { "Project archive is too large" }
        var manifest: String? = null
        var document: ByteArray? = null
        var preview: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in setOf("manifest.json", "document.tgs", "thumbnail.png")) {
                    val data = zip.readBytes()
                    require(data.size <= 48 * 1024 * 1024) { "Project entry is too large" }
                    when (entry.name) {
                        "manifest.json" -> manifest = data.toString(Charsets.UTF_8)
                        "document.tgs" -> document = data
                        "thumbnail.png" -> preview = data
                    }
                }
                zip.closeEntry()
            }
        }
        val metadata = JSONObject(manifest ?: error("Project manifest is missing"))
        require(metadata.optString("format") == "tgsstudio-project") { "Unsupported project format" }
        require(metadata.optInt("version") == 1) { "Unsupported project version" }
        val doc = TgsCodec.read(document ?: error("Project document is missing"))
        val name = metadata.optString("name").takeIf { it.isNotBlank() } ?: doc.name ?: "Imported project"
        doc.name = name
        val imported = create(doc, name)
        preview?.let { File(dir, "${imported.id}.png").writeBytes(it) }
        return imported
    }

    fun deleteMany(ids: Set<String>) {
        ids.forEach { id ->
            File(dir, "$id.tgs").delete()
            File(dir, "$id.png").delete()
        }
        writeIndex(list().filterNot { it.id in ids })
    }

    fun info(id: String): ProjectInfo? = list().firstOrNull { it.id == id }
}
