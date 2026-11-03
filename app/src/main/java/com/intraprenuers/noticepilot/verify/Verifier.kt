package com.intraprenuers.noticepilot.verify

import com.intraprenuers.noticepilot.extract.ExtractedItem
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Checks every extracted item against the OCR text Gemini did not produce. Pure Kotlin. */
object Verifier {
    const val FUZZY_THRESHOLD = 0.85

    private enum class Match { EXACT, FUZZY, NONE }

    fun verifyAll(items: List<ExtractedItem>, lines: List<SourceLine>): List<CheckedItem> =
        items.map { verify(it, lines) }

    fun verify(item: ExtractedItem, lines: List<SourceLine>): CheckedItem {
        return try {
            check(item, lines)
        } catch (e: Exception) {
            CheckedItem(item, Status.NEEDS_CHECK, listOf("could not verify this item"))
        }
    }

    private fun check(item: ExtractedItem, lines: List<SourceLine>): CheckedItem {
        var status = Status.VERIFIED
        val reasons = LinkedHashSet<String>()
        fun flag(s: Status, reason: String?) {
            if (s.ordinal > status.ordinal) status = s
            if (reason != null) reasons.add(reason)
        }

        val quote = Normalizer.normalize(item.quote)

        // 1. Quote check
        if (quote.isEmpty()) {
            flag(Status.NEEDS_CHECK, "quote is empty")
        } else {
            val citedKeys = item.lineIds.map { idKey(it) }.toSet()
            val cited = lines.filter { idKey(it.id) in citedKeys }
                .map { Normalizer.normalize(it.text) }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            val inCited = if (cited.isEmpty()) Match.NONE else match(quote, cited)
            when (inCited) {
                Match.EXACT -> {}
                Match.FUZZY -> flag(Status.APPROXIMATE, "quote only approximately matches the scanned text")
                Match.NONE -> {
                    val all = lines.map { Normalizer.normalize(it.text) }
                        .filter { it.isNotEmpty() }
                        .joinToString(" ")
                    if (match(quote, all) != Match.NONE) {
                        flag(Status.APPROXIMATE, "cited the wrong line")
                    } else {
                        flag(Status.NEEDS_CHECK, "quote not found in scanned text")
                    }
                }
            }
        }

        // 2. Date checks, against the dates found in the quote
        val quoteDates = DateParser.findDates(quote)
        checkDate(item.date, quoteDates, "")?.let { flag(Status.NEEDS_CHECK, it) }
        checkDate(item.endDate, quoteDates, "end ")?.let { flag(Status.NEEDS_CHECK, it) }

        // 3. Gemini's own doubts
        val unclear = item.unclearReason
        if (unclear != null && unclear.isNotBlank()) flag(Status.NEEDS_CHECK, unclear)

        return CheckedItem(item, status, reasons.toList())
    }

    /** Returns a reason when the date is not supported by the quote, null when it is fine or absent. */
    private fun checkDate(value: String?, quoteDates: List<DateParser.ParsedDate>, prefix: String): String? {
        if (value == null || value.isBlank()) return null
        val date = try {
            LocalDate.parse(value.trim())
        } catch (e: DateTimeParseException) {
            return "${prefix}date is not a valid date"
        }
        if (quoteDates.any { it.year == date.year && it.month == date.monthValue && it.day == date.dayOfMonth }) {
            return null
        }
        if (quoteDates.any { it.year == null && it.month == date.monthValue && it.day == date.dayOfMonth }) {
            return "year missing in source"
        }
        return if (quoteDates.isNotEmpty()) "${prefix}date doesn't match source" else "${prefix}date not found in quote"
    }

    /** "l12", " L12 " and "12" all mean line L12. */
    private fun idKey(id: String): String {
        val t = id.trim().uppercase()
        return if (t.isNotEmpty() && t.all { it.isDigit() }) "L$t" else t
    }

    private fun match(quote: String, text: String): Match {
        if (text.contains(quote)) return Match.EXACT
        return if (similarity(quote, text) >= FUZZY_THRESHOLD) Match.FUZZY else Match.NONE
    }

    /** 1 - (smallest edit distance between the quote and any substring of text) / quote length. */
    private fun similarity(quote: String, text: String): Double {
        if (quote.isEmpty()) return 0.0
        val m = quote.length
        var prev = IntArray(text.length + 1) // row 0: a match may start anywhere, so all zeros
        var cur = IntArray(text.length + 1)
        for (i in 1..m) {
            cur[0] = i
            val qc = quote[i - 1]
            for (j in 1..text.length) {
                val cost = if (qc == text[j - 1]) 0 else 1
                cur[j] = minOf(prev[j - 1] + cost, prev[j] + 1, cur[j - 1] + 1)
            }
            val t = prev; prev = cur; cur = t
        }
        val best = prev.min()
        return (1.0 - best.toDouble() / m).coerceIn(0.0, 1.0)
    }
}
