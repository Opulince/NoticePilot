package com.intraprenuers.noticepilot.extract

import org.json.JSONArray
import org.json.JSONObject

object ExtractionSchema {

    private fun str(description: String) = JSONObject().put("type", "STRING").put("description", description)

    private fun strList(description: String) =
        JSONObject().put("type", "ARRAY").put("description", description).put("items", JSONObject().put("type", "STRING"))

    fun responseSchema(): JSONObject {
        val props = JSONObject()
            .put(
                "type", JSONObject().put("type", "STRING")
                    .put("enum", JSONArray(listOf("task", "event", "deadline", "info")))
                    .put("description", "task, event, deadline or info")
            )
            .put("title", str("Short title of the item"))
            .put("date", str("ISO yyyy-MM-dd"))
            .put("end_date", str("ISO yyyy-MM-dd, only for ranges"))
            .put("time", str("HH:mm 24-hour"))
            .put("conditions", strList("Eligibility rules, fees, required documents; empty if none stated"))
            .put("line_ids", strList("Ids of the lines the quote comes from, e.g. L12"))
            .put("quote", str("Exact text copied from the lines, shortest span with the fact and its date"))
            .put("unclear_reason", str("Plain-words reason if year is missing or the date is ambiguous"))
        val item = JSONObject()
            .put("type", "OBJECT")
            .put("properties", props)
            .put("required", JSONArray(listOf("type", "title", "conditions", "line_ids", "quote")))
            .put(
                "propertyOrdering",
                JSONArray(listOf("type", "title", "date", "end_date", "time", "conditions", "line_ids", "quote", "unclear_reason"))
            )
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject().put("items", JSONObject().put("type", "ARRAY").put("items", item)))
            .put("required", JSONArray(listOf("items")))
    }
}
