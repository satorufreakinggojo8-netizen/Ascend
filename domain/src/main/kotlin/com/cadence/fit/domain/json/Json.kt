package com.cadence.fit.domain.json

/**
 * A tiny JSON tree used for data export and import.
 *
 * Cadence deliberately avoids a JSON library here: the format only has to round
 * trip its own documents, and keeping it in the domain module means the whole
 * backup format is testable with plain JVM unit tests.
 */
sealed interface JsonValue {

    data class Obj(val entries: Map<String, JsonValue>) : JsonValue {
        operator fun get(key: String): JsonValue? = entries[key]

        fun string(key: String): String? = (entries[key] as? Str)?.value

        fun long(key: String): Long? = (entries[key] as? Num)?.value?.toLong()

        fun int(key: String): Int? = (entries[key] as? Num)?.value?.toInt()

        fun double(key: String): Double? = (entries[key] as? Num)?.value

        fun bool(key: String): Boolean? = (entries[key] as? Bool)?.value

        fun obj(key: String): Obj? = entries[key] as? Obj

        fun array(key: String): Arr? = entries[key] as? Arr
    }

    data class Arr(val items: List<JsonValue>) : JsonValue {
        val objects: List<Obj> get() = items.filterIsInstance<Obj>()
        val strings: List<String> get() = items.filterIsInstance<Str>().map { it.value }
    }

    data class Str(val value: String) : JsonValue

    data class Num(val value: Double) : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    data object Null : JsonValue

    companion object {
        fun of(value: String): JsonValue = Str(value)

        fun of(value: Int): JsonValue = Num(value.toDouble())

        fun of(value: Long): JsonValue = Num(value.toDouble())

        fun of(value: Double): JsonValue = Num(value)

        fun of(value: Boolean): JsonValue = Bool(value)

        fun ofObj(vararg pairs: Pair<String, JsonValue?>): Obj =
            Obj(pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap())

        fun ofArray(values: Iterable<JsonValue>): Arr = Arr(values.toList())

        fun ofStrings(values: Iterable<String>): Arr = Arr(values.map { Str(it) })
    }
}

class JsonParseException(message: String) : Exception(message)

object Json {

    fun write(value: JsonValue, pretty: Boolean = true): String {
        val builder = StringBuilder()
        writeValue(value, builder, pretty, 0)
        return builder.toString()
    }

    fun parse(text: String): JsonValue = Parser(text).parse()

    private fun writeValue(value: JsonValue, out: StringBuilder, pretty: Boolean, depth: Int) {
        when (value) {
            is JsonValue.Obj -> {
                if (value.entries.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append('{')
                val items = value.entries.entries.toList()
                items.forEachIndexed { index, (key, child) ->
                    if (index > 0) out.append(',')
                    if (pretty) newLine(out, depth + 1)
                    writeString(key, out)
                    out.append(':')
                    if (pretty) out.append(' ')
                    writeValue(child, out, pretty, depth + 1)
                }
                if (pretty) newLine(out, depth)
                out.append('}')
            }

            is JsonValue.Arr -> {
                if (value.items.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append('[')
                value.items.forEachIndexed { index, child ->
                    if (index > 0) out.append(',')
                    if (pretty) newLine(out, depth + 1)
                    writeValue(child, out, pretty, depth + 1)
                }
                if (pretty) newLine(out, depth)
                out.append(']')
            }

            is JsonValue.Str -> writeString(value.value, out)
            is JsonValue.Num -> out.append(formatNumber(value.value))
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            JsonValue.Null -> out.append("null")
        }
    }

    private fun newLine(out: StringBuilder, depth: Int) {
        out.append('\n')
        repeat(depth) { out.append("  ") }
    }

    private fun formatNumber(value: Double): String {
        if (!value.isFinite()) return "0"
        val asLong = value.toLong()
        return if (asLong.toDouble() == value) asLong.toString() else value.toString()
    }

    private fun writeString(value: String, out: StringBuilder) {
        out.append('"')
        value.forEach { character ->
            when (character) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (character < ' ') {
                    out.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(character)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        private var position = 0

        fun parse(): JsonValue {
            skipWhitespace()
            val value = readValue()
            skipWhitespace()
            if (position < text.length) throw JsonParseException("Unexpected content at $position")
            return value
        }

        private fun readValue(): JsonValue {
            if (position >= text.length) throw JsonParseException("Unexpected end of input")
            return when (val character = text[position]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> JsonValue.Str(readString())
                't', 'f' -> readBoolean()
                'n' -> readNull()
                else -> if (character == '-' || character.isDigit()) readNumber()
                else throw JsonParseException("Unexpected character '$character' at $position")
            }
        }

        private fun readObject(): JsonValue.Obj {
            expect('{')
            val entries = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return JsonValue.Obj(entries)
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                entries[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return JsonValue.Obj(entries)
                    }

                    else -> throw JsonParseException("Expected ',' or '}' at $position")
                }
            }
        }

        private fun readArray(): JsonValue.Arr {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return JsonValue.Arr(items)
            }
            while (true) {
                skipWhitespace()
                items += readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return JsonValue.Arr(items)
                    }

                    else -> throw JsonParseException("Expected ',' or ']' at $position")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (position >= text.length) throw JsonParseException("Unterminated string")
                when (val character = text[position++]) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (position >= text.length) throw JsonParseException("Unterminated escape")
                        when (val escape = text[position++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (position + 4 > text.length) throw JsonParseException("Bad \\u escape")
                                val hex = text.substring(position, position + 4)
                                position += 4
                                val code = hex.toIntOrNull(16)
                                    ?: throw JsonParseException("Bad \\u escape: $hex")
                                builder.append(code.toChar())
                            }

                            else -> throw JsonParseException("Unknown escape '\\$escape'")
                        }
                    }

                    else -> builder.append(character)
                }
            }
        }

        private fun readNumber(): JsonValue.Num {
            val start = position
            if (peek() == '-') position++
            while (position < text.length && (text[position].isDigit() || text[position] in ".eE+-")) {
                position++
            }
            val raw = text.substring(start, position)
            val value = raw.toDoubleOrNull() ?: throw JsonParseException("Bad number '$raw' at $start")
            return JsonValue.Num(value)
        }

        private fun readBoolean(): JsonValue.Bool {
            return when {
                text.startsWith("true", position) -> {
                    position += 4
                    JsonValue.Bool(true)
                }

                text.startsWith("false", position) -> {
                    position += 5
                    JsonValue.Bool(false)
                }

                else -> throw JsonParseException("Bad literal at $position")
            }
        }

        private fun readNull(): JsonValue {
            if (!text.startsWith("null", position)) throw JsonParseException("Bad literal at $position")
            position += 4
            return JsonValue.Null
        }

        private fun peek(): Char =
            if (position < text.length) text[position] else throw JsonParseException("Unexpected end of input")

        private fun expect(character: Char) {
            if (peek() != character) throw JsonParseException("Expected '$character' at $position")
            position++
        }

        private fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }
    }
}
