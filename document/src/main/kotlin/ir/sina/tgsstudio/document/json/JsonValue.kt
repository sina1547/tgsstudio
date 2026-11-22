package ir.sina.tgsstudio.document.json

/**
 * Lossless JSON tree. Object key order and the Long/Double distinction are preserved,
 * so unknown Lottie fields survive a read/write round trip untouched.
 */
sealed interface JsonValue

data object JsonNull : JsonValue
data class JsonBool(val value: Boolean) : JsonValue
/** [value] is a Long for integer tokens and a Double otherwise. */
data class JsonNum(val value: Number) : JsonValue
data class JsonStr(val value: String) : JsonValue

class JsonArr(val items: MutableList<JsonValue> = mutableListOf()) : JsonValue {
    override fun equals(other: Any?) = other is JsonArr && items == other.items
    override fun hashCode() = items.hashCode()
    override fun toString() = JsonWriter.write(this)
}

class JsonObj(val fields: LinkedHashMap<String, JsonValue> = LinkedHashMap()) : JsonValue {
    operator fun get(key: String): JsonValue? = fields[key]
    operator fun set(key: String, value: JsonValue) { fields[key] = value }
    fun remove(key: String): JsonValue? = fields.remove(key)
    fun has(key: String) = fields.containsKey(key)

    fun obj(key: String): JsonObj? = fields[key] as? JsonObj
    fun arr(key: String): JsonArr? = fields[key] as? JsonArr
    fun string(key: String): String? = (fields[key] as? JsonStr)?.value
    fun double(key: String): Double? = (fields[key] as? JsonNum)?.value?.toDouble()
    fun int(key: String): Int? = (fields[key] as? JsonNum)?.value?.let {
        val d = it.toDouble()
        if (d.isFinite() && d >= Int.MIN_VALUE && d <= Int.MAX_VALUE) it.toInt() else null
    }
    fun bool(key: String): Boolean? = when (val v = fields[key]) {
        is JsonBool -> v.value
        is JsonNum -> v.value.toDouble() != 0.0 // Lottie uses 0/1 for many flags
        else -> null
    }

    fun putString(key: String, v: String) { fields[key] = JsonStr(v) }
    fun putInt(key: String, v: Int) { fields[key] = JsonNum(v.toLong()) }
    fun putDouble(key: String, v: Double) { fields[key] = JsonNum(v) }
    fun putBool(key: String, v: Boolean) { fields[key] = JsonBool(v) }

    override fun equals(other: Any?) = other is JsonObj && fields == other.fields
    override fun hashCode() = fields.hashCode()
    override fun toString() = JsonWriter.write(this)
}

fun JsonValue.deepCopy(): JsonValue = when (this) {
    is JsonArr -> JsonArr(items.mapTo(mutableListOf()) { it.deepCopy() })
    is JsonObj -> JsonObj(LinkedHashMap<String, JsonValue>().also { m -> fields.forEach { (k, v) -> m[k] = v.deepCopy() } })
    else -> this // immutable leaves
}
