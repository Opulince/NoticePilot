package com.intraprenuers.noticepilot.extract

import com.intraprenuers.noticepilot.verify.SourceLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionPromptTest {
    private val lines = listOf(SourceLine("L1", "Dear parents"), SourceLine("L2", "Trip on 26/10/2026"))

    @Test fun formatLinesExact() {
        assertEquals("L1: Dear parents\nL2: Trip on 26/10/2026", ExtractionPrompt.formatLines(lines))
    }

    @Test fun requestBodyShape() {
        val body = ExtractionPrompt.buildRequestBody(lines)
        assertEquals(
            ExtractionPrompt.SYSTEM_INSTRUCTION,
            body.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        )
        val content = body.getJSONArray("contents").getJSONObject(0)
        assertEquals("user", content.getString("role"))
        assertEquals(
            "L1: Dear parents\nL2: Trip on 26/10/2026",
            content.getJSONArray("parts").getJSONObject(0).getString("text")
        )
        assertEquals("application/json", body.getJSONObject("generationConfig").getString("responseMimeType"))
    }

    @Test fun schemaRequiredExcludesOptionalFields() {
        val schema = ExtractionPrompt.buildRequestBody(lines)
            .getJSONObject("generationConfig").getJSONObject("responseSchema")
        assertEquals("items", schema.getJSONArray("required").getString(0))
        val item = schema.getJSONObject("properties").getJSONObject("items").getJSONObject("items")
        val required = (0 until item.getJSONArray("required").length()).map { item.getJSONArray("required").getString(it) }
        assertEquals(listOf("type", "title", "conditions", "line_ids", "quote"), required)
        for (optional in listOf("date", "end_date", "time", "unclear_reason")) {
            assertFalse(optional in required)
            assertTrue(item.getJSONObject("properties").has(optional))
        }
        assertEquals(9, item.getJSONArray("propertyOrdering").length())
    }
}
