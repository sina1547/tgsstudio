package ir.sina.tgsstudio.parser

import ir.sina.tgsstudio.document.DocumentException
import ir.sina.tgsstudio.document.LottieDocument
import ir.sina.tgsstudio.document.json.JsonObj
import ir.sina.tgsstudio.document.json.JsonParseException
import ir.sina.tgsstudio.document.json.JsonParser
import ir.sina.tgsstudio.document.json.JsonWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException

/** Reads and writes TGS (gzip-compressed Lottie JSON). Plain Lottie JSON is accepted on input too. */
object TgsCodec {
    /** Safety cap against decompression bombs; real stickers are a few hundred KB of JSON. */
    const val MAX_JSON_BYTES = 32 * 1024 * 1024

    fun isGzip(bytes: ByteArray) = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()

    fun read(bytes: ByteArray): LottieDocument {
        val jsonBytes = if (isGzip(bytes)) gunzip(ByteArrayInputStream(bytes)) else bytes
        if (jsonBytes.size > MAX_JSON_BYTES) throw DocumentException("File is too large to open")
        return fromJson(jsonBytes.toString(Charsets.UTF_8))
    }

    fun read(input: InputStream): LottieDocument = read(input.readBytesLimited(MAX_JSON_BYTES.toLong() + 1))

    fun fromJson(json: String): LottieDocument {
        val root = try {
            JsonParser.parse(json)
        } catch (e: JsonParseException) {
            throw DocumentException("Invalid JSON: ${e.message}", e)
        }
        if (root !is JsonObj) throw DocumentException("Invalid Lottie file: top level is not an object")
        return LottieDocument(root)
    }

    fun toJson(doc: LottieDocument): String = JsonWriter.write(doc.root)

    fun writeTgs(doc: LottieDocument): ByteArray {
        val json = toJson(doc).toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(json.size / 3 + 64)
        object : GZIPOutputStream(out) { init { def.setLevel(9) } }.use { it.write(json) }
        return out.toByteArray()
    }

    private fun gunzip(input: InputStream): ByteArray {
        try {
            GZIPInputStream(input).use { return it.readBytesLimited(MAX_JSON_BYTES.toLong() + 1).also { b ->
                if (b.size > MAX_JSON_BYTES) throw DocumentException("File is too large after decompression")
            } }
        } catch (e: ZipException) {
            throw DocumentException("Not a valid TGS (gzip) file: ${e.message}", e)
        } catch (e: IOException) {
            throw DocumentException("Could not read TGS file: ${e.message}", e)
        }
    }

    private fun InputStream.readBytesLimited(limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
