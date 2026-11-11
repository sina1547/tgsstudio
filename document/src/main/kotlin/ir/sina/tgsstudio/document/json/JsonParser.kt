package ir.sina.tgsstudio.document.json

class JsonParseException(message: String, val position: Int) : Exception("$message at offset $position")

/** Small strict-ish recursive-descent JSON parser with no dependencies (runs on JVM and Android). */
object JsonParser {
    private const val MAX_DEPTH = 512

    fun parse(text: String): JsonValue {
        val p = Impl(text)
        p.skipBom()
        val v = p.value(0)
        p.skipWs()
        if (!p.atEnd()) throw JsonParseException("Unexpected trailing content", p.pos)
        return v
    }

    private class Impl(val s: String) {
        var pos = 0
        fun atEnd() = pos >= s.length
        fun skipBom() { if (s.isNotEmpty() && s[0] == '\uFEFF') pos = 1 }
        fun skipWs() { while (pos < s.length && s[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++ }
        fun fail(msg: String): Nothing = throw JsonParseException(msg, pos)

        fun value(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) fail("Nesting too deep")
            skipWs()
            if (atEnd()) fail("Unexpected end of input")
            return when (val c = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> JsonStr(str())
                't' -> lit("true", JsonBool(true))
                'f' -> lit("false", JsonBool(false))
                'n' -> lit("null", JsonNull)
                else -> if (c == '-' || c in '0'..'9') num() else fail("Unexpected character '$c'")
            }
        }

        fun lit(word: String, v: JsonValue): JsonValue {
            if (!s.startsWith(word, pos)) fail("Invalid literal")
            pos += word.length
            return v
        }

        fun obj(depth: Int): JsonObj {
            pos++ // {
            val o = JsonObj()
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return o }
            while (true) {
                skipWs()
                if (atEnd() || s[pos] != '"') fail("Expected string key")
                val k = str()
                skipWs()
                if (atEnd() || s[pos] != ':') fail("Expected ':'")
                pos++
                o.fields[k] = value(depth + 1)
                skipWs()
                if (atEnd()) fail("Unterminated object")
                when (s[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return o }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        fun arr(depth: Int): JsonArr {
            pos++ // [
            val a = JsonArr()
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return a }
            while (true) {
                a.items.add(value(depth + 1))
                skipWs()
                if (atEnd()) fail("Unterminated array")
                when (s[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return a }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) fail("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) fail("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) fail("Bad \\u escape")
                                val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: fail("Bad \\u escape")
                                sb.append(code.toChar()); pos += 4
                            }
                            else -> fail("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun num(): JsonNum {
            val start = pos
            if (s[pos] == '-') pos++
            var isDouble = false
            while (pos < s.length) {
                val c = s[pos]
                if (c in '0'..'9') pos++
                else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { isDouble = true; pos++ }
                else break
            }
            val tok = s.substring(start, pos)
            if (!isDouble) tok.toLongOrNull()?.let { return JsonNum(it) }
            val d = tok.toDoubleOrNull() ?: throw JsonParseException("Invalid number '$tok'", start)
            if (!d.isFinite()) throw JsonParseException("Number out of range '$tok'", start)
            return JsonNum(d)
        }
    }
}
