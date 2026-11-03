package com.intraprenuers.noticepilot.extract

import com.intraprenuers.noticepilot.verify.SourceLine
import org.json.JSONArray
import org.json.JSONObject

object ExtractionPrompt {

    val SYSTEM_INSTRUCTION: String = """
You extract actionable items from a school notice. The notice is given as OCR lines, one per line, formatted "L<n>: text".

Rules:
- Only extract what the text states. Never invent dates, times, fees or conditions. Do not turn vague statements into confirmed events or deadlines.
- type: "task" = something the reader must do; "event" = something happening on a date; "deadline" = a last date to do something; "info" = an important fact without an action.
- quote: copy it character-for-character from the given lines (without the "L12: " prefix). Use the shortest span that contains the fact, including the date if there is one. line_ids: the ids of the lines the quote comes from.
- Dates in these documents are day-first (DD/MM/YYYY). Output ISO yyyy-MM-dd. Output time as HH:mm, 24-hour, only if it is stated.
- If the year is missing, do not invent one: still give your best reading of the date, but fill unclear_reason in plain words (for example "year not stated"). Do the same if it is unclear which instruction a date belongs to.
- conditions: eligibility rules, fees, required documents, who it applies to. Only if stated; otherwise an empty list.
- Skip marketing text, contact details, greetings and signatures.
- If there is nothing actionable, return {"items": []}.
""".trim()

    fun formatLines(lines: List<SourceLine>): String = lines.joinToString("\n") { "${it.id}: ${it.text}" }

    private fun parts(text: String) = JSONArray().put(JSONObject().put("text", text))

    fun buildRequestBody(lines: List<SourceLine>): JSONObject = JSONObject()
        .put("systemInstruction", JSONObject().put("parts", parts(SYSTEM_INSTRUCTION)))
        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts(formatLines(lines)))))
        .put(
            "generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", ExtractionSchema.responseSchema())
        )
}
