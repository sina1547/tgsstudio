package ir.sina.tgsstudio.document

import ir.sina.tgsstudio.document.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonTest {
    @Test fun keepsIntegerAndDoubleDistinct() {
        val o = JsonParser.parse("""{"a":1,"b":1.0,"c":-2,"d":1e-05,"e":9223372036854775808}""") as JsonObj
        assertEquals(JsonNum(1L), o["a"])
        assertEquals(JsonNum(1.0), o["b"])
        assertEquals(JsonNum(-2L), o["c"])
        assertEquals(JsonNum(1e-5), o["d"])
        assertTrue((o["e"] as JsonNum).value is Double) // too big for Long
    }

    @Test fun preservesKeyOrder() {
        val text = """{"z":1,"a":2,"m":{"y":[],"b":{}}}"""
        assertEquals(text, JsonWriter.write(JsonParser.parse(text)))
    }

    @Test fun handlesUnicodeAndEscapes() {
        val o = JsonParser.parse("""{"nm":"\u0633\u0644\u0627\u0645 \"q\"\n\ud83d\ude00"}""") as JsonObj
        assertEquals("سلام \"q\"\n😀", o.string("nm"))
        val again = JsonParser.parse(JsonWriter.write(o)) as JsonObj
        assertEquals(o, again)
    }

    @Test fun rejectsBadInput() {
        assertFailsWith<JsonParseException> { JsonParser.parse("""{"a":1,}""") }
        assertFailsWith<JsonParseException> { JsonParser.parse("""[1,2""") }
        assertFailsWith<JsonParseException> { JsonParser.parse("""{"a":1} x""") }
        assertFailsWith<JsonParseException> { JsonParser.parse("[".repeat(5000)) }
    }

    @Test fun refusesToWriteNonFiniteNumbers() {
        assertFailsWith<IllegalArgumentException> { JsonWriter.write(JsonNum(Double.NaN)) }
    }

    @Test fun deepCopyIsIndependent() {
        val a = JsonParser.parse("""{"x":[1,{"y":2}]}""") as JsonObj
        val b = a.deepCopy() as JsonObj
        (b.arr("x")!!.items[1] as JsonObj).putInt("y", 3)
        assertEquals(2, ((a.arr("x")!!.items[1]) as JsonObj).int("y"))
    }
}
