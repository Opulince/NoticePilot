package com.intraprenuers.noticepilot.extract

import com.intraprenuers.noticepilot.extract.ExtractionException.Kind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ExtractionParserTest {

    private fun response(vararg texts: String, thought: Set<Int> = emptySet()): String {
        val parts = JSONArray()
        texts.forEachIndexed { i, t ->
            val p = JSONObject().put("text", t)
            if (i in thought) p.put("thought", true)
            parts.put(p)
        }
        return JSONObject().put(
            "candidates",
            JSONArray().put(JSONObject().put("content", JSONObject().put("parts", parts)).put("finishReason", "STOP"))
        ).toString()
    }

    private fun kindOf(body: String): Kind {
        try {
            ExtractionParser.parse(body)
        } catch (e: ExtractionException) {
            return e.kind
        }
        fail("expected ExtractionException")
        throw IllegalStateException()
    }

    private val twoItems = """{"items":[
        {"type":"event","title":"Sports day","date":"2026-10-26","time":"09:30","conditions":["Wear PE kit"],"line_ids":["L3"],"quote":"Sports day on 26/10/2026"},
        {"type":"deadline","title":"Fee payment","date":"2026-11-05","end_date":"2026-11-10","conditions":[],"line_ids":["L5","L6"],"quote":"pay by 5 Nov","unclear_reason":"year not stated"}
    ]}"""

    @Test fun validResponseWithTwoItems() {
        val items = ExtractionParser.parse(response(twoItems))
        assertEquals(2, items.size)
        assertEquals("event", items[0].type)
        assertEquals("Sports day", items[0].title)
        assertEquals("2026-10-26", items[0].date)
        assertEquals("09:30", items[0].time)
        assertEquals(listOf("Wear PE kit"), items[0].conditions)
        assertEquals(listOf("L3"), items[0].lineIds)
        assertEquals("2026-11-10", items[1].endDate)
        assertEquals(listOf("L5", "L6"), items[1].lineIds)
        assertEquals("year not stated", items[1].unclearReason)
    }

    @Test fun jsonNullsBecomeKotlinNull() {
        val json = """{"items":[{"type":"task","title":"Bring form","date":null,"end_date":null,"time":null,"conditions":[],"line_ids":["L1"],"quote":"q","unclear_reason":null}]}"""
        val item = ExtractionParser.parse(response(json)).single()
        assertNull(item.date); assertNull(item.endDate); assertNull(item.time); assertNull(item.unclearReason)
    }

    @Test fun literalNullStringAndBlankBecomeNull() {
        val json = """{"items":[{"type":"task","title":"T","date":"null","time":"  ","conditions":[],"line_ids":[],"quote":"q"}]}"""
        val item = ExtractionParser.parse(response(json)).single()
        assertNull(item.date); assertNull(item.time); assertNull(item.unclearReason)
    }

    @Test fun fencedJsonIsAccepted() {
        val fenced = "```json\n$twoItems\n```"
        assertEquals(2, ExtractionParser.parse(response(fenced)).size)
    }

    @Test fun topLevelArrayIsAccepted() {
        val arr = """[{"type":"info","title":"Uniform","conditions":[],"line_ids":["L2"],"quote":"q"}]"""
        assertEquals("Uniform", ExtractionParser.parse(response(arr)).single().title)
    }

    @Test fun thoughtPartsAreSkipped() {
        val items = ExtractionParser.parse(response("thinking... not json", twoItems, thought = setOf(0)))
        assertEquals(2, items.size)
    }

    @Test fun emptyItemsListIsFine() {
        assertEquals(0, ExtractionParser.parse(response("""{"items":[]}""")).size)
    }

    @Test fun emptyCandidatesIsEmpty() {
        assertEquals(Kind.EMPTY, kindOf("""{"candidates":[]}"""))
        assertEquals(Kind.EMPTY, kindOf("""{}"""))
    }

    @Test fun blankTextIsEmpty() {
        assertEquals(Kind.EMPTY, kindOf(response("   ")))
    }

    @Test fun blockedPrompt() {
        assertEquals(Kind.BLOCKED, kindOf("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
    }

    @Test fun safetyFinishWithoutTextIsBlocked() {
        val body = """{"candidates":[{"finishReason":"SAFETY"}]}"""
        assertEquals(Kind.BLOCKED, kindOf(body))
    }

    @Test fun malformedInnerJson() {
        assertEquals(Kind.BAD_JSON, kindOf(response("""{"items":[{"title":""")))
    }

    @Test fun nonJsonBody() {
        assertEquals(Kind.BAD_JSON, kindOf("<html>oops</html>"))
    }

    @Test fun itemWithoutTitleIsSkipped() {
        val json = """{"items":[{"type":"task","conditions":[],"line_ids":[],"quote":"q"},{"type":"task","title":"  ","quote":"q"},"junk",{"type":"task","title":"Keep","quote":"q"}]}"""
        val items = ExtractionParser.parse(response(json))
        assertEquals(listOf("Keep"), items.map { it.title })
    }

    @Test fun unknownTypeBecomesInfoAndTypeIsLowercased() {
        val json = """{"items":[{"type":"party","title":"A","quote":"q"},{"type":" EVENT ","title":"B","quote":"q"}]}"""
        val items = ExtractionParser.parse(response(json))
        assertEquals("info", items[0].type)
        assertEquals("event", items[1].type)
    }

    @Test fun lineIdsAreNormalised() {
        val json = """{"items":[{"type":"task","title":"A","line_ids":["12"," l7 ","L3"],"quote":"q"}]}"""
        assertEquals(listOf("L12", "L7", "L3"), ExtractionParser.parse(response(json)).single().lineIds)
    }
}
