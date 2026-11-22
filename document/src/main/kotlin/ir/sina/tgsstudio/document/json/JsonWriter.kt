package ir.sina.tgsstudio.document.json

/** Compact JSON writer (smallest output, which matters for the TGS size limit). */
object JsonWriter {
    fun write(v: JsonValue): String = StringBuilder().also { append(it, v) }.toString()

    private fun append(sb: StringBuilder, v: JsonValue) {
        when (v) {
            JsonNull -> sb.append("null")
            is JsonBool -> sb.append(if (v.value) "true" else "false")
            is JsonNum -> number(sb, v.value)
            is JsonStr -> string(sb, v.value)
            is JsonArr -> {
                sb.append('[')
                v.items.forEachIndexed { i, x -> if (i > 0) sb.append(','); append(sb, x) }
                sb.append(']')
            }
            is JsonObj -> {
                sb.append('{')
                var first = true
                for ((k, x) in v.fields) {
                    if (!first) sb.append(',')
                    first = false
                    string(sb, k); sb.append(':'); append(sb, x)
                }
                sb.append('}')
            }
        }
    }

    private fun number(sb: StringBuilder, n: Number) {
        val d = n.toDouble()
        require(d.isFinite()) { "NaN/Infinity cannot be written as JSON" }
        if (n is Double || n is Float) {
            // Whole-valued doubles keep ".0"-free form only when they were parsed that way; Kotlin prints 5.0 as "5.0".
            sb.append(d.toString())
        } else sb.append(n.toLong())
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' || c == '\u2028' || c == '\u2029' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
            else -> sb.append(c)
        }
        sb.append('"')
    }
}
