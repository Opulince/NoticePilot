package com.intraprenuers.noticepilot.extract

import com.intraprenuers.noticepilot.extract.ExtractionException.Kind
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ExtractionParser {

    private val BLOCKED_REASONS = setOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")

    fun parse(responseBody: String): List<ExtractedItem> {
        val root = try {
            JSONObject(responseBody)
        } catch (e: JSONException) {
            throw ExtractionException(Kind.BAD_JSON, "Response body is not JSON", e)
        }

        val blockReason = optStringOrNull(root.optJSONObject("promptFeedback"), "blockReason")
        if (blockReason != null) throw ExtractionException(Kind.BLOCKED, "Prompt blocked: $blockReason")

        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw ExtractionException(Kind.EMPTY, "No candidates in response")

        val text = candidateText(candidate)
        if (text.isBlank()) {
            val finish = optStringOrNull(candidate, "finishReason")
            if (finish != null && finish in BLOCKED_REASONS) {
                throw ExtractionException(Kind.BLOCKED, "Response blocked: $finish")
            }
            throw ExtractionException(Kind.EMPTY, "Empty response text")
        }

        val itemsArray = parseItemsArray(stripFence(text))
        val result = ArrayList<ExtractedItem>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.optJSONObject(i) ?: continue
            toItem(obj)?.let { result.add(it) }
        }
        return result
    }

    private fun candidateText(candidate: JSONObject): String {
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            val t = optStringOrNull(part, "text") ?: continue
            sb.append(t)
        }
        return sb.toString()
    }

    private fun stripFence(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trimStart()
            s = s.removeSuffix("```").trimEnd()
        }
        return s
    }

    private fun parseItemsArray(text: String): JSONArray {
        try {
            if (text.startsWith("[")) return JSONArray(text)
            val obj = JSONObject(text)
            return obj.optJSONArray("items")
                ?: throw ExtractionException(Kind.BAD_JSON, "JSON has no items array")
        } catch (e: JSONException) {
            throw ExtractionException(Kind.BAD_JSON, "Model output is not valid JSON", e)
        }
    }

    private fun toItem(obj: JSONObject): ExtractedItem? {
        val title = optStringOrNull(obj, "title") ?: return null
        val type = (optStringOrNull(obj, "type") ?: "info").lowercase()
            .let { if (it in setOf("task", "event", "deadline", "info")) it else "info" }
        return ExtractedItem(
            type = type,
            title = title,
            date = optStringOrNull(obj, "date"),
            endDate = optStringOrNull(obj, "end_date"),
            time = optStringOrNull(obj, "time"),
            conditions = stringList(obj, "conditions"),
            lineIds = stringList(obj, "line_ids").map { normalizeLineId(it) },
            quote = if (obj.isNull("quote")) "" else obj.optString("quote", "").trim(),
            unclearReason = optStringOrNull(obj, "unclear_reason")
        )
    }

    private fun normalizeLineId(raw: String): String {
        val id = raw.trim().uppercase()
        return if (id.isNotEmpty() && id.all { it.isDigit() }) "L$id" else id
    }

    private fun stringList(obj: JSONObject, key: String): List<String> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            if (arr.isNull(i)) continue
            val s = arr.opt(i)?.toString()?.trim().orEmpty()
            if (s.isNotEmpty() && s != "null") out.add(s)
        }
        return out
    }

    /** org.json's optString returns "null" for JSON null; this returns null for missing, null, blank or "null". */
    internal fun optStringOrNull(obj: JSONObject?, key: String): String? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        val s = obj.opt(key)?.toString()?.trim() ?: return null
        return if (s.isEmpty() || s.equals("null", ignoreCase = true)) null else s
    }
}
