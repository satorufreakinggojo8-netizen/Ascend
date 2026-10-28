package com.cadence.fit.domain.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun `integers are written without a decimal part`() {
        assertEquals("42", Json.write(JsonValue.of(42)))
        assertEquals("0", Json.write(JsonValue.of(0)))
    }

    @Test
    fun `fractional numbers keep their precision`() {
        assertEquals("62.5", Json.write(JsonValue.of(62.5)))
        assertEquals("1.25", Json.write(JsonValue.of(1.25)))
    }

    @Test
    fun `strings are escaped`() {
        val written = Json.write(JsonValue.of("line\n\"quoted\"\\back\ttab"))
        assertEquals("\"line\\n\\\"quoted\\\"\\\\back\\ttab\"", written)
        assertEquals("line\n\"quoted\"\\back\ttab", (Json.parse(written) as JsonValue.Str).value)
    }

    @Test
    fun `unicode escapes are parsed`() {
        val parsed = Json.parse("\"caf\\u00e9\"") as JsonValue.Str
        assertEquals("café", parsed.value)
    }

    @Test
    fun `nested documents round trip`() {
        val original = JsonValue.ofObj(
            "name" to JsonValue.of("Cadence"),
            "count" to JsonValue.of(3),
            "enabled" to JsonValue.of(true),
            "missing" to JsonValue.Null,
            "tags" to JsonValue.ofStrings(listOf("a", "b")),
            "nested" to JsonValue.ofObj("depth" to JsonValue.of(2)),
            "items" to JsonValue.ofArray(
                listOf(
                    JsonValue.ofObj("id" to JsonValue.of("one"), "value" to JsonValue.of(1.5)),
                    JsonValue.ofObj("id" to JsonValue.of("two"), "value" to JsonValue.of(2)),
                ),
            ),
        )

        val reparsed = Json.parse(Json.write(original))
        assertEquals(original, reparsed)
    }

    @Test
    fun `compact output contains no newlines`() {
        val value = JsonValue.ofObj("a" to JsonValue.of(1))
        assertEquals("{\"a\":1}", Json.write(value, pretty = false))
        assertTrue(Json.write(value, pretty = true).contains("\n"))
    }

    @Test
    fun `empty containers round trip`() {
        assertEquals(JsonValue.Obj(emptyMap()), Json.parse(Json.write(JsonValue.Obj(emptyMap()))))
        assertEquals(JsonValue.Arr(emptyList()), Json.parse(Json.write(JsonValue.Arr(emptyList()))))
    }

    @Test
    fun `object accessors return null for the wrong type`() {
        val obj = Json.parse("""{"s":"text","n":42,"b":true,"o":{},"a":[]}""") as JsonValue.Obj

        assertEquals("text", obj.string("s"))
        assertEquals(42L, obj.long("n"))
        assertEquals(42, obj.int("n")!!)
        assertEquals(42.0, obj.double("n")!!, 1e-9)
        assertEquals(true, obj.bool("b"))
        assertEquals("", obj.obj("o")!!.entries.keys.joinToString(""))
        assertTrue(obj.array("a")!!.items.isEmpty())
        assertNull(obj.string("n"))
        assertNull(obj.long("s"))
        assertNull(obj.bool("missing"))
    }

    @Test
    fun `negative and exponent numbers parse`() {
        assertEquals(-12.5, (Json.parse("-12.5") as JsonValue.Num).value, 1e-9)
        assertEquals(1000.0, (Json.parse("1e3") as JsonValue.Num).value, 1e-9)
    }

    @Test
    fun `trailing content is rejected`() {
        assertThrows { Json.parse("{}[]") }
    }

    @Test
    fun `an unterminated string is rejected`() {
        assertThrows { Json.parse("\"unfinished") }
    }

    @Test
    fun `an unknown literal is rejected`() {
        assertThrows { Json.parse("nul") }
        assertThrows { Json.parse("tru") }
    }

    @Test
    fun `missing separators are rejected`() {
        assertThrows { Json.parse("{\"a\":1 \"b\":2}") }
        assertThrows { Json.parse("[1 2]") }
    }

    private fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected a JsonParseException")
        } catch (expected: JsonParseException) {
            // expected
        }
    }
}
