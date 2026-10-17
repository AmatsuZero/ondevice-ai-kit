package io.ondeviceai.identity

/** Bounded contract codec, without reflection or an additional runtime dependency. */
internal object IdentityContractJSON {
    fun parse(json: String): Map<String, Any?> = Parser(json).objectRoot()
    fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> buildString {
            append('"')
            for (c in value) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 32) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
            }
            append('"')
        }
        is Long, is Int, is Boolean -> value.toString()
        is Double -> if (value.isFinite()) java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
            else throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        is Map<*, *> -> {
            if (value.keys.any { it !is String }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            value.entries.sortedBy { it.key as String }.joinToString(",", "{", "}") { encode(it.key) + ":" + encode(it.value) }
        }
        is List<*> -> value.joinToString(",", "[", "]") { encode(it) }
        else -> throw IdentityError(IdentityErrorCode.INVALID_INPUT)
    }
    private class Parser(private val source: String) {
        private var index = 0
        private fun invalid(): Nothing = throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        init { if (source.toByteArray(Charsets.UTF_8).size > 65536) invalid() }
        private fun whitespace() { while (index < source.length && source[index] in " \t\r\n") index++ }
        private fun take(expected: Char) {
            whitespace()
            if (index >= source.length || source[index++] != expected) invalid()
        }
        fun objectRoot(): Map<String, Any?> {
            val result = value(0)
            whitespace()
            if (index != source.length || result !is Map<*, *>) invalid()
            @Suppress("UNCHECKED_CAST")
            return result as Map<String, Any?>
        }
        private fun value(depth: Int): Any? {
            if (depth > 16) invalid()
            whitespace()
            if (index >= source.length) invalid()
            return when (source[index]) {
                '"' -> string()
                '{' -> {
                    index++; whitespace()
                    val result = linkedMapOf<String, Any?>()
                    if (index < source.length && source[index] == '}') { index++; result } else {
                        while (true) {
                            whitespace()
                            if (index >= source.length || source[index] != '"') invalid()
                            val key = string()
                            if (result.containsKey(key)) invalid()
                            take(':'); result[key] = value(depth + 1); whitespace()
                            if (index >= source.length) invalid()
                            if (source[index] == '}') { index++; break }
                            take(',')
                        }
                        result
                    }
                }
                '[' -> {
                    index++; whitespace()
                    val result = mutableListOf<Any?>()
                    if (index < source.length && source[index] == ']') { index++; result } else {
                        while (true) {
                            result.add(value(depth + 1)); whitespace()
                            if (index >= source.length) invalid()
                            if (source[index] == ']') { index++; break }
                            take(',')
                        }
                        result
                    }
                }
                'n' -> literal("null", null)
                't' -> literal("true", true)
                'f' -> literal("false", false)
                else -> number()
            }
        }
        private fun literal(text: String, value: Any?): Any? {
            if (!source.startsWith(text, index)) invalid()
            index += text.length
            return value
        }
        private fun number(): Number {
            val match = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").find(source, index) ?: invalid()
            if (match.range.first != index) invalid()
            index = match.range.last + 1
            val text = match.value
            // All reference/authorization wire numbers are integers. Reject before
            // nested objects are re-encoded and a whole-valued Double loses its type.
            if (text.any { it in ".eE" }) invalid()
            return text.toLongOrNull() ?: invalid()
        }
        private fun string(): String {
            take('"')
            val result = StringBuilder()
            while (index < source.length) {
                val c = source[index++]
                if (c == '"') return result.toString()
                if (c.code < 32) invalid()
                if (c != '\\') { result.append(c); continue }
                if (index >= source.length) invalid()
                result.append(when (val escape = source[index++]) {
                    '"', '\\', '/' -> escape
                    'b' -> '\b'
                    'f' -> '\u000c'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'u' -> {
                        if (index + 4 > source.length) invalid()
                        val hex = source.substring(index, index + 4)
                        if (hex.any { it !in "0123456789abcdefABCDEF" }) invalid()
                        index += 4
                        hex.toInt(16).toChar()
                    }
                    else -> invalid()
                })
            }
            invalid()
        }
    }
}
