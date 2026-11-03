package com.intraprenuers.noticepilot.storage

import com.intraprenuers.noticepilot.extract.ExtractedItem
import com.intraprenuers.noticepilot.ocr.OcrLine
import com.intraprenuers.noticepilot.ui.ResultItem
import com.intraprenuers.noticepilot.verify.CheckedItem
import com.intraprenuers.noticepilot.verify.Status
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved notices <-> JSON (org.json). Bounding boxes are not saved (nothing uses them after the scan).
 * Decoding is defensive: a broken notice is skipped, never crashes the app.
 */
object NoticeJson {

    fun encode(notices: List<SavedNotice>): String {
        val arr = JSONArray()
        for (n in notices) arr.put(encodeNotice(n))
        return JSONObject().put("version", 1).put("notices", arr).toString()
    }

    fun decode(text: String): List<SavedNotice> {
        val arr = try {
            JSONObject(text).optJSONArray("notices")
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val out = ArrayList<SavedNotice>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            try {
                out += decodeNotice(obj)
            } catch (e: Exception) {
                // skip a corrupt entry
            }
        }
        return out
    }

    private fun encodeNotice(n: SavedNotice): JSONObject {
        val lines = JSONArray()
        for (l in n.lines) lines.put(JSONObject().put("id", l.id).put("text", l.text).put("page", l.page))
        val items = JSONArray()
        for (r in n.items) {
            val o = JSONObject()
                .put("id", r.id)
                .put("item", encodeItem(r.checked.item))
                .put("status", r.checked.status.name)
                .put("reasons", JSONArray(r.checked.reasons))
                .put("confirmed", r.confirmed)
            val edited = r.edited
            if (edited != null) o.put("edited", encodeItem(edited))
            items.put(o)
        }
        return JSONObject().put("id", n.id).put("savedAt", n.savedAt).put("lines", lines).put("items", items)
    }

    private fun decodeNotice(o: JSONObject): SavedNotice {
        val linesArr = o.getJSONArray("lines")
        val lines = (0 until linesArr.length()).map { i ->
            val l = linesArr.getJSONObject(i)
            OcrLine(id = l.getString("id"), text = l.getString("text"), box = null, page = l.optInt("page", 1))
        }
        val itemsArr = o.getJSONArray("items")
        val items = (0 until itemsArr.length()).map { i ->
            val r = itemsArr.getJSONObject(i)
            val status = try {
                Status.valueOf(r.getString("status"))
            } catch (e: IllegalArgumentException) {
                Status.NEEDS_CHECK
            }
            ResultItem(
                id = r.getInt("id"),
                checked = CheckedItem(decodeItem(r.getJSONObject("item")), status, strings(r.optJSONArray("reasons"))),
                edited = r.optJSONObject("edited")?.let { decodeItem(it) },
                confirmed = r.optBoolean("confirmed", false)
            )
        }
        return SavedNotice(id = o.getLong("id"), savedAt = o.optLong("savedAt", o.getLong("id")), lines = lines, items = items)
    }

    private fun encodeItem(it: ExtractedItem): JSONObject = JSONObject()
        .put("type", it.type)
        .put("title", it.title)
        .putOpt("date", it.date)
        .putOpt("endDate", it.endDate)
        .putOpt("time", it.time)
        .put("conditions", JSONArray(it.conditions))
        .put("lineIds", JSONArray(it.lineIds))
        .put("quote", it.quote)
        .putOpt("unclearReason", it.unclearReason)

    private fun decodeItem(o: JSONObject) = ExtractedItem(
        type = o.optString("type", "info"),
        title = o.getString("title"),
        date = str(o, "date"),
        endDate = str(o, "endDate"),
        time = str(o, "time"),
        conditions = strings(o.optJSONArray("conditions")),
        lineIds = strings(o.optJSONArray("lineIds")),
        quote = o.optString("quote", ""),
        unclearReason = str(o, "unclearReason")
    )

    /** null for missing or JSON null (org.json's optString would give "null"). */
    private fun str(o: JSONObject, key: String): String? =
        if (!o.has(key) || o.isNull(key)) null else o.getString(key)

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> if (arr.isNull(i)) null else arr.getString(i) }
    }
}
